package com.example.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity

private val ObsidianWidgetBg = Color(0xFF0A0F1D)
private val ObsidianCardSurface = Color(0xFF131C31)
private val ObsidianButtonSurface = Color(0xFF1A2540)
private val EmeraldNeon = Color(0xFF34D399)
private val EmeraldDeep = Color(0xFF064E3B)
private val RoseNeon = Color(0xFFF43F5E)
private val RoseDeep = Color(0xFF4C0519)
private val IndigoNeon = Color(0xFF818CF8)
private val AmberNeon = Color(0xFFFBBF24)
private val TextPrimaryWhite = Color(0xFFF8FAFC)
private val TextSecondarySlate = Color(0xFF94A3B8)

private val ActionAppWidgetIdKey = ActionParameters.Key<Int>("david_app_widget_id")
private val ActionPeriodCodeKey = ActionParameters.Key<String>("david_period_code")

private fun createMainActivityIntent(
    context: Context,
    widgetAction: String,
    goalId: String? = null
): Intent {
    return Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(WidgetActions.EXTRA_WIDGET_ACTION, widgetAction)
        if (!goalId.isNullOrBlank()) {
            putExtra(WidgetActions.EXTRA_WIDGET_GOAL_ID, goalId)
        }
    }
}

private fun resolveAppWidgetId(context: Context, glanceId: GlanceId): Int {
    return try {
        GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
    } catch (_: Exception) {
        AppWidgetManager.INVALID_APPWIDGET_ID
    }
}

class ToggleWidgetPrivacyAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appWidgetId = parameters[ActionAppWidgetIdKey] ?: resolveAppWidgetId(context, glanceId)
        WidgetPreferencesStore.togglePrivacyMode(context, appWidgetId)
        DavidWidgetsUpdater.updateAllSuspend(context)
    }
}

class SwitchMiniAnalyticsPeriodAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appWidgetId = parameters[ActionAppWidgetIdKey] ?: resolveAppWidgetId(context, glanceId)
        val requestedCode = parameters[ActionPeriodCodeKey]
        val nextPeriod = if (!requestedCode.isNullOrBlank()) {
            MiniAnalyticsPeriod.fromCode(requestedCode)
        } else {
            WidgetPreferencesStore.getAnalyticsPeriod(context, appWidgetId).next()
        }
        WidgetPreferencesStore.setAnalyticsPeriod(context, appWidgetId, nextPeriod)
        MiniAnalyticsWidget().update(context, glanceId)
    }
}

// =========================================================================
// 1. ВИДЖЕТ «ДАВИД СЕГОДНЯ» (2x2 и 4x2)
// =========================================================================
class DavidTodayWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp), // 2x2
            DpSize(250.dp, 110.dp)  // 4x2
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = resolveAppWidgetId(context, id)
        val isPrivacy = WidgetPreferencesStore.isPrivacyMode(context, appWidgetId)
        val snapshot = WidgetDataLoader.loadSnapshot(context)
        val model = DavidWidgetStateCalculator.computeTodayState(
            transactions = snapshot.transactions,
            categories = snapshot.categories,
            todayIso = snapshot.todayIso,
            explicitMonthlyLimit = snapshot.explicitMonthlyLimit,
            isPrivacyMode = isPrivacy
        )
        val frogBitmap = DavidFrogBitmapRenderer.renderFrogAvatarBitmap(
            mood = model.mood,
            sizePx = 144,
            withAuraBadge = true
        )

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val isWide = size.width >= 210.dp
                DavidTodayWidgetContent(
                    context = context,
                    appWidgetId = appWidgetId,
                    model = model,
                    frogBitmap = frogBitmap,
                    isWide = isWide
                )
            }
        }
    }
}

@Composable
private fun DavidTodayWidgetContent(
    context: Context,
    appWidgetId: Int,
    model: DavidTodayWidgetModel,
    frogBitmap: android.graphics.Bitmap,
    isWide: Boolean
) {
    val openIntent = createMainActivityIntent(context, WidgetActions.ACTION_OPEN_TODAY)
    val expenseStr = DavidWidgetStateCalculator.formatWidgetCurrency(
        amount = model.todayExpense,
        isPrivacyMode = model.isPrivacyMode,
        showSign = if (model.todayExpense > 0) "-" else ""
    )
    val remainingLabel = when {
        model.dailyBudgetLimit == null -> "Лимит не задан"
        model.isDailyBudgetExceeded -> {
            val over = kotlin.math.abs(model.remainingDailyBudget ?: 0.0)
            "Перерасход ${DavidWidgetStateCalculator.formatCompactCurrency(over, model.isPrivacyMode)}"
        }
        else -> {
            val rem = (model.remainingDailyBudget ?: 0.0).coerceAtLeast(0.0)
            "На день: ${DavidWidgetStateCalculator.formatCompactCurrency(rem, model.isPrivacyMode)}"
        }
    }
    val remainingColor = when {
        model.dailyBudgetLimit == null -> TextSecondarySlate
        model.isDailyBudgetExceeded -> RoseNeon
        else -> EmeraldNeon
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ObsidianWidgetBg)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity(openIntent))
            .padding(12.dp)
    ) {
        if (isWide) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(frogBitmap),
                    contentDescription = "Давид Жабов",
                    modifier = GlanceModifier.size(64.dp)
                )
                Spacer(modifier = GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ДАВИД СЕГОДНЯ",
                            style = TextStyle(
                                color = ColorProvider(EmeraldNeon),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        PrivacyEyePill(appWidgetId = appWidgetId, isPrivacy = model.isPrivacyMode)
                    }
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = expenseStr,
                            style = TextStyle(
                                color = ColorProvider(TextPrimaryWhite),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            text = "• $remainingLabel",
                            style = TextStyle(
                                color = ColorProvider(remainingColor),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = model.davidComment,
                        style = TextStyle(
                            color = ColorProvider(TextSecondarySlate),
                            fontSize = 11.sp
                        ),
                        maxLines = 2
                    )
                }
            }
        } else {
            Column(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(frogBitmap),
                        contentDescription = "Давид Жабов",
                        modifier = GlanceModifier.size(40.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = "СЕГОДНЯ",
                            style = TextStyle(
                                color = ColorProvider(EmeraldNeon),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = expenseStr,
                            style = TextStyle(
                                color = ColorProvider(TextPrimaryWhite),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                    PrivacyEyePill(appWidgetId = appWidgetId, isPrivacy = model.isPrivacyMode)
                }
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = remainingLabel,
                    style = TextStyle(
                        color = ColorProvider(remainingColor),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = model.davidComment,
                    style = TextStyle(
                        color = ColorProvider(TextSecondarySlate),
                        fontSize = 10.sp
                    ),
                    maxLines = 2
                )
            }
        }
    }
}

class DavidTodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DavidTodayWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetPreferencesStore.clearWidgetInstance(context, it) }
    }
}

// =========================================================================
// 2. ВИДЖЕТ «БЫСТРАЯ ОПЕРАЦИЯ» (2x1 и 4x1)
// =========================================================================
class QuickOperationWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 55.dp), // 2x1
            DpSize(250.dp, 55.dp)  // 4x1
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = resolveAppWidgetId(context, id)
        val isPrivacy = WidgetPreferencesStore.isPrivacyMode(context, appWidgetId)
        val snapshot = WidgetDataLoader.loadSnapshot(context)
        val model = DavidWidgetStateCalculator.computeQuickOperationState(
            transactions = snapshot.transactions,
            todayIso = snapshot.todayIso,
            isPrivacyMode = isPrivacy
        )
        val frogBitmap = DavidFrogBitmapRenderer.renderFrogAvatarBitmap(
            mood = ZhabometerMood.CALM,
            sizePx = 96,
            withAuraBadge = true
        )

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val isWide = size.width >= 210.dp
                QuickOperationWidgetContent(
                    context = context,
                    model = model,
                    frogBitmap = frogBitmap,
                    isWide = isWide
                )
            }
        }
    }
}

@Composable
private fun QuickOperationWidgetContent(
    context: Context,
    model: QuickOperationWidgetModel,
    frogBitmap: android.graphics.Bitmap,
    isWide: Boolean
) {
    val addExpenseIntent = createMainActivityIntent(context, WidgetActions.ACTION_ADD_EXPENSE)
    val addIncomeIntent = createMainActivityIntent(context, WidgetActions.ACTION_ADD_INCOME)
    val voiceIntent = createMainActivityIntent(context, WidgetActions.ACTION_VOICE_INPUT)
    val openHomeIntent = createMainActivityIntent(context, WidgetActions.ACTION_OPEN_TODAY)

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ObsidianWidgetBg)
            .cornerRadius(20.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isWide) {
            Row(
                modifier = GlanceModifier
                    .clickable(actionStartActivity(openHomeIntent))
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(frogBitmap),
                    contentDescription = "Давид",
                    modifier = GlanceModifier.size(34.dp)
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Column {
                    Text(
                        text = "Давид AI",
                        style = TextStyle(
                            color = ColorProvider(EmeraldNeon),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = DavidWidgetStateCalculator.formatCompactCurrency(
                            model.todayExpense,
                            model.isPrivacyMode
                        ),
                        style = TextStyle(
                            color = ColorProvider(TextSecondarySlate),
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = GlanceModifier.width(4.dp))
        }

        // Expense Button
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .background(RoseDeep)
                .cornerRadius(14.dp)
                .clickable(actionStartActivity(addExpenseIntent))
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isWide) "− Расход" else "−",
                style = TextStyle(
                    color = ColorProvider(RoseNeon),
                    fontSize = if (isWide) 12.sp else 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = GlanceModifier.width(6.dp))

        // Income Button
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .background(EmeraldDeep)
                .cornerRadius(14.dp)
                .clickable(actionStartActivity(addIncomeIntent))
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isWide) "+ Доход" else "+",
                style = TextStyle(
                    color = ColorProvider(EmeraldNeon),
                    fontSize = if (isWide) 12.sp else 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = GlanceModifier.width(6.dp))

        // Voice Input Button
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .background(ObsidianButtonSurface)
                .cornerRadius(14.dp)
                .clickable(actionStartActivity(voiceIntent))
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isWide) "🎙 Голос" else "🎙",
                style = TextStyle(
                    color = ColorProvider(TextPrimaryWhite),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

class QuickOperationWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickOperationWidget()
}

// =========================================================================
// 3. ВИДЖЕТ «МОЙ БЮДЖЕТ» (2x2 и 4x2)
// =========================================================================
class MyBudgetWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp), // 2x2
            DpSize(250.dp, 110.dp)  // 4x2
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = resolveAppWidgetId(context, id)
        val isPrivacy = WidgetPreferencesStore.isPrivacyMode(context, appWidgetId)
        val snapshot = WidgetDataLoader.loadSnapshot(context)
        val model = DavidWidgetStateCalculator.computeBudgetState(
            transactions = snapshot.transactions,
            categories = snapshot.categories,
            todayIso = snapshot.todayIso,
            explicitMonthlyLimit = snapshot.explicitMonthlyLimit,
            isPrivacyMode = isPrivacy
        )
        val barColorHex = when {
            model.isExceeded -> 0xFFF43F5E.toInt()
            model.usageRatio >= 0.8f -> 0xFFFBBF24.toInt()
            else -> 0xFF10B981.toInt()
        }
        val progressBitmap = DavidFrogBitmapRenderer.renderProgressBarBitmap(
            progressRatio = model.usageRatio.coerceIn(0f, 1f),
            primaryColorHex = barColorHex,
            isExceeded = model.isExceeded
        )

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val isWide = size.width >= 210.dp
                MyBudgetWidgetContent(
                    context = context,
                    appWidgetId = appWidgetId,
                    model = model,
                    progressBitmap = progressBitmap,
                    isWide = isWide
                )
            }
        }
    }
}

@Composable
private fun MyBudgetWidgetContent(
    context: Context,
    appWidgetId: Int,
    model: MyBudgetWidgetModel,
    progressBitmap: android.graphics.Bitmap,
    isWide: Boolean
) {
    val openBudgetIntent = createMainActivityIntent(context, WidgetActions.ACTION_OPEN_BUDGET)
    val spentStr = DavidWidgetStateCalculator.formatCompactCurrency(model.monthSpent, model.isPrivacyMode)
    val limitStr = if (model.isBudgetConfigured) {
        DavidWidgetStateCalculator.formatCompactCurrency(model.monthlyBudgetLimit, model.isPrivacyMode)
    } else "Не задан"
    val remOrOverStr = when {
        !model.isBudgetConfigured -> "Задать лимит ›"
        model.isExceeded -> "-${DavidWidgetStateCalculator.formatCompactCurrency(model.overBudgetAmount, model.isPrivacyMode)}"
        else -> DavidWidgetStateCalculator.formatCompactCurrency(model.remainingBudget, model.isPrivacyMode)
    }
    val statusColor = when {
        model.isExceeded -> RoseNeon
        model.usageRatio >= 0.8f -> AmberNeon
        else -> EmeraldNeon
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ObsidianWidgetBg)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity(openBudgetIntent))
            .padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isWide) "МОЙ БЮДЖЕТ • ${model.monthTitleRu.uppercase()}" else "МОЙ БЮДЖЕТ",
                style = TextStyle(
                    color = ColorProvider(EmeraldNeon),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1
            )
            PrivacyEyePill(appWidgetId = appWidgetId, isPrivacy = model.isPrivacyMode)
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        if (isWide) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BudgetMetricColumn(
                    label = "Потрачено",
                    value = spentStr,
                    valueColor = TextPrimaryWhite,
                    modifier = GlanceModifier.defaultWeight()
                )
                BudgetMetricColumn(
                    label = "Лимит",
                    value = limitStr,
                    valueColor = TextSecondarySlate,
                    modifier = GlanceModifier.defaultWeight()
                )
                BudgetMetricColumn(
                    label = if (model.isExceeded) "Перерасход" else "Остаток",
                    value = remOrOverStr,
                    valueColor = statusColor,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        } else {
            Text(
                text = spentStr,
                style = TextStyle(
                    color = ColorProvider(TextPrimaryWhite),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Text(
                text = if (model.isExceeded) "Сверх: $remOrOverStr" else "Остаток: $remOrOverStr",
                style = TextStyle(
                    color = ColorProvider(statusColor),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        Image(
            provider = ImageProvider(progressBitmap),
            contentDescription = "Прогресс бюджета",
            modifier = GlanceModifier.fillMaxWidth().height(8.dp)
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        Text(
            text = model.statusText,
            style = TextStyle(
                color = ColorProvider(if (model.isExceeded) RoseNeon else TextSecondarySlate),
                fontSize = 10.sp,
                fontWeight = if (model.isExceeded) FontWeight.Bold else FontWeight.Normal
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun BudgetMetricColumn(
    label: String,
    value: String,
    valueColor: Color,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(TextSecondarySlate),
                fontSize = 10.sp
            ),
            maxLines = 1
        )
        Text(
            text = value,
            style = TextStyle(
                color = ColorProvider(valueColor),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

class MyBudgetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyBudgetWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetPreferencesStore.clearWidgetInstance(context, it) }
    }
}

// =========================================================================
// 4. ВИДЖЕТ «ФИНАНСОВАЯ ЦЕЛЬ» (2x2 и 4x2) + per-instance выбор цели
// =========================================================================
class FinancialGoalWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp), // 2x2
            DpSize(250.dp, 110.dp)  // 4x2
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = resolveAppWidgetId(context, id)
        val isPrivacy = WidgetPreferencesStore.isPrivacyMode(context, appWidgetId)
        val selectedGoalId = WidgetPreferencesStore.getSelectedGoalId(context, appWidgetId)
        val snapshot = WidgetDataLoader.loadSnapshot(context)
        val model = DavidWidgetStateCalculator.computeGoalState(
            goals = snapshot.goals,
            selectedGoalId = selectedGoalId,
            isPrivacyMode = isPrivacy
        )
        val progressBitmap = DavidFrogBitmapRenderer.renderProgressBarBitmap(
            progressRatio = model.progressRatio,
            primaryColorHex = 0xFF818CF8.toInt(),
            isExceeded = false
        )

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val isWide = size.width >= 210.dp
                FinancialGoalWidgetContent(
                    context = context,
                    appWidgetId = appWidgetId,
                    model = model,
                    progressBitmap = progressBitmap,
                    isWide = isWide
                )
            }
        }
    }
}

@Composable
private fun FinancialGoalWidgetContent(
    context: Context,
    appWidgetId: Int,
    model: FinancialGoalWidgetModel,
    progressBitmap: android.graphics.Bitmap,
    isWide: Boolean
) {
    val openGoalsIntent = createMainActivityIntent(
        context = context,
        widgetAction = WidgetActions.ACTION_OPEN_GOALS,
        goalId = model.goalId
    )
    val configIntent = Intent(context, GoalWidgetConfigActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ObsidianWidgetBg)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity(openGoalsIntent))
            .padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎯 ЦЕЛЬ • ${model.progressPercent}%",
                style = TextStyle(
                    color = ColorProvider(IndigoNeon),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1
            )
            if (model.totalGoalsCount > 1) {
                Box(
                    modifier = GlanceModifier
                        .background(ObsidianCardSurface)
                        .cornerRadius(8.dp)
                        .clickable(actionStartActivity(configIntent))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Сменить",
                        style = TextStyle(
                            color = ColorProvider(EmeraldNeon),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.width(4.dp))
            }
            PrivacyEyePill(appWidgetId = appWidgetId, isPrivacy = model.isPrivacyMode)
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        Text(
            text = model.goalTitle,
            style = TextStyle(
                color = ColorProvider(TextPrimaryWhite),
                fontSize = if (isWide) 15.sp else 13.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        if (model.isGoalFound) {
            val savedStr = DavidWidgetStateCalculator.formatCompactCurrency(model.savedAmount, model.isPrivacyMode)
            val remStr = DavidWidgetStateCalculator.formatCompactCurrency(model.remainingAmount, model.isPrivacyMode)
            if (isWide) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = "Накоплено: $savedStr",
                        style = TextStyle(
                            color = ColorProvider(EmeraldNeon),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight(),
                        maxLines = 1
                    )
                    Text(
                        text = "Осталось: $remStr",
                        style = TextStyle(
                            color = ColorProvider(TextSecondarySlate),
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    text = "$savedStr (ост. $remStr)",
                    style = TextStyle(
                        color = ColorProvider(EmeraldNeon),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        Image(
            provider = ImageProvider(progressBitmap),
            contentDescription = "Прогресс цели",
            modifier = GlanceModifier.fillMaxWidth().height(8.dp)
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        Text(
            text = model.statusHint,
            style = TextStyle(
                color = ColorProvider(TextSecondarySlate),
                fontSize = 10.sp
            ),
            maxLines = 1
        )
    }
}

class FinancialGoalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FinancialGoalWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetPreferencesStore.clearWidgetInstance(context, it) }
    }
}

// =========================================================================
// 5. ВИДЖЕТ «МИНИ-АНАЛИТИКА» (4x2)
// =========================================================================
class MiniAnalyticsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 110.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = resolveAppWidgetId(context, id)
        val isPrivacy = WidgetPreferencesStore.isPrivacyMode(context, appWidgetId)
        val period = WidgetPreferencesStore.getAnalyticsPeriod(context, appWidgetId)
        val snapshot = WidgetDataLoader.loadSnapshot(context)
        val model = DavidWidgetStateCalculator.computeMiniAnalyticsState(
            transactions = snapshot.transactions,
            period = period,
            todayIso = snapshot.todayIso,
            isPrivacyMode = isPrivacy
        )
        val chartBitmap = DavidFrogBitmapRenderer.renderMiniAnalyticsChartBitmap(
            expensePoints = model.chartExpensePoints,
            incomePoints = model.chartIncomePoints,
            labels = model.chartLabels
        )

        provideContent {
            GlanceTheme {
                MiniAnalyticsWidgetContent(
                    context = context,
                    appWidgetId = appWidgetId,
                    model = model,
                    chartBitmap = chartBitmap
                )
            }
        }
    }
}

@Composable
private fun MiniAnalyticsWidgetContent(
    context: Context,
    appWidgetId: Int,
    model: MiniAnalyticsWidgetModel,
    chartBitmap: android.graphics.Bitmap
) {
    val openAnalyticsIntent = createMainActivityIntent(context, WidgetActions.ACTION_OPEN_ANALYTICS)
    val expStr = DavidWidgetStateCalculator.formatCompactCurrency(model.totalExpense, model.isPrivacyMode)
    val incStr = DavidWidgetStateCalculator.formatCompactCurrency(model.totalIncome, model.isPrivacyMode)

    val deltaText = model.expenseDeltaPercent?.let { pct ->
        val sign = if (pct > 0) "+" else ""
        "${sign}${pct}% к прошл."
    } ?: model.periodSubtitle

    val deltaColor = when {
        model.expenseDeltaPercent == null -> TextSecondarySlate
        model.expenseDeltaPercent <= 0 -> EmeraldNeon
        else -> RoseNeon
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ObsidianWidgetBg)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity(openAnalyticsIntent))
            .padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "МИНИ-АНАЛИТИКА",
                    style = TextStyle(
                        color = ColorProvider(EmeraldNeon),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "−$expStr",
                        style = TextStyle(
                            color = ColorProvider(TextPrimaryWhite),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = "+$incStr",
                        style = TextStyle(
                            color = ColorProvider(EmeraldNeon),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = deltaText,
                        style = TextStyle(
                            color = ColorProvider(deltaColor),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }

            // Period Switcher Pills (Нед / Мес / Год)
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniAnalyticsPeriod.entries.forEach { p ->
                    val isSelected = p == model.period
                    Box(
                        modifier = GlanceModifier
                            .background(if (isSelected) EmeraldDeep else ObsidianCardSurface)
                            .cornerRadius(8.dp)
                            .clickable(
                                actionRunCallback<SwitchMiniAnalyticsPeriodAction>(
                                    actionParametersOf(
                                        ActionAppWidgetIdKey to appWidgetId,
                                        ActionPeriodCodeKey to p.code
                                    )
                                )
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = p.shortLabelRu,
                            style = TextStyle(
                                color = ColorProvider(if (isSelected) EmeraldNeon else TextSecondarySlate),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(3.dp))
                }
                PrivacyEyePill(appWidgetId = appWidgetId, isPrivacy = model.isPrivacyMode)
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        Image(
            provider = ImageProvider(chartBitmap),
            contentDescription = "График мини-аналитики",
            modifier = GlanceModifier.fillMaxWidth().defaultWeight()
        )
    }
}

class MiniAnalyticsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MiniAnalyticsWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetPreferencesStore.clearWidgetInstance(context, it) }
    }
}

// =========================================================================
// 6. ВИДЖЕТ «ЖАБОМЕТР» (2x2 и 4x2)
// =========================================================================
class ZhabometerWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp), // 2x2
            DpSize(250.dp, 110.dp)  // 4x2
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = resolveAppWidgetId(context, id)
        val isPrivacy = WidgetPreferencesStore.isPrivacyMode(context, appWidgetId)
        val snapshot = WidgetDataLoader.loadSnapshot(context)
        val model = DavidWidgetStateCalculator.computeZhabometerState(
            transactions = snapshot.transactions,
            categories = snapshot.categories,
            goals = snapshot.goals,
            todayIso = snapshot.todayIso,
            explicitMonthlyLimit = snapshot.explicitMonthlyLimit,
            isPrivacyMode = isPrivacy
        )
        val frogBitmap = DavidFrogBitmapRenderer.renderFrogAvatarBitmap(
            mood = model.mood,
            sizePx = 152,
            withAuraBadge = true
        )
        val gaugeBitmap = DavidFrogBitmapRenderer.renderZhabometerGaugeBitmap(
            score = model.score,
            mood = model.mood
        )

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val isWide = size.width >= 210.dp
                ZhabometerWidgetContent(
                    context = context,
                    appWidgetId = appWidgetId,
                    model = model,
                    frogBitmap = frogBitmap,
                    gaugeBitmap = gaugeBitmap,
                    isWide = isWide
                )
            }
        }
    }
}

@Composable
private fun ZhabometerWidgetContent(
    context: Context,
    appWidgetId: Int,
    model: ZhabometerWidgetModel,
    frogBitmap: android.graphics.Bitmap,
    gaugeBitmap: android.graphics.Bitmap,
    isWide: Boolean
) {
    val openChatIntent = createMainActivityIntent(context, WidgetActions.ACTION_OPEN_DAVID_CHAT)
    val moodColor = Color(model.mood.primaryColorHex)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ObsidianWidgetBg)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity(openChatIntent))
            .padding(12.dp)
    ) {
        if (isWide) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(frogBitmap),
                    contentDescription = "Жабометр Давида",
                    modifier = GlanceModifier.size(66.dp)
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ЖАБОМЕТР • ${model.mood.badgeText}",
                            style = TextStyle(
                                color = ColorProvider(moodColor),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = GlanceModifier.defaultWeight(),
                            maxLines = 1
                        )
                        PrivacyEyePill(appWidgetId = appWidgetId, isPrivacy = model.isPrivacyMode)
                    }
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = model.headline,
                        style = TextStyle(
                            color = ColorProvider(TextPrimaryWhite),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(3.dp))
                    Text(
                        text = model.tip,
                        style = TextStyle(
                            color = ColorProvider(TextSecondarySlate),
                            fontSize = 11.sp
                        ),
                        maxLines = 2
                    )
                }
                Spacer(modifier = GlanceModifier.width(6.dp))
                Image(
                    provider = ImageProvider(gaugeBitmap),
                    contentDescription = "Шкала Жабометра",
                    modifier = GlanceModifier.width(56.dp).height(34.dp)
                )
            }
        } else {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(frogBitmap),
                        contentDescription = "Жабометр Давида",
                        modifier = GlanceModifier.size(42.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = "ЖАБОМЕТР",
                            style = TextStyle(
                                color = ColorProvider(moodColor),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = "${model.score}/100",
                            style = TextStyle(
                                color = ColorProvider(TextPrimaryWhite),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.height(3.dp))
                Text(
                    text = model.mood.titleRu,
                    style = TextStyle(
                        color = ColorProvider(moodColor),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = model.tip,
                    style = TextStyle(
                        color = ColorProvider(TextSecondarySlate),
                        fontSize = 10.sp
                    ),
                    maxLines = 2
                )
            }
        }
    }
}

class ZhabometerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ZhabometerWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetPreferencesStore.clearWidgetInstance(context, it) }
    }
}

@Composable
private fun PrivacyEyePill(
    appWidgetId: Int,
    isPrivacy: Boolean
) {
    Box(
        modifier = GlanceModifier
            .background(ObsidianCardSurface)
            .cornerRadius(8.dp)
            .clickable(
                actionRunCallback<ToggleWidgetPrivacyAction>(
                    actionParametersOf(ActionAppWidgetIdKey to appWidgetId)
                )
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isPrivacy) "🙈" else "👁",
            style = TextStyle(
                color = ColorProvider(TextSecondarySlate),
                fontSize = 10.sp
            )
        )
    }
}

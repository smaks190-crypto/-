package com.example.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.example.data.db.GoalEntity
import com.example.ui.components.BrandFrogAvatar
import com.example.ui.components.formatShortCurrency
import com.example.ui.theme.BudgetTheme
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Конфигурационный экран для виджета «Финансовая цель» (FinancialGoalWidget).
 * Позволяет пользователю выбрать конкретную финансовую цель для каждого экземпляра виджета
 * (appWidgetId) и включить/выключить режим приватности сумм.
 */
class GoalWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent?.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // По стандарту Android AppWidget configuration: по умолчанию RESULT_CANCELED
        val cancelResult = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_CANCELED, cancelResult)

        setContent {
            BudgetTheme {
                val scope = rememberCoroutineScope()
                var goals by remember { mutableStateOf<List<GoalEntity>>(emptyList()) }
                var selectedGoalId by remember {
                    mutableStateOf(WidgetPreferencesStore.getSelectedGoalId(this, appWidgetId))
                }
                var isPrivacyEnabled by remember {
                    mutableStateOf(WidgetPreferencesStore.isPrivacyMode(this, appWidgetId))
                }
                var isLoading by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    val snapshot = WidgetDataLoader.loadSnapshot(this@GoalWidgetConfigActivity)
                    goals = snapshot.goals
                    if (selectedGoalId == null && snapshot.goals.isNotEmpty()) {
                        selectedGoalId = snapshot.goals.first().id
                    }
                    isLoading = false
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BrandFrogAvatar(size = 48.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "НАСТРОЙКА ВИДЖЕТА ЦЕЛИ",
                                    color = Emerald400,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Выберите финансовую цель",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Privacy mode toggle row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(ObsidianSurface)
                                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Режим приватности (•••• ₽)",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Скрывать денежные суммы на рабочем столе",
                                    color = Slate400,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily
                                )
                            }
                            Switch(
                                checked = isPrivacyEnabled,
                                onCheckedChange = { isPrivacyEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkBg,
                                    checkedTrackColor = Emerald400
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!isLoading && goals.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(ObsidianSurface)
                                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "У вас пока нет созданных целей",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Виджет будет автоматически отображать вашу первую цель, как только вы добавите её в приложении.",
                                        color = Slate400,
                                        fontSize = 12.sp,
                                        fontFamily = ManropeFontFamily
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(goals, key = { it.id }) { goal ->
                                    val isSelected = goal.id == selectedGoalId
                                    val pct = ((goal.currentAmount / goal.targetAmount.coerceAtLeast(1.0)) * 100.0)
                                        .roundToInt()
                                        .coerceIn(0, 100)

                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (isSelected) Emerald400.copy(alpha = 0.14f) else ObsidianSurface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) Emerald400 else GlassBorder
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedGoalId = goal.id }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = goal.name,
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontFamily = ManropeFontFamily,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Накоплено ${formatShortCurrency(goal.currentAmount)} из ${formatShortCurrency(goal.targetAmount)}",
                                                    color = Slate300,
                                                    fontSize = 12.sp,
                                                    fontFamily = SpaceGroteskFontFamily
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) Emerald400 else Indigo500.copy(alpha = 0.2f)
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = "$pct%",
                                                    color = if (isSelected) DarkBg else Color.White,
                                                    fontSize = 12.sp,
                                                    fontFamily = SpaceGroteskFontFamily,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                WidgetPreferencesStore.setSelectedGoalId(
                                    this@GoalWidgetConfigActivity,
                                    appWidgetId,
                                    selectedGoalId
                                )
                                WidgetPreferencesStore.setPrivacyMode(
                                    this@GoalWidgetConfigActivity,
                                    appWidgetId,
                                    isPrivacyEnabled
                                )
                                try {
                                    val manager = GlanceAppWidgetManager(this@GoalWidgetConfigActivity)
                                    val glanceId = manager.getGlanceIdBy(appWidgetId)
                                    FinancialGoalWidget().update(this@GoalWidgetConfigActivity, glanceId)
                                } catch (_: Exception) {
                                    DavidWidgetsUpdater.updateAllSuspend(this@GoalWidgetConfigActivity)
                                }
                                val resultValue = Intent().putExtra(
                                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                                    appWidgetId
                                )
                                setResult(Activity.RESULT_OK, resultValue)
                                finish()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald400,
                            contentColor = DarkBg
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = "Сохранить виджет",
                            fontSize = 15.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

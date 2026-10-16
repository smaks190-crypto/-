package com.example.davidapp

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Модель достижения (Ачивки) от ИИ-аудитора Давида Жабова
 */
@Immutable
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val davidQuote: String,
    val iconEmoji: String,
    val xpReward: Int,
    val isSecret: Boolean = false,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null
)

/**
 * Информация о текущем игровом ранге и прогрессе XP пользователя
 */
@Immutable
data class PlayerRankInfo(
    val level: Int,
    val title: String,
    val currentXp: Int,
    val currentLevelMinXp: Int,
    val nextLevelXp: Int,
    val progress: Float,
    val unlockedCount: Int,
    val totalCount: Int
)

/**
 * Синглтон менеджера достижений с локальным сохранением в SharedPreferences,
 * реактивными StateFlow и звуковым оповещением.
 */
object AchievementManager {

    private const val PREFS_NAME = "david_achievements_prefs"
    private const val KEY_PREFIX_UNLOCKED = "unlocked_"
    private const val KEY_PREFIX_UNLOCKED_AT = "unlocked_at_"

    private val INITIAL_ACHIEVEMENTS = listOf(
        Achievement(
            id = "first_audit",
            title = "Знакомство с Жабой",
            description = "Запросить первый финансовый отчет у Давида",
            davidQuote = "Ну что, головастик, готов взглянуть правде в глаза? Спойлер: твои финансы поют романсы.",
            iconEmoji = "🐸",
            xpReward = 50,
            isSecret = false
        ),
        Achievement(
            id = "first_chat",
            title = "Допрос с пристрастием",
            description = "Написать первое сообщение Давиду в чат",
            davidQuote = "Решил поболтать с жабой? Надеюсь, ты не будешь оправдывать покупку того кофе.",
            iconEmoji = "💬",
            xpReward = 50,
            isSecret = false
        ),
        Achievement(
            id = "first_goal",
            title = "Первая заначка",
            description = "Создать свою первую финансовую цель",
            davidQuote = "Цель поставлена! Теперь главное — не спустить всё на спонтанную ерунду через три дня.",
            iconEmoji = "💰",
            xpReward = 100,
            isSecret = false
        ),
        Achievement(
            id = "goal_completed",
            title = "Цель достигнута",
            description = "Накопить 100% на финансовую цель",
            davidQuote = "Ква-а-а! Неужели дотерпел?! Снимаю шляпу перед твоей жабьей выдержкой!",
            iconEmoji = "🎯",
            xpReward = 200,
            isSecret = false
        ),
        Achievement(
            id = "debt_slayer",
            title = "Разрушитель оков",
            description = "Полностью погасить долг",
            davidQuote = "Свобода! Минус один камень с твоей шеи. Смотри больше не лезь в эту кабалу.",
            iconEmoji = "⚔️",
            xpReward = 150,
            isSecret = false
        ),
        Achievement(
            id = "voice_master",
            title = "Голос капитала",
            description = "Добавить финансовую операцию голосом",
            davidQuote = "Голосовой ввод освоен! Жабий слух всё зафиксировал, ни рубля мимо кассы.",
            iconEmoji = "🎙️",
            xpReward = 100,
            isSecret = false
        ),
        Achievement(
            id = "budget_limit",
            title = "Рамки приличия",
            description = "Установить лимит на категорию расходов",
            davidQuote = "Наконец-то надел намордник на свои импульсивные хотелки. Теперь попробуй не сорваться!",
            iconEmoji = "🛡️",
            xpReward = 100,
            isSecret = false
        ),
        Achievement(
            id = "expense_share",
            title = "Честный дележ",
            description = "Создать совместный расход",
            davidQuote = "Правильно, пусть и другие раскошеливаются. Кормить всех за свой счёт — плохая примета.",
            iconEmoji = "🤝",
            xpReward = 100,
            isSecret = false
        ),
        Achievement(
            id = "security_backup",
            title = "Сейф под замком",
            description = "Создать резервную копию или включить PIN-код",
            davidQuote = "Параноик — это просто информированный оптимист. Твои жабьи сокровища под замком.",
            iconEmoji = "🔒",
            xpReward = 100,
            isSecret = false
        ),
        Achievement(
            id = "millionaire",
            title = "Магнат из болота",
            description = "Достичь общего баланса от 1 000 000 ₽",
            davidQuote = "Семь цифр на счету?! Вот это жабий размах! Пора открывать собственное болото инвестиций!",
            iconEmoji = "💎",
            xpReward = 250,
            isSecret = true
        )
    )

    private val _achievementsFlow = MutableStateFlow<List<Achievement>>(INITIAL_ACHIEVEMENTS)
    val achievementsFlow: StateFlow<List<Achievement>> = _achievementsFlow.asStateFlow()

    private val _unlockedBannerQueue = MutableStateFlow<Achievement?>(null)
    val unlockedBannerQueue: StateFlow<Achievement?> = _unlockedBannerQueue.asStateFlow()

    private val _playerRankFlow = MutableStateFlow(calculateRankInfo(INITIAL_ACHIEVEMENTS))
    val playerRankFlow: StateFlow<PlayerRankInfo> = _playerRankFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var bannerDismissJob: Job? = null
    private var isInitialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Инициализация состояния из SharedPreferences
     */
    fun initIfNeeded(context: Context) {
        if (isInitialized) return
        val prefs = getPrefs(context)
        loadFromPrefs(prefs)
        isInitialized = true
    }

    private fun loadFromPrefs(prefs: SharedPreferences) {
        val updated = INITIAL_ACHIEVEMENTS.map { item ->
            val unlocked = prefs.getBoolean(KEY_PREFIX_UNLOCKED + item.id, false)
            val unlockedAt = if (unlocked) prefs.getLong(KEY_PREFIX_UNLOCKED_AT + item.id, 0L) else null
            item.copy(isUnlocked = unlocked, unlockedAt = unlockedAt)
        }
        _achievementsFlow.value = updated
        _playerRankFlow.value = calculateRankInfo(updated)
    }

    /**
     * Разблокировка достижения по ID
     */
    fun unlock(context: Context, achievementId: String) {
        val appContext = context.applicationContext
        val prefs = getPrefs(appContext)

        if (!isInitialized) {
            loadFromPrefs(prefs)
            isInitialized = true
        }

        val currentList = _achievementsFlow.value
        val target = currentList.find { it.id == achievementId } ?: return

        if (target.isUnlocked) {
            // Уже открыта, повторно не активируем
            return
        }

        val now = System.currentTimeMillis()
        prefs.edit()
            .putBoolean(KEY_PREFIX_UNLOCKED + achievementId, true)
            .putLong(KEY_PREFIX_UNLOCKED_AT + achievementId, now)
            .apply()

        val updatedAchievement = target.copy(isUnlocked = true, unlockedAt = now)
        val updatedList = currentList.map {
            if (it.id == achievementId) updatedAchievement else it
        }

        _achievementsFlow.value = updatedList
        _playerRankFlow.value = calculateRankInfo(updatedList)

        // 1. Звуковой победный сигнал
        try {
            val soundManager = SoundManager(appContext)
            soundManager.playAchievement()
        } catch (_: Exception) {}

        // 2. Добавление в баннер-очередь на 4 секунды
        bannerDismissJob?.cancel()
        _unlockedBannerQueue.value = updatedAchievement

        bannerDismissJob = scope.launch {
            delay(4000)
            if (_unlockedBannerQueue.value?.id == achievementId) {
                _unlockedBannerQueue.value = null
            }
        }
    }

    /**
     * Ручное закрытие баннера пользователем по клику
     */
    fun dismissBanner() {
        bannerDismissJob?.cancel()
        _unlockedBannerQueue.value = null
    }

    /**
     * Расчет звания, уровня и шкалы XP по списку достижений
     */
    private fun calculateRankInfo(achievements: List<Achievement>): PlayerRankInfo {
        val unlockedList = achievements.filter { it.isUnlocked }
        val totalXp = unlockedList.sumOf { it.xpReward }
        val unlockedCount = unlockedList.size
        val totalCount = achievements.size

        return when {
            totalXp >= 1000 -> PlayerRankInfo(
                level = 6,
                title = "Алмазный Жаб",
                currentXp = totalXp,
                currentLevelMinXp = 1000,
                nextLevelXp = 1200,
                progress = 1.0f,
                unlockedCount = unlockedCount,
                totalCount = totalCount
            )
            totalXp >= 700 -> {
                val min = 700
                val max = 1000
                val prog = ((totalXp - min).toFloat() / (max - min)).coerceIn(0f, 1f)
                PlayerRankInfo(
                    level = 5,
                    title = "Гроссмейстер ква-питала",
                    currentXp = totalXp,
                    currentLevelMinXp = min,
                    nextLevelXp = max,
                    progress = prog,
                    unlockedCount = unlockedCount,
                    totalCount = totalCount
                )
            }
            totalXp >= 450 -> {
                val min = 450
                val max = 700
                val prog = ((totalXp - min).toFloat() / (max - min)).coerceIn(0f, 1f)
                PlayerRankInfo(
                    level = 4,
                    title = "Старший аудитор трясины",
                    currentXp = totalXp,
                    currentLevelMinXp = min,
                    nextLevelXp = max,
                    progress = prog,
                    unlockedCount = unlockedCount,
                    totalCount = totalCount
                )
            }
            totalXp >= 250 -> {
                val min = 250
                val max = 450
                val prog = ((totalXp - min).toFloat() / (max - min)).coerceIn(0f, 1f)
                PlayerRankInfo(
                    level = 3,
                    title = "Финансовая лягушка",
                    currentXp = totalXp,
                    currentLevelMinXp = min,
                    nextLevelXp = max,
                    progress = prog,
                    unlockedCount = unlockedCount,
                    totalCount = totalCount
                )
            }
            totalXp >= 100 -> {
                val min = 100
                val max = 250
                val prog = ((totalXp - min).toFloat() / (max - min)).coerceIn(0f, 1f)
                PlayerRankInfo(
                    level = 2,
                    title = "Болотный накопитель",
                    currentXp = totalXp,
                    currentLevelMinXp = min,
                    nextLevelXp = max,
                    progress = prog,
                    unlockedCount = unlockedCount,
                    totalCount = totalCount
                )
            }
            else -> {
                val min = 0
                val max = 100
                val prog = (totalXp.toFloat() / max).coerceIn(0f, 1f)
                PlayerRankInfo(
                    level = 1,
                    title = "Головастик-транжира",
                    currentXp = totalXp,
                    currentLevelMinXp = min,
                    nextLevelXp = max,
                    progress = prog,
                    unlockedCount = unlockedCount,
                    totalCount = totalCount
                )
            }
        }
    }
}

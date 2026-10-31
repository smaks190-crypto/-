package com.example.ui.viewmodel

import com.example.data.db.AccountEntity
import com.example.data.db.TransactionEntity
import com.example.ui.components.SettingsScreen

enum class PeriodType { DAY, WEEK, MONTH, ALL }

sealed interface BottomCapsuleModal {
    data object None : BottomCapsuleModal
    data class ManualAddTransaction(val initialType: String = "expense") : BottomCapsuleModal
    data class EditTransaction(val transaction: TransactionEntity) : BottomCapsuleModal
    data object VoiceInput : BottomCapsuleModal
    data class AllTransactions(
        val initialFilterType: String = "all",
        val initialDate: String? = null
    ) : BottomCapsuleModal
    data class SettingsHub(
        val initialScreen: SettingsScreen = SettingsScreen.HUB
    ) : BottomCapsuleModal
    data object CategoryLimits : BottomCapsuleModal
    data object Categories : BottomCapsuleModal
    data object AddGoal : BottomCapsuleModal
    data object AddDebt : BottomCapsuleModal
    data class DebtDetails(val debt: AccountEntity) : BottomCapsuleModal
    data class GeminiConsent(
        val messageOverride: String? = null,
        val triggerAuditAfterAccept: Boolean = false
    ) : BottomCapsuleModal
    data object DavidChat : BottomCapsuleModal
}

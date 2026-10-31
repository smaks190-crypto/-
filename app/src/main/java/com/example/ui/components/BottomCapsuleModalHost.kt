package com.example.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import com.example.data.SecurityManager
import com.example.data.db.AccountEntity
import com.example.data.db.BudgetProfileEntity
import com.example.data.db.CategoryEntity
import com.example.data.db.TransactionEntity
import com.example.data.preferences.BudgetPreferencesRepository
import com.example.davidapp.DavidChatScreen
import com.example.davidapp.DavidViewModel
import com.example.ui.components.dialogs.AddTransactionDialog
import com.example.ui.components.dialogs.AllTransactionsDialog
import com.example.ui.components.dialogs.CategoriesDialog
import com.example.ui.components.dialogs.CategoryLimitsDialog
import com.example.ui.screens.AddDebtDialog
import com.example.ui.screens.DebtDetailsDialog
import com.example.ui.viewmodel.BottomCapsuleModal
import com.example.ui.viewmodel.BudgetViewModel

@Composable
fun BottomCapsuleModalHost(
    modal: BottomCapsuleModal,
    context: Context,
    viewModel: BudgetViewModel,
    davidViewModel: DavidViewModel,
    securityManager: SecurityManager,
    currentProfile: BudgetProfileEntity?,
    apiKey: String,
    categories: List<CategoryEntity>,
    transactions: List<TransactionEntity>,
    filteredTransactions: List<TransactionEntity>,
    accounts: List<AccountEntity>,
    triggerAuditAfterKeySave: Boolean,
    onSetTriggerAuditAfterKeySave: (Boolean) -> Unit,
    onAvatarChanged: () -> Unit,
    onUnlockAppIfPinDisabled: () -> Unit
) {
    when (modal) {
        is BottomCapsuleModal.EditTransaction -> {
            AddTransactionDialog(
                initialType = modal.transaction.type,
                categories = categories,
                onDismiss = { viewModel.closeModal() },
                onSave = { type, date, category, subcategory, amount ->
                    viewModel.updateTransaction(
                        modal.transaction.id,
                        type,
                        date,
                        category,
                        subcategory,
                        amount
                    )
                    viewModel.closeModal()
                },
                onSuggestCategory = { txName, type, catList ->
                    viewModel.suggestCategory(txName, type, catList)
                },
                editingTransaction = modal.transaction
            )
        }

        is BottomCapsuleModal.AllTransactions -> {
            AllTransactionsDialog(
                transactions = transactions,
                initialFilterType = modal.initialFilterType,
                initialDate = modal.initialDate,
                onDismiss = { viewModel.closeModal() },
                onEditTransaction = { tx ->
                    viewModel.openModal(BottomCapsuleModal.EditTransaction(tx))
                },
                onDeleteTransaction = { txId ->
                    viewModel.deleteTransaction(txId)
                },
                onRestoreTransaction = { tx ->
                    viewModel.restoreTransaction(tx)
                }
            )
        }

        is BottomCapsuleModal.SettingsHub -> {
            SettingsHubDialog(
                securityManager = securityManager,
                apiKey = apiKey,
                currentProfileName = currentProfile?.name ?: "",
                profileId = currentProfile?.id ?: "default",
                onAvatarChanged = onAvatarChanged,
                onRenameProfile = { newName ->
                    currentProfile?.let { viewModel.renameBudget(it.id, newName) }
                },
                onResetAllData = {
                    viewModel.clearAllDataAndResetSecurity(securityManager)
                    viewModel.closeModal()
                },
                initialScreen = modal.initialScreen,
                onDismiss = {
                    viewModel.closeModal()
                    onSetTriggerAuditAfterKeySave(false)
                },
                onSaveApiKey = { newKey ->
                    viewModel.saveApiKey(newKey)
                    if (triggerAuditAfterKeySave && newKey.isNotBlank()) {
                        onSetTriggerAuditAfterKeySave(false)
                        viewModel.requestAiAudit(filteredTransactions)
                    }
                },
                onSecurityUpdated = onUnlockAppIfPinDisabled,
                onOpenSecurity = {},
                onOpenReminders = {},
                onOpenApiKey = {},
                onOpenCategories = {
                    viewModel.openModal(BottomCapsuleModal.Categories)
                },
                onExitBudget = {
                    viewModel.selectBudget(null)
                    viewModel.closeModal()
                }
            )
        }

        is BottomCapsuleModal.CategoryLimits -> {
            CategoryLimitsDialog(
                categories = categories,
                transactions = filteredTransactions,
                onUpdateLimit = { catName, limit ->
                    viewModel.updateCategoryLimit(
                        categoryName = catName,
                        type = "expense",
                        newLimit = limit
                    )
                },
                onDismiss = { viewModel.closeModal() }
            )
        }

        is BottomCapsuleModal.Categories -> {
            CategoriesDialog(
                categories = categories,
                onDismiss = { viewModel.closeModal() },
                onAddCategory = { type, name -> viewModel.addCategory(type, name) },
                onDeleteCategory = { viewModel.deleteCategory(it) }
            )
        }

        is BottomCapsuleModal.AddGoal -> {
            AddGoalDialog(
                onDismiss = { viewModel.closeModal() },
                onSave = { name, target, current ->
                    viewModel.saveNewGoal(name, target, current)
                    viewModel.closeModal()
                }
            )
        }

        is BottomCapsuleModal.AddDebt -> {
            AddDebtDialog(
                onDismiss = { viewModel.closeModal() },
                onConfirm = { name, amount, type, comment ->
                    viewModel.addAccount(name, amount, type, comment)
                    viewModel.closeModal()
                }
            )
        }

        is BottomCapsuleModal.DebtDetails -> {
            val liveDebt = accounts.find { it.id == modal.debt.id } ?: modal.debt
            val debtTransactions = transactions.filter { it.accountId == liveDebt.id }
            val isWeOwe = liveDebt.type != "owes_us"
            val paidSoFar = debtTransactions.sumOf { tx ->
                val matchesRepayment = if (isWeOwe) tx.type == "expense" else tx.type == "income"
                if (matchesRepayment) tx.amount else -tx.amount
            }
            val remaining = (liveDebt.balance - paidSoFar).coerceAtLeast(0.0)
            DebtDetailsDialog(
                debt = liveDebt,
                remainingBalance = remaining,
                transactions = debtTransactions,
                onDismiss = { viewModel.closeModal() },
                onAddPayment = { type, date, amt, subcat ->
                    viewModel.addTransaction(type, date, "Долги", subcat, amt, liveDebt.id)
                }
            )
        }

        is BottomCapsuleModal.GeminiConsent -> {
            GeminiConsentDialog(
                messageOverride = modal.messageOverride ?: "Для использования ИИ-функций требуется ваше согласие на обработку данных.",
                currentApiKey = apiKey,
                onSaveApiKey = { newKey -> viewModel.saveApiKey(newKey) },
                onAccept = {
                    viewModel.setGeminiConsentGiven(true)
                    if (apiKey.isBlank()) {
                        onSetTriggerAuditAfterKeySave(modal.triggerAuditAfterAccept)
                        viewModel.openModal(
                            BottomCapsuleModal.SettingsHub(SettingsScreen.API_KEY)
                        )
                    } else {
                        viewModel.closeModal()
                        if (modal.triggerAuditAfterAccept) {
                            viewModel.requestAiAudit(filteredTransactions)
                        }
                    }
                },
                onDecline = {
                    viewModel.setGeminiConsentGiven(false)
                    viewModel.closeModal()
                }
            )
        }

        is BottomCapsuleModal.DavidChat -> {
            val profileKey = currentProfile?.name ?: "default"
            DavidChatScreen(
                budgetId = currentProfile?.id ?: "default",
                profileName = currentProfile?.name ?: "Максим",
                apiKey = apiKey,
                viewModel = davidViewModel,
                isEmbeddedInCapsule = true,
                showStandaloneLiveOverlay = true,
                onMinimizeToBottomBar = {
                    davidViewModel.minimizeDavidLive()
                    viewModel.closeModal()
                    BudgetPreferencesRepository.markDavidChatOpened(context, profileKey)
                    viewModel.markNotificationsAsRead()
                },
                onBack = {
                    viewModel.closeModal()
                    BudgetPreferencesRepository.markDavidChatOpened(context, profileKey)
                    viewModel.markNotificationsAsRead()
                }
            )
        }

        else -> {}
    }
}

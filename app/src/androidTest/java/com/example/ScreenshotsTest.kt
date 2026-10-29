package com.example

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ui.viewmodel.BottomCapsuleModal
import com.example.ui.viewmodel.BudgetViewModel
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/**
 * Запускает настоящее приложение на эмуляторе 1080x1920 (9:16), загружает встроенные
 * демо-данные и снимает: главный экран, голосовой ввод, чат с Давидом.
 * Скриншот David Live снимает DavidLiveScreenshotTest.
 */
class ScreenshotsTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun captureMainVoiceAndChatScreens() {
        val context = instrumentation.targetContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        val activity = instrumentation.startActivitySync(launchIntent)
        val viewModel = ViewModelProvider(activity as ViewModelStoreOwner)[BudgetViewModel::class.java]

        // Профиль бюджета + встроенные демо-данные (без личных финансовых сведений)
        waitFor("профили бюджета", 30_000) { viewModel.budgetProfiles.value.isNotEmpty() }
        if (viewModel.selectedBudgetId.value == null) {
            val firstId = viewModel.budgetProfiles.value.firstOrNull()?.id
            if (firstId != null) onMain { viewModel.selectBudget(firstId) }
        }
        waitFor("выбранный бюджет", 30_000) { viewModel.selectedBudgetId.value != null }
        onMain { viewModel.loadFullYearDemoData() }
        waitFor("демо-операции", 60_000) { viewModel.transactions.value.isNotEmpty() }

        // 1. Главный экран (ждём конец заставки и приветственной плашки)
        capture(name = "01_main", settleMs = 14_000)

        // 2. Голосовой ввод: нижняя капсула с визуализатором
        capture(
            name = "02_voice_input",
            settleMs = 3_000,
            prepare = {
                onMain {
                    viewModel.setVoiceActive(true)
                    viewModel.openModal(BottomCapsuleModal.VoiceInput)
                }
            },
            cleanup = {
                onMain {
                    viewModel.setVoiceActive(false)
                    viewModel.closeModal()
                }
            }
        )

        // 3. Чат с Давидом
        capture(
            name = "03_david_chat",
            settleMs = 4_000,
            prepare = { onMain { viewModel.openModal(BottomCapsuleModal.DavidChat) } },
            cleanup = { onMain { viewModel.closeModal() } }
        )
    }

    private fun capture(
        name: String,
        settleMs: Long,
        prepare: () -> Unit = {},
        cleanup: () -> Unit = {}
    ) {
        try {
            prepare()
        } catch (e: Throwable) {
            Log.e(TAG, "Prepare failed for $name", e)
        }
        Thread.sleep(settleMs)
        ScreenshotHelper.takeScreenshot(name)
        try {
            cleanup()
        } catch (e: Throwable) {
            Log.e(TAG, "Cleanup failed for $name", e)
        }
        Thread.sleep(1_500)
    }

    private fun onMain(block: () -> Unit) {
        instrumentation.runOnMainSync { block() }
    }

    private fun waitFor(what: String, timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(250)
        }
        Log.w(TAG, "Timeout while waiting for: $what")
        return false
    }

    companion object {
        private const val TAG = "ScreenshotsTest"

        @JvmStatic
        @BeforeClass
        fun prepareDevice() {
            ScreenshotHelper.prepareDevice()
        }

        @JvmStatic
        @AfterClass
        fun restoreDevice() {
            ScreenshotHelper.restoreDevice()
        }
    }
}

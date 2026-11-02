package com.example.davidapp.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.davidapp.ChatMessageType
import com.example.davidapp.DavidChatInputBar
import com.example.davidapp.DavidLiveSummaryBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DavidLiveUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sendButtonLongPressLaunchesDavidLiveWithoutClearingOrSendingDraftText() {
        var draftText by mutableStateOf("Кофе 350")
        var sendTriggeredCount = 0
        var liveStartedCount = 0

        composeTestRule.setContent {
            DavidChatInputBar(
                text = draftText,
                onTextChange = { draftText = it },
                onSend = {
                    sendTriggeredCount++
                    draftText = ""
                },
                onStartDavidLive = {
                    liveStartedCount++
                }
            )
        }

        // Проверяем наличие ненавязчивой подсказки о долгом нажатии
        composeTestRule.onNodeWithTag("david_live_long_press_hint").assertIsDisplayed()

        // Долгое нажатие (~600 мс) на кнопку отправки запускает David Live,
        // НЕ отправляет сообщение и НЕ стирает уже набранный текст
        composeTestRule.onNodeWithTag("david_send_button").performTouchInput {
            longClick(durationMillis = 650L)
        }
        composeTestRule.waitForIdle()

        assertEquals(1, liveStartedCount)
        assertEquals(0, sendTriggeredCount)
        assertEquals("Кофе 350", draftText)

        // Короткое нажатие отправляет сообщение
        composeTestRule.onNodeWithTag("david_send_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, sendTriggeredCount)
        assertEquals("", draftText)
    }

    @Test
    fun davidLiveSheetDisplaysHeaderSubtitleCircularVisualizerAndHandlesControls() {
        var micMuted by mutableStateOf(false)
        var sessionEnded = false

        composeTestRule.setContent {
            DavidLiveSheet(
                connectionState = DavidLiveConnectionState.LISTENING,
                liveSubtitle = "Говорите, я слушаю...",
                rayMagnitudes = List(DavidLiveSpectrumAnalyzer.RAY_COUNT) { 0.35f },
                overallEnergy = 0.42f,
                activeSpeaker = DavidLiveSpeaker.USER,
                isMicMuted = micMuted,
                errorMessage = null,
                onToggleMicMute = { micMuted = !micMuted },
                onRetryConnection = {},
                onOpenApiKeySettings = {},
                onEndSession = { sessionEnded = true }
            )
        }

        composeTestRule.onNodeWithText("Давид Жабов").assertIsDisplayed()
        composeTestRule.onNodeWithText("AI-помощник по финансам").assertIsDisplayed()
        composeTestRule.onNodeWithTag("david_live_subtitle_bubble").assertIsDisplayed()
        composeTestRule.onNodeWithTag("david_live_circular_visualizer").assertIsDisplayed()

        // Переключение микрофона по нажатию на центр кругового визуализатора
        composeTestRule.onNodeWithTag("david_live_mic_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(micMuted)

        // Завершение сессии по кнопке «Отменить»
        composeTestRule.onNodeWithTag("david_live_cancel_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(sessionEnded)
    }

    @Test
    fun davidLiveSummaryBubbleRendersGroundedSectionsAndSupportsRetryOnError() {
        var retried = false
        val summary = DavidLiveSummary(
            topics = listOf("Анализ расходов на кафе и продукты"),
            keyFindings = listOf("Продукты: 15 000 ₽ из лимита 25 000 ₽ (60%)"),
            recommendations = listOf("Сократить спонтанные заказы кофе навынос")
        )

        composeTestRule.setContent {
            DavidLiveSummaryBubble(
                summaryState = ChatMessageType.LiveSessionSummary(
                    summary = summary,
                    isGenerating = false,
                    errorMessage = null
                ),
                timestamp = "14:20",
                onRetry = { retried = true }
            )
        }

        composeTestRule.onNodeWithTag("david_live_summary_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Итоги разговора • David Live").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Анализ расходов на кафе и продукты").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Продукты: 15 000 ₽ из лимита 25 000 ₽ (60%)").assertIsDisplayed()
    }

    @Test
    fun davidLiveMinimizesToCompactCapsuleHandlesMuteExpandAndEndsCleanly() {
        var displayMode by mutableStateOf(DavidLiveDisplayMode.EXPANDED)
        var micMuted by mutableStateOf(false)
        var endCallCount = 0
        var expandCount = 0

        composeTestRule.setContent {
            DavidLiveSheet(
                connectionState = DavidLiveConnectionState.LISTENING,
                liveSubtitle = "Слушаю тебя...",
                rayMagnitudes = List(DavidLiveSpectrumAnalyzer.RAY_COUNT) { 0.42f },
                overallEnergy = 0.48f,
                activeSpeaker = DavidLiveSpeaker.USER,
                isMicMuted = micMuted,
                errorMessage = null,
                onToggleMicMute = { micMuted = !micMuted },
                onRetryConnection = {},
                onOpenApiKeySettings = {},
                onEndSession = {
                    endCallCount++
                    displayMode = DavidLiveDisplayMode.HIDDEN
                },
                displayMode = displayMode,
                onMinimize = { displayMode = DavidLiveDisplayMode.MINIMIZED },
                onExpand = {
                    expandCount++
                    displayMode = DavidLiveDisplayMode.EXPANDED
                },
                renderEmbeddedMinimizedCapsule = true
            )
        }

        // 1. В полноэкранном режиме отображается круговой визуализатор и кнопка сворачивания
        composeTestRule.onNodeWithTag("david_live_circular_visualizer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("david_live_minimize_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(DavidLiveDisplayMode.MINIMIZED, displayMode)
        assertEquals(0, endCallCount)

        // 2. В компактном режиме отображается нижняя капсула со звуковой волной, кнопкой микрофона и завершения
        composeTestRule.onNodeWithTag("david_live_minimized_capsule").assertIsDisplayed()
        composeTestRule.onNodeWithTag("david_live_capsule_waveform").assertIsDisplayed()
        composeTestRule.onNodeWithText("Давид").assertIsDisplayed()
        composeTestRule.onNodeWithText("LIVE").assertIsDisplayed()

        // 3. Нажатие на кнопку микрофона в мини-капсуле переключает mute, но НЕ разворачивает экран
        composeTestRule.onNodeWithTag("david_live_capsule_mute_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(micMuted)
        assertEquals(DavidLiveDisplayMode.MINIMIZED, displayMode)
        assertEquals(0, expandCount)

        // 4. Нажатие на свободную область компактной капсулы разворачивает обратно полноэкранный David Live
        composeTestRule.onNodeWithTag("david_live_minimized_capsule").performClick()
        composeTestRule.waitForIdle()
        assertEquals(DavidLiveDisplayMode.EXPANDED, displayMode)
        assertEquals(1, expandCount)
        assertTrue("Mic mute state must persist across expand", micMuted)

        // 5. Снова сворачиваем и завершаем звонок красной кнопкой из мини-капсулы
        composeTestRule.onNodeWithTag("david_live_chat_minimize_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(DavidLiveDisplayMode.MINIMIZED, displayMode)

        composeTestRule.onNodeWithTag("david_live_capsule_end_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, endCallCount)
        assertEquals(DavidLiveDisplayMode.HIDDEN, displayMode)
    }

    @Test
    fun davidLiveEmbeddedInModalCardDistinguishesMinimizeToBottomBarAndMinimizeToChat() {
        var displayMode by mutableStateOf(DavidLiveDisplayMode.EXPANDED)
        var minimizedToBottomBarCount = 0
        var minimizedToChatCount = 0

        composeTestRule.setContent {
            DavidLiveSheet(
                connectionState = DavidLiveConnectionState.LISTENING,
                liveSubtitle = "Слушаю тебя...",
                rayMagnitudes = List(DavidLiveSpectrumAnalyzer.RAY_COUNT) { 0.42f },
                overallEnergy = 0.48f,
                activeSpeaker = DavidLiveSpeaker.USER,
                isMicMuted = false,
                errorMessage = null,
                onToggleMicMute = {},
                onRetryConnection = {},
                onOpenApiKeySettings = {},
                onEndSession = {},
                displayMode = displayMode,
                onMinimize = {
                    minimizedToBottomBarCount++
                    displayMode = DavidLiveDisplayMode.MINIMIZED
                },
                onMinimizeToChat = {
                    minimizedToChatCount++
                    displayMode = DavidLiveDisplayMode.MINIMIZED
                },
                onExpand = {
                    displayMode = DavidLiveDisplayMode.EXPANDED
                },
                isEmbeddedInModalCard = true,
                renderEmbeddedMinimizedCapsule = false
            )

            if (displayMode == DavidLiveDisplayMode.MINIMIZED) {
                DavidLiveMinimizedCapsule(
                    connectionState = DavidLiveConnectionState.LISTENING,
                    rayMagnitudes = List(DavidLiveSpectrumAnalyzer.RAY_COUNT) { 0.42f },
                    overallEnergy = 0.48f,
                    activeSpeaker = DavidLiveSpeaker.USER,
                    isMicMuted = false,
                    onExpand = { displayMode = DavidLiveDisplayMode.EXPANDED },
                    onToggleMicMute = {},
                    onEndSession = {},
                    embeddedInBottomBar = true
                )
            }
        }

        // 1. Кнопка `<` (david_live_minimize_button) сворачивает окно сразу в нижнюю капсулу MorphingBottomBar
        composeTestRule.onNodeWithTag("david_live_minimize_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, minimizedToBottomBarCount)
        assertEquals(0, minimizedToChatCount)
        composeTestRule.onNodeWithTag("david_live_minimized_capsule").assertIsDisplayed()

        // 2. Разворачиваем обратно из нижней капсулы
        composeTestRule.onNodeWithTag("david_live_minimized_capsule").performClick()
        composeTestRule.waitForIdle()
        assertEquals(DavidLiveDisplayMode.EXPANDED, displayMode)

        // 3. Кнопка иконки чата (david_live_chat_minimize_button) сворачивает режим внутрь открытого чата
        composeTestRule.onNodeWithTag("david_live_chat_minimize_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, minimizedToBottomBarCount)
        assertEquals(1, minimizedToChatCount)
    }

    @Test
    fun davidLiveSphereTapTriggersReconnectInOfflineStateAndSettingsInApiMissingState() {
        var retryCount = 0
        var apiSettingsCount = 0
        var currentVisualState by mutableStateOf(DavidLiveVisualState.OFFLINE)

        composeTestRule.setContent {
            DavidLiveSheet(
                connectionState = if (currentVisualState == DavidLiveVisualState.OFFLINE) {
                    DavidLiveConnectionState.OFFLINE
                } else {
                    DavidLiveConnectionState.NO_API_KEY
                },
                liveSubtitle = "Статус подключения",
                rayMagnitudes = List(DavidLiveSpectrumAnalyzer.RAY_COUNT) { 0.1f },
                overallEnergy = 0.05f,
                activeSpeaker = DavidLiveSpeaker.NONE,
                isMicMuted = false,
                errorMessage = null,
                onToggleMicMute = {},
                onRetryConnection = { retryCount++ },
                onOpenApiKeySettings = { apiSettingsCount++ },
                onEndSession = {},
                visualState = currentVisualState
            )
        }

        // 1. В состоянии OFFLINE прямое нажатие на сферу запускает reconnect
        composeTestRule.onNodeWithTag("david_live_circular_visualizer").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, retryCount)
        assertEquals(0, apiSettingsCount)

        // 2. В состоянии API_MISSING прямое нажатие на сферу открывает настройки API
        currentVisualState = DavidLiveVisualState.API_MISSING
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("david_live_circular_visualizer").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, retryCount)
        assertEquals(1, apiSettingsCount)
    }

    @Test
    fun davidLiveDebugStateSwitcherTogglesVisualStatesAndRendersMeshAccents() {
        var selectedState by mutableStateOf<DavidLiveVisualState?>(null)

        composeTestRule.setContent {
            DavidLiveSheet(
                connectionState = DavidLiveConnectionState.LISTENING,
                liveSubtitle = "Слушаю вас...",
                rayMagnitudes = List(DavidLiveSpectrumAnalyzer.RAY_COUNT) { 0.2f },
                overallEnergy = 0.15f,
                activeSpeaker = DavidLiveSpeaker.NONE,
                isMicMuted = false,
                errorMessage = null,
                onToggleMicMute = {},
                onRetryConnection = {},
                onOpenApiKeySettings = {},
                onEndSession = {},
                visualState = selectedState,
                debugOverrideVisualState = selectedState,
                onSelectDebugVisualState = { selectedState = it },
                enableDebugStateSwitcher = true
            )
        }

        // Открываем отладочную панель переключения состояний и выбираем чип FINANCE_LOOKUP
        composeTestRule.onNodeWithTag("david_live_debug_toggle_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("david_live_debug_chip_FINANCE_LOOKUP").performClick()
        composeTestRule.waitForIdle()

        assertEquals(DavidLiveVisualState.FINANCE_LOOKUP, selectedState)
        composeTestRule.onNodeWithTag("david_live_circular_visualizer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("david_live_state_badge").assertIsDisplayed()
    }
    @Test
    fun davidLiveSphereLongPressDragAttractsParticlesWithoutTriggeringTapAction() {
        var tapCount = 0
        var longPressStartCount = 0
        var moveCount = 0
        var endCount = 0

        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .davidLiveSphereGesture(
                        actionDescription = "Тестовая энергетическая сфера",
                        onTap = { tapCount++ },
                        onLongPressStart = { longPressStartCount++ },
                        onLongPressMove = { moveCount++ },
                        onLongPressEnd = { endCount++ },
                        onLongPressCancel = {}
                    )
                    .testTag("david_live_sphere_gesture_test")
            )
        }

        composeTestRule.onNodeWithTag("david_live_sphere_gesture_test").performTouchInput {
            down(center)
            advanceEventTime(650L)
            moveTo(center + Offset(18f, 12f), delayMillis = 100L)
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(0, tapCount)
        assertEquals(1, longPressStartCount)
        assertTrue(moveCount > 0)
        assertEquals(1, endCount)
    }

}



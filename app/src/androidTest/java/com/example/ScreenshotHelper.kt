package com.example

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/**
 * Вспомогательные функции для съёмки скриншотов 9:16 на эмуляторе.
 * Снимки сохраняются в /sdcard/Download/ui_screenshots и забираются CI через adb pull.
 */
object ScreenshotHelper {
    private const val TAG = "ScreenshotHelper"
    private const val APP_PACKAGE = "ru.personalbudget.app.aadece"
    const val SCREENSHOT_DIR = "/sdcard/Download/ui_screenshots"

    private fun device(): UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun shell(command: String) {
        try {
            device().executeShellCommand(command)
        } catch (e: Exception) {
            Log.w(TAG, "Shell command failed: $command", e)
        }
    }

    /** Экран 1080x1920 (строго 9:16), разрешения и «чистая» строка состояния. */
    fun prepareDevice() {
        shell("wm size 1080x1920")
        shell("wm density 420")
        shell("pm grant $APP_PACKAGE android.permission.RECORD_AUDIO")
        shell("pm grant $APP_PACKAGE android.permission.POST_NOTIFICATIONS")
        shell("mkdir -p $SCREENSHOT_DIR")
        // Демо-режим системной панели: ровное время, полная батарея, без лишних иконок
        shell("settings put global sysui_demo_allowed 1")
        shell("am broadcast -a com.android.systemui.demo -e command enter")
        shell("am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000")
        shell("am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false")
        shell("am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4")
        shell("am broadcast -a com.android.systemui.demo -e command notifications -e visible false")
    }

    fun restoreDevice() {
        shell("am broadcast -a com.android.systemui.demo -e command exit")
        shell("wm density reset")
        shell("wm size reset")
    }

    fun takeScreenshot(name: String) {
        shell("screencap -p $SCREENSHOT_DIR/$name.png")
        Log.i(TAG, "Screenshot saved: $name")
    }
}

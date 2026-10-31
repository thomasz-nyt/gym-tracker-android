package com.gymtracker

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.test.assertTrue

/** Captures native review fixtures to the isolated test package, never the member's installation. */
internal fun captureNativeReview(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    // Platform dialog-window fades continue after Compose becomes idle.
    instrumentation.uiAutomation.waitForIdle(400, 4_000)
    instrumentation.waitForIdleSync()
    val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    File(instrumentation.targetContext.cacheDir, name).outputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    bitmap.recycle()
}

/** Compose visibility does not account for a separate input-method window covering a button. */
internal fun assertNativeActionAboveKeyboard(label: String) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val info = checkNotNull(automation.serviceInfo)
    val originalFlags = info.flags
    try {
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        automation.waitForIdle(400, 4_000)
        val windows = automation.windows
        val keyboard =
            checkNotNull(windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) {
                "The physical keyboard test requires the software keyboard to be open"
            }
        val keyboardBounds = Rect().also { keyboard.getBoundsInScreen(it) }
        val actions =
            windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.flatMap { window ->
                window.root
                    ?.let { descendants(it) }
                    .orEmpty()
                    .filter { it.text?.toString() == label || it.contentDescription?.toString() == label }
            }
        val bounds =
            Rect().also {
                checkNotNull(
                    actions.firstOrNull(),
                ) { "No accessible action labelled $label in the dialog" }.getBoundsInScreen(it)
            }
        assertTrue(
            !bounds.isEmpty && bounds.bottom <= keyboardBounds.top,
            "$label must be above the keyboard: action=$bounds, keyboard=$keyboardBounds",
        )
    } finally {
        info.flags = originalFlags
        automation.serviceInfo = info
    }
}

private fun descendants(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> =
    listOf(root) + (0 until root.childCount).flatMap { index -> root.getChild(index)?.let(::descendants).orEmpty() }

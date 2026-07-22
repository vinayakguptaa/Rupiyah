package com.krtky.financetracker.ui.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Reliable device haptics via View API (Compose LocalHapticFeedback is often silent). */
class AppHaptics(private val view: View) {
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    fun click() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.KEYBOARD_TAP,
    )

    fun select() = perform(
        if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.CONTEXT_CLICK
        else HapticFeedbackConstants.VIRTUAL_KEY,
    )

    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)

    fun reject() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.VIRTUAL_KEY,
    )

    private fun perform(constant: Int) {
        view.isHapticFeedbackEnabled = true
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val view = LocalView.current
    return remember(view) { AppHaptics(view) }
}

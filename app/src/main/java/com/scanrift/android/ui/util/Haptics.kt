package com.scanrift.android.ui.util

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

object Haptic {
    val Light = HapticFeedbackType.SegmentTick
    val Medium = HapticFeedbackType.ContextClick
    val Heavy = HapticFeedbackType.LongPress
    val Selection = HapticFeedbackType.SegmentFrequentTick
}

fun HapticFeedback.lightImpact() = performHapticFeedback(Haptic.Light)

fun HapticFeedback.mediumImpact() = performHapticFeedback(Haptic.Medium)

fun HapticFeedback.heavyImpact() = performHapticFeedback(Haptic.Heavy)

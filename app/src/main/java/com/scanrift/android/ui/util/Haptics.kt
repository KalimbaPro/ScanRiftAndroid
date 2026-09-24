package com.scanrift.android.ui.util

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

fun HapticFeedback.lightImpact() = performHapticFeedback(HapticFeedbackType.SegmentTick)

fun HapticFeedback.mediumImpact() = performHapticFeedback(HapticFeedbackType.ContextClick)

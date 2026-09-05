package com.scanrift.android.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Animation specs translated from the SwiftUI originals.
 *
 * SwiftUI expresses springs as (response, dampingFraction); Compose wants
 * (stiffness, dampingRatio). The conversion is `stiffness = (2 * PI / response)^2`,
 * with dampingRatio carried across unchanged.
 */
object Motion {

    /** SwiftUI `.spring(response: 0.35, dampingFraction: 0.75)` — expanding pills. */
    fun <T> pill() = spring<T>(dampingRatio = 0.75f, stiffness = 322f)

    /** SwiftUI `.spring(response: 0.3, dampingFraction: 0.6)` — button press scale. */
    fun <T> press() = spring<T>(dampingRatio = 0.6f, stiffness = 438f)

    /** SwiftUI `.snappy`. */
    fun <T> snappy() = spring<T>(dampingRatio = 0.85f, stiffness = 438f)

    /** iOS `.interpolatingSpring(stiffness: 150, damping: 15)` — card tilt release. */
    fun <T> cardTilt() = spring<T>(dampingRatio = 0.61f, stiffness = 150f)

    fun <T> standard() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    fun <T> fade(durationMs: Int = 300) = tween<T>(durationMillis = durationMs)

    const val FOIL_CYCLE_MS = 1_000
    const val CHIP_PULSE_MS = 550
    const val FULLSCREEN_TOGGLE_MS = 250
    const val CHOSEN_FADE_MS = 300
    const val HIGHLIGHT_BORDER_MS = 400
    const val CHART_ENTRY_MS = 800
    const val OVERLAY_STATE_MS = 200
}

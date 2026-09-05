package com.scanrift.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * @param dynamicColor opt *in* to Material You. Off by default, and deliberately so:
 *   the app's chrome sits directly against six fixed domain hues, a fixed yellow
 *   starting-player chip and three fixed score-category colours. A wallpaper-derived
 *   primary routinely collides with them — a purple wallpaper makes `primary`
 *   indistinguishable from `DomainChaos` (#6B4891). The hand-tuned scheme also mirrors
 *   the iOS accent, which keeps the two apps looking like the same product.
 *   Settings exposes a toggle for users who want it anyway.
 */
@Composable
fun ScanRiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ScanRiftDarkColorScheme
        else -> ScanRiftLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ScanRiftTypography,
        content = content,
    )
}

package com.scanrift.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ── Brand ────────────────────────────────────────────────────────────────────
// Matches the iOS accent (#007AFF).
val ScanRiftBlue = Color(0xFF007AFF)
val ScanRiftBlueDark = Color(0xFF0A84FF)

// ── Domains ──────────────────────────────────────────────────────────────────
// Canonical palette from iOS `Constants.DomainColors`.
//
// Two prior palettes disagreed with this one: the old Android `Color.kt` (which had
// Body green, Fury orange and Chaos red — all wrong) and iOS's `CardIconTile`, which
// uses SwiftUI semantic colours. This is the set the rest of the app renders against
// (player tiles, deck stats, deck tiles, rune chips), so it wins.
val DomainBody = Color(0xFFE2710C)
val DomainCalm = Color(0xFF15AA71)
val DomainChaos = Color(0xFF6B4891)
val DomainFury = Color(0xFFCB202D)
val DomainMind = Color(0xFF22779A)
val DomainOrder = Color(0xFFCCA900)
val DomainColorless = Color(0xFF8E8E93)

/** Colourless and unknown domains both fall through to grey, as on iOS. */
fun domainColor(domain: String?): Color = when (domain) {
    "Body" -> DomainBody
    "Calm" -> DomainCalm
    "Chaos" -> DomainChaos
    "Fury" -> DomainFury
    "Mind" -> DomainMind
    "Order" -> DomainOrder
    else -> DomainColorless
}

// ── Rarity ───────────────────────────────────────────────────────────────────
val RarityCommon = Color(0xFF8E8E93)
val RarityUncommon = Color(0xFF34C759)
val RarityRare = Color(0xFF007AFF)
val RarityEpic = Color(0xFFAF52DE)
val RarityShowcase = Color(0xFFFFD60A)

fun rarityColor(rarity: String?): Color = when (rarity) {
    "Common" -> RarityCommon
    "Uncommon" -> RarityUncommon
    "Rare" -> RarityRare
    "Epic" -> RarityEpic
    "Showcase" -> RarityShowcase
    else -> RarityCommon
}

// ── Point tracker score categories ───────────────────────────────────────────
// iOS `ScoreCategory.color`.
val ScoreConquer = Color(0xFFE8641B)
val ScoreHold = Color(0xFF2E7CD6)
val ScoreAbility = Color(0xFF8B5CC9)

// ── Card list presets ────────────────────────────────────────────────────────
// iOS `CreateListSheet` swatches. Order matters — it drives the 5-per-row picker grid.
val ListColorPresets: List<Pair<String, String>> = listOf(
    "Orange" to "#FF9500",
    "Red" to "#FF3B30",
    "Pink" to "#FF2D55",
    "Purple" to "#AF52DE",
    "Blue" to "#007AFF",
    "Teal" to "#5AC8FA",
    "Green" to "#34C759",
    "Yellow" to "#FFD60A",
    "Mint" to "#00C7BE",
    "Indigo" to "#5856D6",
)

const val DEFAULT_LIST_COLOR_HEX = "#FF9500"
val WishlistYellow = Color(0xFFFFD60A)

/** Foil badge gold. */
val FoilGold = Color(0xFFFFD700)

/**
 * Parses `#RRGGBB` / `#AARRGGBB`, falling back to the default list colour.
 * List colours come from persisted user data, so this must never throw.
 */
fun parseHexColor(hex: String?, fallback: Color = Color(0xFFFF9500)): Color {
    val cleaned = hex?.trim()?.removePrefix("#") ?: return fallback
    return when (cleaned.length) {
        6 -> cleaned.toLongOrNull(16)?.let { Color(it or 0xFF000000L) } ?: fallback
        8 -> cleaned.toLongOrNull(16)?.let { Color(it) } ?: fallback
        else -> fallback
    }
}

// ── Material 3 schemes ───────────────────────────────────────────────────────
internal val ScanRiftLightColorScheme: ColorScheme = lightColorScheme(
    primary = ScanRiftBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF545F70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3DAFF),
    onTertiaryContainer = Color(0xFF251431),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color.White,
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

internal val ScanRiftDarkColorScheme: ColorScheme = darkColorScheme(
    primary = ScanRiftBlueDark,
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFBCC7DB),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3C4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFD7BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F5F),
    onTertiaryContainer = Color(0xFFF3DAFF),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

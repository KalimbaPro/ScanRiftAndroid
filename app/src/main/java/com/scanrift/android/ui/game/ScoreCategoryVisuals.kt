package com.scanrift.android.ui.game

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.scanrift.android.domain.model.GameResult
import com.scanrift.android.domain.model.ScoreCategory
import com.scanrift.android.ui.theme.ScoreAbility
import com.scanrift.android.ui.theme.ScoreConquer
import com.scanrift.android.ui.theme.ScoreHold

/**
 * How a score category looks, in one place.
 *
 * The category buttons, the picker that pops out of a tap zone and the breakdown bar
 * all have to agree on colour and glyph, or the bar stops being readable as "these are
 * the points I scored with that button".
 *
 * Glyphs match iOS: `bolt.fill`, `shield.fill`, `sparkles`.
 */
val ScoreCategory.color: Color
    get() = when (this) {
        ScoreCategory.CONQUER -> ScoreConquer
        ScoreCategory.HOLD -> ScoreHold
        ScoreCategory.ABILITY -> ScoreAbility
    }

val ScoreCategory.icon: ImageVector
    get() = when (this) {
        ScoreCategory.CONQUER -> Icons.Filled.Bolt
        ScoreCategory.HOLD -> Icons.Filled.Shield
        ScoreCategory.ABILITY -> Icons.Filled.AutoAwesome
    }

val GameResult.icon: ImageVector
    get() = when (this) {
        GameResult.WIN -> Icons.Filled.EmojiEvents
        GameResult.LOSS -> Icons.Filled.Cancel
        GameResult.DRAW -> Icons.Filled.Balance
    }

val GameResult.tint: Color
    get() = when (this) {
        GameResult.WIN -> Color(0xFF34C759)
        GameResult.LOSS -> Color(0xFFFF3B30)
        GameResult.DRAW -> Color(0xFFFF9500)
    }

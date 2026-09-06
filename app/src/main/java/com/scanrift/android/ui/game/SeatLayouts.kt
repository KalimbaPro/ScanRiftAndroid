package com.scanrift.android.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Seat geometry for 2, 3 and 4 players.
 *
 * Two independent rotations per seat, exactly as on iOS:
 *
 * - `rotation` turns the whole tile so the seated player reads it right way up.
 * - `legendRotation` additionally turns the tile's *content* for side seats, which is
 *   what makes a player sitting on the left of the phone see their own tile upright.
 *
 * Not size-class aware — like iOS, this is driven purely by player count. On a larger
 * screen the seats simply get bigger.
 */
typealias SeatContent = @Composable (index: Int, rotation: Float, legendRotation: Float) -> Unit

@Composable
fun SeatLayout(
    playerCount: Int,
    modifier: Modifier = Modifier,
    centerBar: @Composable () -> Unit,
    seat: SeatContent,
) {
    when (playerCount) {
        2 -> TwoPlayerLayout(modifier, centerBar, seat)
        3 -> ThreePlayerLayout(modifier, centerBar, seat)
        else -> FourPlayerLayout(modifier, centerBar, seat)
    }
}

@Composable
private fun TwoPlayerLayout(modifier: Modifier, centerBar: @Composable () -> Unit, seat: SeatContent) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { seat(1, 180f, 0f) }
        Row(Modifier.fillMaxWidth()) { centerBar() }
        Box(Modifier.weight(1f)) { seat(0, 0f, 0f) }
    }
}

@Composable
private fun ThreePlayerLayout(modifier: Modifier, centerBar: @Composable () -> Unit, seat: SeatContent) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { seat(0, 180f, 0f) }
        Row(Modifier.fillMaxWidth()) { centerBar() }
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f)) { seat(1, 0f, 90f) }
            Box(Modifier.weight(1f)) { seat(2, 0f, -90f) }
        }
    }
}

@Composable
private fun FourPlayerLayout(modifier: Modifier, centerBar: @Composable () -> Unit, seat: SeatContent) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f)) { seat(0, 180f, -90f) }
            Box(Modifier.weight(1f)) { seat(2, 180f, 90f) }
        }
        Row(Modifier.fillMaxWidth()) { centerBar() }
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f)) { seat(1, 0f, 90f) }
            Box(Modifier.weight(1f)) { seat(3, 0f, -90f) }
        }
    }
}

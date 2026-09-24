package com.scanrift.android.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.GameResult
import com.scanrift.android.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSheet(
    title: String,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    scrollable: Boolean = true,
    action: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = Dimens.SheetMaxWidth,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = Dimens.SheetMaxWidth)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(dismissLabel)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(Modifier.align(Alignment.CenterEnd)) { action() }
            }
            Column(
                modifier = Modifier
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
fun SectionFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun GameRecordSheet(
    players: List<PlayerState>,
    deckNames: Map<String, String>,
    onSkip: () -> Unit,
    onSave: (List<PendingGameRecord>) -> Unit,
) {
    val isBestOfThree = players.size == 2
    var gameName by rememberSaveable { mutableStateOf("") }
    var rounds by remember { mutableStateOf(unsetRounds) }
    var entries by remember { mutableStateOf(if (isBestOfThree) emptyList() else freeForAllRecords(players)) }
    val canSave = if (isBestOfThree) rounds.any { it != RoundOutcome.UNSET } else entries.isNotEmpty()

    GameSheet(
        title = "End Game",
        onDismiss = onSkip,
        dismissLabel = "Skip",
        action = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        if (isBestOfThree) {
                            bestOfThreeRecords(players, rounds, gameName)
                        } else {
                            entries.map { it.copy(gameName = gameName) }
                        },
                    )
                },
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        },
    ) {
        SectionHeader("Game")
        OutlinedTextField(
            value = gameName,
            onValueChange = { gameName = it },
            label = { Text("Game name (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isBestOfThree) {
            BestOfThreeSection(players[0].name, players[1].name, rounds) { rounds = it }
        } else {
            entries.forEachIndexed { index, entry ->
                fun update(transform: (PendingGameRecord) -> PendingGameRecord) {
                    entries = entries.toMutableList().apply { this[index] = transform(entry) }
                }
                SectionHeader(
                    entry.deckId?.let { deckNames[it] }?.let { "${entry.playerName} • $it" } ?: entry.playerName,
                )
                ResultPicker(entry.result) { picked -> update { it.copy(result = picked) } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Final score")
                    Text("${entry.pointsScored}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = entry.opponentName,
                    onValueChange = { name -> update { it.copy(opponentName = name) } },
                    label = { Text("Opponent name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun BestOfThreeSection(
    leftName: String,
    rightName: String,
    rounds: List<RoundOutcome>,
    onRoundsChange: (List<RoundOutcome>) -> Unit,
) {
    SectionHeader("Best of 3")
    BestOfThreeEditor(leftName, rightName, rounds, onRoundsChange)
    SectionFooter("Tap a player to mark the round winner. Tap again to clear.")
}

@Composable
fun ResultPicker(selected: GameResult, onSelect: (GameResult) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        GameResult.entries.forEachIndexed { index, result ->
            SegmentedButton(
                selected = selected == result,
                onClick = { onSelect(result) },
                shape = SegmentedButtonDefaults.itemShape(index, GameResult.entries.size),
                icon = { Icon(result.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            ) { Text(result.displayName) }
        }
    }
}

@Composable
private fun BestOfThreeEditor(
    leftName: String,
    rightName: String,
    rounds: List<RoundOutcome>,
    onRoundsChange: (List<RoundOutcome>) -> Unit,
) {
    fun toggle(index: Int, outcome: RoundOutcome) {
        onRoundsChange(
            rounds.toMutableList().apply {
                this[index] = if (this[index] == outcome) RoundOutcome.UNSET else outcome
            },
        )
    }

    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rounds.forEachIndexed { index, outcome ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundPlayerCell(
                    name = leftName,
                    state = outcome.sideState(RoundOutcome.LEFT_WON, RoundOutcome.RIGHT_WON),
                    onClick = { toggle(index, RoundOutcome.LEFT_WON) },
                    modifier = Modifier.weight(1f),
                )
                TieCell(
                    active = outcome == RoundOutcome.TIED,
                    round = index + 1,
                    onClick = { toggle(index, RoundOutcome.TIED) },
                )
                RoundPlayerCell(
                    name = rightName,
                    state = outcome.sideState(RoundOutcome.RIGHT_WON, RoundOutcome.LEFT_WON),
                    onClick = { toggle(index, RoundOutcome.RIGHT_WON) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            val secondary = MaterialTheme.colorScheme.onSurfaceVariant
            val footnote = MaterialTheme.typography.bodySmall
            Text(leftName, style = footnote, fontWeight = FontWeight.SemiBold, color = secondary)
            Text(
                "${rounds.leftWins} – ${rounds.rightWins}",
                style = footnote,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(rightName, style = footnote, fontWeight = FontWeight.SemiBold, color = secondary)
        }
    }
}

private enum class CellState { WINNER, LOSER, NEUTRAL }

private fun RoundOutcome.sideState(won: RoundOutcome, lost: RoundOutcome) = when (this) {
    won -> CellState.WINNER
    lost -> CellState.LOSER
    else -> CellState.NEUTRAL
}

@Composable
private fun RoundPlayerCell(name: String, state: CellState, onClick: () -> Unit, modifier: Modifier) {
    val (fill, stroke) = when (state) {
        CellState.WINNER -> Color(0xFF34C759).copy(alpha = 0.22f) to Color(0xFF34C759).copy(alpha = 0.45f)
        CellState.LOSER -> Color(0xFFFF3B30).copy(alpha = 0.20f) to Color(0xFFFF3B30).copy(alpha = 0.40f)
        CellState.NEUTRAL -> Color.Gray.copy(alpha = 0.10f) to Color.Gray.copy(alpha = 0.20f)
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, stroke, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun TieCell(active: Boolean, round: Int, onClick: () -> Unit) {
    val orange = Color(0xFFFF9500)
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 56.dp)
            .clip(shape)
            .background(if (active) orange else Color.Gray.copy(alpha = 0.10f))
            .border(1.dp, if (active) orange.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.20f), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Tie round $round" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Tie",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

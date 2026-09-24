package com.scanrift.android.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.GameRecord
import com.scanrift.android.domain.model.GameResult
import com.scanrift.android.domain.model.ScoreCategory
import com.scanrift.android.ui.components.CardThumbnail
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun DeckGameHistoryButton(
    deckId: String,
    deckName: String,
    viewModel: GameHistoryViewModel = hiltViewModel(),
) {
    LaunchedEffect(deckId) { viewModel.load(deckId) }
    val records by viewModel.records.collectAsStateWithLifecycle()
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val wins = records.count { it.result == GameResult.WIN }
    val losses = records.count { it.result == GameResult.LOSS }

    TextButton(
        onClick = { showHistory = true },
        modifier = Modifier.semantics {
            contentDescription = if (records.isEmpty()) "Game history" else "Game history, $wins wins, $losses losses"
        },
    ) {
        Icon(Icons.Outlined.EmojiEvents, contentDescription = null)
        if (records.isNotEmpty()) {
            Text(
                "$wins–$losses",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }

    if (showHistory) {
        DeckGameHistorySheet(
            deckName = deckName,
            records = records,
            viewModel = viewModel,
            onDismiss = { showHistory = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckGameHistorySheet(
    deckName: String,
    records: List<GameRecord>,
    viewModel: GameHistoryViewModel,
    onDismiss: () -> Unit,
) {
    val cardsById by viewModel.cardsById.collectAsStateWithLifecycle()
    val legends by viewModel.legends.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GameRecord?>(null) }
    var pendingDelete by remember { mutableStateOf<GameRecord?>(null) }
    val scope = rememberCoroutineScope()

    GameSheet(title = "$deckName — Games", onDismiss = onDismiss, dismissLabel = "Close", scrollable = false) {
        if (records.isEmpty()) {
            EmptyHint(Icons.Filled.History, "No games yet", "Finished games for this deck will appear here.")
        } else {
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(records, key = { it.id }) { record ->
                    val swipe = rememberSwipeToDismissBoxState()
                    SwipeToDismissBox(
                        state = swipe,
                        enableDismissFromStartToEnd = false,
                        onDismiss = {
                            pendingDelete = record
                            scope.launch { swipe.reset() }
                        },
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error).padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                            }
                        },
                    ) {
                        GameHistoryRow(
                            record = record,
                            cardsById = cardsById,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable { editing = record },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete record?") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(record); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    editing?.let { record ->
        GameRecordEditorSheet(
            record = record,
            cardsById = cardsById,
            legends = legends,
            onSave = { viewModel.save(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun GameHistoryRow(record: GameRecord, cardsById: Map<String, Card>, modifier: Modifier = Modifier) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val caption = MaterialTheme.typography.labelSmall
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(record.result.icon, contentDescription = record.result.displayName, tint = record.result.tint, modifier = Modifier.width(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(record.name ?: "Game", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (record.notes != null) {
                    Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = "Has notes", tint = secondary, modifier = Modifier.size(12.dp))
                }
            }
            val opponentLegend = record.opponentLegendId?.let { cardsById[it] }
            if (record.opponentName != null || opponentLegend != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(listOfNotNull("vs", record.opponentName).joinToString(" "), style = caption, color = secondary, maxLines = 1)
                    opponentLegend?.let { legend ->
                        LegendThumb(legend, width = 16.dp, height = 22.dp)
                        Text(legend.name, style = caption, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (record.hasCategoryBreakdown) CategoryBreakdown(record)
        }
        if (record.pointsScored != null && record.pointsAllowed != null) {
            Text(
                "${record.pointsScored}–${record.pointsAllowed}",
                style = caption,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = secondary,
            )
        }
        Text(
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(record.date)),
            style = caption,
            color = secondary.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun CategoryBreakdown(record: GameRecord) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            ScoreCategory.CONQUER to record.conquerCount,
            ScoreCategory.HOLD to record.holdCount,
            ScoreCategory.ABILITY to record.abilityCount,
        ).forEach { (category, count) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(category.icon, contentDescription = category.displayName, tint = category.color, modifier = Modifier.size(12.dp))
                Text(
                    "${count ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LegendThumb(legend: Card, width: Dp, height: Dp) {
    CardThumbnail(
        card = legend,
        quantity = 1,
        showQuantityBadge = false,
        cornerRadius = 2.dp,
        modifier = Modifier.size(width = width, height = height),
    )
}

private enum class LegendSlot { PLAYER, OPPONENT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameRecordEditorSheet(
    record: GameRecord,
    cardsById: Map<String, Card>,
    legends: List<Card>,
    onSave: (GameRecord) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(record.name.orEmpty()) }
    var opponentName by remember { mutableStateOf(record.opponentName.orEmpty()) }
    var notes by remember { mutableStateOf(record.notes.orEmpty()) }
    var date by remember { mutableLongStateOf(record.date) }
    var legendId by remember { mutableStateOf(record.legendId) }
    var opponentLegendId by remember { mutableStateOf(record.opponentLegendId) }
    val initialRounds = remember { roundsOf(record) }
    var rounds by remember { mutableStateOf(initialRounds ?: unsetRounds) }
    var result by remember { mutableStateOf(record.result) }
    var scored by remember { mutableIntStateOf(record.pointsScored ?: 0) }
    var allowed by remember { mutableIntStateOf(record.pointsAllowed ?: 0) }
    var conquer by remember { mutableIntStateOf(record.conquerCount ?: 0) }
    var hold by remember { mutableIntStateOf(record.holdCount ?: 0) }
    var ability by remember { mutableIntStateOf(record.abilityCount ?: 0) }
    var choosingLegend by remember { mutableStateOf<LegendSlot?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

    fun save() {
        val edited = record.copy(
            name = name.trim().ifEmpty { null },
            opponentName = opponentName.trim().ifEmpty { null },
            notes = notes.trim().ifEmpty { null },
            date = date,
            legendId = legendId,
            opponentLegendId = opponentLegendId,
        ).let { base ->
            if (initialRounds != null) {
                base.withRounds(rounds)
            } else {
                base.copy(pointsScored = scored, pointsAllowed = allowed, result = result)
            }
        }.let { base ->
            if (record.hasCategoryBreakdown) {
                base.copy(conquerCount = conquer, holdCount = hold, abilityCount = ability)
            } else {
                base
            }
        }
        onSave(edited)
    }

    GameSheet(
        title = "Edit Game",
        onDismiss = onDismiss,
        action = { TextButton(onClick = ::save) { Text("Save", fontWeight = FontWeight.SemiBold) } },
    ) {
        SectionHeader("Game")
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(opponentName, { opponentName = it }, label = { Text("Opponent") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FormRow(title = "Date", onClick = { pickingDate = true }) {
            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(date)), color = MaterialTheme.colorScheme.primary)
        }

        SectionHeader("Legends")
        LegendRow("Your legend", legendId?.let { cardsById[it] }) { choosingLegend = LegendSlot.PLAYER }
        LegendRow("Opponent legend", opponentLegendId?.let { cardsById[it] }) { choosingLegend = LegendSlot.OPPONENT }

        if (initialRounds != null) {
            BestOfThreeSection(
                leftName = record.playerName?.ifEmpty { null } ?: "You",
                rightName = opponentName.trim().ifEmpty { "Opponent" },
                rounds = rounds,
                onRoundsChange = { rounds = it },
            )
        } else {
            SectionHeader("Result")
            ResultPicker(result) { result = it }
            StepperRow("Scored", scored) { scored = it }
            StepperRow("Allowed", allowed) { allowed = it }
        }

        if (record.hasCategoryBreakdown) {
            SectionHeader("Point Breakdown")
            StepperRow(ScoreCategory.CONQUER.displayName, conquer, ScoreCategory.CONQUER.icon, ScoreCategory.CONQUER.color) { conquer = it }
            StepperRow(ScoreCategory.HOLD.displayName, hold, ScoreCategory.HOLD.icon, ScoreCategory.HOLD.color) { hold = it }
            StepperRow(ScoreCategory.ABILITY.displayName, ability, ScoreCategory.ABILITY.icon, ScoreCategory.ABILITY.color) { ability = it }
        }

        SectionHeader("Notes")
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            placeholder = { Text("Add a note") },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    choosingLegend?.let { slot ->
        LegendChooserSheet(
            legends = legends,
            onSelect = { picked ->
                when (slot) {
                    LegendSlot.PLAYER -> legendId = picked?.id
                    LegendSlot.OPPONENT -> opponentLegendId = picked?.id
                }
                choosingLegend = null
            },
            onDismiss = { choosingLegend = null },
        )
    }

    if (pickingDate) {
        val zone = ZoneId.systemDefault()
        val original = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), zone)
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = original.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { picked ->
                        val day = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                        date = day.atTime(original.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(picker) }
    }
}

@Composable
private fun FormRow(title: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun LegendRow(title: String, legend: Card?, onClick: () -> Unit) {
    FormRow(title, onClick) {
        if (legend != null) {
            LegendThumb(legend, width = 22.dp, height = 31.dp)
            Text(legend.name, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text("None", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    icon: ImageVector? = null,
    tint: Color = Color.Unspecified,
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.width(22.dp))
        Text(label, Modifier.padding(start = if (icon != null) 8.dp else 0.dp))
        Spacer(Modifier.weight(1f))
        Text("$value", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        IconButton(onClick = { onChange(value - 1) }, enabled = value > 0) { Text("−") }
        IconButton(onClick = { onChange(value + 1) }, enabled = value < STEPPER_MAX) { Text("+") }
    }
}

private const val STEPPER_MAX = 99

@Composable
private fun LegendChooserSheet(legends: List<Card>, onSelect: (Card?) -> Unit, onDismiss: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val query = search.trim()
    GameSheet(
        title = "Choose Legend",
        onDismiss = onDismiss,
        scrollable = false,
        action = {
            TextButton(onClick = { onSelect(null) }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
        },
    ) {
        if (legends.isEmpty()) {
            EmptyHint(Icons.Filled.WorkspacePremium, "No legends loaded", "Sync the card database first.")
        } else {
            OutlinedTextField(search, { search = it }, placeholder = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(legends.filter { it.name.contains(query, ignoreCase = true) }, key = { it.id }) { legend ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(legend) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LegendThumb(legend, width = 30.dp, height = 42.dp)
                        Text(legend.name)
                    }
                }
            }
        }
    }
}

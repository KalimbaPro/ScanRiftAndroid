package com.scanrift.android.ui.decks

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.domain.model.Card
import com.scanrift.android.ui.adaptive.AdaptiveRules
import com.scanrift.android.ui.game.DeckGameHistoryButton

sealed interface DetailTarget {
    data class Browser(val card: Card) : DetailTarget
    data class Editor(val entryId: Long) : DetailTarget
}

/**
 * The deck builder.
 *
 * Browser and editor sit side by side once the pane is 600dp or wider; below that they
 * share the space behind a segmented control, exactly as iOS does. The split keys off
 * the **pane's** width, so it behaves correctly whether this screen is full-window or
 * the detail half of a list-detail layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckBuilderScreen(
    deckId: String,
    onBack: () -> Unit,
    viewModel: DeckBuilderViewModel = hiltViewModel(),
) {
    LaunchedEffect(deckId) { viewModel.load(deckId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var exportMenuOpen by remember { mutableStateOf(false) }
    var importMenuOpen by remember { mutableStateOf(false) }
    var importMode by remember { mutableStateOf<DeckImportMode?>(null) }
    // Held while the replace warning is up, so the pasted text survives the dialog.
    var pendingImport by remember { mutableStateOf<Pair<DeckImportMode, String>?>(null) }
    var detailTarget by remember { mutableStateOf<DetailTarget?>(null) }

    fun share(text: String?) {
        if (text.isNullOrBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share deck"))
    }

    fun runImport(mode: DeckImportMode, raw: String) {
        when (mode) {
            DeckImportMode.TEXT -> viewModel.importText(raw)
            DeckImportMode.TTS -> viewModel.importTts(raw)
        }
    }

    val onOpenCard: (Card) -> Unit = { detailTarget = DetailTarget.Browser(it) }
    val onOpenEntry: (Long) -> Unit = { detailTarget = DetailTarget.Editor(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.deck?.name ?: "Deck") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    state.deck?.let { DeckGameHistoryButton(deckId = deckId, deckName = it.name) }
                    IconButton(onClick = { importMenuOpen = true }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Import deck")
                    }
                    DropdownMenu(expanded = importMenuOpen, onDismissRequest = { importMenuOpen = false }) {
                        DeckImportMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.title) },
                                onClick = { importMenuOpen = false; importMode = mode },
                            )
                        }
                    }

                    IconButton(onClick = { exportMenuOpen = true }) {
                        Icon(Icons.Filled.IosShare, contentDescription = "Export deck")
                    }
                    DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Export for TTS") },
                            onClick = { exportMenuOpen = false; share(viewModel.exportAsTts()) },
                        )
                        DropdownMenuItem(
                            text = { Text("Export as Text") },
                            onClick = { exportMenuOpen = false; share(viewModel.exportAsText()) },
                        )
                    }
                },
            )
        },
        bottomBar = { ValidationBanner(state) },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            if (AdaptiveRules.useSplitDeckBuilder(maxWidth)) {
                // The split starts even but is draggable — how much room the browser
                // versus the editor deserves depends on what you are doing, and on a
                // fold there is enough width for the choice to matter.
                var browserFraction by rememberSaveable { mutableFloatStateOf(0.5f) }
                val totalWidth = maxWidth
                val density = LocalDensity.current

                Row(Modifier.fillMaxSize()) {
                    DeckCardBrowser(state, viewModel, onOpenCard, Modifier.weight(browserFraction))
                    ResizeHandle(
                        onDrag = { deltaPx ->
                            val deltaFraction = with(density) { deltaPx.toDp() } / totalWidth
                            browserFraction = (browserFraction + deltaFraction).coerceIn(0.25f, 0.75f)
                        },
                    )
                    DeckEditorPanel(state, viewModel, onOpenEntry, Modifier.weight(1f - browserFraction))
                }
            } else {
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                Column(Modifier.fillMaxSize()) {
                    SingleChoiceSegmentedButtonRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        listOf("Cards", "Deck").forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                            ) { Text(label) }
                        }
                    }
                    if (selectedTab == 0) {
                        DeckCardBrowser(state, viewModel, onOpenCard, Modifier.fillMaxSize())
                    } else {
                        DeckEditorPanel(state, viewModel, onOpenEntry, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    detailTarget?.let { target ->
        DeckCardDetailSheet(
            target = target,
            state = state,
            viewModel = viewModel,
            onDismiss = { detailTarget = null },
        )
    }

    importMode?.let { mode ->
        DeckImportSheet(
            mode = mode,
            onDismiss = { importMode = null },
            onImport = { raw ->
                importMode = null
                // An import describes a whole deck, so it replaces what is there. Say so
                // first when that would actually cost the user something.
                if (state.deck?.hasContents == true) pendingImport = mode to raw else runImport(mode, raw)
            },
        )
    }

    pendingImport?.let { (mode, raw) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace this deck?") },
            text = { Text("Importing replaces everything already in this deck.") },
            confirmButton = {
                TextButton(onClick = { pendingImport = null; runImport(mode, raw) }) {
                    Text("Replace", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            },
        )
    }

    importResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::clearImportResult,
            title = { Text("Import complete") },
            text = { Text(result.summary) },
            confirmButton = { TextButton(onClick = viewModel::clearImportResult) { Text("OK") } },
        )
    }
}

/**
 * The draggable divider between the browser and the editor.
 *
 * Wider than the hairline it draws so it is actually grabbable, and it advertises a
 * resize cursor for anyone on a desktop-class device.
 */
@Composable
private fun ResizeHandle(onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(12.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { onDrag(it) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider(Modifier.fillMaxHeight())
    }
}

@Composable
private fun ValidationBanner(state: DeckBuilderState) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val errors = state.validationErrors

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        HorizontalDivider()
        Column(
            Modifier
                .clickable(
                    enabled = errors.isNotEmpty(),
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (errors.isEmpty()) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (errors.isEmpty()) ValidGreen else WarningOrange,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (errors.isEmpty()) {
                        "Deck is valid"
                    } else {
                        "${errors.size} issue${if (errors.size == 1) "" else "s"}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (state.missingCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.ShoppingBag, contentDescription = null, tint = MissingRed, modifier = Modifier.size(16.dp))
                        Text(
                            "${state.missingCount} missing",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MissingRed,
                        )
                    }
                }
                Text(
                    "${state.deck?.totalCardCount ?: 0} cards",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (errors.isNotEmpty()) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                        contentDescription = if (expanded) "Hide issues" else "Show issues",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded && errors.isNotEmpty(),
                enter = expandVertically(tween(SECTION_TOGGLE_MS)),
                exit = shrinkVertically(tween(SECTION_TOGGLE_MS)),
            ) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    errors.forEach { error ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                Modifier
                                    .padding(top = 6.dp)
                                    .size(4.dp)
                                    .background(WarningOrange, CircleShape),
                            )
                            Text(
                                text = error.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

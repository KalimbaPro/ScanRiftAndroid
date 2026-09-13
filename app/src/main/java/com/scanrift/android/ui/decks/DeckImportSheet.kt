package com.scanrift.android.ui.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp

/** Which of the two pasteable formats the sheet is collecting. */
enum class DeckImportMode(val title: String, val hint: String) {
    TEXT(
        title = "Import from Text",
        hint = "Paste a decklist with Legend:, Champion:, MainDeck:, Battlefields:, Runes: and Sideboard: headers.",
    ),
    TTS(
        title = "Import TTS Code",
        hint = "Paste a Tabletop Simulator code string. It carries no sections, so cards are placed by type.",
    ),
}

/**
 * Paste sheet for both import formats.
 *
 * The text box is the primary affordance rather than a bare "paste from clipboard"
 * button, because a decklist often arrives half-edited and the user wants to see what
 * they are importing before committing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckImportSheet(
    mode: DeckImportMode,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(mode.title, style = MaterialTheme.typography.titleLarge)
            Text(
                mode.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Decklist") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 320.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { clipboard.getText()?.text?.let { text = it } }) {
                    Text("Paste from clipboard")
                }
                Button(
                    onClick = { onImport(text) },
                    enabled = text.isNotBlank(),
                ) {
                    Text("Import")
                }
            }
        }
    }
}

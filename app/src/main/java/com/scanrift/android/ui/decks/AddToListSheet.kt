package com.scanrift.android.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardList
import com.scanrift.android.ui.theme.DEFAULT_LIST_COLOR_HEX
import com.scanrift.android.ui.theme.ListColorPresets
import com.scanrift.android.ui.theme.parseHexColor
import com.scanrift.android.ui.util.lightImpact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(
    cards: List<Card>,
    lists: List<CardList>,
    onToggle: (CardList, List<Card>) -> Unit,
    onCreateList: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var creating by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (cards.size == 1) "Add to Collection" else "Add ${cards.size} Cards to Collection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Done") }
            }

            if (lists.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No Custom Lists", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Create a list to organize your cards.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            lists.forEach { list ->
                val color = parseHexColor(list.colorHex)
                val ids = list.cards.map { it.id }.toSet()
                val count = cards.count { it.id in ids }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { haptics.lightImpact(); onToggle(list, cards) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(width = 4.dp, height = 32.dp).background(color, RoundedCornerShape(2.dp)))
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = color)
                    Text(list.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (count > 0) {
                        Text(
                            "$count/${cards.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            if (count == cards.size) Icons.Filled.Check else Icons.Filled.Remove,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { creating = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Create New Collection", color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (creating) {
        CreateListDialog(
            existingNames = lists.map { it.name },
            onCreate = { name, color -> onCreateList(name, color); creating = false },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun CreateListDialog(
    existingNames: List<String>,
    onCreate: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf(DEFAULT_LIST_COLOR_HEX) }
    val trimmed = name.trim()
    val duplicate = existingNames.any { it.equals(trimmed, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("List name") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) {
                        { Text("A collection with this name already exists. Please choose a different name.") }
                    } else {
                        null
                    },
                )
                ListColorPresets.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (label, hex) ->
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .background(parseHexColor(hex), CircleShape)
                                    .border(
                                        width = if (hex == colorHex) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape,
                                    )
                                    .clickable { colorHex = hex },
                            ) {
                                if (hex == colorHex) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = label,
                                        tint = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.align(Alignment.Center).size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(trimmed, colorHex) }, enabled = trimmed.isNotEmpty() && !duplicate) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

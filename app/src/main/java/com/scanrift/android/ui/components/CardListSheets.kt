package com.scanrift.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardList
import com.scanrift.android.ui.theme.DEFAULT_LIST_COLOR_HEX
import com.scanrift.android.ui.theme.Dimens
import com.scanrift.android.ui.theme.ListColorPresets
import com.scanrift.android.ui.theme.parseHexColor
import com.scanrift.android.ui.util.lightImpact
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(
    cards: List<Card>,
    lists: List<CardList>,
    onToggle: (CardList, List<Card>) -> Unit,
    onSaveList: suspend (CardList?, String, String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val feedback = LocalHapticFeedback.current
    var creating by rememberSaveable { mutableStateOf(false) }
    val cardIds = cards.map { it.id }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = Dimens.SheetMaxWidth,
    ) {
        SheetHeader(
            title = if (cards.size == 1) "Add to Collection" else "Add ${cards.size} Cards to Collection",
            confirmLabel = "Done",
            onConfirm = onDismiss,
        )
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            if (lists.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Folder,
                        title = "No Custom Lists",
                        message = "Create a list to organize your cards.",
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            items(lists, key = { it.id }) { list ->
                val inList = list.cards.count { it.id in cardIds }
                val color = parseHexColor(list.colorHex)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            feedback.lightImpact()
                            onToggle(list, cards)
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(width = 4.dp, height = 32.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = color)
                    Text(list.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (cards.size > 1) {
                        Text(
                            "$inList/${cards.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when {
                        inList == cards.size && inList > 0 ->
                            Icon(Icons.Filled.Check, contentDescription = "In list", tint = MaterialTheme.colorScheme.primary)
                        inList > 0 ->
                            Icon(Icons.Filled.Remove, contentDescription = "Partly in list", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { creating = true }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Create New Collection", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (creating) {
        CreateListSheet(editing = null, onSave = onSaveList, onDismiss = { creating = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListSheet(
    editing: CardList?,
    onSave: suspend (CardList?, String, String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    var colorHex by rememberSaveable { mutableStateOf(editing?.colorHex ?: DEFAULT_LIST_COLOR_HEX) }
    var showDuplicate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val color = parseHexColor(colorHex)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = Dimens.SheetMaxWidth,
    ) {
        SheetHeader(
            title = if (editing == null) "New Collection" else "Edit Collection",
            confirmLabel = if (editing == null) "Create" else "Save",
            confirmEnabled = name.isNotBlank(),
            onCancel = onDismiss,
            onConfirm = {
                scope.launch {
                    if (onSave(editing, name, colorHex)) onDismiss() else showDuplicate = true
                }
            },
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("Name")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("List name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel("Color")
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                items(ListColorPresets, key = { it.second }) { (label, hex) ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(hex))
                                .clickable(onClickLabel = label) { colorHex = hex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (hex.equals(colorHex, ignoreCase = true)) {
                                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            SectionLabel("Preview")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(width = 4.dp, height = 40.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Icon(Icons.Filled.Folder, contentDescription = null, tint = color)
                Text(
                    text = name.ifBlank { "Preview" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    if (showDuplicate) {
        AlertDialog(
            onDismissRequest = { showDuplicate = false },
            title = { Text("Duplicate Name") },
            text = { Text("A collection with this name already exists. Please choose a different name.") },
            confirmButton = { TextButton(onClick = { showDuplicate = false }) { Text("OK") } },
        )
    }
}

@Composable
fun SheetHeader(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    onCancel: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        onCancel?.let { TextButton(onClick = it, modifier = Modifier.align(Alignment.CenterStart)) { Text("Cancel") } }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 88.dp),
        )
        TextButton(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.align(Alignment.CenterEnd)) {
            Text(confirmLabel, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.size(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

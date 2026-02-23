package com.scanrift.android.ui.screens.collection

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.entity.CardListCrossRef
import com.scanrift.android.data.local.entity.CardListEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddToListViewModel(application: Application) : AndroidViewModel(application) {
    private val cardListDao = ScanRiftDatabase.getInstance(application).cardListDao()

    val lists: StateFlow<List<CardListEntity>> = cardListDao.getAllLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getListIdsForCard(cardId: String): List<String> =
        cardListDao.getListIdsForCard(cardId)

    fun addCardToList(cardId: String, listId: String) {
        viewModelScope.launch {
            cardListDao.addCardToList(CardListCrossRef(listId = listId, cardId = cardId))
        }
    }

    fun removeCardFromList(cardId: String, listId: String) {
        viewModelScope.launch {
            cardListDao.removeCardFromList(CardListCrossRef(listId = listId, cardId = cardId))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(
    cardId: String,
    onDismiss: () -> Unit,
    viewModel: AddToListViewModel = viewModel()
) {
    val lists by viewModel.lists.collectAsState()
    var memberListIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(cardId) {
        memberListIds = viewModel.getListIdsForCard(cardId).toSet()
    }

    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Add to List",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(lists, key = { it.id }) { list ->
                    val isInList = list.id in memberListIds

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isInList) {
                                    viewModel.removeCardFromList(cardId, list.id)
                                    memberListIds = memberListIds - list.id
                                } else {
                                    viewModel.addCardToList(cardId, list.id)
                                    memberListIds = memberListIds + list.id
                                }
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isInList,
                            onCheckedChange = null
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            list.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (list.isSystem) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

package com.scanrift.android.ui.screens.collection

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.entity.CardEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CardPagerViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ScanRiftDatabase.getInstance(application)
    private val cardDao = database.cardDao()
    private val collectionEntryDao = database.collectionEntryDao()

    val allCards: StateFlow<List<CardEntity>> = cardDao.getAllCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ownedQuantities: StateFlow<Map<String, Int>> = collectionEntryDao.getAllWithCards()
        .map { entries ->
            entries.groupBy { it.entry.cardId }
                .mapValues { (_, group) -> group.sumOf { it.entry.quantity } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
}

@Composable
fun CardPagerScreen(
    startIndex: Int = 0,
    viewModel: CardPagerViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val cards by viewModel.allCards.collectAsState()
    val ownedQuantities by viewModel.ownedQuantities.collectAsState()

    if (cards.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (cards.size - 1).coerceAtLeast(0)),
        pageCount = { cards.size }
    )

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val card = cards[page]
        val quantity = ownedQuantities[card.id] ?: 0
        CardDetailScreen(
            card = card,
            quantity = quantity,
            onBack = onBack
        )
    }
}

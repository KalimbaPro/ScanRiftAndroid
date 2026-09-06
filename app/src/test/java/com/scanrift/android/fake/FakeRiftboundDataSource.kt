package com.scanrift.android.fake

import com.scanrift.android.data.remote.datasource.RiftboundDataSource
import com.scanrift.android.data.remote.dto.CardDto
import com.scanrift.android.data.remote.dto.RiftboundSetDto

/** Stands in for the network. Mirrors iOS's `MockAPI` in the de-duplication tests. */
class FakeRiftboundDataSource(
    var cards: List<CardDto> = emptyList(),
    var sets: List<RiftboundSetDto> = emptyList(),
) : RiftboundDataSource {

    var fetchedSetIds: MutableList<String> = mutableListOf()

    override suspend fun fetchAllCards(onProgress: ((Int, Int) -> Unit)?): List<CardDto> {
        onProgress?.invoke(1, 1)
        return cards
    }

    override suspend fun fetchCardsInSet(setId: String, onProgress: ((Int, Int) -> Unit)?): List<CardDto> {
        fetchedSetIds += setId
        onProgress?.invoke(1, 1)
        return cards.filter { it.set.setId == setId }
    }

    override suspend fun fetchSets(): List<RiftboundSetDto> = sets
}

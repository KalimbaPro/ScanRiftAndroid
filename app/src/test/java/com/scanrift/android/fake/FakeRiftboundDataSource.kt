package com.scanrift.android.fake

import com.scanrift.android.data.remote.datasource.RiftboundDataSource
import com.scanrift.android.data.remote.dto.CardDto
import com.scanrift.android.data.remote.dto.LegalityResponseDto
import com.scanrift.android.data.remote.dto.RiftboundSetDto
import com.scanrift.android.data.remote.dto.VersionDto

/** Stands in for the network. Mirrors iOS's `MockAPI` in the de-duplication tests. */
class FakeRiftboundDataSource(
    var cards: List<CardDto> = emptyList(),
    var sets: List<RiftboundSetDto> = emptyList(),
) : RiftboundDataSource {

    var fetchedSetIds: MutableList<String> = mutableListOf()

    /** What `/version` answers. Null means the source has no version endpoint. */
    var versionTag: String? = null

    /** What `/legality` answers, per region. Absent means "no legality endpoint". */
    var legality: MutableMap<String, LegalityResponseDto> = mutableMapOf()

    var fetchAllCalls = 0
    var legalityCalls = 0

    override suspend fun fetchAllCards(onProgress: ((Int, Int) -> Unit)?): List<CardDto> {
        fetchAllCalls++
        onProgress?.invoke(1, 1)
        return cards
    }

    override suspend fun fetchVersion(): VersionDto? = versionTag?.let { VersionDto(tag = it) }

    override suspend fun fetchLegality(region: String): LegalityResponseDto? {
        legalityCalls++
        return legality[region]
    }

    override suspend fun fetchCardsInSet(setId: String, onProgress: ((Int, Int) -> Unit)?): List<CardDto> {
        fetchedSetIds += setId
        onProgress?.invoke(1, 1)
        return cards.filter { it.set.setId == setId }
    }

    override suspend fun fetchSets(): List<RiftboundSetDto> = sets
}

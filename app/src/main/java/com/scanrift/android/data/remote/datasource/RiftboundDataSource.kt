package com.scanrift.android.data.remote.datasource

import com.scanrift.android.data.remote.dto.CardDto
import com.scanrift.android.data.remote.dto.RiftboundSetDto

/**
 * The catalogue source seam.
 *
 * Three implementations: the network, the bundled JSON used to seed a cold start, and
 * a fake in tests. Everything above this interface — sync, de-duplication, bootstrap —
 * is written against it, which is what makes the ported iOS de-duplication tests
 * possible without a network or a real API.
 */
interface RiftboundDataSource {

    /** @param onProgress invoked as (currentPage, totalPages). */
    suspend fun fetchAllCards(onProgress: ((Int, Int) -> Unit)? = null): List<CardDto>

    suspend fun fetchCardsInSet(setId: String, onProgress: ((Int, Int) -> Unit)? = null): List<CardDto>

    suspend fun fetchSets(): List<RiftboundSetDto>
}

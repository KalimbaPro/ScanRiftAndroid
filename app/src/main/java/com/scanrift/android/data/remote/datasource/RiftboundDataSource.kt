package com.scanrift.android.data.remote.datasource

import com.scanrift.android.data.remote.dto.CardDto
import com.scanrift.android.data.remote.dto.LegalityResponseDto
import com.scanrift.android.data.remote.dto.RiftboundSetDto
import com.scanrift.android.data.remote.dto.VersionDto

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

    /**
     * `GET /legality?region=`. Null when the source has no such endpoint (the bundled
     * seed, test fakes), which callers treat as "leave legality as it is".
     */
    suspend fun fetchLegality(region: String): LegalityResponseDto? = null

    /**
     * The data tag this source serves: `GET /version` for the network, the envelope's
     * `tag` for the bundled seed. Null when the source has none (test fakes, a seed
     * built before tags existed).
     */
    suspend fun fetchVersion(): VersionDto? = null
}

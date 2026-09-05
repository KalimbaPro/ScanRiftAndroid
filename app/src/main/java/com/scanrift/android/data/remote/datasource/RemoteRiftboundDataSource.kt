package com.scanrift.android.data.remote.datasource

import com.scanrift.android.core.Constants
import com.scanrift.android.data.remote.api.RiftboundApi
import com.scanrift.android.data.remote.dto.CardDto
import com.scanrift.android.data.remote.dto.RiftboundSetDto
import javax.inject.Inject

/** Pages through `/cards` and `/sets`, 100 at a time, exactly as iOS does. */
class RemoteRiftboundDataSource @Inject constructor(
    private val api: RiftboundApi,
) : RiftboundDataSource {

    override suspend fun fetchAllCards(onProgress: ((Int, Int) -> Unit)?): List<CardDto> =
        paginate(onProgress) { page -> api.cards(page = page, size = PAGE_SIZE) }

    override suspend fun fetchCardsInSet(setId: String, onProgress: ((Int, Int) -> Unit)?): List<CardDto> =
        paginate(onProgress) { page -> api.cardsInSet(setId = setId, page = page, size = PAGE_SIZE) }

    override suspend fun fetchSets(): List<RiftboundSetDto> {
        val first = api.sets(page = 1, size = PAGE_SIZE)
        if (first.pages <= 1) return first.items
        val rest = (2..first.pages).flatMap { api.sets(page = it, size = PAGE_SIZE).items }
        return first.items + rest
    }

    private suspend inline fun paginate(
        noinline onProgress: ((Int, Int) -> Unit)?,
        fetch: (Int) -> com.scanrift.android.data.remote.dto.PaginatedCardsResponse,
    ): List<CardDto> {
        val first = fetch(1)
        val totalPages = first.pages.coerceAtLeast(1)
        onProgress?.invoke(1, totalPages)
        if (totalPages <= 1) return first.items

        val all = ArrayList<CardDto>(first.total.coerceAtLeast(first.items.size))
        all += first.items
        for (page in 2..totalPages) {
            all += fetch(page).items
            onProgress?.invoke(page, totalPages)
        }
        return all
    }

    private companion object {
        const val PAGE_SIZE = Constants.Api.DEFAULT_PAGE_SIZE
    }
}

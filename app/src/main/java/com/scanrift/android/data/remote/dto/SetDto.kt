package com.scanrift.android.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaginatedSetsResponse(
    val items: List<RiftboundSetDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 0,
    val pages: Int = 1,
)

/**
 * A set as returned by `/sets`.
 *
 * `cardmarket_id` is deliberately not modelled: the live API returns it as a bare
 * string for most sets but as an *array* for others (`"6322"` vs `["6322","6483"]`),
 * so a naive field would fail to decode the whole response. Nothing needs it.
 */
@Serializable
data class RiftboundSetDto(
    val id: String = "",
    val name: String = "",
    @SerialName("set_id") val setId: String = "",
    @SerialName("card_count") val cardCount: Int = 0,
    @SerialName("published_on") val publishedOn: String? = null,
)

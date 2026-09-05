package com.scanrift.android.data.remote.api

import com.scanrift.android.data.remote.dto.PaginatedCardsResponse
import com.scanrift.android.data.remote.dto.PaginatedSetsResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface RiftboundApi {

    @GET("/cards")
    suspend fun cards(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 100,
    ): PaginatedCardsResponse

    @GET("/cards")
    suspend fun cardsInSet(
        @Query("set_id") setId: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 100,
    ): PaginatedCardsResponse

    @GET("/cards")
    suspend fun searchCards(
        @Query("search") query: String,
        @Query("size") size: Int = 100,
    ): PaginatedCardsResponse

    @GET("/sets")
    suspend fun sets(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 100,
    ): PaginatedSetsResponse
}

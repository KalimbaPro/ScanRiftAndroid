package com.scanrift.android.data.remote.api

import com.scanrift.android.data.remote.dto.PaginatedResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface RiftboundApi {

    @GET("/cards")
    suspend fun getCards(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 100
    ): PaginatedResponse

    @GET("/cards")
    suspend fun searchCards(
        @Query("search") query: String,
        @Query("size") size: Int = 100
    ): PaginatedResponse
}

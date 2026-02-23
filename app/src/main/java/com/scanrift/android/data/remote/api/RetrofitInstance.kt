package com.scanrift.android.data.remote.api

import com.scanrift.android.util.Constants
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.Api.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: RiftboundApi by lazy {
        retrofit.create(RiftboundApi::class.java)
    }
}

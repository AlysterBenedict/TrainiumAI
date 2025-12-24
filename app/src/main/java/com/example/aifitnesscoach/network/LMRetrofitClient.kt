package com.example.aifitnesscoach.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object LMRetrofitClient {
    // REPLACE WITH YOUR COMPUTER'S LOCAL IP ADDRESS (e.g., 192.168.1.X)
    // 10.0.2.2 only works for the Android Emulator.
    private const val BASE_URL = "http://192.168.1.6:1234/"

    val instance: LMApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(LMApiService::class.java)
    }
}

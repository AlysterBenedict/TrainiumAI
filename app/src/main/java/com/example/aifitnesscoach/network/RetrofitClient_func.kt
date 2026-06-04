package com.example.aifitnesscoach.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient_func {
    private const val BASE_URL = "http://10.116.91.215:8000/"
    private const val LM_BASE_URL = "http://10.116.91.215:1234/"

    private val commonOkHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val fitnessApi: FitnessApiService_func by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(commonOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FitnessApiService_func::class.java)
    }

    val chatbotApi: ChatbotApiService_func by lazy {
        Retrofit.Builder()
            .baseUrl(LM_BASE_URL)
            .client(commonOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatbotApiService_func::class.java)
    }
}

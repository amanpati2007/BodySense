package com.bodysense

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton network module providing [okHttpClient] and [apiService].
 *
 * Design decisions:
 *  - Logging: BODY in debug, NONE in release (health data must never appear in production logs).
 *  - Retry logic: removed Thread.sleep-based interceptor — retries are handled at the coroutine
 *    level in the Repository using suspend + try/catch, so no thread-pool blocking occurs.
 *  - retryOnConnectionFailure = true handles TCP-level transient errors safely.
 *  - Timeouts: 15s connect, 30s read (prediction inference can take a few seconds).
 */
object NetworkModule {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC  // BASIC only — avoids logging full health data bodies
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .retryOnConnectionFailure(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)   // ML inference can take a moment
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.ML_API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}

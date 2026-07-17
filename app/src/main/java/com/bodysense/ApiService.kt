package com.bodysense

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    // ── Status ───────────────────────────────────────────────────────────────
    @GET("/")
    suspend fun getRoot(): Map<String, String>

    @GET("/health")
    suspend fun getHealth(): HealthResponse

    // ── Per-Disease Prediction ────────────────────────────────────────────────
    @POST("/predict/{disease}")
    suspend fun predict(
        @Path("disease") disease: String,
        @Body data: Map<String, Float>
    ): PredictionResponse
}

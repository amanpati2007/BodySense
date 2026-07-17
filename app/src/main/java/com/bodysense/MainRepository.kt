package com.bodysense

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.bodysense.data.AppDatabase
import com.bodysense.data.HistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * MainRepository — single source of truth for all data operations.
 *
 * Handles:
 *  - API calls (health, prediction, PDF report) with graceful error mapping
 *  - Room database access (history CRUD)
 *  - PDF download and FileProvider sharing
 */
class MainRepository(
    private val application: Application,
    private val apiService: ApiService = NetworkModule.apiService
) {
    private val db = AppDatabase.getDatabase(application)
    private val historyDao = db.historyDao()

    // ── Error mapping ─────────────────────────────────────────────────────────

    /**
     * Maps network exceptions to user-friendly messages.
     * Handles: no internet, server unreachable, timeout, and HTTP error codes.
     */
    private fun mapNetworkError(e: Exception): Exception {
        return when (e) {
            is UnknownHostException ->
                Exception("Server unreachable — check your Wi-Fi and make sure the backend is running.")
            is SocketTimeoutException ->
                Exception("Connection timed out — the server took too long to respond.")
            is ConnectException ->
                Exception("Cannot connect to backend — is it running on ${BuildConfig.ML_API_BASE_URL}?")
            is HttpException -> when (e.code()) {
                422 -> Exception("Invalid input data sent to server (HTTP 422). Check field values.")
                500 -> Exception("Backend error (HTTP 500) — check server logs.")
                503 -> Exception("Backend is starting up, please wait a moment.")
                else -> Exception("Server returned HTTP ${e.code()}: ${e.message()}")
            }
            else -> e
        }
    }

    // ── History ───────────────────────────────────────────────────────────────

    fun getAllHistory() = historyDao.getAllHistory()

    suspend fun deleteHistory(item: HistoryEntity) = withContext(Dispatchers.IO) {
        historyDao.deleteHistory(item)
    }

    suspend fun deleteAllHistory() = withContext(Dispatchers.IO) {
        historyDao.deleteAllHistory()
    }

    // ── Health ────────────────────────────────────────────────────────────────

    suspend fun getHealth(): Result<HealthResponse> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getHealth())
        } catch (e: Exception) {
            Result.failure(mapNetworkError(e))
        }
    }

    // ── Prediction ────────────────────────────────────────────────────────────

    /**
     * Run a prediction for the given disease using the fields from [DiseaseConfig].
     *
     * On success, saves the result to the local history database.
     *
     * @param diseaseId  The disease identifier (e.g. "heart", "diabetes")
     * @param fieldValues Map of field key → float value
     */
    suspend fun predict(
        diseaseId: String,
        fieldValues: Map<String, Float>
    ): Result<PredictionResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.predict(diseaseId, fieldValues)

            // Persist to history
            val inputJson = JSONObject(fieldValues.mapValues { it.value.toString() }).toString()
            val contribsJson = response.topContributors?.let { list ->
                val array = org.json.JSONArray()
                list.forEach { contrib ->
                    org.json.JSONObject().apply {
                        put("feature", contrib.feature)
                        put("contribution", contrib.contribution)
                        put("description", contrib.description)
                    }.also { array.put(it) }
                }
                array.toString()
            }

            historyDao.insertHistory(
                HistoryEntity(
                    diseaseId = diseaseId,
                    riskScore = response.risk,
                    confidence = response.confidence,
                    diagnosis = response.disease,
                    dateMillis = System.currentTimeMillis(),
                    inputDataJson = inputJson,
                    topContributorsJson = contribsJson,
                    explanationText = response.explanation
                )
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(mapNetworkError(e))
        }
    }

    // ── PDF Report ────────────────────────────────────────────────────────────

    /**
     * Request a PDF report from the backend and return a shareable [Intent].
     *
     * Uses the raw OkHttp client because the /report endpoint returns binary (PDF),
     * not JSON, so Retrofit's converter chain is bypassed.
     */
    suspend fun downloadReport(
        diseaseId: String,
        riskScore: Float,
        diagnosis: String,
        fieldValues: Map<String, Float>
    ): Result<Intent> = withContext(Dispatchers.IO) {
        try {
            val reportPayload = JSONObject().apply {
                put("disease_name", diseaseId.replaceFirstChar { it.uppercase() })
                put("risk", riskScore.toDouble())
                put("confidence", 0.85)   // fallback — server already has real value
                put("patient_inputs", JSONObject(fieldValues.mapValues { it.value.toString() }))
            }

            // Build URL safely using the configured base URL
            val baseUrl = BuildConfig.ML_API_BASE_URL.trimEnd('/')
            val url = "$baseUrl/report"

            val requestBody = reportPayload.toString()
                .toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = NetworkModule.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Report generation failed: HTTP ${response.code}")
            }

            val pdfBytes = response.body?.bytes()
                ?: throw Exception("Empty PDF response from server")

            val file = File(
                application.cacheDir,
                "BodySense_Report_${diseaseId.replaceFirstChar { it.uppercase() }}.pdf"
            )
            FileOutputStream(file).use { it.write(pdfBytes) }

            val uri = FileProvider.getUriForFile(
                application,
                "${application.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            Result.success(intent)
        } catch (e: Exception) {
            Result.failure(mapNetworkError(e))
        }
    }
}

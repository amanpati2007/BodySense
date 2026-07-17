package com.bodysense

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bodysense.data.HistoryEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─── UI State ─────────────────────────────────────────────────────────────────

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(
        val risk: Float,
        val confidence: Float,
        val method: String,
        val disease: String,
        val topContributors: List<FeatureContribution>? = null,
        val explanation: String? = null
    ) : UiState()
    data class Error(val message: String) : UiState()
}

// ─── Health State ──────────────────────────────────────────────────────────────

sealed class HealthState {
    object Checking : HealthState()
    data class Healthy(val modelsLoaded: Int, val totalModels: Int) : HealthState()
    object Offline : HealthState()
    data class PartiallyOnline(val modelsLoaded: Int, val totalModels: Int) : HealthState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

/**
 * Main ViewModel for BodySense.
 *
 * Responsibilities:
 *  - Runs disease predictions via [MainRepository]
 *  - Periodically polls /health every [HEALTH_POLL_INTERVAL_MS] when the device is online
 *  - Pauses polling when device is offline (observed from [NetworkMonitor])
 *  - Exposes clean state flows for all UI screens
 *  - Exposes [downloadReport] directly to avoid reflection hacks in the UI layer
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val HEALTH_POLL_INTERVAL_MS = 30_000L  // 30 seconds
    }

    private val repository = MainRepository(application)
    private val networkMonitor =
        (application as BodySenseApplication).networkMonitor

    // ── UI State ──────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ── Health State ──────────────────────────────────────────────────────────
    private val _healthState = MutableStateFlow<HealthState>(HealthState.Checking)
    val healthState: StateFlow<HealthState> = _healthState.asStateFlow()

    /** Human-readable status string for the AssessmentScreen toolbar. */
    private val _healthStatus = MutableStateFlow("Checking connection...")
    val healthStatus: StateFlow<String> = _healthStatus.asStateFlow()

    // ── Connectivity ──────────────────────────────────────────────────────────
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    // ── History ───────────────────────────────────────────────────────────────
    val history = repository.getAllHistory()

    // ── Polling ───────────────────────────────────────────────────────────────
    private var healthPollJob: Job? = null

    init {
        startHealthMonitoring()
    }

    /**
     * Starts a connectivity-aware periodic health monitor.
     * Polls every [HEALTH_POLL_INTERVAL_MS] when online; pauses when offline.
     */
    private fun startHealthMonitoring() {
        viewModelScope.launch {
            networkMonitor.isOnline.collectLatest { online ->
                if (online) {
                    // Device just came online — check immediately, then poll
                    healthPollJob?.cancel()
                    healthPollJob = viewModelScope.launch {
                        while (true) {
                            performHealthCheck()
                            delay(HEALTH_POLL_INTERVAL_MS)
                        }
                    }
                } else {
                    // Device went offline — cancel poll and show offline immediately
                    healthPollJob?.cancel()
                    healthPollJob = null
                    _healthState.update { HealthState.Offline }
                    _healthStatus.update { "No internet connection" }
                }
            }
        }
    }

    /** Manually trigger a health check (e.g. from Refresh button). */
    fun checkHealth() {
        viewModelScope.launch { performHealthCheck() }
    }

    private suspend fun performHealthCheck() {
        _healthState.update { HealthState.Checking }
        _healthStatus.update { "Checking connection..." }

        val result = repository.getHealth()
        if (result.isSuccess) {
            val response = result.getOrNull()!!
            val models = response.models ?: emptyMap()
            val loaded = models.values.count { it == "loaded" }
            val total = if (models.isEmpty()) 1 else models.size

            _healthState.update {
                when {
                    models.isEmpty() -> HealthState.Healthy(1, 1)
                    loaded == total  -> HealthState.Healthy(loaded, total)
                    loaded == 0      -> HealthState.Offline
                    else             -> HealthState.PartiallyOnline(loaded, total)
                }
            }
            _healthStatus.update { "API Healthy • $loaded/$total models" }
        } else {
            _healthState.update { HealthState.Offline }
            _healthStatus.update {
                "Backend Offline: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            }
        }
    }

    // ── Predictions ────────────────────────────────────────────────────────────

    /**
     * Run a disease prediction.
     *
     * @param diseaseId   The disease key (e.g. "heart", "diabetes")
     * @param fieldValues The field values from the assessment form
     */
    fun predict(diseaseId: String, fieldValues: Map<String, Float>) {
        viewModelScope.launch {
            _uiState.update { UiState.Loading }
            val result = repository.predict(diseaseId, fieldValues)
            _uiState.update {
                if (result.isSuccess) {
                    val r = result.getOrNull()!!
                    UiState.Success(
                        risk = r.risk,
                        confidence = r.confidence,
                        method = r.method,
                        disease = r.disease,
                        topContributors = r.topContributors,
                        explanation = r.explanation
                    )
                } else {
                    UiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
                }
            }
        }
    }

    fun reset() {
        _uiState.update { UiState.Idle }
    }

    // ── History ────────────────────────────────────────────────────────────────

    fun deleteHistory(item: HistoryEntity) {
        viewModelScope.launch { repository.deleteHistory(item) }
    }

    fun deleteAllHistory() {
        viewModelScope.launch { repository.deleteAllHistory() }
    }

    // ── Report Download ────────────────────────────────────────────────────────

    /**
     * Download a PDF report for a given history record.
     * Exposed directly to avoid reflection hacks in the UI layer.
     */
    suspend fun downloadReport(
        diseaseId: String,
        riskScore: Float,
        diagnosis: String,
        fieldValues: Map<String, Float>
    ) = repository.downloadReport(diseaseId, riskScore, diagnosis, fieldValues)
}

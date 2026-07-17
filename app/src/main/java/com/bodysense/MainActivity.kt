package com.bodysense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bodysense.ui.*
import com.bodysense.ui.theme.BodySenseTheme
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BodySenseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BodySenseNavHost()
                }
            }
        }
    }
}

@Composable
private fun BodySenseNavHost() {
    val navController = rememberNavController()

    // Single shared ViewModel instance for the whole nav graph
    val viewModel: MainViewModel = viewModel()

    NavHost(navController = navController, startDestination = "loading") {

        composable("loading") {
            LoadingScreen(
                onLoadingComplete = {
                    navController.navigate("dashboard") {
                        popUpTo("loading") { inclusive = true }
                    }
                }
            )
        }

        composable("dashboard") {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToAssessment = { diseaseId ->
                    navController.navigate("assessment/$diseaseId")
                },
                onNavigateToHistory = {
                    navController.navigate("history")
                }
            )
        }

        composable(
            route = "assessment/{diseaseId}",
            arguments = listOf(navArgument("diseaseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val diseaseId = backStackEntry.arguments?.getString("diseaseId") ?: "heart"
            AssessmentScreen(
                diseaseId = diseaseId,
                onBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }

        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onViewReport = { historyItem ->
                    navController.navigate("report/${historyItem.id}")
                }
            )
        }

        composable(
            route = "report/{historyId}",
            arguments = listOf(navArgument("historyId") { type = NavType.IntType })
        ) { backStackEntry ->
            val historyId = backStackEntry.arguments?.getInt("historyId")
                ?: return@composable

            val historyList by viewModel.history.collectAsState(initial = emptyList())
            val historyItem = historyList.find { it.id == historyId }
                ?: return@composable

            val coroutineScope = rememberCoroutineScope()

            val inputsMap = remember(historyItem.inputDataJson) {
                buildMap {
                    try {
                        val json = JSONObject(historyItem.inputDataJson)
                        json.keys().forEach { key -> put(key, json.getString(key)) }
                    } catch (_: Exception) { /* malformed JSON — show empty */ }
                }
            }

            val predictionResponse = remember(historyItem) {
                PredictionResponse(
                    risk = historyItem.riskScore,
                    confidence = historyItem.confidence,
                    method = "ml_model",
                    disease = historyItem.diseaseId
                )
            }

            ReportScreen(
                result = predictionResponse,
                inputs = inputsMap,
                onBack = { navController.popBackStack() },
                onDownloadPdf = {
                    coroutineScope.launch {
                        // Clean API — no reflection hack
                        val intentResult = viewModel.downloadReport(
                            diseaseId = historyItem.diseaseId,
                            riskScore = historyItem.riskScore,
                            diagnosis = historyItem.diagnosis,
                            fieldValues = inputsMap.mapValues { it.value.toFloatOrNull() ?: 0f }
                        )
                        intentResult.onSuccess { intent ->
                            navController.context.startActivity(intent)
                        }
                        intentResult.onFailure { error ->
                            // TODO Phase 3: show snackbar with error.message
                        }
                    }
                }
            )
        }
    }
}

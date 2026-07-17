package com.bodysense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bodysense.PredictionResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    result: PredictionResponse,
    inputs: Map<String, String>,
    onBack: () -> Unit,
    onDownloadPdf: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assessment Report", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onDownloadPdf) {
                        Icon(Icons.Default.Download, contentDescription = "Download PDF")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Clinical Summary",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("Risk Score", style = MaterialTheme.typography.labelMedium)
                        Text("${result.risk}%", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("Confidence: ${result.confidence * 100}%", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Text(
                    text = "Patient Inputs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(inputs.toList()) { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            
            item {
                Spacer(Modifier.height(16.dp))
            }
            
            if (!result.explanation.isNullOrBlank()) {
                item {
                    Text(
                        text = "AI Explanation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = result.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (!result.topContributors.isNullOrEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Top Contributing Factors",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                }
                
                items(result.topContributors) { contrib ->
                    val isPositive = contrib.contribution > 0
                    val impactColor = if (isPositive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    val impactLabel = if (isPositive) "Increased Risk" else "Lowered Risk"
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contrib.feature.replace("_", " ").uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = impactLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = impactColor
                            )
                        }
                        
                        // Visual bar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp),
                            contentAlignment = if (isPositive) Alignment.CenterStart else Alignment.CenterEnd
                        ) {
                            if (isPositive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth((contrib.contribution * 5).toFloat().coerceIn(0.1f, 1.0f))
                                        .background(impactColor)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth((-contrib.contribution * 5).toFloat().coerceIn(0.1f, 1.0f))
                                        .background(impactColor)

                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

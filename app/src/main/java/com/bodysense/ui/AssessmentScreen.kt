package com.bodysense.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bodysense.AssessmentField
import com.bodysense.DiseaseConfig
import com.bodysense.DiseaseConfigs
import com.bodysense.FieldType
import com.bodysense.HealthState
import com.bodysense.MainViewModel
import com.bodysense.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bodysense.ui.animations.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentScreen(
    diseaseId: String,
    onBack: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val config = DiseaseConfigs.forId(diseaseId)
    val uiState by viewModel.uiState.collectAsState()
    val healthState by viewModel.healthState.collectAsState()
    val healthStatus by viewModel.healthStatus.collectAsState()

    // Initialise all field values as empty strings
    val fieldValues = remember(diseaseId) {
        config.fields.associate { it.key to mutableStateOf("") }.toMutableMap()
    }

    val accentColor = Color(config.accentColor)
    val accentContainerColor = accentColor.copy(alpha = 0.12f)
    val primary = MaterialTheme.colorScheme.primary

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Reset prediction when disease changes
    LaunchedEffect(diseaseId) { viewModel.reset() }

    // Auto-scroll to result when prediction completes
    LaunchedEffect(uiState) {
        if (uiState is UiState.Success || uiState is UiState.Error) {
            coroutineScope.launch {
                // Wait for the expand animation to partially finish so layout size updates
                delay(150)
                scrollState.animateScrollTo(
                    scrollState.maxValue,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                )
                // Ensure it's fully at the bottom after animation completes
                delay(300)
                scrollState.animateScrollTo(
                    scrollState.maxValue,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = config.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (dotColor, statusLabel) = when (healthState) {
                                is HealthState.Healthy      -> Color(0xFF4CAF50) to "Connected"
                                is HealthState.PartiallyOnline -> Color(0xFFFF9800) to "Partial"
                                is HealthState.Offline      -> Color(0xFFEF5350) to "Offline"
                                is HealthState.Checking     -> Color(0xFFFFB300) to "Checking"
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(dotColor, CircleShape)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = { viewModel.checkHealth() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh health status")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Disease Hero Card ─────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = accentContainerColor),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(accentColor.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LocalHospital, null, tint = accentColor, modifier = Modifier.size(28.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            config.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${config.fields.size} clinical parameters",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Input Fields ──────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Create, null, tint = primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Clinical Parameters",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    config.fields.forEach { field ->
                        val stateHolder = fieldValues[field.key] ?: remember { mutableStateOf("") }
                        AssessmentFieldInput(
                            field = field,
                            value = stateHolder.value,
                            onValueChange = { stateHolder.value = it },
                        )
                    }
                }
            }

            // ── Predict Button ────────────────────────────────────────────────
            val allFilled = config.fields.all { field ->
                val v = fieldValues[field.key]?.value ?: ""
                v.isNotBlank()
            }

            Button(
                onClick = {
                    val values = config.fields.associate { field ->
                        field.key to (fieldValues[field.key]?.value?.toFloatOrNull() ?: 0f)
                    }
                    viewModel.predict(diseaseId, values)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("predict_button")
                    .bounceClick {
                        val values = config.fields.associate { field ->
                            field.key to (fieldValues[field.key]?.value?.toFloatOrNull() ?: 0f)
                        }
                        if (allFilled && uiState !is UiState.Loading) {
                            viewModel.predict(diseaseId, values)
                        }
                    },
                enabled = allFilled && uiState !is UiState.Loading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.White,
                )
            ) {
                if (uiState is UiState.Loading) {
                    LoadingSpinner(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Analyzing...", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Run Assessment", style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── Error State (below button) ─────────────────────────────────────
            AnimatedVisibility(
                visible = uiState is UiState.Error,
                enter = fadeIn(tween(300)) + expandVertically(tween(300))
            ) {
                if (uiState is UiState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = (uiState as UiState.Error).message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // ── Success Result Card (below button — only shown after prediction) ──
            AnimatedVisibility(
                visible = uiState is UiState.Success,
                enter = fadeIn(tween(400)) + expandVertically(
                    animationSpec = tween(450, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top
                )
            ) {
                if (uiState is UiState.Success) {
                    val result = uiState as UiState.Success
                    RiskResultCard(result = result, config = config, accentColor = accentColor)
                }
            }

            // ── Disclaimer ────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(
                        Icons.Default.Info,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        config.disclaimer,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

// ── Risk Result Card ───────────────────────────────────────────────────────────
@Composable
private fun RiskResultCard(result: UiState.Success, config: DiseaseConfig, accentColor: Color) {
    val riskLevel = when {
        result.risk < 25f -> "Low Risk"
        result.risk < 50f -> "Moderate Risk"
        result.risk < 75f -> "High Risk"
        else              -> "Critical Risk"
    }
    val riskColor = when {
        result.risk < 25f -> Color(0xFF4CAF50)
        result.risk < 50f -> Color(0xFFFFC107)
        result.risk < 75f -> Color(0xFFFF5722)
        else              -> Color(0xFFD32F2F)
    }

    val animatedRisk by animateFloatAsState(
        targetValue = result.risk,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "risk_animation"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = riskColor.copy(alpha = 0.1f)),
        border = BorderStroke(1.5.dp, riskColor.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = config.riskLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = riskColor,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${String.format("%.1f", animatedRisk)}%",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = riskColor,
            )
            Text(
                text = riskLevel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedRisk / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = riskColor,
                trackColor = riskColor.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AssessmentBadge("Confidence", "${String.format("%.0f", result.confidence * 100)}%")
                AssessmentBadge("Method", if (result.method == "ml_model") "ML Model" else "Heuristic")
            }
            if (config.educationalNote.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = config.educationalNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun AssessmentBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

// ── Field Input ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssessmentFieldInput(
    field: AssessmentField,
    value: String,
    onValueChange: (String) -> Unit,
) {
    when (field.type) {
        FieldType.CHOICE -> {
            ChoiceFieldInput(field = field, selected = value, onSelect = onValueChange)
        }
        else -> {
            OutlinedTextField(
                value = value,
                onValueChange = { new ->
                    // Validate number range if bounds set
                    val num = new.toFloatOrNull()
                    if (new.isEmpty() || num != null) {
                        onValueChange(new)
                    }
                },
                label = { Text(field.label, style = MaterialTheme.typography.labelSmall) },
                placeholder = { Text(field.hint, style = MaterialTheme.typography.bodySmall) },
                suffix = if (field.unit.isNotEmpty()) ({
                    Text(field.unit, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }) else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (field.type == FieldType.INTEGER) KeyboardType.Number else KeyboardType.Decimal
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("field_${field.key}"),
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceFieldInput(
    field: AssessmentField,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column {
        Text(
            field.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        // FlowRow wraps chips to the next line when they overflow,
        // eliminating the large gap caused by a single Row with many long labels.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            field.choices.forEach { (label, numVal) ->
                val isSelected = selected == numVal.toString()
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(numVal.toString()) },
                    label = {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    modifier = Modifier.testTag("choice_${field.key}_$label"),
                )
            }
        }
    }
}

// ── Legacy BentoTextField (preserved for test compatibility) ──────────────────
@Composable
fun BentoTextField(value: String, onValueChange: (String) -> Unit, label: String, testTag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        singleLine = true,
    )
}

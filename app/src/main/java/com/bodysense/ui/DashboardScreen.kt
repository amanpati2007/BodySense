package com.bodysense.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bodysense.DiseaseConfig
import com.bodysense.DiseaseConfigs
import com.bodysense.HealthState
import com.bodysense.MainViewModel
import kotlinx.coroutines.delay

// ─── Disease card visual metadata ─────────────────────────────────────────────
private data class DiseaseVisual(
    val config: DiseaseConfig,
    val icon: ImageVector,
    val gradient: List<Color>,
    val duration: String,
)

private val diseaseVisuals = listOf(
    DiseaseVisual(DiseaseConfigs.HEART,      Icons.Default.Favorite,       listOf(Color(0xFFE53935), Color(0xFFFF6F60)), "3 min"),
    DiseaseVisual(DiseaseConfigs.DIABETES,   Icons.Default.WaterDrop,      listOf(Color(0xFF1E88E5), Color(0xFF42A5F5)), "3 min"),
    DiseaseVisual(DiseaseConfigs.KIDNEY,     Icons.Default.LocalHospital,  listOf(Color(0xFF8E24AA), Color(0xFFBA68C8)), "3 min"),
    DiseaseVisual(DiseaseConfigs.STROKE,     Icons.Default.Warning,        listOf(Color(0xFFFB8C00), Color(0xFFFFCC02)), "4 min"),
    DiseaseVisual(DiseaseConfigs.PARKINSONS, Icons.Default.Accessibility,  listOf(Color(0xFF6D4C41), Color(0xFFA1887F)), "5 min"),
    DiseaseVisual(DiseaseConfigs.LIVER,      Icons.Default.Science,        listOf(Color(0xFF43A047), Color(0xFF81C784)), "3 min"),
    DiseaseVisual(DiseaseConfigs.LUNG,       Icons.Default.Air,            listOf(Color(0xFF00ACC1), Color(0xFF4DD0E1)), "3 min"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToAssessment: (String) -> Unit,
    onNavigateToHistory: () -> Unit = {}
) {
    val healthState by viewModel.healthState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "BodySense",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Select a health assessment",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = { viewModel.checkHealth() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh backend status",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .clickable { onNavigateToHistory() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "History",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
            )
        }
    ) { padding ->

        Column(modifier = Modifier.padding(padding)) {

            // ── Backend Status Card ────────────────────────────────────────────
            BackendStatusCard(
                healthState = healthState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // ── Summary row ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryChip(
                    modifier = Modifier.weight(1f),
                    label = "${diseaseVisuals.size} Screenings",
                    icon = Icons.Default.CheckCircle,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                SummaryChip(
                    modifier = Modifier.weight(1f),
                    label = "AI-Powered",
                    icon = Icons.Default.Star,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            // ── Disease Grid ───────────────────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(diseaseVisuals, key = { _, visual -> visual.config.id }) { index, visual ->
                    DiseaseCard(
                        visual = visual,
                        animationDelay = index * 60,
                        onNavigate = onNavigateToAssessment
                    )
                }
            }
        }
    }
}

// ─── Backend Status Card ───────────────────────────────────────────────────────

@Composable
fun BackendStatusCard(
    healthState: HealthState,
    modifier: Modifier = Modifier
) {
    val (dotColor, label, subLabel, showPulse) = when (healthState) {
        is HealthState.Checking ->
            StatusVisuals(Color(0xFFFFB300), "Connecting…", "Checking backend health", true)
        is HealthState.Healthy ->
            StatusVisuals(
                Color(0xFF4CAF50),
                "Backend Connected",
                "Ready • ${healthState.modelsLoaded}/${healthState.totalModels} models loaded",
                false
            )
        is HealthState.PartiallyOnline ->
            StatusVisuals(
                Color(0xFFFF9800),
                "Partially Online",
                "${healthState.modelsLoaded}/${healthState.totalModels} models loaded",
                true
            )
        is HealthState.Offline ->
            StatusVisuals(Color(0xFFEF5350), "Backend Offline", "Tap refresh to retry", false)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_pulse"
    )

    AnimatedContent(
        targetState = healthState,
        label = "status_card_transition"
    ) { state ->
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            color = dotColor.copy(alpha = 0.08f),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Animated status dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .alpha(if (showPulse) pulseAlpha else 1f)
                        .background(dotColor, CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = dotColor
                    )
                    Text(
                        text = subLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class StatusVisuals(
    val dotColor: Color,
    val label: String,
    val subLabel: String,
    val showPulse: Boolean
)

// ─── Summary Chip ──────────────────────────────────────────────────────────────

@Composable
private fun SummaryChip(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor, fontWeight = FontWeight.Medium)
        }
    }
}

// ─── Disease Card ──────────────────────────────────────────────────────────────

@Composable
private fun DiseaseCard(
    visual: DiseaseVisual,
    animationDelay: Int,
    onNavigate: (String) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val animatedAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = animationDelay, easing = FastOutSlowInEasing),
        label = "card_alpha",
    )

    LaunchedEffect(Unit) {
        delay(50)
        visible = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .alpha(animatedAlpha)
            .clickable { onNavigate(visual.config.id) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 8.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Subtle gradient accent at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                visual.gradient.first().copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Brush.linearGradient(visual.gradient.map { it.copy(alpha = 0.15f) }),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        visual.icon,
                        contentDescription = null,
                        tint = visual.gradient.first(),
                        modifier = Modifier.size(26.dp),
                    )
                }

                // Text
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        visual.config.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        visual.config.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp,
                        maxLines = 2,
                    )
                }

                // Duration row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        null,
                        modifier = Modifier.size(11.dp),
                        tint = visual.gradient.first(),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        visual.duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = visual.gradient.first(),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalFocusUiTokens

data class DashboardPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

val MasterMindDashboards = listOf(
    DashboardPreset("command_center", "Command Center", "Balanced multi-device overview", Icons.Default.Dashboard),
    DashboardPreset("anti_doomscroll", "Anti-Doomscroll Shield", "App limits & active lockouts", Icons.Default.Shield),
    DashboardPreset("sleep_circadian", "Sleep & Circadian Lab", "REM wake optimizer & bedtime", Icons.Default.Bedtime),
    DashboardPreset("deep_work", "Deep-Work Cockpit", "Focus sprint, DND & web gate", Icons.Default.Bolt),
    DashboardPreset("habit_matrix", "Habit & Streak Matrix", "Daily habits & consistency", Icons.Default.CheckCircle),
    DashboardPreset("battery_process", "Battery & Process Guard", "Background apps & thermals", Icons.Default.BatteryChargingFull),
    DashboardPreset("shop_inventory", "Shop & Inventory Ops", "Stock health & auto-reorders", Icons.Default.Inventory2),
    DashboardPreset("financial_pulse", "Financial & Profit Pulse", "Revenue, COGS & margins", Icons.Default.AccountBalanceWallet),
    DashboardPreset("voice_diary", "Voice Diary Studio", "Daily log & mood check-in", Icons.Default.Mic),
    DashboardPreset("ai_buddy_hub", "Gemini AI Buddy Hub", "Coach chat & smart nudges", Icons.Default.AutoAwesome),
    DashboardPreset("notebook_reminders", "Notebook & Reminders", "Pinned notes & timed alerts", Icons.Default.EditNote),
    DashboardPreset("minimal_zen", "Minimalist Zen", "Distraction-free top priority", Icons.Default.SelfImprovement),
    DashboardPreset("executive_telemetry", "Executive Telemetry", "High-density KPI scorecard", Icons.Default.TableChart),
    DashboardPreset("creator_sprint", "Creator & Student Sprint", "Deep creation & social lock", Icons.Default.RocketLaunch),
    DashboardPreset("evening_winddown", "Evening Wind-Down", "Sunset lock & tomorrow prep", Icons.Default.NightsStay)
)

data class AppLayoutOption(
    val id: String,
    val title: String,
    val description: String
)

val MasterMindLayoutOptions = listOf(
    AppLayoutOption("bento_grid", "Bento Grid", "Multi-column adaptive tiles side by side"),
    AppLayoutOption("split_carousel", "Spotlight Carousel", "Swipeable horizontal cards + quick board"),
    AppLayoutOption("compact_tiles", "Command Tiles", "High-density dual-pane executive layout"),
    AppLayoutOption("editorial_stream", "Editorial Stream", "Spacious full-width layered cards")
)

@Composable
fun PulseCard(
    modifier: Modifier = Modifier,
    accentBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalFocusUiTokens.current
    val shape = RoundedCornerShape(tokens.cornerRadius)
    val pad = if (tokens.compactDensity) 12.dp else 18.dp
    val borderColor = if (accentBorder) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    when (tokens.cardStyle) {
        "outlined" -> {
            OutlinedCard(
                shape = shape,
                border = BorderStroke(1.2.dp, if (accentBorder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(pad),
                    verticalArrangement = Arrangement.spacedBy(if (tokens.compactDensity) 8.dp else 12.dp),
                    content = content
                )
            }
        }
        "tonal" -> {
            Card(
                shape = shape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, borderColor),
                modifier = modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(pad),
                    verticalArrangement = Arrangement.spacedBy(if (tokens.compactDensity) 8.dp else 12.dp),
                    content = content
                )
            }
        }
        else -> {
            Card(
                shape = shape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (tokens.backgroundPreset == "none") 3.dp else 0.dp),
                border = BorderStroke(1.dp, borderColor),
                modifier = modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(pad),
                    verticalArrangement = Arrangement.spacedBy(if (tokens.compactDensity) 8.dp else 12.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * Renders modular content blocks cleanly according to the user's selected App Layout Style
 * (`bento_grid`, `split_carousel`, `compact_tiles`, or `editorial_stream`), ensuring the app
 * is never locked to a single vertical list.
 */
@Composable
fun MasterMindAdaptiveBoard(
    layoutStyle: String,
    blocks: List<@Composable (Modifier) -> Unit>,
    modifier: Modifier = Modifier
) {
    if (blocks.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wideCols = if (maxWidth >= 700.dp) 3 else 2
        when (layoutStyle) {
            "split_carousel" -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(blocks.take(4).indices.toList()) { idx ->
                            Box(modifier = Modifier.width(305.dp)) {
                                blocks[idx](Modifier.fillMaxWidth())
                            }
                        }
                    }
                    if (blocks.size > 4) {
                        val remaining = blocks.drop(4)
                        remaining.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pair.forEach { block ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        block(Modifier.fillMaxWidth())
                                    }
                                }
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            "bento_grid", "compact_tiles" -> {
                val cols = if (layoutStyle == "compact_tiles") wideCols else 2
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // First item spans full width as focal anchor in bento_grid; compact_tiles grids everything
                    val rest = if (layoutStyle == "bento_grid" && blocks.size > 1) {
                        blocks.first()(Modifier.fillMaxWidth())
                        blocks.drop(1)
                    } else {
                        blocks
                    }
                    rest.chunked(cols).forEach { rowBlocks ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            rowBlocks.forEach { block ->
                                Box(modifier = Modifier.weight(1f)) {
                                    block(Modifier.fillMaxWidth())
                                }
                            }
                            repeat((cols - rowBlocks.size).coerceAtLeast(0)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            else -> { // editorial_stream
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    blocks.forEach { block ->
                        block(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeaderRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(7.dp)
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun PulseBarChart(
    values: List<Float>,
    labels: List<String>,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier: Modifier = Modifier
) {
    val safeValues = if (values.isEmpty()) listOf(0f) else values
    val maxVal = (safeValues.maxOrNull() ?: 1f).coerceAtLeast(1f)
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            val count = safeValues.size
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.56f).coerceAtMost(36.dp.toPx())
            safeValues.forEachIndexed { index, v ->
                val left = index * slotWidth + (slotWidth - barWidth) / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                val ratio = (v / maxVal).coerceIn(0f, 1f)
                val h = size.height * ratio
                if (h > 2f) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
            }
        }
        if (labels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                labels.take(safeValues.size).forEach { lbl ->
                    Text(
                        text = lbl.take(6),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PulseRingGauge(
    progress: Float,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        val stroke = 10.dp.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = ringColor,
            startAngle = -90f,
            sweepAngle = 360f * clamped,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun PulseTrendSparkline(
    points: List<Float>,
    lineColor: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier
) {
    val safe = if (points.size < 2) listOf(0.4f, 0.6f, 0.55f, 0.8f) else points
    val min = safe.minOrNull() ?: 0f
    val max = (safe.maxOrNull() ?: 1f).let { if (it == min) min + 1f else it }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        val stepX = size.width / (safe.size - 1).coerceAtLeast(1)
        val path = Path()
        safe.forEachIndexed { idx, value ->
            val x = idx * stepX
            val norm = (value - min) / (max - min)
            val y = size.height - norm * (size.height - 12f) - 6f
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

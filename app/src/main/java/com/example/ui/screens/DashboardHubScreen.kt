package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserWorkspaceConfig
import com.example.system.ProfileMediaStorage
import com.example.ui.components.ActivityTimeline
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassRingGauge
import com.example.ui.components.GlassTopBar
import com.example.ui.components.InsightCard
import com.example.ui.components.MasterMindAdaptiveBoard
import com.example.ui.components.MasterMindDashboards
import com.example.ui.components.ProgressIndicator
import com.example.ui.components.PulseBarChart
import com.example.ui.components.PulseTrendSparkline
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.StatisticsCard
import com.example.ui.components.TimelineEntryUi
import com.example.ui.viewmodel.FocusPulseViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardHubScreen(
    viewModel: FocusPulseViewModel,
    onNavigateTab: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBusiness: () -> Unit = { onNavigateTab("business") },
    onOpenNotifications: () -> Unit = {}
) {
    val context = LocalContext.current
    val configState by viewModel.workspaceConfig.collectAsStateWithLifecycle()
    val appearance by viewModel.appearanceState.collectAsStateWithLifecycle()
    val intelligence by viewModel.intelligenceState.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsFeed.collectAsStateWithLifecycle()
    val profilePhotoRefreshKey by viewModel.profilePhotoRefreshKey.collectAsStateWithLifecycle()
    val appRules by viewModel.appRules.collectAsStateWithLifecycle()
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val reorders by viewModel.reorders.collectAsStateWithLifecycle()
    val battery by viewModel.batteryTelemetry.collectAsStateWithLifecycle()
    val sleepInsights by viewModel.sleepInsights.collectAsStateWithLifecycle()
    val deepWorkRemSec by viewModel.deepWorkRemainingSec.collectAsStateWithLifecycle()

    val config = (configState as? UiState.Success)?.data ?: UserWorkspaceConfig()
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showWidgetCustomizer by remember { mutableStateOf(false) }

    val uid = viewModel.currentUid
    LaunchedEffect(profilePhotoRefreshKey, uid) {
        profileBitmap = withContext(Dispatchers.IO) {
            ProfileMediaStorage.loadBitmap(context, uid)
        }
    }

    val greeting = remember(config.displayName) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val period = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        val first = config.displayName.trim().substringBefore(" ").ifBlank { "Master Mind" }
        "$period, $first"
    }
    val todayDateLabel = remember {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
    }
    val profileInitial = config.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"

    val analytics = intelligence.analytics
    val habitsDone = remember(habits) { habits.count { it.completedToday >= it.targetPerDay } }
    val pendingTasks = remember(notes) {
        notes.filter { !it.isCompleted }
            .sortedWith(compareByDescending<com.example.data.model.NoteItem> { it.isPinned }.thenBy { it.reminderAtMillis })
    }
    val nowMs = System.currentTimeMillis()
    val lowStockCount = remember(inventory) { inventory.count { it.isLowStock } }
    val totalUnitsInStock = remember(inventory) { inventory.sumOf { it.stockQty } }
    val totalRevenue = remember(inventory) { inventory.sumOf { it.totalRevenue } }
    val totalProfit = remember(inventory) { inventory.sumOf { it.grossProfit } }
    val pricedRetail = remember(inventory) { inventory.filter { it.hasBuyingAndSellingPrice }.sumOf { it.retailValuation } }
    val pricedStockProfit = remember(inventory) { inventory.filter { it.hasBuyingAndSellingPrice }.sumOf { it.expectedStockProfit } }
    val avgMarginPct = if (pricedRetail > 0.0) (pricedStockProfit / pricedRetail) * 100.0 else 0.0

    val timelineEntries = remember(intelligence.recentActivities) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        intelligence.recentActivities.take(6).map { rec ->
            TimelineEntryUi(
                id = rec.id,
                title = rec.label,
                detail = rec.details.ifBlank { "${rec.durationMinutes}m tracked" },
                timeLabel = fmt.format(Date(rec.timestampMs)),
                category = rec.sourceType.replace('_', ' ').replaceFirstChar { it.uppercase() }
            )
        }
    }

    val widgetBlocks = buildList<@Composable (Modifier) -> Unit> {
        appearance.dashboardWidgets.filter { it.visible }.forEach { widget ->
            when (widget.id) {
                "productivity_score" -> add { mod ->
                    val score = analytics.dailyProductivityScore
                    val delta = analytics.scoreDeltaVsYesterday
                    val deltaText = if (delta >= 0) "+$delta vs yesterday" else "$delta vs yesterday"
                    GlassCard(modifier = mod, accentBorder = true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            GlassRingGauge(
                                progress = score / 100f,
                                centerLabel = "$score",
                                modifier = Modifier.size(82.dp)
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Daily Productivity Score",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$deltaText • ${intelligence.profile.peakProductiveWindow} peak window",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AssistChip(
                                        onClick = { onNavigateTab("intelligence") },
                                        label = { Text("Intelligence") },
                                        leadingIcon = { Icon(Icons.Default.Psychology, null, Modifier.size(16.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }

                "ai_daily_insight" -> add { mod ->
                    val briefing = intelligence.briefing
                    val topInsight = intelligence.insights.firstOrNull()
                    if (briefing != null) {
                        InsightCard(
                            modifier = mod,
                            title = briefing.headline,
                            summary = briefing.summary,
                            evidence = briefing.routineRecommendations.firstOrNull() ?: briefing.confidenceLabel,
                            confidence = 0.88f,
                            timestampLabel = "Gemini AI",
                            comparisonPeriod = briefing.confidenceLabel,
                            categoryLabel = "DAILY BRIEFING",
                            actionRecommendation = "Open Intelligence Hub",
                            onActionClick = { onNavigateTab("intelligence") }
                        )
                    } else if (topInsight != null) {
                        InsightCard(
                            modifier = mod,
                            title = topInsight.title,
                            summary = topInsight.summary,
                            evidence = topInsight.evidence,
                            confidence = topInsight.confidence,
                            timestampLabel = "Today",
                            comparisonPeriod = topInsight.comparisonPeriod,
                            categoryLabel = topInsight.category,
                            actionRecommendation = topInsight.actionRecommendation.ifBlank { "Generate Gemini Briefing" },
                            onActionClick = {
                                if (topInsight.actionRecommendation == "Start Deep-Work Sprint") {
                                    viewModel.startDeepWorkSession(config.deepWorkMinutes)
                                } else {
                                    viewModel.generateDailyAiBriefing()
                                }
                            }
                        )
                    } else {
                        GlassCard(modifier = mod, accentBorder = true) {
                            SectionHeaderRow(
                                title = "AI Daily Insight",
                                subtitle = "Synthesize your habits, screen time, and focus patterns with Gemini",
                                icon = Icons.Default.AutoAwesome
                            )
                            GlassButton(
                                text = if (intelligence.briefingLoading) "Analyzing..." else "Generate Daily Briefing",
                                icon = Icons.Default.AutoAwesome,
                                enabled = !intelligence.briefingLoading,
                                onClick = { viewModel.generateDailyAiBriefing() },
                                modifier = Modifier.fillMaxWidth().testTag("home_generate_briefing_btn")
                            )
                        }
                    }
                }

                "habits_today" -> add { mod ->
                    GlassCard(modifier = mod) {
                        SectionHeaderRow(
                            title = "Today's Habit Completion",
                            subtitle = if (habits.isEmpty()) "No habits created yet" else "$habitsDone of ${habits.size} targets reached",
                            icon = Icons.Default.CheckCircle,
                            trailing = {
                                TextButton(onClick = { onNavigateTab("activities") }) {
                                    Text("Manage")
                                }
                            }
                        )
                        val ratio = if (habits.isEmpty()) 0f else habitsDone.toFloat() / habits.size.toFloat()
                        ProgressIndicator(
                            progress = ratio,
                            label = "Daily consistency",
                            trailingLabel = "${(ratio * 100).roundToInt()}%"
                        )
                        habits.take(3).forEach { habit ->
                            val done = habit.completedToday >= habit.targetPerDay
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(habit.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${habit.completedToday}/${habit.targetPerDay} • ${habit.streakDays}d streak",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AssistChip(
                                    onClick = { viewModel.incrementHabit(habit) },
                                    label = { Text(if (done) "Done" else "+1") }
                                )
                            }
                        }
                    }
                }

                "focus_screentime" -> add { mod ->
                    GlassCard(modifier = mod) {
                        SectionHeaderRow(
                            title = "Focus & Screen-Time Summary",
                            subtitle = "${appRules.count { it.isLocked }} locked apps • ${analytics.todayAppLaunches} launches today",
                            icon = Icons.Default.Timer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatisticsCard(
                                label = "Focus Today",
                                value = "${analytics.todayFocusMinutes}m",
                                delta = if (analytics.weekComparison.focusDeltaPercent >= 0) {
                                    "+${analytics.weekComparison.focusDeltaPercent}% vs 7d"
                                } else {
                                    "${analytics.weekComparison.focusDeltaPercent}% vs 7d"
                                },
                                icon = Icons.Default.Bolt,
                                modifier = Modifier.weight(1f)
                            )
                            StatisticsCard(
                                label = "Screen Time",
                                value = "${analytics.todayScreenMinutes}m",
                                delta = if (intelligence.usageAccessGranted) "Live telemetry" else "Usage off",
                                icon = Icons.Default.Schedule,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (deepWorkRemSec > 0L) {
                            GlassButton(
                                text = String.format(Locale.US, "Stop Deep Work (%02d:%02d)", deepWorkRemSec / 60, deepWorkRemSec % 60),
                                icon = Icons.Default.Stop,
                                tonal = true,
                                onClick = { viewModel.stopDeepWorkSession() },
                                modifier = Modifier.fillMaxWidth().testTag("stop_deep_work_btn")
                            )
                        } else {
                            GlassButton(
                                text = "Start ${config.deepWorkMinutes}m Deep-Work Sprint",
                                icon = Icons.Default.PlayArrow,
                                onClick = { viewModel.startDeepWorkSession(config.deepWorkMinutes) },
                                modifier = Modifier.fillMaxWidth().testTag("start_deep_work_btn")
                            )
                        }
                    }
                }

                "pending_tasks" -> add { mod ->
                    val overdueCount = pendingTasks.count { it.reminderAtMillis in 1L..nowMs }
                    GlassCard(modifier = mod, accentBorder = overdueCount > 0) {
                        SectionHeaderRow(
                            title = "Pending Tasks & Reminders",
                            subtitle = if (overdueCount > 0) "$overdueCount overdue • ${pendingTasks.size} open" else "${pendingTasks.size} pending item(s)",
                            icon = Icons.Default.Schedule,
                            trailing = {
                                TextButton(onClick = { onNavigateTab("activities") }) {
                                    Text("View All")
                                }
                            }
                        )
                        if (pendingTasks.isEmpty()) {
                            Text(
                                text = "All tasks and reminders are clear.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            pendingTasks.take(4).forEach { note ->
                                val overdue = note.reminderAtMillis in 1L..nowMs
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(onClick = { viewModel.toggleNoteComplete(note) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = "Complete task",
                                            tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(note.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = if (overdue) "Overdue • ${note.category}" else note.content.ifBlank { note.category },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                "weekly_summary" -> add { mod ->
                    val series = analytics.weeklySeries
                    val comp = analytics.weekComparison
                    GlassCard(modifier = mod) {
                        SectionHeaderRow(
                            title = "Weekly Performance Summary",
                            subtitle = "Avg Score ${comp.currentAvgScore} (${if (comp.scoreDelta >= 0) "+${comp.scoreDelta}" else comp.scoreDelta} vs prior 7d)",
                            icon = Icons.Default.TrendingUp
                        )
                        if (series.isNotEmpty()) {
                            PulseBarChart(
                                values = series.map { it.score.toFloat() },
                                labels = series.map { it.dayLabel }
                            )
                            Spacer(Modifier.height(6.dp))
                            PulseTrendSparkline(
                                points = series.map { (it.focusMinutes + 10L).toFloat() }
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "Focus: ${comp.currentFocusMinutes}m",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Screen: ${comp.currentScreenMinutes}m",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                "Habits: ${comp.currentHabitsDone}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                "recent_activity" -> add { mod ->
                    GlassCard(modifier = mod) {
                        SectionHeaderRow(
                            title = "Recent Activity Timeline",
                            subtitle = "Recorded habits, focus blocks, diary & usage",
                            icon = Icons.Default.AutoAwesome
                        )
                        ActivityTimeline(entries = timelineEntries)
                    }
                }

                "quick_modules" -> add { mod ->
                    GlassCard(modifier = mod) {
                        SectionHeaderRow(
                            title = "Stock, Profit Margin & Sleep Guard",
                            subtitle = "$totalUnitsInStock units across ${inventory.size} goods • Sleep ${String.format(Locale.US, "%.1f", sleepInsights.estimatedSleepHours)}h",
                            icon = Icons.Default.Inventory2
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Available Stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$totalUnitsInStock ($lowStockCount low)", fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Avg Margin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (pricedRetail > 0.0) "${String.format(Locale.US, "%.1f", avgMarginPct)}%" else "—",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Column {
                                Text("Gross Profit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$${totalProfit.roundToInt()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(
                                text = "Stock & Profit Table",
                                icon = Icons.Default.Inventory2,
                                tonal = true,
                                onClick = onOpenBusiness,
                                modifier = Modifier.weight(1f).testTag("home_open_business_button")
                            )
                            GlassButton(
                                text = "Sleep Guard",
                                icon = Icons.Default.Bedtime,
                                tonal = true,
                                onClick = { onNavigateTab("activities") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassTopBar(
                title = greeting,
                subtitle = "$todayDateLabel • ${if (intelligence.monitoringEnabled) "Intelligence Active" else "Monitoring Paused"}",
                profileInitial = profileInitial,
                profileBitmap = profileBitmap,
                unreadNotifications = notifications.size,
                onOpenProfile = onOpenProfile,
                onOpenNotifications = onOpenNotifications,
                trailingExtra = {
                    IconButton(
                        onClick = { showWidgetCustomizer = true },
                        modifier = Modifier.testTag("customize_widgets_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = "Customize Dashboard Widgets"
                        )
                    }
                }
            )
        }

        // Quick Module Launch & Dashboard Preset Strip
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = false,
                        onClick = onOpenBusiness,
                        label = { Text("Stock & Profit (${inventory.size})") },
                        leadingIcon = { Icon(Icons.Default.Inventory2, null, Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("chip_open_business")
                    )
                }
                items(MasterMindDashboards.take(6), key = { it.id }) { preset ->
                    FilterChip(
                        selected = config.activeDashboardId == preset.id,
                        onClick = { viewModel.selectDashboard(preset.id) },
                        label = { Text(preset.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(preset.icon, null, Modifier.size(16.dp)) }
                    )
                }
            }
        }

        item {
            if (widgetBlocks.isEmpty()) {
                GlassCard(accentBorder = true) {
                    Text("All dashboard widgets are hidden.", fontWeight = FontWeight.Bold)
                    GlassButton(
                        text = "Customize Dashboard Widgets",
                        icon = Icons.Default.DashboardCustomize,
                        onClick = { showWidgetCustomizer = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                MasterMindAdaptiveBoard(
                    layoutStyle = appearance.appLayoutStyle,
                    blocks = widgetBlocks
                )
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    if (showWidgetCustomizer) {
        AlertDialog(
            onDismissRequest = { showWidgetCustomizer = false },
            title = { Text("Customize Home Widgets") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(appearance.dashboardWidgets, key = { it.id }) { widget ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = widget.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.moveDashboardWidget(widget.id, -1) }) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                                    }
                                    IconButton(onClick = { viewModel.moveDashboardWidget(widget.id, 1) }) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                                    }
                                    Switch(
                                        checked = widget.visible,
                                        onCheckedChange = { viewModel.toggleDashboardWidget(widget.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showWidgetCustomizer = false }) {
                    Text("Done")
                }
            }
        )
    }
}

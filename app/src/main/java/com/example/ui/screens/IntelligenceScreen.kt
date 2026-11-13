package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.intelligence.MemoryManager
import com.example.data.local.AiMemoryEntity
import com.example.data.local.BehaviouralPatternEntity
import com.example.data.model.UserWorkspaceConfig
import com.example.system.ProfileMediaStorage
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassRingGauge
import com.example.ui.components.GlassTopBar
import com.example.ui.components.InsightCard
import com.example.ui.components.ProgressIndicator
import com.example.ui.components.PulseBarChart
import com.example.ui.components.PulseTrendSparkline
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.StatisticsCard
import com.example.ui.viewmodel.FocusPulseViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntelligenceScreen(
    viewModel: FocusPulseViewModel,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onNavigateActivities: () -> Unit
) {
    val context = LocalContext.current
    val configState by viewModel.workspaceConfig.collectAsStateWithLifecycle()
    val intel by viewModel.intelligenceState.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsFeed.collectAsStateWithLifecycle()
    val profilePhotoRefreshKey by viewModel.profilePhotoRefreshKey.collectAsStateWithLifecycle()
    val config = (configState as? UiState.Success)?.data ?: UserWorkspaceConfig()

    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val uid = viewModel.currentUid
    LaunchedEffect(profilePhotoRefreshKey, uid) {
        profileBitmap = withContext(Dispatchers.IO) {
            ProfileMediaStorage.loadBitmap(context, uid)
        }
    }

    // 0: Briefing & Trends, 1: Patterns & Profile, 2: Memory & Ask Gemini
    var activeTab by remember { mutableIntStateOf(0) }
    var editingPattern by remember { mutableStateOf<BehaviouralPatternEntity?>(null) }
    var editingMemory by remember { mutableStateOf<AiMemoryEntity?>(null) }
    var memoryTierFilter by remember { mutableStateOf("all") }
    var qaQuestion by remember { mutableStateOf("") }

    val profileInitial = config.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"
    val analytics = intel.analytics
    val profile = intel.profile
    val timeFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassTopBar(
                title = "Personal Intelligence",
                subtitle = "Evidence engine • ${intel.patterns.size} patterns • ${intel.memories.size} AI memories",
                profileInitial = profileInitial,
                profileBitmap = profileBitmap,
                unreadNotifications = notifications.size,
                onOpenProfile = onOpenProfile,
                onOpenNotifications = onOpenNotifications,
                trailingExtra = {
                    IconButton(
                        onClick = { viewModel.refreshIntelligencePipeline(runBriefingIfMissing = false) },
                        modifier = Modifier.testTag("intelligence_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recompute patterns & telemetry")
                    }
                }
            )
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = { Text("Briefing & Trends") },
                    leadingIcon = { Icon(Icons.Default.Insights, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("intel_tab_overview")
                )
                FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = { Text("Patterns (${intel.patterns.size})") },
                    leadingIcon = { Icon(Icons.Default.Psychology, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("intel_tab_patterns")
                )
                FilterChip(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    label = { Text("Memory & Ask AI") },
                    leadingIcon = { Icon(Icons.Default.Memory, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("intel_tab_memory")
                )
            }
        }

        if (activeTab == 0) {
            // 1. Daily AI Briefing Card
            item {
                GlassCard(accentBorder = true) {
                    SectionHeaderRow(
                        title = intel.briefing?.headline ?: "Daily AI Intelligence Briefing",
                        subtitle = intel.briefing?.confidenceLabel
                            ?: "Grounded in ${analytics.observationDays} day(s) of usage, habits & focus logs",
                        icon = Icons.Default.AutoAwesome
                    )
                    if (intel.briefingLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Gemini is synthesizing verified patterns & retrieved AI memory...")
                        }
                    } else if (intel.briefing != null) {
                        val b = intel.briefing!!
                        Text(b.summary, style = MaterialTheme.typography.bodyMedium)
                        if (b.routineRecommendations.isNotEmpty()) {
                            Text(
                                text = "Routine Optimizations:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            b.routineRecommendations.forEach { rec ->
                                Text("• $rec", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Text(
                            text = "Tap below to interpret your productivity metrics, app launch patterns, and habit consistency using Gemini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!intel.briefingError.isNullOrBlank()) {
                        Text(
                            text = intel.briefingError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = if (intel.briefing == null) "Generate Briefing" else "Refresh Briefing",
                            icon = Icons.Default.AutoAwesome,
                            enabled = !intel.briefingLoading,
                            onClick = { viewModel.generateDailyAiBriefing() },
                            modifier = Modifier.weight(1f).testTag("intel_generate_briefing_btn")
                        )
                    }
                }
            }

            // 2. Productivity Trends & Weekly Comparison
            item {
                val comp = analytics.weekComparison
                GlassCard {
                    SectionHeaderRow(
                        title = "Productivity & Historical Comparison",
                        subtitle = "${comp.currentLabel} vs ${comp.previousLabel}",
                        icon = Icons.Default.TrendingUp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        GlassRingGauge(
                            progress = analytics.dailyProductivityScore / 100f,
                            centerLabel = "${analytics.dailyProductivityScore}"
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Weekly Avg: ${comp.currentAvgScore}/100 (${if (comp.scoreDelta >= 0) "+${comp.scoreDelta}" else comp.scoreDelta} vs prior)",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Focus: ${comp.currentFocusMinutes}m (${comp.focusDeltaPercent}% vs prior 7d)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Screen: ${comp.currentScreenMinutes}m (${comp.screenDeltaPercent}% vs prior 7d)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    if (analytics.weeklySeries.isNotEmpty()) {
                        PulseTrendSparkline(
                            points = analytics.weeklySeries.map { it.score.toFloat() }
                        )
                        PulseBarChart(
                            values = analytics.weeklySeries.map { it.focusMinutes.toFloat() },
                            labels = analytics.weeklySeries.map { it.dayLabel }
                        )
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { viewModel.generatePeriodReport("Weekly") },
                            label = { Text(if (intel.periodReportLoading) "Generating..." else "Weekly Gemini Report") },
                            enabled = !intel.periodReportLoading
                        )
                        AssistChip(
                            onClick = { viewModel.generatePeriodReport("Monthly") },
                            label = { Text("Monthly Gemini Report") },
                            enabled = !intel.periodReportLoading
                        )
                    }
                    if (!intel.periodReport.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = intel.periodReport!!,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // 3. Screen-Time Categories & Hourly Distribution
            item {
                GlassCard {
                    SectionHeaderRow(
                        title = "Screen-Time & Hourly Profile",
                        subtitle = "Peak Productive: ${profile.peakProductiveWindow} • Peak Active: ${profile.peakActiveWindow}",
                        icon = Icons.Default.Schedule
                    )
                    if (analytics.categoryBreakdown.isEmpty()) {
                        Text(
                            text = if (intel.usageAccessGranted) {
                                "No app foreground sessions recorded yet today."
                            } else {
                                "Grant Usage Access to analyze category duration & hourly distribution."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val maxCatMins = analytics.categoryBreakdown.maxOf { it.minutes }.coerceAtLeast(1L)
                        analytics.categoryBreakdown.take(5).forEach { cat ->
                            ProgressIndicator(
                                progress = cat.minutes.toFloat() / maxCatMins.toFloat(),
                                label = "${cat.category.replaceFirstChar { it.uppercase() }} (${cat.launches} launches)",
                                trailingLabel = "${cat.minutes}m"
                            )
                        }
                    }

                    val hourBuckets = listOf(0, 4, 8, 12, 16, 20)
                    val bucketValues = hourBuckets.map { startHr ->
                        (startHr until startHr + 4).sumOf { hr -> analytics.hourlyActivityMinutes[hr] ?: 0L }.toFloat()
                    }
                    if (bucketValues.any { it > 0f }) {
                        Spacer(Modifier.height(6.dp))
                        Text("4-Hour Activity Distribution", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        PulseBarChart(
                            values = bucketValues,
                            labels = listOf("00h", "04h", "08h", "12h", "16h", "20h"),
                            barColor = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // 4. Evidence-Backed Insight Cards
            items(intel.insights, key = { it.id }) { insight ->
                InsightCard(
                    title = insight.title,
                    summary = insight.summary,
                    evidence = insight.evidence,
                    confidence = insight.confidence,
                    timestampLabel = timeFmt.format(Date(insight.generatedAtMs)),
                    comparisonPeriod = insight.comparisonPeriod,
                    categoryLabel = insight.category,
                    actionRecommendation = insight.actionRecommendation.ifBlank { null },
                    onActionClick = when (insight.actionRecommendation) {
                        "Enable Usage Access" -> { { viewModel.openUsageAccessSettings() } }
                        "Start Deep-Work Sprint" -> { { viewModel.startDeepWorkSession(config.deepWorkMinutes) } }
                        "Review App Limits", "Clear Overdue Tasks", "Adjust Sleep Shield" -> onNavigateActivities
                        else -> null
                    }
                )
            }
        } else if (activeTab == 1) {
            // Personal Intelligence Profile Grid + Editable Pattern Inspector
            item {
                GlassCard(accentBorder = true) {
                    SectionHeaderRow(
                        title = "Personal Intelligence Profile",
                        subtitle = "Synthesized over ${profile.observationDays} observation day(s)",
                        icon = Icons.Default.Verified
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatisticsCard(
                            label = "Peak Focus Hour",
                            value = profile.peakProductiveWindow,
                            icon = Icons.Default.Bolt,
                            modifier = Modifier.weight(1f)
                        )
                        StatisticsCard(
                            label = "Habit Adherence",
                            value = "${profile.habitConsistencyPercent}%",
                            icon = Icons.Default.Verified,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "Primary Distraction Trigger: ${profile.primaryDistractionTrigger}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Sleep Alignment: ${profile.sleepAlignmentLabel} • Avg Daily Screen: ${profile.averageDailyScreenMinutes}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (intel.patterns.isEmpty()) {
                item {
                    GlassCard {
                        Text(
                            text = "No behavioural patterns detected yet. Log habits, tasks, focus sprints, or grant Usage Access and tap Refresh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(intel.patterns, key = { it.id }) { pattern ->
                    val confPct = (pattern.confidence * 100f).roundToInt()
                    GlassCard(accentBorder = pattern.userVerified) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "${pattern.category.uppercase()} • $confPct%${if (pattern.userVerified) " • VERIFIED" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Row {
                                IconButton(onClick = { editingPattern = pattern }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Correct or annotate pattern")
                                }
                                IconButton(onClick = { viewModel.deletePattern(pattern.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete pattern")
                                }
                            }
                        }
                        Text(pattern.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(pattern.summary, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Evidence: ${pattern.evidence} (${pattern.comparisonPeriod})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // 3-Tier AI Memory Manager + Historical Ask Gemini Q&A
            item {
                GlassCard(accentBorder = true) {
                    SectionHeaderRow(
                        title = "Ask Intelligence Engine",
                        subtitle = "Retrieves targeted 3-tier AI memory & compares historical windows",
                        icon = Icons.Default.Psychology
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "How did this week compare to last week?",
                            "When are my peak focus hours?",
                            "What is my biggest distraction trigger?"
                        ).forEach { prompt ->
                            AssistChip(
                                onClick = { viewModel.askIntelligenceQuestion(prompt) },
                                label = { Text(prompt) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = qaQuestion,
                            onValueChange = { qaQuestion = it },
                            label = { Text("Ask about your habits, focus, or trends") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("intel_qa_input")
                        )
                        IconButton(
                            onClick = {
                                val q = qaQuestion.trim()
                                if (q.isNotEmpty()) {
                                    qaQuestion = ""
                                    viewModel.askIntelligenceQuestion(q)
                                }
                            },
                            enabled = !intel.qaLoading && qaQuestion.isNotBlank(),
                            modifier = Modifier.testTag("intel_qa_send_btn")
                        ) {
                            if (intel.qaLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask Gemini Intelligence")
                            }
                        }
                    }
                    intel.qaMessages.reversed().forEach { msg ->
                        Surface(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Q: ${msg.question}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = msg.answer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                if (msg.citedEvidence.isNotEmpty()) {
                                    Text(
                                        text = "Cited: ${msg.citedEvidence.joinToString(" | ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                GlassCard {
                    SectionHeaderRow(
                        title = "Three-Tier AI Memory",
                        subtitle = "Inspect, correct, or remove Short-Term, Long-Term & Analytical memory",
                        icon = Icons.Default.Memory
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "all" to "All (${intel.memories.size})",
                            MemoryManager.TIER_SHORT_TERM to "Short-Term",
                            MemoryManager.TIER_LONG_TERM to "Long-Term",
                            MemoryManager.TIER_ANALYTICAL to "Analytical"
                        ).forEach { (id, label) ->
                            FilterChip(
                                selected = memoryTierFilter == id,
                                onClick = { memoryTierFilter = id },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            val visibleMemories = intel.memories.filter {
                memoryTierFilter == "all" || it.tier == memoryTierFilter
            }
            items(visibleMemories, key = { it.id }) { mem ->
                GlassCard(accentBorder = mem.userEdited) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "${mem.tier.replace('_', ' ').uppercase()}${if (mem.userEdited) " • USER EDITED" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Row {
                            IconButton(onClick = { editingMemory = mem }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit AI memory")
                            }
                            IconButton(onClick = { viewModel.deleteAiMemory(mem.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete AI memory")
                            }
                        }
                    }
                    Text(mem.summary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Evidence samples: ${mem.evidenceCount} • Updated ${timeFmt.format(Date(mem.updatedAtMs))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    editingPattern?.let { pat ->
        var noteDraft by remember(pat.id) { mutableStateOf(pat.userNote.ifBlank { pat.summary }) }
        AlertDialog(
            onDismissRequest = { editingPattern = null },
            title = { Text("Correct / Verify Pattern") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pat.title, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        label = { Text("Corrected observation or personal note") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.verifyOrEditPattern(pat.id, noteDraft)
                        editingPattern = null
                    }
                ) { Text("Verify & Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingPattern = null }) { Text("Cancel") }
            }
        )
    }

    editingMemory?.let { mem ->
        var memDraft by remember(mem.id) { mutableStateOf(mem.summary) }
        AlertDialog(
            onDismissRequest = { editingMemory = null },
            title = { Text("Edit AI Memory (${mem.tier.replace('_', ' ')})") },
            text = {
                OutlinedTextField(
                    value = memDraft,
                    onValueChange = { memDraft = it },
                    label = { Text("Memory summary") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.editAiMemory(mem.id, memDraft)
                        editingMemory = null
                    }
                ) { Text("Save Memory") }
            },
            dismissButton = {
                TextButton(onClick = { editingMemory = null }) { Text("Cancel") }
            }
        )
    }
}

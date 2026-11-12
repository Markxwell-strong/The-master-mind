package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.UserWorkspaceConfig
import com.example.system.ProfileMediaStorage
import com.example.ui.components.MasterMindAdaptiveBoard
import com.example.ui.components.MasterMindDashboards
import com.example.ui.components.MasterMindLayoutOptions
import com.example.ui.components.PulseBarChart
import com.example.ui.components.PulseCard
import com.example.ui.components.PulseRingGauge
import com.example.ui.components.PulseTrendSparkline
import com.example.ui.components.SectionHeaderRow
import com.example.ui.viewmodel.FocusPulseViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun DashboardHubScreen(
    viewModel: FocusPulseViewModel,
    onNavigateTab: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val configState by viewModel.workspaceConfig.collectAsStateWithLifecycle()
    val profilePhotoRefreshKey by viewModel.profilePhotoRefreshKey.collectAsStateWithLifecycle()
    val appRules by viewModel.appRules.collectAsStateWithLifecycle()
    val blockedSites by viewModel.blockedSites.collectAsStateWithLifecycle()
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val reorders by viewModel.reorders.collectAsStateWithLifecycle()
    val diaryEntries by viewModel.diaryEntries.collectAsStateWithLifecycle()
    val focusSessions by viewModel.focusSessions.collectAsStateWithLifecycle()
    val battery by viewModel.batteryTelemetry.collectAsStateWithLifecycle()
    val deviceApps by viewModel.deviceApps.collectAsStateWithLifecycle()
    val sleepInsights by viewModel.sleepInsights.collectAsStateWithLifecycle()
    val deepWorkRemSec by viewModel.deepWorkRemainingSec.collectAsStateWithLifecycle()

    val config = (configState as? UiState.Success)?.data ?: UserWorkspaceConfig()
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val uid = viewModel.currentUid
    LaunchedEffect(profilePhotoRefreshKey, uid) {
        profileBitmap = withContext(Dispatchers.IO) {
            ProfileMediaStorage.loadBitmap(context, uid)
        }
    }

    val activePreset = MasterMindDashboards.firstOrNull { it.id == config.activeDashboardId }
        ?: MasterMindDashboards.first()

    val onlineHours = remember(deviceApps) { deviceApps.sumOf { it.minutesToday } / 60.0 }
    val habitsDone = remember(habits) { habits.count { it.completedToday >= it.targetPerDay } }
    val lowStockItems = remember(inventory) { inventory.filter { it.isLowStock } }
    val totalRevenue = remember(inventory) { inventory.sumOf { it.totalRevenue } }
    val totalProfit = remember(inventory) { inventory.sumOf { it.grossProfit } }
    val todayDiary = remember(diaryEntries) {
        diaryEntries.firstOrNull { it.dateKey == viewModel.todayDateKey() }
    }

    val dashboardBlocks: List<@Composable (Modifier) -> Unit> = when (activePreset.id) {
        "command_center" -> buildList {
            add { mod ->
                HeroCommandBanner(
                    modifier = mod,
                    onlineHours = onlineHours,
                    habitsDone = habitsDone,
                    habitsTotal = habits.size,
                    deepWorkRemSec = deepWorkRemSec,
                    onStartDeepWork = { viewModel.startDeepWorkSession(config.deepWorkMinutes) },
                    onStopDeepWork = { viewModel.stopDeepWorkSession() }
                )
            }
            if ("doomscroll_shield" in config.enabledWidgets) {
                add { mod ->
                    ShieldOverviewWidget(
                        modifier = mod,
                        ruleCount = appRules.size,
                        lockedCount = appRules.count { it.isLocked },
                        siteCount = blockedSites.size,
                        onOpenShield = { onNavigateTab("shield") }
                    )
                }
            }
            if ("sleep_optimizer" in config.enabledWidgets) {
                add { mod ->
                    SleepWidgetCard(
                        modifier = mod,
                        config = config,
                        sleepHours = sleepInsights.estimatedSleepHours,
                        recommendedWindow = sleepInsights.recommendedWakeWindows.firstOrNull().orEmpty(),
                        onTriggerAlarm = { viewModel.triggerSampleAlarm(true) }
                    )
                }
            }
            if ("inventory_alerts" in config.enabledWidgets) {
                add { mod ->
                    InventoryFinancialSummaryCard(
                        modifier = mod,
                        skuCount = inventory.size,
                        lowStockCount = lowStockItems.size,
                        revenue = totalRevenue,
                        profit = totalProfit,
                        openReorders = reorders.count { it.status != "received" },
                        onOpenOps = { onNavigateTab("ops") }
                    )
                }
            }
            if ("diary_ai" in config.enabledWidgets) {
                add { mod ->
                    DiaryAiSummaryCard(
                        modifier = mod,
                        todayScore = todayDiary?.aiScore ?: -1L,
                        feedback = todayDiary?.aiFeedback.orEmpty(),
                        onOpenDiary = { onNavigateTab("diary") }
                    )
                }
            }
        }

        "anti_doomscroll" -> listOf(
            { mod ->
                ShieldOverviewWidget(
                    modifier = mod,
                    ruleCount = appRules.size,
                    lockedCount = appRules.count { it.isLocked },
                    siteCount = blockedSites.size,
                    onOpenShield = { onNavigateTab("shield") }
                )
            },
            { mod ->
                PulseCard(modifier = mod, accentBorder = true) {
                    SectionHeaderRow(
                        title = "Screen Time vs Daily Limits",
                        subtitle = "Live device usage telemetry",
                        icon = Icons.Default.Shield
                    )
                    val topApps = deviceApps.take(5)
                    PulseBarChart(
                        values = topApps.map { it.minutesToday.toFloat().coerceAtLeast(0f) },
                        labels = topApps.map { it.appName }
                    )
                    appRules.forEach { rule ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.appName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${rule.todayUsedMinutes}m / ${rule.dailyLimitMinutes}m limit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (rule.isLocked) {
                                OutlinedButton(onClick = { viewModel.unlockAppRule(rule) }) {
                                    Text("Unlock")
                                }
                            } else {
                                Button(onClick = { viewModel.lockAppUntilWake(rule) }) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Lock")
                                }
                            }
                        }
                    }
                }
            }
        )

        "sleep_circadian" -> listOf(
            { mod ->
                SleepWidgetCard(
                    modifier = mod,
                    config = config,
                    sleepHours = sleepInsights.estimatedSleepHours,
                    recommendedWindow = sleepInsights.recommendedWakeWindows.firstOrNull().orEmpty(),
                    onTriggerAlarm = { viewModel.triggerSampleAlarm(false) }
                )
            },
            { mod ->
                PulseCard(modifier = mod) {
                    SectionHeaderRow(
                        title = "Circadian REM Wake Windows",
                        subtitle = "Synced steps: ${sleepInsights.stepCountToday}",
                        icon = Icons.Default.Bedtime
                    )
                    sleepInsights.recommendedWakeWindows.forEach { win ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = win,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { onNavigateTab("shield") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Adjust Bedtime & Health Sync")
                    }
                }
            }
        )

        "deep_work" -> listOf(
            { mod ->
                DeepWorkHeroCard(
                    modifier = mod,
                    minutes = config.deepWorkMinutes,
                    remainingSec = deepWorkRemSec,
                    dndEnabled = config.deepWorkDndEnabled,
                    sessionsCompleted = focusSessions.size,
                    onStart = { viewModel.startDeepWorkSession(config.deepWorkMinutes) },
                    onStop = { viewModel.stopDeepWorkSession() }
                )
            },
            { mod ->
                PulseCard(modifier = mod) {
                    SectionHeaderRow(
                        title = "Distraction Gateway",
                        subtitle = "${blockedSites.size} websites & ${appRules.count { it.blockDuringDeepWork }} apps shielded",
                        icon = Icons.Default.Bolt
                    )
                    Button(onClick = { onNavigateTab("shield") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Configure Blocked Apps & Domains")
                    }
                }
            }
        )

        "habit_matrix" -> listOf(
            { mod ->
                PulseCard(modifier = mod, accentBorder = true) {
                    SectionHeaderRow(
                        title = "Habit Completion Matrix",
                        subtitle = "$habitsDone of ${habits.size} completed today",
                        icon = Icons.Default.CheckCircle
                    )
                    val progress = if (habits.isEmpty()) 0f else habitsDone.toFloat() / habits.size
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    habits.forEach { h ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.incrementHabit(h) }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(h.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${h.completedToday}/${h.targetPerDay} today • ${h.streakDays}d streak",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AssistChip(onClick = { viewModel.incrementHabit(h) }, label = { Text("+1") })
                        }
                    }
                    OutlinedButton(onClick = { onNavigateTab("habits") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Full Habit Tracker")
                    }
                }
            }
        )

        "battery_process" -> listOf(
            { mod ->
                PulseCard(modifier = mod, accentBorder = true) {
                    SectionHeaderRow(
                        title = "Live Battery Telemetry",
                        subtitle = "${battery.chargeSource} • ${battery.healthLabel}",
                        icon = Icons.Default.BatteryChargingFull
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PulseRingGauge(
                                progress = battery.levelPercent / 100f,
                                modifier = Modifier.size(84.dp)
                            )
                            Text(
                                text = "${battery.levelPercent}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Temp: ${String.format("%.1f", battery.temperatureC)} °C")
                            Text("Voltage: ${battery.voltageMv} mV")
                            Text("Current: ${battery.currentMa} mA")
                            Text("Power Saver: ${if (battery.powerSaveMode) "ON" else "OFF"}")
                        }
                    }
                    Button(onClick = { onNavigateTab("shield") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Manage Background Processes (${deviceApps.count { it.isRunningBackground }} active)")
                    }
                }
            }
        )

        "shop_inventory" -> listOf(
            { mod ->
                InventoryFinancialSummaryCard(
                    modifier = mod,
                    skuCount = inventory.size,
                    lowStockCount = lowStockItems.size,
                    revenue = totalRevenue,
                    profit = totalProfit,
                    openReorders = reorders.count { it.status != "received" },
                    onOpenOps = { onNavigateTab("ops") }
                )
            },
            { mod ->
                PulseCard(modifier = mod) {
                    SectionHeaderRow(
                        title = "Low-Stock & Reorder Queue",
                        subtitle = "${lowStockItems.size} SKUs need attention",
                        icon = Icons.Default.Inventory2
                    )
                    if (lowStockItems.isEmpty()) {
                        Text("All inventory SKUs are above safety stock thresholds.")
                    } else {
                        lowStockItems.take(5).forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${item.name} (${item.sku})", fontWeight = FontWeight.Medium)
                                Text(
                                    "${item.stockQty} left",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        )

        "financial_pulse" -> listOf(
            { mod ->
                InventoryFinancialSummaryCard(
                    modifier = mod,
                    skuCount = inventory.size,
                    lowStockCount = lowStockItems.size,
                    revenue = totalRevenue,
                    profit = totalProfit,
                    openReorders = reorders.count { it.status != "received" },
                    onOpenOps = { onNavigateTab("ops") }
                )
            },
            { mod ->
                PulseCard(modifier = mod) {
                    SectionHeaderRow(
                        title = "SKU Revenue Distribution",
                        subtitle = "Cloud-synced real-time commerce valuation",
                        icon = Icons.Default.AccountBalanceWallet
                    )
                    val topSkus = inventory.sortedByDescending { it.totalRevenue }.take(5)
                    PulseBarChart(
                        values = topSkus.map { it.totalRevenue.toFloat().coerceAtLeast(0f) },
                        labels = topSkus.map { it.sku },
                        barColor = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        )

        "voice_diary" -> listOf(
            { mod ->
                DiaryAiSummaryCard(
                    modifier = mod,
                    todayScore = todayDiary?.aiScore ?: -1L,
                    feedback = todayDiary?.aiFeedback.orEmpty(),
                    onOpenDiary = { onNavigateTab("diary") }
                )
            },
            { mod ->
                PulseCard(modifier = mod) {
                    SectionHeaderRow(
                        title = "Voice-to-Text Journal Status",
                        subtitle = "Today online: ${String.format("%.1f", onlineHours)}h",
                        icon = Icons.Default.Mic
                    )
                    Text(
                        text = todayDiary?.activitiesText?.ifBlank { "No entry recorded yet today." }
                            ?: "Tap below to dictate your day's progress with Voice-to-Text.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = { onNavigateTab("diary") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Record Voice Diary & Run AI Evaluation")
                    }
                }
            }
        )

        "ai_buddy_hub" -> listOf(
            { mod ->
                PulseCard(modifier = mod, accentBorder = true) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_ai_buddy),
                            contentDescription = "Gemini AI Buddy",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gemini AI Buddy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Persona: ${config.aiCoachPersona.replaceFirstChar { it.uppercase() }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = "Ask Gemini for real-time habit coaching, business margin analysis, or online-hour activity recommendations.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = { onNavigateTab("diary") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open AI Buddy Chat")
                    }
                }
            }
        )

        "notebook_reminders" -> listOf(
            { mod ->
                PulseCard(modifier = mod, accentBorder = true) {
                    SectionHeaderRow(
                        title = "Notebook & Due Reminders",
                        subtitle = "${notes.count { !it.isCompleted }} open notes • ${notes.count { it.isPinned }} pinned",
                        icon = Icons.Default.EditNote
                    )
                    notes.sortedByDescending { it.isPinned }.take(5).forEach { n ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(n.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    n.content.take(70),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(n.category.uppercase(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Button(onClick = { onNavigateTab("habits") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Notebook & Reminders")
                    }
                }
            }
        )

        "minimal_zen" -> listOf(
            { mod ->
                DeepWorkHeroCard(
                    modifier = mod,
                    minutes = config.deepWorkMinutes,
                    remainingSec = deepWorkRemSec,
                    dndEnabled = config.deepWorkDndEnabled,
                    sessionsCompleted = focusSessions.size,
                    onStart = { viewModel.startDeepWorkSession(config.deepWorkMinutes, "Zen Single Focus") },
                    onStop = { viewModel.stopDeepWorkSession() }
                )
            },
            { mod ->
                val topHabit = habits.firstOrNull { it.completedToday < it.targetPerDay } ?: habits.firstOrNull()
                PulseCard(modifier = mod) {
                    Text("PRIMARY INTENTION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = topHabit?.title ?: "Log your primary intention in Habits or Diary",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (topHabit != null) {
                        Button(onClick = { viewModel.incrementHabit(topHabit) }) {
                            Text("Complete Priority")
                        }
                    }
                }
            }
        )

        "executive_telemetry" -> listOf(
            { mod ->
                PulseCard(modifier = mod, accentBorder = true) {
                    SectionHeaderRow(
                        title = "Executive KPI Matrix",
                        subtitle = "Cross-domain telemetry across personal & business ops",
                        icon = Icons.Default.AccountBalanceWallet
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        KpiMini("Online Today", "${String.format("%.1f", onlineHours)}h")
                        KpiMini("Habits", "$habitsDone/${habits.size}")
                        KpiMini("Battery", "${battery.levelPercent}%")
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        KpiMini("Revenue", "$${totalRevenue.roundToInt()}")
                        KpiMini("Gross Profit", "$${totalProfit.roundToInt()}")
                        KpiMini("AI Score", if ((todayDiary?.aiScore ?: -1L) >= 0) "${todayDiary?.aiScore}" else "--")
                    }
                    PulseTrendSparkline(
                        points = listOf(42f, 58f, 64f, 61f, 75f, 82f, (todayDiary?.aiScore?.toFloat() ?: 78f))
                    )
                }
            }
        )

        "creator_sprint" -> listOf(
            { mod ->
                DeepWorkHeroCard(
                    modifier = mod,
                    minutes = 60L,
                    remainingSec = deepWorkRemSec,
                    dndEnabled = true,
                    sessionsCompleted = focusSessions.size,
                    onStart = { viewModel.startDeepWorkSession(60L, "Creator Focus Sprint") },
                    onStop = { viewModel.stopDeepWorkSession() }
                )
            },
            { mod ->
                ShieldOverviewWidget(
                    modifier = mod,
                    ruleCount = appRules.size,
                    lockedCount = appRules.count { it.isLocked },
                    siteCount = blockedSites.size,
                    onOpenShield = { onNavigateTab("shield") }
                )
            }
        )

        else -> listOf(
            { mod ->
                SleepWidgetCard(
                    modifier = mod,
                    config = config,
                    sleepHours = sleepInsights.estimatedSleepHours,
                    recommendedWindow = sleepInsights.recommendedWakeWindows.firstOrNull().orEmpty(),
                    onTriggerAlarm = { viewModel.triggerSampleAlarm(true) }
                )
            },
            { mod ->
                DiaryAiSummaryCard(
                    modifier = mod,
                    todayScore = todayDiary?.aiScore ?: -1L,
                    feedback = todayDiary?.aiFeedback.orEmpty(),
                    onOpenDiary = { onNavigateTab("diary") }
                )
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Refined Header Row (zero numbers in title, Sync & Sign Out moved to Profile)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activePreset.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activePreset.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("open_customizer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Customize UI Colors, Background & Layouts"
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenProfile)
                            .testTag("open_profile_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = profileBitmap
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Open Profile & Sync",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val initial = config.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"
                                Text(
                                    text = initial,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dashboard Switcher Strip (clean names, zero numbers)
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(MasterMindDashboards, key = { it.id }) { preset ->
                    val selected = preset.id == activePreset.id
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectDashboard(preset.id) },
                        label = {
                            Text(
                                text = preset.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = preset.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("dashboard_chip_${preset.id}")
                    )
                }
            }
        }

        // Quick App Layout Mode Selector (Bento Grid / Carousel / Command Tiles / Stream)
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Icon(
                        imageVector = Icons.Default.ViewModule,
                        contentDescription = "Layout switcher",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                items(MasterMindLayoutOptions, key = { it.id }) { opt ->
                    FilterChip(
                        selected = config.appLayoutStyle == opt.id,
                        onClick = { viewModel.updateWorkspaceConfig { it.copy(appLayoutStyle = opt.id) } },
                        label = { Text(opt.title) },
                        modifier = Modifier.testTag("quick_layout_${opt.id}")
                    )
                }
            }
        }

        // Adaptive Multi-Layout Board (Not locked to a list)
        item {
            MasterMindAdaptiveBoard(
                layoutStyle = config.appLayoutStyle,
                blocks = dashboardBlocks
            )
        }
    }
}

@Composable
private fun HeroCommandBanner(
    modifier: Modifier = Modifier,
    onlineHours: Double,
    habitsDone: Int,
    habitsTotal: Int,
    deepWorkRemSec: Long,
    onStartDeepWork: () -> Unit,
    onStopDeepWork: () -> Unit
) {
    PulseCard(modifier = modifier, accentBorder = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                val focusRatio = (1f - (onlineHours / 8.0).toFloat()).coerceIn(0.15f, 1f)
                PulseRingGauge(
                    progress = focusRatio,
                    modifier = Modifier.size(76.dp)
                )
                Text(
                    text = "${(focusRatio * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Daily Attention Pulse", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Online: ${String.format("%.1f", onlineHours)}h • Habits: $habitsDone/$habitsTotal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (deepWorkRemSec > 0L) {
                    val m = deepWorkRemSec / 60
                    val s = deepWorkRemSec % 60
                    Button(onClick = onStopDeepWork, modifier = Modifier.testTag("stop_deep_work_btn")) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(String.format("Deep Work %02d:%02d", m, s))
                    }
                } else {
                    Button(onClick = onStartDeepWork, modifier = Modifier.testTag("start_deep_work_btn")) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start Deep Work")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeepWorkHeroCard(
    modifier: Modifier = Modifier,
    minutes: Long,
    remainingSec: Long,
    dndEnabled: Boolean,
    sessionsCompleted: Int,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    PulseCard(modifier = modifier, accentBorder = true) {
        SectionHeaderRow(
            title = "Deep-Work Cockpit",
            subtitle = "DND Silencer: ${if (dndEnabled) "Auto-Priority" else "Manual"} • $sessionsCompleted sprints",
            icon = Icons.Default.Bolt
        )
        if (remainingSec > 0L) {
            Text(
                text = String.format("%02d:%02d remaining", remainingSec / 60, remainingSec % 60),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text("End Focus Sprint")
            }
        } else {
            Text(
                text = "$minutes min focus block ready",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("Launch Focus Sprint & Silence Distractions")
            }
        }
    }
}

@Composable
private fun ShieldOverviewWidget(
    modifier: Modifier = Modifier,
    ruleCount: Int,
    lockedCount: Int,
    siteCount: Int,
    onOpenShield: () -> Unit
) {
    PulseCard(modifier = modifier) {
        SectionHeaderRow(
            title = "Anti-Doomscroll & Web Gateway",
            subtitle = "$ruleCount app rules ($lockedCount locked) • $siteCount blocked sites",
            icon = Icons.Default.Shield,
            trailing = {
                TextButton(onClick = onOpenShield) { Text("Manage") }
            }
        )
    }
}

@Composable
private fun SleepWidgetCard(
    modifier: Modifier = Modifier,
    config: UserWorkspaceConfig,
    sleepHours: Double,
    recommendedWindow: String,
    onTriggerAlarm: () -> Unit
) {
    PulseCard(modifier = modifier) {
        SectionHeaderRow(
            title = "Sleep & Circadian Optimizer",
            subtitle = "Bed ${String.format("%02d:%02d", config.bedtimeHour, config.bedtimeMinute)} → Wake ${String.format("%02d:%02d", config.wakeHour, config.wakeMinute)} (${String.format("%.1f", sleepHours)}h)",
            icon = Icons.Default.Bedtime,
            trailing = {
                IconButton(onClick = onTriggerAlarm) {
                    Icon(Icons.Default.Alarm, contentDescription = "Test Sleep Alarm")
                }
            }
        )
        if (recommendedWindow.isNotBlank()) {
            Text(
                text = "Optimal REM wake window: $recommendedWindow",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun InventoryFinancialSummaryCard(
    modifier: Modifier = Modifier,
    skuCount: Int,
    lowStockCount: Int,
    revenue: Double,
    profit: Double,
    openReorders: Int,
    onOpenOps: () -> Unit
) {
    PulseCard(modifier = modifier) {
        SectionHeaderRow(
            title = "Shop Inventory & Cloud Financials",
            subtitle = "$skuCount SKUs • $lowStockCount low stock • $openReorders active reorders",
            icon = Icons.Default.Inventory2,
            trailing = {
                TextButton(onClick = onOpenOps) { Text("Open Ops") }
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KpiMini("Revenue", "$${String.format("%.0f", revenue)}")
            KpiMini("Gross Profit", "$${String.format("%.0f", profit)}")
            val margin = if (revenue > 0) (profit / revenue) * 100 else 0.0
            KpiMini("Margin", "${String.format("%.0f", margin)}%")
        }
    }
}

@Composable
private fun DiaryAiSummaryCard(
    modifier: Modifier = Modifier,
    todayScore: Long,
    feedback: String,
    onOpenDiary: () -> Unit
) {
    PulseCard(modifier = modifier) {
        SectionHeaderRow(
            title = "Daily Voice Diary & Gemini AI",
            subtitle = if (todayScore >= 0) "Today's Attainment Score: $todayScore/100" else "Tap to log today or get AI activity ideas",
            icon = Icons.Default.AutoAwesome,
            trailing = {
                TextButton(onClick = onOpenDiary) { Text("Journal") }
            }
        )
        if (feedback.isNotBlank()) {
            Text(
                text = feedback,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun KpiMini(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

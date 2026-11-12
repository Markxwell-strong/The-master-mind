package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppLimitRule
import com.example.data.model.UserWorkspaceConfig
import com.example.ui.components.MasterMindAdaptiveBoard
import com.example.ui.components.PulseCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.viewmodel.FocusPulseViewModel
import com.example.ui.viewmodel.UiState
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShieldAndSystemScreen(viewModel: FocusPulseViewModel) {
    val configState by viewModel.workspaceConfig.collectAsStateWithLifecycle()
    val appRules by viewModel.appRules.collectAsStateWithLifecycle()
    val blockedSites by viewModel.blockedSites.collectAsStateWithLifecycle()
    val deviceApps by viewModel.deviceApps.collectAsStateWithLifecycle()
    val battery by viewModel.batteryTelemetry.collectAsStateWithLifecycle()
    val perms by viewModel.permissions.collectAsStateWithLifecycle()
    val sleepInsights by viewModel.sleepInsights.collectAsStateWithLifecycle()
    val deepWorkRemSec by viewModel.deepWorkRemainingSec.collectAsStateWithLifecycle()

    val config = (configState as? UiState.Success)?.data ?: UserWorkspaceConfig()
    var selectedSection by remember { mutableIntStateOf(0) } // 0: Shield & Web, 1: Sleep & DeepWork, 2: Battery & Processes

    var showAddRuleDialog by remember { mutableStateOf(false) }
    var showAddSiteDialog by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshSystemTelemetry() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shield, Sleep & System",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.refreshSystemTelemetry() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh device telemetry")
                }
            }
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    label = { Text("App & Web Shield") },
                    leadingIcon = { Icon(Icons.Default.Shield, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    label = { Text("Sleep & Deep Work") },
                    leadingIcon = { Icon(Icons.Default.Bedtime, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = selectedSection == 2,
                    onClick = { selectedSection = 2 },
                    label = { Text("Processes & Battery") },
                    leadingIcon = { Icon(Icons.Default.BatteryChargingFull, null, Modifier.size(16.dp)) }
                )
            }
        }

        if (selectedSection == 0) {
            val shieldBlocks: List<@Composable (Modifier) -> Unit> = buildList {
                add { mod ->
                    PulseCard(
                        modifier = mod,
                        accentBorder = !perms.accessibilityEnabled || !perms.usageStatsGranted
                    ) {
                        SectionHeaderRow(
                            title = "System Guardrails",
                            subtitle = "Enable OS permissions for real app monitoring & lockouts",
                            icon = Icons.Default.Security
                        )
                        PermissionActionRow(
                            title = "Usage Stats Access",
                            granted = perms.usageStatsGranted,
                            onGrant = { viewModel.openUsageAccessSettings() }
                        )
                        PermissionActionRow(
                            title = "App & Web Guard (Accessibility)",
                            granted = perms.accessibilityEnabled,
                            onGrant = { viewModel.openAccessibilitySettings() }
                        )
                        PermissionActionRow(
                            title = "Do Not Disturb Policy",
                            granted = perms.dndPolicyGranted,
                            onGrant = { viewModel.openDndPolicySettings() }
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            PermissionActionRow(
                                title = "Notifications & Alarms",
                                granted = perms.notificationsGranted,
                                onGrant = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            )
                        }
                    }
                }

                add { mod ->
                    PulseCard(modifier = mod) {
                        SectionHeaderRow(
                            title = "Website Focus Gateway",
                            subtitle = "Blocks distracting domains in browsers during Deep Work",
                            icon = Icons.Default.Language,
                            trailing = {
                                OutlinedButton(
                                    onClick = { showAddSiteDialog = true },
                                    modifier = Modifier.testTag("add_blocked_site_button")
                                ) {
                                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Site")
                                }
                            }
                        )
                        if (blockedSites.isEmpty()) {
                            Text(
                                text = "Add domains like tiktok.com, youtube.com, or reddit.com to block them during focus sessions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            blockedSites.forEach { site ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(site.label, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${site.domain} • ${if (site.blockAlways) "Always Blocked" else "Deep-Work & Sleep"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteBlockedSite(site.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove blocked site")
                                    }
                                }
                            }
                        }
                    }
                }

                add { mod ->
                    PulseCard(modifier = mod) {
                        SectionHeaderRow(
                            title = "App Limits & Doomscroll Lock",
                            subtitle = "Closes & locks apps like TikTok when limit or bedtime hits",
                            icon = Icons.Default.Lock,
                            trailing = {
                                Button(
                                    onClick = { showAddRuleDialog = true },
                                    modifier = Modifier.testTag("add_app_rule_button")
                                ) {
                                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Rule")
                                }
                            }
                        )
                        if (appRules.isEmpty()) {
                            Text(
                                text = "No app rules yet. Add TikTok, Instagram, or any installed app to enforce daily limits and bedtime lockouts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                appRules.forEach { rule ->
                    add { mod ->
                        PulseCard(modifier = mod) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.appName, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${rule.packageName} • Limit ${rule.dailyLimitMinutes}m/day",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Sleep Lock: ${if (rule.blockDuringSleep) "ON" else "OFF"} • Deep Work: ${if (rule.blockDuringDeepWork) "ON" else "OFF"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            if (rule.isLocked) viewModel.unlockAppRule(rule)
                                            else viewModel.lockAppUntilWake(rule)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (rule.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = if (rule.isLocked) "Unlock app" else "Lock app until wake",
                                            tint = if (rule.isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteAppRule(rule.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete app rule")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                MasterMindAdaptiveBoard(
                    layoutStyle = config.appLayoutStyle,
                    blocks = shieldBlocks
                )
            }
        } else if (selectedSection == 1) {
            val sleepDeepWorkBlocks: List<@Composable (Modifier) -> Unit> = listOf(
                { mod ->
                    PulseCard(modifier = mod, accentBorder = deepWorkRemSec > 0) {
                        SectionHeaderRow(
                            title = "Deep-Work Mode",
                            subtitle = "Silences non-essential notifications & locks distracting apps",
                            icon = Icons.Default.Bolt
                        )
                        var deepWorkMinsDraft by remember(config.deepWorkMinutes) {
                            mutableFloatStateOf(config.deepWorkMinutes.toFloat())
                        }
                        Text("Sprint Duration: ${deepWorkMinsDraft.roundToInt()} minutes")
                        Slider(
                            value = deepWorkMinsDraft,
                            onValueChange = { deepWorkMinsDraft = it },
                            onValueChangeFinished = {
                                viewModel.updateWorkspaceConfig { it.copy(deepWorkMinutes = deepWorkMinsDraft.roundToInt().toLong()) }
                            },
                            valueRange = 15f..180f
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.NotificationsOff, null)
                                Text("Silence Non-Essential Notifications")
                            }
                            Switch(
                                checked = config.deepWorkDndEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updateWorkspaceConfig { it.copy(deepWorkDndEnabled = enabled) }
                                }
                            )
                        }
                        if (deepWorkRemSec > 0L) {
                            Text(
                                text = String.format("Active: %02d:%02d left", deepWorkRemSec / 60, deepWorkRemSec % 60),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(onClick = { viewModel.stopDeepWorkSession() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Stop Deep Work")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startDeepWorkSession(config.deepWorkMinutes) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Start Deep-Work Focus")
                            }
                        }
                    }
                },
                { mod ->
                    PulseCard(modifier = mod) {
                        SectionHeaderRow(
                            title = "Sleep Schedule & Wake Optimizer",
                            subtitle = "Locks apps until wake time & aligns with circadian REM cycles",
                            icon = Icons.Default.Bedtime
                        )
                        var bedHourDraft by remember(config.bedtimeHour) {
                            mutableFloatStateOf(config.bedtimeHour.toFloat())
                        }
                        Text("Bedtime Hour: ${String.format("%02d:%02d", bedHourDraft.roundToInt(), config.bedtimeMinute)}")
                        Slider(
                            value = bedHourDraft,
                            onValueChange = { bedHourDraft = it },
                            onValueChangeFinished = {
                                viewModel.updateWorkspaceConfig { it.copy(bedtimeHour = bedHourDraft.roundToInt().toLong()) }
                            },
                            valueRange = 18f..23f
                        )
                        var wakeHourDraft by remember(config.wakeHour) {
                            mutableFloatStateOf(config.wakeHour.toFloat())
                        }
                        Text("Target Wake Hour: ${String.format("%02d:%02d", wakeHourDraft.roundToInt(), config.wakeMinute)}")
                        Slider(
                            value = wakeHourDraft,
                            onValueChange = { wakeHourDraft = it },
                            onValueChangeFinished = {
                                viewModel.updateWorkspaceConfig { it.copy(wakeHour = wakeHourDraft.roundToInt().toLong()) }
                            },
                            valueRange = 4f..11f
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Lock Doomscroll Apps Until Wake")
                            Switch(
                                checked = config.sleepLockEnabled,
                                onCheckedChange = { on -> viewModel.updateWorkspaceConfig { it.copy(sleepLockEnabled = on) } }
                            )
                        }
                        Text(
                            text = "Recommended Wake Windows (${sleepInsights.stepCountToday} steps synced):",
                            style = MaterialTheme.typography.labelLarge
                        )
                        sleepInsights.recommendedWakeWindows.forEach { w ->
                            Text("• $w", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.openHealthConnectSettings() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Favorite, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Health Sync")
                            }
                            Button(
                                onClick = { viewModel.triggerSampleAlarm(true) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Alarm, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Test Alarm")
                            }
                        }
                    }
                },
                { mod ->
                    PulseCard(modifier = mod) {
                        SectionHeaderRow(
                            title = "Inactivity Guard",
                            subtitle = "Nudge or full-screen alarm if inactive for too long",
                            icon = Icons.Default.Alarm
                        )
                        var inactivityDraft by remember(config.inactivityAlertHours) {
                            mutableFloatStateOf(config.inactivityAlertHours.toFloat())
                        }
                        Text("Trigger after ${inactivityDraft.roundToInt()} hours of inactivity")
                        Slider(
                            value = inactivityDraft,
                            onValueChange = { inactivityDraft = it },
                            onValueChangeFinished = {
                                viewModel.updateWorkspaceConfig { it.copy(inactivityAlertHours = inactivityDraft.roundToInt().toLong()) }
                            },
                            valueRange = 1f..12f
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Use Full-Screen Alarm (vs Gentle Nudge)")
                            Switch(
                                checked = config.inactivityFullScreenAlarm,
                                onCheckedChange = { full ->
                                    viewModel.updateWorkspaceConfig { it.copy(inactivityFullScreenAlarm = full) }
                                }
                            )
                        }
                    }
                }
            )
            item {
                MasterMindAdaptiveBoard(
                    layoutStyle = config.appLayoutStyle,
                    blocks = sleepDeepWorkBlocks
                )
            }
        } else {
            val batteryBlocks: List<@Composable (Modifier) -> Unit> = buildList {
                add { mod ->
                    PulseCard(modifier = mod, accentBorder = true) {
                        SectionHeaderRow(
                            title = "Battery & Thermal Telemetry",
                            subtitle = "${battery.levelPercent}% • ${battery.chargeSource} • ${battery.healthLabel}",
                            icon = Icons.Default.BatteryChargingFull
                        )
                        Text(
                            "Temperature: ${String.format("%.1f", battery.temperatureC)}°C • Voltage: ${battery.voltageMv} mV • Current: ${battery.currentMa} mA",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                deviceApps.forEach { app ->
                    add { mod ->
                        PulseCard(modifier = mod) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.appName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${app.minutesToday}m today • ${if (app.isRunningBackground) "Running" else "Idle"} • ${app.standbyBucketLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedButton(onClick = { viewModel.stopBackgroundProcess(app) }) {
                                        Icon(Icons.Default.StopCircle, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Stop")
                                    }
                                    TextButton(onClick = { viewModel.openSystemAppInfo(app.packageName) }) {
                                        Text("Info")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                MasterMindAdaptiveBoard(
                    layoutStyle = config.appLayoutStyle,
                    blocks = batteryBlocks
                )
            }
        }
    }

    if (showAddRuleDialog) {
        AddAppRuleDialog(
            installedApps = deviceApps.map { it.packageName to it.appName },
            onDismiss = { showAddRuleDialog = false },
            onConfirm = { pkg, name, mins, sleepLock, deepBlock ->
                viewModel.saveAppRule(
                    AppLimitRule(
                        packageName = pkg,
                        appName = name,
                        dailyLimitMinutes = mins,
                        blockDuringSleep = sleepLock,
                        blockDuringDeepWork = deepBlock
                    )
                )
                showAddRuleDialog = false
            }
        )
    }

    if (showAddSiteDialog) {
        AddBlockedSiteDialog(
            onDismiss = { showAddSiteDialog = false },
            onConfirm = { domain, label, always ->
                viewModel.addBlockedSite(domain, label, always)
                showAddSiteDialog = false
            }
        )
    }
}

@Composable
private fun PermissionActionRow(
    title: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Security,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
            )
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
        if (granted) {
            Text("Granted", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        } else {
            OutlinedButton(onClick = onGrant) { Text("Enable") }
        }
    }
}

@Composable
private fun AddAppRuleDialog(
    installedApps: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConfirm: (pkg: String, name: String, mins: Long, sleepLock: Boolean, deepBlock: Boolean) -> Unit
) {
    var packageName by remember { mutableStateOf("com.zhiliaoapp.musically") }
    var appName by remember { mutableStateOf("TikTok") }
    var limitMins by remember { mutableStateOf("20") }
    var sleepLock by remember { mutableStateOf(true) }
    var deepBlock by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add App Limit & Lock Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (installedApps.isNotEmpty()) {
                    Text("Quick Pick Installed App:", style = MaterialTheme.typography.labelSmall)
                    LazyColumn(modifier = Modifier.height(90.dp)) {
                        items(installedApps.take(15)) { (pkg, name) ->
                            TextButton(
                                onClick = {
                                    packageName = pkg
                                    appName = name
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("$name ($pkg)", maxLines = 1)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = limitMins,
                    onValueChange = { limitMins = it.filter { c -> c.isDigit() } },
                    label = { Text("Daily Limit (minutes)") },
                    singleLine = true
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Lock During Sleep")
                    Switch(checked = sleepLock, onCheckedChange = { sleepLock = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Block in Deep Work")
                    Switch(checked = deepBlock, onCheckedChange = { deepBlock = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mins = limitMins.toLongOrNull()?.coerceIn(1L, 1440L) ?: 30L
                    if (packageName.isNotBlank() && appName.isNotBlank()) {
                        onConfirm(packageName.trim(), appName.trim(), mins, sleepLock, deepBlock)
                    }
                }
            ) { Text("Save Rule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddBlockedSiteDialog(
    onDismiss: () -> Unit,
    onConfirm: (domain: String, label: String, always: Boolean) -> Unit
) {
    var domain by remember { mutableStateOf("tiktok.com") }
    var label by remember { mutableStateOf("TikTok Web") }
    var always by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block Distracting Website") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain (e.g. reddit.com)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Block 24/7 (else Deep-Work/Sleep only)")
                    Switch(checked = always, onCheckedChange = { always = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (domain.trim().length >= 3) {
                        onConfirm(domain.trim(), label.ifBlank { domain }.trim(), always)
                    }
                }
            ) { Text("Block Site") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

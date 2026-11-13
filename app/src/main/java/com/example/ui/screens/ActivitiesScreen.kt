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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.model.UserWorkspaceConfig
import com.example.system.ProfileMediaStorage
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTopBar
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.StatisticsCard
import com.example.ui.viewmodel.FocusPulseViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivitiesScreen(
    viewModel: FocusPulseViewModel,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    val context = LocalContext.current
    val configState by viewModel.workspaceConfig.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsFeed.collectAsStateWithLifecycle()
    val intelligence by viewModel.intelligenceState.collectAsStateWithLifecycle()
    val profilePhotoRefreshKey by viewModel.profilePhotoRefreshKey.collectAsStateWithLifecycle()
    val config = (configState as? UiState.Success)?.data ?: UserWorkspaceConfig()

    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val uid = viewModel.currentUid
    LaunchedEffect(profilePhotoRefreshKey, uid) {
        profileBitmap = withContext(Dispatchers.IO) {
            ProfileMediaStorage.loadBitmap(context, uid)
        }
    }

    // 0: Habits & Tasks, 1: App Shield & Limits, 2: Usage & Launches Telemetry
    var section by remember { mutableIntStateOf(0) }
    val profileInitial = config.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"
    val analytics = intelligence.analytics

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassTopBar(
                title = "Activities & Shield",
                subtitle = "${analytics.habitsCompletedToday}/${analytics.habitsTotalToday} habits • ${analytics.todayFocusMinutes}m focus • ${analytics.todayAppLaunches} launches",
                profileInitial = profileInitial,
                profileBitmap = profileBitmap,
                unreadNotifications = notifications.size,
                onOpenProfile = onOpenProfile,
                onOpenNotifications = onOpenNotifications,
                trailingExtra = {
                    IconButton(
                        onClick = {
                            viewModel.refreshSystemTelemetry()
                            viewModel.refreshIntelligencePipeline()
                        },
                        modifier = Modifier.testTag("activities_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh activity telemetry")
                    }
                }
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = section == 0,
                    onClick = { section = 0 },
                    label = { Text("Habits & Tasks") },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("activities_tab_habits")
                )
                FilterChip(
                    selected = section == 1,
                    onClick = { section = 1 },
                    label = { Text("Shield, Sleep & Gaming") },
                    leadingIcon = { Icon(Icons.Default.Shield, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("activities_tab_shield")
                )
                FilterChip(
                    selected = section == 2,
                    onClick = { section = 2 },
                    label = { Text("App Launches & Telemetry") },
                    leadingIcon = { Icon(Icons.Default.Timer, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("activities_tab_launches")
                )
                FilterChip(
                    selected = section == 3,
                    onClick = { section = 3 },
                    label = { Text("Stock & Profit") },
                    leadingIcon = { Icon(Icons.Default.Inventory2, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("activities_tab_stock")
                )
            }
        }

        when (section) {
            0 -> HabitsAndNotebookScreen(viewModel = viewModel, showHeader = false)
            1 -> ShieldAndSystemScreen(viewModel = viewModel, showHeader = false)
            2 -> UsageLaunchTelemetrySection(viewModel = viewModel)
            else -> InventoryAnalyticsScreen(viewModel = viewModel, showHeader = false)
        }
    }
}

@Composable
private fun UsageLaunchTelemetrySection(viewModel: FocusPulseViewModel) {
    val intel by viewModel.intelligenceState.collectAsStateWithLifecycle()
    val analytics = intel.analytics

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatisticsCard(
                    label = "Total Screen Time",
                    value = "${analytics.todayScreenMinutes}m",
                    delta = if (intel.usageAccessGranted) "UsageStats active" else "Permission needed",
                    icon = Icons.Default.Timer,
                    modifier = Modifier.weight(1f)
                )
                StatisticsCard(
                    label = "App Launches",
                    value = "${analytics.todayAppLaunches}x",
                    delta = "Distraction: ${analytics.todayDistractionMinutes}m",
                    icon = Icons.Default.Lock,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            GlassCard(accentBorder = !intel.usageAccessGranted) {
                SectionHeaderRow(
                    title = "App Launch Frequency & Duration",
                    subtitle = if (intel.usageAccessGranted) {
                        "Collected via Android UsageStatsManager (events & foreground usage)"
                    } else {
                        "Grant Usage Access in Shield tab or Settings to populate live app launches"
                    },
                    icon = Icons.Default.BatteryChargingFull
                )
                if (analytics.topLaunchedApps.isEmpty()) {
                    Text(
                        text = "No foreground app launches recorded for today yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    analytics.topLaunchedApps.forEach { app ->
                        Surface(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${app.category.uppercase()} • ${app.durationMinutes}m foreground",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = "${app.launchCount} launches",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

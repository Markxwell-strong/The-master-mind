package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.system.SystemPermissionsState

@Composable
fun PermissionsOnboardingDialog(
    permissions: SystemPermissionsState,
    onRefreshStatus: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenDndPolicy: () -> Unit,
    onOpenExactAlarm: () -> Unit,
    callScreeningIntentProvider: () -> Intent? = { null },
    onDismiss: () -> Unit
) {
    val runtimePermsToAsk = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        add(Manifest.permission.RECORD_AUDIO)
    }.toTypedArray()

    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshStatus()
    }

    val callScreeningLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshStatus()
    }

    val allGranted = permissions.notificationsGranted &&
        permissions.microphoneGranted &&
        permissions.usageStatsGranted &&
        permissions.accessibilityEnabled &&
        permissions.dndPolicyGranted &&
        permissions.exactAlarmGranted &&
        permissions.callScreeningRoleGranted

    val needsRuntime = !permissions.notificationsGranted || !permissions.microphoneGranted

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Enable App Permissions",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Welcome to THE MASTER MIND. Grant these permissions so app blocking, sleep lockouts, voice diary, and notifications work automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (needsRuntime) {
                    Button(
                        onClick = { runtimeLauncher.launch(runtimePermsToAsk) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("grant_runtime_permissions_button")
                    ) {
                        Icon(Icons.Default.NotificationsActive, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Allow Notifications & Microphone")
                    }
                }

                PermissionPromptRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "Notifications & Reminders",
                    description = "Bedtime, inactivity alerts, and low-stock warnings.",
                    granted = permissions.notificationsGranted,
                    onAction = { runtimeLauncher.launch(runtimePermsToAsk) }
                )

                PermissionPromptRow(
                    icon = Icons.Default.Mic,
                    title = "Microphone (Voice Diary)",
                    description = "Hands-free voice-to-text daily journal entries.",
                    granted = permissions.microphoneGranted,
                    onAction = { runtimeLauncher.launch(runtimePermsToAsk) }
                )

                PermissionPromptRow(
                    icon = Icons.Default.QueryStats,
                    title = "Usage Stats Access",
                    description = "Monitors screen time & background apps.",
                    granted = permissions.usageStatsGranted,
                    onAction = onOpenUsageAccess
                )

                PermissionPromptRow(
                    icon = Icons.Default.Shield,
                    title = "App & Web Guard (Accessibility)",
                    description = "Enforces doomscroll lockouts & focus site blocking.",
                    granted = permissions.accessibilityEnabled,
                    onAction = onOpenAccessibility
                )

                PermissionPromptRow(
                    icon = Icons.Default.NotificationsOff,
                    title = "Do Not Disturb Policy",
                    description = "Silences distractions during Deep-Work sprints.",
                    granted = permissions.dndPolicyGranted,
                    onAction = onOpenDndPolicy
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    PermissionPromptRow(
                        icon = Icons.Default.PhoneDisabled,
                        title = "Gaming Call Blocker (Caller ID & Spam)",
                        description = "Auto-declines all incoming phone calls when Gaming Mode is active.",
                        granted = permissions.callScreeningRoleGranted,
                        onAction = {
                            callScreeningIntentProvider()?.let { callScreeningLauncher.launch(it) }
                        }
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PermissionPromptRow(
                        icon = Icons.Default.Alarm,
                        title = "Exact Sleep Alarms",
                        description = "Fires scheduled bedtime & wake alarms on time.",
                        granted = permissions.exactAlarmGranted,
                        onAction = onOpenExactAlarm
                    )
                }

                OutlinedButton(
                    onClick = onRefreshStatus,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh Permission Status")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("permissions_prompt_continue_button")
            ) {
                Text(if (allGranted) "All Set — Enter App" else "Continue")
            }
        },
        dismissButton = {
            if (!allGranted) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("permissions_prompt_later_button")
                ) {
                    Text("Set Up Later")
                }
            }
        }
    )
}

@Composable
private fun PermissionPromptRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onAction: () -> Unit
) {
    Surface(
        color = if (granted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else icon,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (granted) {
                Text(
                    text = "Granted",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                OutlinedButton(onClick = onAction) {
                    Text("Enable")
                }
            }
        }
    }
}

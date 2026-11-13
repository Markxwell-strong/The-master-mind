package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.FocusAiRepository
import com.example.data.ai.SupportTriageResult
import com.example.system.SupportHelper
import com.example.ui.viewmodel.FocusPulseViewModel
import com.example.ui.viewmodel.SupportTriageUiState
import kotlinx.coroutines.launch

private val SupportCategories = listOf(
    "support" to "General Help",
    "permissions" to "Permissions / Blocker",
    "sync" to "Account & Sync",
    "ai" to "AI & Diary",
    "bug" to "Bug / Crash"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeveloperSupportDialog(
    viewModel: FocusPulseViewModel,
    initialCategory: String = "support",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val triageState by viewModel.supportTriageState.collectAsStateWithLifecycle()

    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    var issueText by remember { mutableStateOf("") }
    var autoEscalateWhatsApp by remember { mutableStateOf(true) }
    var hasAutoOpenedWa by remember { mutableStateOf(false) }

    fun launchWhatsAppWithReport(result: SupportTriageResult?, ticketId: String) {
        val summary = buildString {
            append("[THE MASTER MIND Support]\n")
            append("Category: $selectedCategory\n")
            if (viewModel.userEmail.isNotBlank()) append("Account: ${viewModel.userEmail}\n")
            append("Problem: ${issueText.trim()}\n")
            if (result != null) {
                append("Gemini Check: ${result.developerSummary}\n")
            }
            if (ticketId.isNotBlank()) append("Ticket ID: $ticketId")
        }
        if (ticketId.isNotBlank() && result != null) {
            viewModel.markSupportTicketSentToWhatsApp(ticketId, result.diagnosis)
        }
        SupportHelper.openDeveloperWhatsApp(context, summary)
    }

    LaunchedEffect(triageState) {
        val state = triageState
        if (state is SupportTriageUiState.Diagnosed && !state.result.resolvedLikely && autoEscalateWhatsApp && !hasAutoOpenedWa) {
            hasAutoOpenedWa = true
            launchWhatsAppWithReport(state.result, state.ticketId)
        }
    }

    AlertDialog(
        onDismissRequest = {
            viewModel.resetSupportTriage()
            onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Default.SupportAgent,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Contact Developer & Gemini Help", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tell us what went wrong first. Gemini will try to solve it immediately and automatically forward your report to the Admin Inbox (${SupportHelper.DEVELOPER_WHATSAPP_RAW}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SupportCategories.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { selectedCategory = key },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = issueText,
                    onValueChange = { issueText = it },
                    label = { Text("Describe the problem") },
                    placeholder = { Text("e.g., App blocker won't stay active, AI evaluation issue, or sync question...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_problem_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = autoEscalateWhatsApp,
                        onCheckedChange = { autoEscalateWhatsApp = it }
                    )
                    Text(
                        text = "Auto-open WhatsApp message if Gemini can't solve it",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }

                when (val state = triageState) {
                    SupportTriageUiState.Idle -> Unit
                    SupportTriageUiState.Loading -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                Text(
                                    text = "Gemini is analyzing your problem & notifying Admin Inbox...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    is SupportTriageUiState.Diagnosed -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Gemini Support Assistant",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = state.result.diagnosis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (state.deliveredToAdminInbox) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Automatically delivered to Admin Inbox.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.markSupportTicketResolvedByAi(state.ticketId, state.result.diagnosis)
                                    viewModel.resetSupportTriage()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("This Helped")
                            }
                            Button(
                                onClick = { launchWhatsAppWithReport(state.result, state.ticketId) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("support_whatsapp_escalate_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("WhatsApp Dev")
                            }
                        }
                    }

                    is SupportTriageUiState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val isLoading = triageState is SupportTriageUiState.Loading
            Button(
                onClick = {
                    hasAutoOpenedWa = false
                    viewModel.diagnoseAndSendSupport(
                        category = selectedCategory,
                        issueText = issueText,
                        autoDelivered = true
                    )
                },
                enabled = !isLoading && issueText.trim().isNotEmpty(),
                modifier = Modifier.testTag("ask_gemini_support_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (triageState is SupportTriageUiState.Diagnosed) "Re-Analyze" else "Ask Gemini & Send")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = {
                        val msg = if (issueText.isNotBlank()) {
                            "[THE MASTER MIND Support - $selectedCategory]\n${issueText.trim()}"
                        } else {
                            "Hello Developer, I need support with THE MASTER MIND app."
                        }
                        SupportHelper.openDeveloperWhatsApp(context, msg)
                    }
                ) {
                    Text("Direct WhatsApp")
                }
                TextButton(
                    onClick = {
                        viewModel.resetSupportTriage()
                        onDismiss()
                    }
                ) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun UnauthSupportDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiRepo = remember { FocusAiRepository() }

    var contactEmail by remember { mutableStateOf("") }
    var issueText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var diagnosis by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun sendToAdminWhatsApp(extraDiagnosis: String? = null) {
        val report = buildString {
            append("[THE MASTER MIND Support]\n")
            if (contactEmail.isNotBlank()) append("Contact Email: ${contactEmail.trim()}\n")
            append("Issue: ${issueText.trim().ifBlank { "Need help with THE MASTER MIND app" }}\n")
            if (!extraDiagnosis.isNullOrBlank()) append("Gemini Brief: $extraDiagnosis")
        }
        SupportHelper.openDeveloperWhatsApp(context, report)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text(
                text = "Gemini & Developer Support",
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
                    text = "Describe your sign-in or app issue. Gemini will try to help first, or forward directly to the Developer (${SupportHelper.DEVELOPER_WHATSAPP_RAW}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = contactEmail,
                    onValueChange = { contactEmail = it },
                    label = { Text("Your email (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = issueText,
                    onValueChange = { issueText = it },
                    label = { Text("What problem are you facing?") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Asking Gemini...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (!diagnosis.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = diagnosis!!,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = issueText.trim()
                    if (clean.isEmpty()) {
                        errorMsg = "Please describe the problem first."
                        return@Button
                    }
                    errorMsg = null
                    isLoading = true
                    scope.launch {
                        val res = aiRepo.troubleshootSupportIssue(
                            issueText = "Email=${contactEmail.ifBlank { "not provided" }} | $clean",
                            category = "support",
                            appDiagnostics = "Unauthenticated Login Screen"
                        )
                        isLoading = false
                        res.onSuccess { triage ->
                            diagnosis = triage.diagnosis
                            if (!triage.resolvedLikely) {
                                sendToAdminWhatsApp(triage.developerSummary)
                            }
                        }.onFailure { err ->
                            errorMsg = err.localizedMessage ?: "Gemini could not respond; tap WhatsApp Dev below."
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ask Gemini & Send")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { sendToAdminWhatsApp(diagnosis) }) {
                    Text("WhatsApp Dev")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.system.SupportHelper
import com.example.ui.components.PulseCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.viewmodel.FocusPulseViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminConsoleScreen(
    viewModel: FocusPulseViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val tickets by viewModel.adminSupportTickets.collectAsStateWithLifecycle()
    val users by viewModel.adminUsers.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Support Tickets, 1 = Users
    var ticketFilter by remember { mutableIntStateOf(0) } // 0 = All, 1 = Needs Attention
    val replyDrafts = remember { mutableStateMapOf<String, String>() }

    val filteredTickets = remember(tickets, ticketFilter) {
        when (ticketFilter) {
            1 -> tickets.filter { it.status == "open" || it.status == "escalated" }
            else -> tickets
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("admin_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Admin Support Console",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Signed in as Admin • ${viewModel.userEmail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (!viewModel.isAdminUser) {
            item {
                PulseCard(accentBorder = true) {
                    Text(
                        text = "Admin Console Access Restricted",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sign in with the designated admin Google account (cleandemon5@gmail.com) to manage support tickets and users.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        label = { Text("Support Tickets (${tickets.size})") },
                        leadingIcon = { Icon(Icons.Default.SupportAgent, null, Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        label = { Text("Registered Users (${users.size})") },
                        leadingIcon = { Icon(Icons.Default.PersonSearch, null, Modifier.size(16.dp)) }
                    )
                }
            }

            if (activeTab == 0) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = ticketFilter == 0,
                            onClick = { ticketFilter = 0 },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = ticketFilter == 1,
                            onClick = { ticketFilter = 1 },
                            label = { Text("Needs Attention") }
                        )
                    }
                }

                if (filteredTickets.isEmpty()) {
                    item {
                        PulseCard {
                            Text(
                                text = "No support tickets match this filter right now.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredTickets, key = { it.id }) { ticket ->
                        PulseCard(accentBorder = ticket.status == "escalated") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${ticket.category.uppercase()} • ${ticket.status.uppercase()}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = ticket.userEmail.ifBlank { ticket.userId.take(12) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = ticket.message,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            if (ticket.aiDiagnosis.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Gemini Summary: ${ticket.aiDiagnosis}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            if (ticket.adminReply.isNotBlank()) {
                                Text(
                                    text = "Admin Reply: ${ticket.adminReply}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            val draft = replyDrafts[ticket.id] ?: ""
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { replyDrafts[ticket.id] = it },
                                label = { Text("Reply / Instructions for User") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.adminReplyTicket(
                                            ticketId = ticket.id,
                                            status = "resolved",
                                            reply = draft.ifBlank { "Resolved by Admin (${viewModel.userEmail})." }
                                        )
                                        replyDrafts.remove(ticket.id)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Resolve")
                                }

                                OutlinedButton(
                                    onClick = {
                                        SupportHelper.openDeveloperWhatsApp(
                                            context,
                                            "Admin follow-up for ${ticket.userEmail.ifBlank { ticket.userId }}: ${ticket.message}"
                                        )
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Open WhatsApp")
                                }
                            }
                        }
                    }
                }
            } else {
                items(users, key = { it.userId }) { u ->
                    PulseCard {
                        SectionHeaderRow(
                            title = u.displayName.ifBlank { "Member" },
                            subtitle = buildString {
                                if (u.email.isNotBlank()) append(u.email) else append(u.userId.take(12))
                                append(" • ${u.role.uppercase()}")
                                if (u.lastActiveDevice.isNotBlank()) append(" • ${u.lastActiveDevice}")
                            },
                            icon = Icons.Default.PersonSearch
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

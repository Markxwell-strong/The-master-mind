package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserWorkspaceConfig
import com.example.system.ProfileMediaStorage
import com.example.ui.components.PulseCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.viewmodel.FocusPulseViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    viewModel: FocusPulseViewModel,
    onBack: (() -> Unit)? = null,
    onOpenSettings: () -> Unit,
    onOpenAdminConsole: () -> Unit,
    onSignOut: () -> Unit
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configState by viewModel.workspaceConfig.collectAsStateWithLifecycle()
    val photoRefreshKey by viewModel.profilePhotoRefreshKey.collectAsStateWithLifecycle()
    val myTickets by viewModel.mySupportTickets.collectAsStateWithLifecycle()
    val adminTickets by viewModel.adminSupportTickets.collectAsStateWithLifecycle()
    val isDelegatedOrMainAdmin by viewModel.isDelegatedOrMainAdmin.collectAsStateWithLifecycle()
    val config = (configState as? UiState.Success)?.data ?: UserWorkspaceConfig()

    val currentUser = remember { Firebase.auth.currentUser }
    val accountEmail = currentUser?.email?.ifBlank { null } ?: config.email.ifBlank { "Authenticated Cloud Account" }

    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var hasPhoto by remember { mutableStateOf(false) }
    var displayNameDraft by remember(config.displayName) { mutableStateOf(config.displayName) }
    var usernameDraft by remember(config.username) { mutableStateOf(config.username) }

    val uid = viewModel.currentUid
    LaunchedEffect(photoRefreshKey, uid) {
        val loaded = withContext(Dispatchers.IO) {
            ProfileMediaStorage.loadBitmap(context, uid)
        }
        profileBitmap = loaded
        hasPhoto = loaded != null
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    ProfileMediaStorage.savePickedProfileImage(context, uri, uid)
                }
                if (saved) {
                    viewModel.notifyProfilePhotoChanged()
                    viewModel.showStatusMessage("Profile picture updated.")
                } else {
                    viewModel.showStatusMessage("Unable to load selected profile photo.")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("profile_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Profile & Account",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Username, avatar photo, cloud sync & session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Profile Photo, Username & Display Name Hero
        item {
            PulseCard(accentBorder = true) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val bmp = profileBitmap
                    Box(
                        modifier = Modifier
                            .size(108.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Profile Picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val initial = config.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Text(
                        text = config.displayName.ifBlank { "Member" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (config.username.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "@${config.username}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = buildString {
                            append(accountEmail)
                            if (isDelegatedOrMainAdmin) append(" • ADMIN")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDelegatedOrMainAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pick_profile_photo_button")
                        ) {
                            Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (hasPhoto) "Change Photo" else "Add Profile Picture")
                        }

                        if (hasPhoto) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            ProfileMediaStorage.clearProfilePhoto(context, uid)
                                        }
                                        viewModel.notifyProfilePhotoChanged()
                                        viewModel.showStatusMessage("Profile picture removed.")
                                    }
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("clear_profile_photo_button")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove profile picture")
                            }
                        }
                    }
                }
            }
        }

        if (isDelegatedOrMainAdmin) {
            item {
                val pendingCount = adminTickets.count { it.status == "open" || it.status == "escalated" }
                PulseCard(accentBorder = true) {
                    SectionHeaderRow(
                        title = "Admin Console",
                        subtitle = if (viewModel.isMainAdminUser) {
                            "$pendingCount active support requests • Full administration"
                        } else {
                            "$pendingCount assigned support requests"
                        },
                        icon = Icons.Default.AdminPanelSettings
                    )
                    Button(
                        onClick = onOpenAdminConsole,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_admin_console_button")
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Admin Console")
                    }
                }
            }
        }

        // Editable Profile Identity (Display Name + Username)
        item {
            PulseCard {
                SectionHeaderRow(
                    title = "Profile Identity",
                    subtitle = "Set your display name and unique @username handle",
                    icon = Icons.Default.VerifiedUser
                )
                OutlinedTextField(
                    value = usernameDraft,
                    onValueChange = { usernameDraft = it.take(40) },
                    label = { Text("Username") },
                    prefix = { Text("@") },
                    supportingText = { Text("Letters, numbers, ., _, - (shown on your profile)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_username_input")
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = displayNameDraft,
                    onValueChange = { displayNameDraft = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_display_name_input")
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.updateProfileIdentity(displayNameDraft, usernameDraft) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_profile_name_button")
                ) {
                    Text("Save Profile Identity")
                }
            }
        }

        if (myTickets.isNotEmpty()) {
            item {
                PulseCard {
                    SectionHeaderRow(
                        title = "My Support Updates",
                        subtitle = "Responses from Support Admins & Gemini",
                        icon = Icons.Default.SupportAgent
                    )
                    myTickets.take(4).forEach { ticket ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${ticket.category.uppercase()} • ${ticket.status.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(text = ticket.message, style = MaterialTheme.typography.bodySmall)
                                if (ticket.adminReply.isNotBlank()) {
                                    Text(
                                        text = "Admin: ${ticket.adminReply}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Cloud Sync Status
        item {
            PulseCard {
                SectionHeaderRow(
                    title = "Real-Time Cloud Sync",
                    subtitle = "All habits, diary logs, limits, business records & shop stock stay synced",
                    icon = Icons.Default.CloudDone
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Synced",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Synced • ${config.lastActiveDevice.ifBlank { "This Device" }}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Connected to Firebase Cloud Firestore across your devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Quick Workspace Settings & Sign Out
        item {
            PulseCard {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("profile_open_settings_button")
                ) {
                    Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Customize Workspace, Colors & Wallpapers")
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("profile_sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

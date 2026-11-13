package com.example

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ai.FocusAiRepository
import com.example.data.firebase.configureAppCheck
import com.example.data.preferences.AppearancePreferencesState
import com.example.data.repository.FocusPulseRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.DeveloperSupportDialog
import com.example.ui.components.GlassNavDestination
import com.example.ui.components.GlassNavigationBar
import com.example.ui.components.GlassNavigationRail
import com.example.ui.components.PermissionsOnboardingDialog
import com.example.ui.screens.ActivitiesScreen
import com.example.ui.screens.AdminConsoleScreen
import com.example.ui.screens.DashboardHubScreen
import com.example.ui.screens.DiaryAndAiBuddyScreen
import com.example.ui.screens.IntelligenceScreen
import com.example.ui.screens.InventoryAnalyticsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.GlassBackgroundHost
import com.example.ui.theme.MasterMindGlassTheme
import com.example.ui.viewmodel.AppNotificationItem
import com.example.ui.viewmodel.FocusPulseViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_HOME = "home"
const val ROUTE_ACTIVITIES = "activities"
const val ROUTE_INTELLIGENCE = "intelligence"
const val ROUTE_JOURNAL = "journal"
const val ROUTE_SETTINGS = "settings"

// Secondary module routes (kept out of primary bottom navigation bar)
const val ROUTE_BUSINESS = "business"
const val ROUTE_PROFILE = "profile"
const val ROUTE_ADMIN = "admin"

private val PrimaryDestinations = listOf(
    GlassNavDestination(ROUTE_HOME, "Home", Icons.Default.Dashboard),
    GlassNavDestination(ROUTE_ACTIVITIES, "Activities", Icons.Default.Bolt),
    GlassNavDestination(ROUTE_INTELLIGENCE, "Intelligence", Icons.Default.Psychology),
    GlassNavDestination(ROUTE_JOURNAL, "Journal", Icons.Default.EditNote),
    GlassNavDestination(ROUTE_SETTINGS, "Settings", Icons.Default.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before rendering UI or creating ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            AppNavigation()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        MasterMindGlassTheme(appearance = AppearancePreferencesState()) {
            GlassBackgroundHost(
                appearanceState = AppearancePreferencesState(),
                activeRoute = ROUTE_HOME,
                backgroundRefreshKey = 0
            ) {
                AuthScreen(onAuthSuccess = {})
            }
        }
    } else {
        val uid = user.uid
        val displayName = user.displayName ?: user.email ?: "Master Mind User"
        val email = user.email.orEmpty()
        key(uid) {
            AuthenticatedMasterMindShell(
                currentUserId = uid,
                displayName = displayName,
                userEmail = email
            )
        }
    }
}

@Composable
private fun AuthenticatedMasterMindShell(
    currentUserId: String,
    displayName: String,
    userEmail: String
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: FocusPulseViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                FocusPulseViewModel(
                    application = app,
                    repository = FocusPulseRepository(db),
                    aiRepository = FocusAiRepository(),
                    currentUserId = currentUserId,
                    displayName = displayName,
                    userEmail = userEmail
                )
            }
        }
    )

    val appearance by viewModel.appearanceState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val bgRefreshKey by viewModel.customBgRefreshKey.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val showPermissionsPrompt by viewModel.showPermissionsPrompt.collectAsStateWithLifecycle()
    val notificationsFeed by viewModel.notificationsFeed.collectAsStateWithLifecycle()

    var showSupportDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    // Refresh permission checks automatically whenever user returns from System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemTelemetry(checkOnboardingPrompt = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val activeRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME

    MasterMindGlassTheme(appearance = appearance, activeRoute = activeRoute) {
        fun navigateTopLevel(route: String) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        fun openSecondary(route: String) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }

        if (showPermissionsPrompt) {
            PermissionsOnboardingDialog(
                permissions = permissions,
                onRefreshStatus = { viewModel.refreshSystemTelemetry(checkOnboardingPrompt = false) },
                onOpenUsageAccess = { viewModel.openUsageAccessSettings() },
                onOpenAccessibility = { viewModel.openAccessibilitySettings() },
                onOpenDndPolicy = { viewModel.openDndPolicySettings() },
                onOpenExactAlarm = { viewModel.openExactAlarmSettings() },
                onDismiss = { viewModel.dismissPermissionsPrompt() }
            )
        }

        if (showSupportDialog) {
            DeveloperSupportDialog(
                viewModel = viewModel,
                initialCategory = "support",
                onDismiss = { showSupportDialog = false }
            )
        }

        if (showNotificationsDialog) {
            NotificationsCenterDialog(
                notifications = notificationsFeed,
                onOpenActivities = {
                    showNotificationsDialog = false
                    navigateTopLevel(ROUTE_ACTIVITIES)
                },
                onOpenPermissions = {
                    showNotificationsDialog = false
                    viewModel.openPermissionsPrompt()
                },
                onDismiss = { showNotificationsDialog = false }
            )
        }

        GlassBackgroundHost(
            appearanceState = appearance,
            activeRoute = activeRoute,
            backgroundRefreshKey = bgRefreshKey
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useSideRail = maxWidth >= 700.dp

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (!useSideRail) {
                            GlassNavigationBar(
                                destinations = PrimaryDestinations,
                                activeRoute = activeRoute,
                                onSelect = { dest -> navigateTopLevel(dest.route) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useSideRail) {
                            GlassNavigationRail(
                                destinations = PrimaryDestinations,
                                activeRoute = activeRoute,
                                onSelect = { dest -> navigateTopLevel(dest.route) }
                            )
                        }

                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_HOME,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(ROUTE_HOME) {
                                DashboardHubScreen(
                                    viewModel = viewModel,
                                    onNavigateTab = { route -> navigateTopLevel(route) },
                                    onOpenProfile = { openSecondary(ROUTE_PROFILE) },
                                    onOpenSettings = { navigateTopLevel(ROUTE_SETTINGS) },
                                    onOpenBusiness = { openSecondary(ROUTE_BUSINESS) },
                                    onOpenNotifications = { showNotificationsDialog = true }
                                )
                            }
                            composable(ROUTE_ACTIVITIES) {
                                ActivitiesScreen(
                                    viewModel = viewModel,
                                    onOpenProfile = { openSecondary(ROUTE_PROFILE) },
                                    onOpenNotifications = { showNotificationsDialog = true }
                                )
                            }
                            composable(ROUTE_INTELLIGENCE) {
                                IntelligenceScreen(
                                    viewModel = viewModel,
                                    onOpenProfile = { openSecondary(ROUTE_PROFILE) },
                                    onOpenNotifications = { showNotificationsDialog = true },
                                    onNavigateActivities = { navigateTopLevel(ROUTE_ACTIVITIES) }
                                )
                            }
                            composable(ROUTE_JOURNAL) {
                                DiaryAndAiBuddyScreen(
                                    viewModel = viewModel,
                                    onOpenProfile = { openSecondary(ROUTE_PROFILE) },
                                    onOpenNotifications = { showNotificationsDialog = true }
                                )
                            }
                            composable(ROUTE_SETTINGS) {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = { navigateTopLevel(ROUTE_HOME) },
                                    onOpenProfile = { openSecondary(ROUTE_PROFILE) },
                                    onOpenNotifications = { showNotificationsDialog = true },
                                    onOpenBusiness = { openSecondary(ROUTE_BUSINESS) },
                                    onOpenSupport = { showSupportDialog = true },
                                    onSignOut = { signOut(credentialManager, scope) }
                                )
                            }
                            composable(ROUTE_BUSINESS) {
                                InventoryAnalyticsScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(ROUTE_PROFILE) {
                                ProfileScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onOpenSettings = { navigateTopLevel(ROUTE_SETTINGS) },
                                    onOpenAdminConsole = { openSecondary(ROUTE_ADMIN) },
                                    onSignOut = { signOut(credentialManager, scope) }
                                )
                            }
                            composable(ROUTE_ADMIN) {
                                AdminConsoleScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsCenterDialog(
    notifications: List<AppNotificationItem>,
    onOpenActivities: () -> Unit,
    onOpenPermissions: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notifications & Alerts") },
        text = {
            if (notifications.isEmpty()) {
                Text(
                    text = "All clear! No overdue reminders, low-stock alerts, or missing permissions.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        val tint = when (item.severity) {
                            "urgent" -> MaterialTheme.colorScheme.errorContainer
                            "warning" -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                        Surface(
                            color = tint.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenActivities) {
                Text("View Tasks")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onOpenPermissions) { Text("Permissions") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

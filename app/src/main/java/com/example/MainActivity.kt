package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ai.FocusAiRepository
import com.example.data.firebase.configureAppCheck
import com.example.data.model.UserWorkspaceConfig
import com.example.data.repository.FocusPulseRepository
import com.example.system.SupportHelper
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.DashboardHubScreen
import com.example.ui.screens.DiaryAndAiBuddyScreen
import com.example.ui.screens.HabitsAndNotebookScreen
import com.example.ui.screens.InventoryAnalyticsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShieldAndSystemScreen
import com.example.ui.theme.LocalFocusUiTokens
import com.example.ui.theme.MasterMindBackgroundHost
import com.example.ui.theme.MasterMindTheme
import com.example.ui.viewmodel.FocusPulseViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_SHIELD = "shield"
const val ROUTE_HABITS = "habits"
const val ROUTE_OPS = "ops"
const val ROUTE_DIARY = "diary"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_PROFILE = "profile"
private const val ACTION_WHATSAPP_DEV = "whatsapp_dev"

private data class BottomNavEntry(
    val id: String,
    val labelRes: Int,
    val icon: ImageVector,
    val isRoute: Boolean = true
)

private val NavBarEntries = listOf(
    BottomNavEntry(ROUTE_DASHBOARD, R.string.nav_home, Icons.Default.Dashboard),
    BottomNavEntry(ROUTE_SHIELD, R.string.nav_shield, Icons.Default.Shield),
    BottomNavEntry(ROUTE_HABITS, R.string.nav_habits, Icons.Default.CheckCircle),
    BottomNavEntry(ROUTE_OPS, R.string.nav_ops, Icons.Default.Inventory2),
    BottomNavEntry(ROUTE_DIARY, R.string.nav_diary, Icons.Default.AutoAwesome),
    BottomNavEntry(ROUTE_SETTINGS, R.string.nav_settings, Icons.Default.Settings),
    BottomNavEntry(ACTION_WHATSAPP_DEV, R.string.nav_support, Icons.AutoMirrored.Filled.Chat, isRoute = false)
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
        MasterMindTheme(UserWorkspaceConfig()) {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        val uid = user.uid
        val displayName = user.displayName ?: user.email ?: "Master Mind User"
        key(uid) {
            AuthenticatedMasterMindShell(
                currentUserId = uid,
                displayName = displayName
            )
        }
    }
}

@Composable
private fun AuthenticatedMasterMindShell(
    currentUserId: String,
    displayName: String
) {
    val context = LocalContext.current
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
                    displayName = displayName
                )
            }
        }
    )

    val configState by viewModel.workspaceConfig.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val bgRefreshKey by viewModel.customBgRefreshKey.collectAsStateWithLifecycle()
    val config = (configState as? UiState.Success)?.data ?: UserWorkspaceConfig()

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
    val currentDestination = navBackStackEntry?.destination
    val activeRoute = currentDestination?.route ?: ROUTE_DASHBOARD

    MasterMindTheme(config = config, activeRoute = activeRoute) {
        val tokens = LocalFocusUiTokens.current

        fun navigateTopLevel(route: String) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        fun openProfile() {
            navController.navigate(ROUTE_PROFILE) {
                launchSingleTop = true
            }
        }

        fun handleNavEntryClick(entry: BottomNavEntry) {
            if (entry.isRoute) {
                navigateTopLevel(entry.id)
            } else {
                SupportHelper.openDeveloperWhatsApp(context)
            }
        }

        MasterMindBackgroundHost(backgroundRefreshKey = bgRefreshKey) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useSideRail = maxWidth >= 700.dp || config.navBarStyle == "side_rail"
                val useFloatingDock = !useSideRail && config.navBarStyle == "floating_dock"
                val useBottomBar = !useSideRail && !useFloatingDock

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (useBottomBar) {
                            Surface(
                                color = tokens.resolvedNavBarColor.copy(alpha = 0.96f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.navigationBarsPadding()) {
                                    HorizontalDivider(color = tokens.resolvedBorderColor.copy(alpha = 0.65f))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        NavBarEntries.forEach { entry ->
                                            val selected = entry.isRoute &&
                                                currentDestination?.hierarchy?.any { it.route == entry.id } == true
                                            val label = stringResource(entry.labelRes)
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .minimumInteractiveComponentSize()
                                                    .heightIn(min = 54.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { handleNavEntryClick(entry) }
                                                    .padding(vertical = 4.dp)
                                                    .testTag("nav_${entry.id}"),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(
                                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                                            else Color.Transparent
                                                        )
                                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = entry.icon,
                                                        contentDescription = label,
                                                        tint = if (selected) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Text(
                                                    text = label,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (selected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (useFloatingDock) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = tokens.resolvedNavBarColor.copy(alpha = 0.95f),
                                    shape = RoundedCornerShape(28.dp),
                                    border = BorderStroke(1.dp, tokens.resolvedBorderColor),
                                    shadowElevation = 6.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        NavBarEntries.forEach { entry ->
                                            val selected = entry.isRoute &&
                                                currentDestination?.hierarchy?.any { it.route == entry.id } == true
                                            IconButton(
                                                onClick = { handleNavEntryClick(entry) },
                                                modifier = Modifier.testTag("dock_${entry.id}")
                                            ) {
                                                Icon(
                                                    imageVector = entry.icon,
                                                    contentDescription = stringResource(entry.labelRes),
                                                    tint = if (selected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useSideRail) {
                            NavigationRail(
                                containerColor = tokens.resolvedNavBarColor.copy(alpha = 0.92f)
                            ) {
                                Spacer(Modifier.size(8.dp))
                                NavBarEntries.forEach { entry ->
                                    val selected = entry.isRoute &&
                                        currentDestination?.hierarchy?.any { it.route == entry.id } == true
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { handleNavEntryClick(entry) },
                                        icon = { Icon(entry.icon, contentDescription = stringResource(entry.labelRes)) },
                                        label = { Text(stringResource(entry.labelRes)) },
                                        modifier = Modifier.testTag("rail_${entry.id}")
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_DASHBOARD,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(ROUTE_DASHBOARD) {
                                DashboardHubScreen(
                                    viewModel = viewModel,
                                    onNavigateTab = { route -> navigateTopLevel(route) },
                                    onOpenProfile = { openProfile() },
                                    onOpenSettings = { navigateTopLevel(ROUTE_SETTINGS) }
                                )
                            }
                            composable(ROUTE_SHIELD) {
                                ShieldAndSystemScreen(viewModel = viewModel)
                            }
                            composable(ROUTE_HABITS) {
                                HabitsAndNotebookScreen(viewModel = viewModel)
                            }
                            composable(ROUTE_OPS) {
                                InventoryAnalyticsScreen(viewModel = viewModel)
                            }
                            composable(ROUTE_DIARY) {
                                DiaryAndAiBuddyScreen(viewModel = viewModel)
                            }
                            composable(ROUTE_SETTINGS) {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = { navigateTopLevel(ROUTE_DASHBOARD) },
                                    onOpenProfile = { openProfile() },
                                    onSignOut = { signOut(credentialManager, scope) }
                                )
                            }
                            composable(ROUTE_PROFILE) {
                                ProfileScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onOpenSettings = { navigateTopLevel(ROUTE_SETTINGS) },
                                    onSignOut = { signOut(credentialManager, scope) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

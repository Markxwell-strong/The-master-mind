package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.FocusAiRepository
import com.example.data.ai.GeminiService
import com.example.data.ai.SupportTriageResult
import com.example.data.intelligence.InsightManager
import com.example.data.intelligence.IntelligencePipelineResult
import com.example.data.intelligence.IntelligenceRepository
import com.example.data.model.AppLimitRule
import com.example.data.model.BlockedSite
import com.example.data.model.BusinessAccountConfig
import com.example.data.model.BusinessDebtRecord
import com.example.data.model.BusinessFinancialSummary
import com.example.data.model.BusinessLedgerEntry
import com.example.data.model.DiaryEntry
import com.example.data.model.FocusSession
import com.example.data.model.HabitItem
import com.example.data.model.InventoryItem
import com.example.data.model.NoteItem
import com.example.data.model.ReorderRecord
import com.example.data.model.SupportTicket
import com.example.data.model.UserWorkspaceConfig
import com.example.data.model.computeBusinessFinancialSummary
import com.example.data.preferences.AppearancePreferencesState
import com.example.data.preferences.AppearancePreferencesStore
import com.example.data.preferences.SavedThemePreset
import com.example.data.preferences.WallpaperConfig
import com.example.data.repository.FocusPulseRepository
import com.example.system.AccountSecurityHelper
import com.example.system.AppUsageInfo
import com.example.system.BatteryTelemetry
import com.example.system.DeviceTelemetryManager
import com.example.system.FocusGuardPreferences
import com.example.system.IntelligenceSyncWorker
import com.example.system.SleepAlarmScheduler
import com.example.system.SleepCircadianInsights
import com.example.system.SystemPermissionsState
import com.example.system.UsageMonitor
import com.example.system.formatKsh
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class BuddyMessage(
    val id: String,
    val isUser: Boolean,
    val text: String
)

sealed interface AiBuddyUiState {
    data object Idle : AiBuddyUiState
    data object Loading : AiBuddyUiState
    data class Streaming(val text: String) : AiBuddyUiState
    data class Error(val message: String) : AiBuddyUiState
}

sealed interface SupportTriageUiState {
    data object Idle : SupportTriageUiState
    data object Loading : SupportTriageUiState
    data class Diagnosed(
        val ticketId: String,
        val result: SupportTriageResult,
        val deliveredToAdminInbox: Boolean
    ) : SupportTriageUiState
    data class Error(val message: String) : SupportTriageUiState
}

class FocusPulseViewModel(
    application: Application,
    private val repository: FocusPulseRepository,
    private val aiRepository: FocusAiRepository,
    private val currentUserId: String,
    displayName: String,
    val userEmail: String = ""
) : AndroidViewModel(application) {

    val currentUid: String get() = currentUserId
    val isMainAdminUser: Boolean = AccountSecurityHelper.isMainAdminEmail(userEmail)
    val isAdminUser: Boolean get() = isMainAdminUser || currentConfigOrDefault().role == "admin"

    private val telemetryManager = DeviceTelemetryManager(application)
    private val usageMonitor = UsageMonitor(application)
    private val guardPrefs = FocusGuardPreferences(application)
    private val alarmScheduler = SleepAlarmScheduler(application)
    private val appearanceStore = AppearancePreferencesStore(application)
    private val intelligenceRepo = IntelligenceRepository(application)
    private val geminiService = GeminiService()
    private val insightManager = InsightManager(usageMonitor, intelligenceRepo, geminiService)

    val appearanceState: StateFlow<AppearancePreferencesState> = appearanceStore.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppearancePreferencesState()
        )

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val workspaceConfig: StateFlow<UiState<UserWorkspaceConfig>> =
        repository.observeWorkspaceConfig(currentUserId)
            .map<UserWorkspaceConfig?, UiState<UserWorkspaceConfig>> { cfg ->
                UiState.Success(cfg ?: UserWorkspaceConfig(userId = currentUserId, displayName = displayName))
            }
            .catch { error ->
                val msg = unwrapFirestoreMessage(error, "Failed to sync workspace preferences")
                Log.w(TAG, "observeWorkspaceConfig error", error)
                emit(UiState.Error(msg))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val appRules: StateFlow<List<AppLimitRule>> =
        repository.observeAppRules(currentUserId)
            .catch { Log.w(TAG, "observeAppRules error", it); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val blockedSites: StateFlow<List<BlockedSite>> =
        repository.observeBlockedSites(currentUserId)
            .catch { Log.w(TAG, "observeBlockedSites error", it); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val habits: StateFlow<List<HabitItem>> =
        repository.observeHabits(currentUserId)
            .catch { Log.w(TAG, "observeHabits error", it); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notes: StateFlow<List<NoteItem>> =
        repository.observeNotes(currentUserId)
            .catch { Log.w(TAG, "observeNotes error", it); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val inventory: StateFlow<List<InventoryItem>> =
        repository.observeInventory(currentUserId)
            .catch { Log.w(TAG, "observeInventory error", it); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val reorders: StateFlow<List<ReorderRecord>> =
        repository.observeReorders(currentUserId)
            .catch { Log.w(TAG, "observeReorders error", it); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val businessAccount: StateFlow<BusinessAccountConfig> =
        repository.observeBusinessAccount(currentUserId)
            .map { it ?: BusinessAccountConfig(userId = currentUserId) }
            .catch { Log.w(TAG, "observeBusinessAccount error", it); emit(BusinessAccountConfig(userId = currentUserId)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), BusinessAccountConfig(userId = currentUserId))

    val businessLedger: StateFlow<List<BusinessLedgerEntry>> =
        repository.observeBusinessLedger(currentUserId)
            .catch { Log.w(TAG, "observeBusinessLedger error", it); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val businessDebts: StateFlow<List<BusinessDebtRecord>> =
        repository.observeBusinessDebts(currentUserId)
            .catch { Log.w(TAG, "observeBusinessDebts error", it); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val businessSummary: StateFlow<BusinessFinancialSummary> = combine(
        businessAccount,
        inventory,
        businessLedger,
        businessDebts
    ) { account, inv, ledger, debts ->
        computeBusinessFinancialSummary(account, inv, ledger, debts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), BusinessFinancialSummary())

    val diaryEntries: StateFlow<List<DiaryEntry>> =
        repository.observeDiaryEntries(currentUserId)
            .catch { Log.w(TAG, "observeDiaryEntries error", it); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val focusSessions: StateFlow<List<FocusSession>> =
        repository.observeFocusSessions(currentUserId)
            .catch { Log.w(TAG, "observeFocusSessions error", it); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val mySupportTickets: StateFlow<List<SupportTicket>> =
        repository.observeMySupportTickets(currentUserId)
            .catch { Log.w(TAG, "observeMySupportTickets error", it); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val isDelegatedOrMainAdmin: StateFlow<Boolean> = workspaceConfig
        .map { state ->
            val cfg = (state as? UiState.Success)?.data
            isMainAdminUser || cfg?.role == "admin"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), isMainAdminUser)

    @OptIn(ExperimentalCoroutinesApi::class)
    val adminSupportTickets: StateFlow<List<SupportTicket>> =
        if (isMainAdminUser) {
            repository.observeAllSupportTicketsForAdmin()
                .catch { Log.w(TAG, "observeAllSupportTicketsForAdmin error", it); emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
        } else {
            workspaceConfig.flatMapLatest { state ->
                val cfg = (state as? UiState.Success)?.data
                if (cfg?.role == "admin") {
                    repository.observeAssignedSupportTicketsForSubAdmin(currentUserId)
                        .catch { Log.w(TAG, "observeAssignedTickets error", it); emit(emptyList()) }
                } else {
                    flowOf(emptyList())
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
        }

    // Secret Main-Admin only: only the Main Admin ever sees the user roster & promoted admins
    val adminUsers: StateFlow<List<UserWorkspaceConfig>> =
        if (isMainAdminUser) {
            repository.observeAllUsersForAdmin()
                .catch { Log.w(TAG, "observeAllUsersForAdmin error", it); emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
        } else {
            flowOf(emptyList<UserWorkspaceConfig>())
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
        }

    // --- Real-Time Device Telemetry ---
    private val _batteryTelemetry = MutableStateFlow(BatteryTelemetry())
    val batteryTelemetry: StateFlow<BatteryTelemetry> = _batteryTelemetry.asStateFlow()

    private val _deviceApps = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val deviceApps: StateFlow<List<AppUsageInfo>> = _deviceApps.asStateFlow()

    private val _permissions = MutableStateFlow(SystemPermissionsState())
    val permissions: StateFlow<SystemPermissionsState> = _permissions.asStateFlow()

    private val _showPermissionsPrompt = MutableStateFlow(false)
    val showPermissionsPrompt: StateFlow<Boolean> = _showPermissionsPrompt.asStateFlow()

    private val _supportTriageState = MutableStateFlow<SupportTriageUiState>(SupportTriageUiState.Idle)
    val supportTriageState: StateFlow<SupportTriageUiState> = _supportTriageState.asStateFlow()

    private val _stepsToday = MutableStateFlow(0)
    val stepsToday: StateFlow<Int> = _stepsToday.asStateFlow()

    private val _sleepInsights = MutableStateFlow(
        SleepCircadianInsights("22:30", 8.0, listOf("06:15 (7.5h / 5 cycles)"), 0, false)
    )
    val sleepInsights: StateFlow<SleepCircadianInsights> = _sleepInsights.asStateFlow()

    private val _deepWorkRemainingSec = MutableStateFlow(0L)
    val deepWorkRemainingSec: StateFlow<Long> = _deepWorkRemainingSec.asStateFlow()

    // --- AI Buddy & Diary AI State ---
    private val _aiEvaluatingDiary = MutableStateFlow(false)
    val aiEvaluatingDiary: StateFlow<Boolean> = _aiEvaluatingDiary.asStateFlow()

    private val _aiDiaryError = MutableStateFlow<String?>(null)
    val aiDiaryError: StateFlow<String?> = _aiDiaryError.asStateFlow()

    private val _buddyMessages = MutableStateFlow(
        listOf(
            BuddyMessage(
                id = "welcome",
                isUser = false,
                text = "Hi! I'm THE MASTER MIND AI Buddy powered by Gemini. Ask me to audit your screen time, plan deep work, review business margins, or suggest offline activities."
            )
        )
    )
    val buddyMessages: StateFlow<List<BuddyMessage>> = _buddyMessages.asStateFlow()

    private val _customBgRefreshKey = MutableStateFlow(0)
    val customBgRefreshKey: StateFlow<Int> = _customBgRefreshKey.asStateFlow()

    private val _profilePhotoRefreshKey = MutableStateFlow(0)
    val profilePhotoRefreshKey: StateFlow<Int> = _profilePhotoRefreshKey.asStateFlow()

    private val _buddyState = MutableStateFlow<AiBuddyUiState>(AiBuddyUiState.Idle)
    val buddyState: StateFlow<AiBuddyUiState> = _buddyState.asStateFlow()

    private val _intelligenceState = MutableStateFlow(IntelligenceUiState())
    val intelligenceState: StateFlow<IntelligenceUiState> = _intelligenceState.asStateFlow()

    private var latestPipelineResult: IntelligencePipelineResult? = null
    private var buddyJob: Job? = null
    private var intelligenceJob: Job? = null

    val notificationsFeed: StateFlow<List<AppNotificationItem>> = combine(
        notes,
        inventory,
        permissions,
        _intelligenceState
    ) { noteList, invList, perms, intel ->
        buildList {
            val now = System.currentTimeMillis()
            noteList.filter { !it.isCompleted && it.reminderAtMillis in 1L..now }.forEach { n ->
                add(
                    AppNotificationItem(
                        id = "overdue_${n.id}",
                        title = "Overdue Reminder: ${n.title}",
                        detail = n.content.ifBlank { "Scheduled task reminder is overdue." },
                        severity = "urgent"
                    )
                )
            }
            invList.filter { it.isLowStock }.forEach { item ->
                add(
                    AppNotificationItem(
                        id = "stock_${item.id}",
                        title = "Low Stock: ${item.name}",
                        detail = "${item.stockQty} units left (threshold ${item.lowStockThreshold}).",
                        severity = "warning"
                    )
                )
            }
            if (!perms.usageStatsGranted) {
                add(
                    AppNotificationItem(
                        id = "perm_usage",
                        title = "Usage Access Needed",
                        detail = "Enable Android Usage Access in Settings so AI Intelligence can track screen-time shifts.",
                        severity = "info"
                    )
                )
            }
            intel.insights.take(3).forEach { ins ->
                add(
                    AppNotificationItem(
                        id = "ins_${ins.id}",
                        title = ins.title,
                        detail = ins.summary,
                        severity = "info"
                    )
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureWorkspaceConfig(displayName)
        }
        guardPrefs.touchUserActivity()
        refreshSystemTelemetry(checkOnboardingPrompt = true)
        startSyncBridgeLoop()
        startTickerLoop()
        observeIntelligenceStreams()
    }

    private fun observeIntelligenceStreams() {
        viewModelScope.launch {
            combine(
                intelligenceRepo.observePatterns(),
                intelligenceRepo.observeActiveInsights(),
                intelligenceRepo.observeMemories(),
                intelligenceRepo.observeRecentActivities(60),
                appearanceState
            ) { pats, ins, mems, acts, appPref ->
                val st = _intelligenceState.value
                st.copy(
                    monitoringEnabled = appPref.monitoringEnabled,
                    retentionDays = appPref.retentionDays,
                    patterns = pats,
                    insights = ins,
                    memories = mems,
                    recentActivities = acts
                )
            }.collect { next ->
                _intelligenceState.value = next
            }
        }

        viewModelScope.launch {
            appearanceState.collect { pref ->
                if (pref.monitoringEnabled) {
                    IntelligenceSyncWorker.schedulePeriodic(getApplication())
                } else {
                    IntelligenceSyncWorker.cancelPeriodic(getApplication())
                }
            }
        }

        viewModelScope.launch {
            combine(
                habits,
                notes,
                focusSessions,
                diaryEntries,
                appRules
            ) { h, n, f, d, r ->
                listOf(h.size, n.size, f.size, d.size, r.size, h.sumOf { it.completedToday }.toInt())
            }.collect {
                delay(400L)
                refreshIntelligencePipeline(runBriefingIfMissing = false)
            }
        }
    }

    private fun startSyncBridgeLoop() {
        viewModelScope.launch {
            var lastScheduleKey: List<Long>? = null
            combine(workspaceConfig, appRules, blockedSites) { cfgState, rules, sites ->
                Triple((cfgState as? UiState.Success)?.data, rules, sites)
            }.collect { (cfg, rules, sites) ->
                if (cfg != null) {
                    guardPrefs.syncState(cfg, rules, sites)
                    val scheduleKey = listOf(
                        cfg.bedtimeHour,
                        cfg.bedtimeMinute,
                        cfg.wakeHour,
                        cfg.wakeMinute,
                        cfg.inactivityAlertHours
                    )
                    if (scheduleKey != lastScheduleKey) {
                        lastScheduleKey = scheduleKey
                        alarmScheduler.scheduleBedtimeAndWake(
                            bedHour = cfg.bedtimeHour.toInt(),
                            bedMinute = cfg.bedtimeMinute.toInt(),
                            wakeHour = cfg.wakeHour.toInt(),
                            wakeMinute = cfg.wakeMinute.toInt(),
                            inactivityHours = cfg.inactivityAlertHours.toInt()
                        )
                    }
                    _sleepInsights.value = telemetryManager.computeSleepCircadianInsights(
                        bedtimeHour = cfg.bedtimeHour.toInt(),
                        bedtimeMinute = cfg.bedtimeMinute.toInt(),
                        wakeHour = cfg.wakeHour.toInt(),
                        wakeMinute = cfg.wakeMinute.toInt(),
                        stepCount = _stepsToday.value
                    )
                }
            }
        }
    }

    private fun startTickerLoop() {
        viewModelScope.launch {
            while (true) {
                val cfg = (workspaceConfig.value as? UiState.Success)?.data
                val until = cfg?.deepWorkActiveUntilMs ?: 0L
                val rem = ((until - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                _deepWorkRemainingSec.value = rem
                delay(1000L)
            }
        }
    }

    fun refreshSystemTelemetry(checkOnboardingPrompt: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val perms = telemetryManager.checkPermissions()
            val battery = telemetryManager.readBatteryTelemetry()
            val apps = telemetryManager.queryAppsAndUsage()
            val missingAny = !perms.notificationsGranted ||
                !perms.microphoneGranted ||
                !perms.usageStatsGranted ||
                !perms.accessibilityEnabled ||
                !perms.dndPolicyGranted ||
                !perms.exactAlarmGranted
            withContext(Dispatchers.Main) {
                _permissions.value = perms
                _batteryTelemetry.value = battery
                _deviceApps.value = apps
                if (checkOnboardingPrompt && missingAny && !guardPrefs.hasPromptedPermissionsForUid(currentUserId)) {
                    _showPermissionsPrompt.value = true
                }
                val cfg = (workspaceConfig.value as? UiState.Success)?.data
                if (cfg != null) {
                    _sleepInsights.value = telemetryManager.computeSleepCircadianInsights(
                        cfg.bedtimeHour.toInt(),
                        cfg.bedtimeMinute.toInt(),
                        cfg.wakeHour.toInt(),
                        cfg.wakeMinute.toInt(),
                        _stepsToday.value
                    )
                }
            }
        }
        telemetryManager.readStepSensorSnapshot { steps ->
            _stepsToday.value = steps
        }
    }

    fun openPermissionsPrompt() {
        _showPermissionsPrompt.value = true
    }

    fun dismissPermissionsPrompt() {
        guardPrefs.markPermissionsPromptedForUid(currentUserId)
        _showPermissionsPrompt.value = false
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun showStatusMessage(message: String) {
        _statusBanner.value = message
    }

    fun notifyCustomBackgroundChanged(preset: String) {
        _customBgRefreshKey.value += 1
        updateWorkspaceConfig { it.copy(backgroundPreset = preset) }
    }

    fun notifyProfilePhotoChanged() {
        _profilePhotoRefreshKey.value += 1
    }

    fun updatePartColor(partKey: String, hexColor: String?) {
        updateWorkspaceConfig { cfg ->
            val next = cfg.partColors.toMutableMap()
            if (hexColor.isNullOrBlank()) {
                next.remove(partKey)
            } else {
                next[partKey] = hexColor
            }
            cfg.copy(partColors = next)
        }
    }

    fun resetAllPartColors() {
        updateWorkspaceConfig { it.copy(partColors = emptyMap()) }
    }

    fun currentConfigOrDefault(): UserWorkspaceConfig =
        (workspaceConfig.value as? UiState.Success)?.data ?: UserWorkspaceConfig(userId = currentUserId)

    // --- Workspace & Dashboard Customization ---

    fun updateWorkspaceConfig(transform: (UserWorkspaceConfig) -> UserWorkspaceConfig) {
        viewModelScope.launch {
            val next = transform(currentConfigOrDefault())
            repository.saveWorkspaceConfig(next).onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to sync settings"
            }
        }
    }

    fun selectDashboard(dashboardId: String) {
        updateWorkspaceConfig { it.copy(activeDashboardId = dashboardId) }
    }

    // --- Deep Work & DND ---

    fun startDeepWorkSession(minutes: Long, title: String = "Deep-Work Sprint") {
        viewModelScope.launch {
            val cfg = currentConfigOrDefault()
            val endMs = System.currentTimeMillis() + minutes * 60_000L
            if (cfg.deepWorkDndEnabled) {
                val applied = telemetryManager.setDeepWorkDnd(true)
                if (!applied) {
                    _statusBanner.value = "Focus timer started. Grant DND Policy Access in Shield tab to auto-silence notifications."
                }
            }
            repository.saveWorkspaceConfig(
                cfg.copy(deepWorkMinutes = minutes, deepWorkActiveUntilMs = endMs)
            )
            repository.logFocusSession(
                FocusSession(
                    title = title,
                    durationMinutes = minutes,
                    mode = "deep_work",
                    completed = true,
                    distractionsBlocked = blockedSites.value.size.toLong() + appRules.value.count { it.blockDuringDeepWork }
                )
            )
        }
    }

    fun stopDeepWorkSession() {
        viewModelScope.launch {
            telemetryManager.setDeepWorkDnd(false)
            updateWorkspaceConfig { it.copy(deepWorkActiveUntilMs = 0L) }
        }
    }

    // --- App Limits, Doomscroll Lockout & Background Process Stop ---

    fun saveAppRule(rule: AppLimitRule) {
        viewModelScope.launch {
            val liveMins = _deviceApps.value.firstOrNull { it.packageName == rule.packageName }?.minutesToday
                ?: rule.todayUsedMinutes
            repository.upsertAppRule(rule.copy(todayUsedMinutes = liveMins)).onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to save app rule"
            }
        }
    }

    fun lockAppUntilWake(rule: AppLimitRule) {
        viewModelScope.launch {
            val cfg = currentConfigOrDefault()
            val wakeCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, cfg.wakeHour.toInt())
                set(Calendar.MINUTE, cfg.wakeMinute.toInt())
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            repository.upsertAppRule(
                rule.copy(isLocked = true, lockedUntilMillis = wakeCal.timeInMillis)
            )
            telemetryManager.killBackgroundApp(rule.packageName)
            _statusBanner.value = "${rule.appName} locked until ${String.format("%02d:%02d", cfg.wakeHour, cfg.wakeMinute)}."
        }
    }

    fun unlockAppRule(rule: AppLimitRule) {
        viewModelScope.launch {
            repository.upsertAppRule(rule.copy(isLocked = false, lockedUntilMillis = 0L))
        }
    }

    fun deleteAppRule(ruleId: String) {
        viewModelScope.launch { repository.deleteAppRule(ruleId) }
    }

    fun stopBackgroundProcess(app: AppUsageInfo) {
        val ok = telemetryManager.killBackgroundApp(app.packageName)
        refreshSystemTelemetry()
        _statusBanner.value = if (ok) {
            "Requested OS to stop background processes for ${app.appName}."
        } else {
            "Could not stop ${app.appName} directly; opening system App Info."
        }
        if (!ok) {
            telemetryManager.openAppDetailsSettings(app.packageName)
        }
    }

    fun openSystemAppInfo(packageName: String) {
        telemetryManager.openAppDetailsSettings(packageName)
    }

    // --- Gaming Mode & Auto Call-Decline ---

    fun setGamingModeEnabled(enabled: Boolean) {
        updateWorkspaceConfig { it.copy(gamingModeEnabled = enabled) }
        if (enabled && !permissions.value.callScreeningRoleGranted && !permissions.value.dndPolicyGranted) {
            _statusBanner.value = "Gaming Mode ON. Grant Call Screening Role or DND access below to auto-decline incoming calls."
        } else {
            _statusBanner.value = if (enabled) {
                "Gaming Mode ON — Incoming calls will auto-decline."
            } else {
                "Gaming Mode OFF."
            }
        }
    }

    fun setGamingAutoDeclineCalls(enabled: Boolean) {
        updateWorkspaceConfig { it.copy(gamingAutoDeclineCalls = enabled) }
    }

    fun toggleGamingAppPackage(pkg: String) {
        val clean = pkg.trim()
        if (clean.isEmpty()) return
        updateWorkspaceConfig { cfg ->
            val current = cfg.gamingAppPackages.toMutableList()
            if (clean in current) current.remove(clean) else current.add(clean)
            cfg.copy(gamingAppPackages = current)
        }
    }

    fun createCallScreeningRoleIntent(): Intent? = telemetryManager.createCallScreeningRoleIntent()

    fun quickToggleAppBlock(app: AppUsageInfo, dailyLimitMinutes: Long = 25L) {
        val existing = appRules.value.firstOrNull { it.packageName == app.packageName }
        if (existing != null) {
            deleteAppRule(existing.id)
            _statusBanner.value = "Removed block rule for ${app.appName}."
        } else {
            saveAppRule(
                AppLimitRule(
                    packageName = app.packageName,
                    appName = app.appName,
                    category = app.categoryGuess,
                    dailyLimitMinutes = dailyLimitMinutes,
                    blockDuringSleep = true,
                    blockDuringDeepWork = true
                )
            )
            _statusBanner.value = "${app.appName} added to Focus & Doomscroll Shield."
        }
    }

    // --- Blocked Sites Gateway ---

    fun addBlockedSite(domain: String, label: String, blockAlways: Boolean) {
        viewModelScope.launch {
            repository.upsertBlockedSite(
                BlockedSite(
                    domain = domain.trim().lowercase(),
                    label = label.ifBlank { domain }.trim(),
                    blockDuringDeepWork = true,
                    blockAlways = blockAlways
                )
            ).onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to block domain"
            }
        }
    }

    fun deleteBlockedSite(siteId: String) {
        viewModelScope.launch { repository.deleteBlockedSite(siteId) }
    }

    // --- Habits ---

    fun createHabit(title: String, category: String, targetPerDay: Long, reminderTime: String) {
        viewModelScope.launch {
            repository.upsertHabit(
                HabitItem(
                    title = title,
                    category = category,
                    targetPerDay = targetPerDay.coerceAtLeast(1L),
                    reminderTime = reminderTime
                )
            ).onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun incrementHabit(habit: HabitItem) {
        viewModelScope.launch {
            val todayKey = todayDateKey()
            val newCompleted = habit.completedToday + 1L
            val reachedTarget = newCompleted >= habit.targetPerDay
            val newStreak = if (reachedTarget && habit.lastCompletedDate != todayKey) {
                habit.streakDays + 1L
            } else {
                habit.streakDays
            }
            repository.upsertHabit(
                habit.copy(
                    completedToday = newCompleted,
                    streakDays = newStreak,
                    lastCompletedDate = if (reachedTarget) todayKey else habit.lastCompletedDate
                )
            )
            guardPrefs.touchUserActivity()
        }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch { repository.deleteHabit(habitId) }
    }

    // --- Notebook & Reminders ---

    fun saveNote(note: NoteItem) {
        viewModelScope.launch {
            repository.upsertNote(note).onSuccess { id ->
                if (note.reminderAtMillis > System.currentTimeMillis()) {
                    alarmScheduler.scheduleNoteReminder(id, note.title, note.reminderAtMillis)
                }
                guardPrefs.touchUserActivity()
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to save note"
            }
        }
    }

    fun toggleNotePin(note: NoteItem) {
        viewModelScope.launch { repository.upsertNote(note.copy(isPinned = !note.isPinned)) }
    }

    fun toggleNoteComplete(note: NoteItem) {
        viewModelScope.launch { repository.upsertNote(note.copy(isCompleted = !note.isCompleted)) }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch { repository.deleteNote(noteId) }
    }

    // --- Shop Inventory, Business Accounting (KSh) & Automated Reordering ---

    fun initializeBusinessAccount(businessName: String, openingCash: Double) {
        viewModelScope.launch {
            repository.initializeBusinessAccount(businessName, openingCash).onSuccess {
                _statusBanner.value = "Business account initialized with ${formatKsh(openingCash)}."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to initialize business account"
            }
        }
    }

    fun addBusinessCash(amount: Double, note: String = "Capital / Funds Added") {
        if (amount <= 0.0) {
            _statusBanner.value = "Enter an amount greater than KSh 0."
            return
        }
        viewModelScope.launch {
            repository.recordCashMovement(
                entryType = "capital_add",
                title = note.trim().ifBlank { "Capital / Funds Added" },
                category = "Capital",
                signedDeltaCash = amount,
                capitalAdditionAmount = amount
            ).onSuccess { newCash ->
                _statusBanner.value = "Added ${formatKsh(amount)}. Cash balance: ${formatKsh(newCash)}."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to add business funds"
            }
        }
    }

    fun recordBusinessExpense(title: String, category: String, amount: Double) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank() || amount <= 0.0) {
            _statusBanner.value = "Provide expense description and amount > 0."
            return
        }
        viewModelScope.launch {
            repository.recordCashMovement(
                entryType = "expense",
                title = cleanTitle,
                category = category.trim().ifBlank { "Operating" },
                signedDeltaCash = -amount
            ).onSuccess { newCash ->
                _statusBanner.value = "Expense recorded (${formatKsh(amount)}). Cash balance: ${formatKsh(newCash)}."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to record expense"
            }
        }
    }

    fun saveDebtRecord(record: BusinessDebtRecord) {
        if (record.personName.isBlank() || record.totalAmount <= 0.0) {
            _statusBanner.value = "Enter person name and valid KSh amount."
            return
        }
        viewModelScope.launch {
            repository.upsertDebtRecord(record).onSuccess {
                _statusBanner.value = if (record.debtType == "creditor") {
                    "Creditor ${record.personName} saved."
                } else {
                    "Debtor ${record.personName} saved."
                }
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to save record"
            }
        }
    }

    fun settleDebtRecord(record: BusinessDebtRecord, paymentAmount: Double) {
        val pay = paymentAmount.coerceIn(0.0, record.remainingAmount)
        if (pay <= 0.0) return
        viewModelScope.launch {
            val newSettled = (record.settledAmount + pay).coerceAtMost(record.totalAmount)
            val nowSettled = newSettled >= (record.totalAmount - 0.005)
            repository.upsertDebtRecord(
                record.copy(settledAmount = newSettled, isSettled = nowSettled)
            ).onSuccess {
                val isDebtor = record.debtType != "creditor"
                repository.recordCashMovement(
                    entryType = if (isDebtor) "debtor_payment" else "creditor_payment",
                    title = if (isDebtor) "Collected from ${record.personName}" else "Paid creditor ${record.personName}",
                    category = if (isDebtor) "Debt Collection" else "Creditor Payment",
                    signedDeltaCash = if (isDebtor) pay else -pay,
                    relatedItemId = record.id
                )
                _statusBanner.value = if (isDebtor) {
                    "Collected ${formatKsh(pay)} from ${record.personName}."
                } else {
                    "Paid ${formatKsh(pay)} to ${record.personName}."
                }
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to settle balance"
            }
        }
    }

    fun deleteDebtRecord(debtId: String) {
        viewModelScope.launch { repository.deleteDebtRecord(debtId) }
    }

    fun deleteLedgerEntry(entryId: String) {
        viewModelScope.launch { repository.deleteLedgerEntry(entryId) }
    }

    fun saveInventoryItem(item: InventoryItem, deductCashForAddedStock: Boolean = true) {
        viewModelScope.launch {
            val previousQty = if (item.id.isNotBlank()) {
                inventory.value.firstOrNull { it.id == item.id }?.stockQty ?: 0L
            } else {
                0L
            }
            val addedQty = (item.stockQty - previousQty).coerceAtLeast(0L)
            repository.upsertInventoryItem(item).onSuccess { savedId ->
                val savedItem = item.copy(id = savedId)
                if (deductCashForAddedStock && addedQty > 0L && item.unitCost > 0.0 && businessAccount.value.isInitialized) {
                    val cost = addedQty * item.unitCost
                    repository.recordCashMovement(
                        entryType = "restock_deduction",
                        title = "Restocked +$addedQty ${item.name}",
                        category = "Restock",
                        signedDeltaCash = -cost,
                        relatedItemId = savedId
                    )
                }
                checkAndTriggerAutoReorder(savedItem)
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to save inventory item"
            }
        }
    }

    fun adjustInventoryStock(
        item: InventoryItem,
        delta: Long,
        recordSaleIfNegative: Boolean = false,
        creditCashOnSale: Boolean = true
    ) {
        viewModelScope.launch {
            if (delta < 0L && item.stockQty <= 0L) {
                _statusBanner.value = "${item.name} is out of stock. Restock before recording a sale."
                return@launch
            }
            val newQty = (item.stockQty + delta).coerceAtLeast(0L)
            val actualSold = if (recordSaleIfNegative && delta < 0L) {
                (item.stockQty - newQty).coerceAtLeast(0L)
            } else {
                0L
            }
            val updated = item.copy(
                stockQty = newQty,
                unitsSold = item.unitsSold + actualSold
            )
            repository.upsertInventoryItem(updated).onSuccess {
                if (delta > 0L && item.unitCost > 0.0) {
                    val restockCost = delta * item.unitCost
                    repository.recordCashMovement(
                        entryType = "restock_deduction",
                        title = "Restocked +$delta ${item.name}",
                        category = "Restock",
                        signedDeltaCash = -restockCost,
                        relatedItemId = item.id
                    )
                } else if (actualSold > 0L && creditCashOnSale && item.unitPrice > 0.0) {
                    val saleRevenue = actualSold * item.unitPrice
                    repository.recordCashMovement(
                        entryType = "sale_inflow",
                        title = "Sold ${actualSold}x ${item.name}",
                        category = "Sales",
                        signedDeltaCash = saleRevenue,
                        relatedItemId = item.id
                    )
                }
                checkAndTriggerAutoReorder(updated)
            }
        }
    }

    fun deleteInventoryItem(itemId: String) {
        viewModelScope.launch { repository.deleteInventoryItem(itemId) }
    }

    fun receiveReorder(reorder: ReorderRecord) {
        viewModelScope.launch {
            val matchingItem = inventory.value.firstOrNull { it.id == reorder.itemId }
            repository.markReorderReceived(reorder, matchingItem).onSuccess {
                if (reorder.totalCost > 0.0) {
                    repository.recordCashMovement(
                        entryType = "restock_deduction",
                        title = "Received PO: ${reorder.itemName} (+${reorder.quantity})",
                        category = "Restock",
                        signedDeltaCash = -reorder.totalCost,
                        relatedItemId = reorder.itemId
                    )
                }
                _statusBanner.value = "Restocked +${reorder.quantity} units of ${reorder.itemName} (${formatKsh(reorder.totalCost)} deducted)."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to receive order"
            }
        }
    }

    private suspend fun checkAndTriggerAutoReorder(item: InventoryItem) {
        if (!item.isLowStock) return
        val hasOpenOrder = reorders.value.any {
            it.itemId == item.id && (it.status == "pending" || it.status == "ordered")
        }
        if (item.autoReorder && !hasOpenOrder) {
            val orderQty = item.reorderQty.coerceAtLeast(1L)
            repository.createReorder(
                ReorderRecord(
                    itemId = item.id,
                    itemName = item.name,
                    sku = item.sku,
                    quantity = orderQty,
                    totalCost = orderQty * item.unitCost,
                    supplierName = item.supplierName.ifBlank { "Primary Supplier" },
                    status = "ordered",
                    isAutoTriggered = true
                )
            )
            alarmScheduler.sendLowStockNotification(item.name, item.stockQty, orderQty)
            _statusBanner.value = "Auto-Reorder triggered: +$orderQty ${item.name} from ${item.supplierName.ifBlank { "Supplier" }}."
        } else if (!item.autoReorder) {
            alarmScheduler.sendLowStockNotification(item.name, item.stockQty, null)
        }
    }

    // --- Daily Diary & Gemini AI Evaluation ---

    fun todayDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun onlineHoursToday(): Double {
        val totalMins = _deviceApps.value.sumOf { it.minutesToday }
        return totalMins / 60.0
    }

    fun saveAndEvaluateDiary(
        activitiesText: String,
        moodScore: Int,
        energyScore: Int,
        voiceTranscribed: Boolean
    ) {
        viewModelScope.launch {
            guardPrefs.touchUserActivity()
            _aiDiaryError.value = null
            _aiEvaluatingDiary.value = true

            val dateKey = todayDateKey()
            val existing = diaryEntries.value.firstOrNull { it.dateKey == dateKey }
            val onlineHrs = onlineHoursToday()
            val habitsDone = habits.value.count { it.completedToday >= it.targetPerDay }
            val topApps = _deviceApps.value.take(4).joinToString(", ") { "${it.appName} (${it.minutesToday}m)" }
                .ifBlank { "No foreground usage stats granted yet" }
            val persona = currentConfigOrDefault().aiCoachPersona

            val evalResult = aiRepository.evaluateDayOrRecommend(
                activitiesText = activitiesText,
                onlineHours = onlineHrs,
                habitsCompleted = habitsDone,
                habitsTotal = habits.value.size,
                moodScore = moodScore,
                energyScore = energyScore,
                topAppSummary = topApps,
                persona = persona
            )

            evalResult.onSuccess { eval ->
                val entry = DiaryEntry(
                    id = existing?.id.orEmpty(),
                    dateKey = dateKey,
                    activitiesText = activitiesText,
                    moodScore = moodScore.toLong(),
                    energyScore = energyScore.toLong(),
                    onlineHours = onlineHrs,
                    voiceTranscribed = voiceTranscribed,
                    aiScore = eval.score,
                    aiFeedback = eval.feedback,
                    aiRecommendations = eval.recommendations
                )
                repository.saveDiaryEntry(entry).onFailure {
                    _aiDiaryError.value = it.localizedMessage ?: "Saved AI feedback failed to sync."
                }
            }.onFailure { err ->
                // Still save the user's diary text so effort is never lost, and surface the real AI error
                val entry = DiaryEntry(
                    id = existing?.id.orEmpty(),
                    dateKey = dateKey,
                    activitiesText = activitiesText,
                    moodScore = moodScore.toLong(),
                    energyScore = energyScore.toLong(),
                    onlineHours = onlineHrs,
                    voiceTranscribed = voiceTranscribed,
                    aiScore = existing?.aiScore ?: -1L,
                    aiFeedback = existing?.aiFeedback.orEmpty(),
                    aiRecommendations = existing?.aiRecommendations.orEmpty()
                )
                repository.saveDiaryEntry(entry)
                _aiDiaryError.value = err.localizedMessage ?: "Gemini AI evaluation failed."
            }
            _aiEvaluatingDiary.value = false
        }
    }

    // --- Streaming AI Buddy Chat ---

    fun sendBuddyMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return
        guardPrefs.touchUserActivity()

        buddyJob?.cancel()
        val historyPairs = _buddyMessages.value
            .filter { it.id != "welcome" && it.text.isNotBlank() }
            .map { msg -> (if (msg.isUser) "user" else "model") to msg.text }

        val userBubble = BuddyMessage("u_${System.currentTimeMillis()}", true, trimmed)
        _buddyMessages.value = _buddyMessages.value + userBubble

        val cfg = currentConfigOrDefault()
        val lowStockCount = inventory.value.count { it.isLowStock }
        val totalRev = inventory.value.sumOf { it.totalRevenue }
        val fin = businessSummary.value
        val todayEntry = diaryEntries.value.firstOrNull { it.dateKey == todayDateKey() }
        val contextSummary = buildString {
            appendLine("- Screen time today: ${String.format(Locale.US, "%.1f", onlineHoursToday())}h across ${_deviceApps.value.size} tracked apps")
            appendLine("- Productivity Score: ${_intelligenceState.value.analytics.dailyProductivityScore}/100")
            appendLine("- Habits completed today: ${habits.value.count { it.completedToday >= it.targetPerDay }}/${habits.value.size}")
            appendLine("- Sleep schedule: Bed ${String.format(Locale.US, "%02d:%02d", cfg.bedtimeHour, cfg.bedtimeMinute)}, Wake ${String.format(Locale.US, "%02d:%02d", cfg.wakeHour, cfg.wakeMinute)}")
            appendLine("- Business Inventory SKUs: ${inventory.value.size} ($lowStockCount low stock), Revenue ${formatKsh(totalRev)}, Cash ${formatKsh(fin.currentCash)}, Worth ${formatKsh(fin.businessWorth)}")
            appendLine("- Today's diary: ${todayEntry?.activitiesText?.ifBlank { "No activities logged yet" } ?: "Not started"}")
        }

        buddyJob = viewModelScope.launch {
            _buddyState.value = AiBuddyUiState.Loading
            var finalResponse = ""
            try {
                val retrieved = intelligenceRepo.memoryManager.retrieveRelevantMemories(trimmed, limit = 4)
                val memNotes = if (retrieved.isNotEmpty()) {
                    "\nRelevant AI Memories:\n" + retrieved.joinToString("\n") { "- [${it.tier}] ${it.summary}" }
                } else ""
                aiRepository.streamBuddyReply(
                    userMessage = trimmed,
                    historyPairs = historyPairs,
                    contextSummary = contextSummary + memNotes,
                    persona = cfg.aiCoachPersona
                ).collect { accumulated ->
                    finalResponse = accumulated
                    _buddyState.value = AiBuddyUiState.Streaming(accumulated)
                }
                _buddyMessages.value = _buddyMessages.value + BuddyMessage(
                    id = "m_${System.currentTimeMillis()}",
                    isUser = false,
                    text = finalResponse
                )
                _buddyState.value = AiBuddyUiState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _buddyState.value = AiBuddyUiState.Error(
                    e.localizedMessage ?: "AI Buddy failed to respond."
                )
            }
        }
    }

    // --- System Actions & Alarms ---

    fun triggerSampleAlarm(isBedtime: Boolean) {
        alarmScheduler.triggerImmediateTestAlarm(
            type = if (isBedtime) SleepAlarmScheduler.TYPE_BEDTIME else SleepAlarmScheduler.TYPE_WAKE,
            title = if (isBedtime) "Bedtime Wind-Down Alarm" else "Circadian Wake-Up Alarm",
            subtitle = if (isBedtime) "Distracting apps are locking for sleep recovery." else "Good morning! Sleep lockout lifted."
        )
    }

    fun openUsageAccessSettings() = telemetryManager.openUsageAccessSettings()
    fun openAccessibilitySettings() = telemetryManager.openAccessibilitySettings()
    fun openDndPolicySettings() = telemetryManager.openDndPolicySettings()
    fun openExactAlarmSettings() = telemetryManager.openExactAlarmSettings()
    fun openHealthConnectSettings() = telemetryManager.openHealthConnectSettings()

    // --- Support Triage (Gemini + Admin Inbox) ---

    fun resetSupportTriage() {
        _supportTriageState.value = SupportTriageUiState.Idle
    }

    fun diagnoseAndSendSupport(
        category: String,
        issueText: String,
        autoDelivered: Boolean = true
    ) {
        val cleanIssue = issueText.trim()
        if (cleanIssue.isBlank()) {
            _supportTriageState.value = SupportTriageUiState.Error("Please describe the problem first.")
            return
        }
        viewModelScope.launch {
            _supportTriageState.value = SupportTriageUiState.Loading
            val cfg = currentConfigOrDefault()
            val p = _permissions.value
            val diagnostics = buildString {
                append("User=${cfg.displayName}, Email=$userEmail, ")
                append("Role=${cfg.role}, UsageStats=${p.usageStatsGranted}, Accessibility=${p.accessibilityEnabled}, ")
                append("DND=${p.dndPolicyGranted}, Notifications=${p.notificationsGranted}, ExactAlarm=${p.exactAlarmGranted}, Mic=${p.microphoneGranted}")
            }

            val aiResult = aiRepository.troubleshootSupportIssue(
                issueText = cleanIssue,
                category = category,
                appDiagnostics = diagnostics
            )

            aiResult.onSuccess { triage ->
                var ticketId = ""
                var delivered = false
                if (autoDelivered) {
                    val ticket = SupportTicket(
                        userId = currentUserId,
                        userEmail = userEmail,
                        category = triage.recommendedCategory,
                        message = cleanIssue,
                        aiDiagnosis = triage.diagnosis,
                        status = if (triage.resolvedLikely) "open" else "escalated",
                        sentToWhatsApp = false
                    )
                    repository.createSupportTicket(ticket).onSuccess { id ->
                        ticketId = id
                        delivered = true
                    }
                }
                _supportTriageState.value = SupportTriageUiState.Diagnosed(
                    ticketId = ticketId,
                    result = triage,
                    deliveredToAdminInbox = delivered
                )
            }.onFailure { err ->
                // Even if Gemini fails, still create the support ticket so Admin gets notified
                var ticketId = ""
                var delivered = false
                if (autoDelivered) {
                    val ticket = SupportTicket(
                        userId = currentUserId,
                        userEmail = userEmail,
                        category = category,
                        message = cleanIssue,
                        aiDiagnosis = "Gemini offline: ${err.localizedMessage ?: "error"}",
                        status = "escalated",
                        sentToWhatsApp = false
                    )
                    repository.createSupportTicket(ticket).onSuccess { id ->
                        ticketId = id
                        delivered = true
                    }
                }
                val msg = buildString {
                    append(err.localizedMessage ?: "Gemini could not reach the server.")
                    if (delivered) append(" Your message was still delivered to the Admin Inbox.")
                }
                _supportTriageState.value = SupportTriageUiState.Error(msg)
            }
        }
    }

    fun markSupportTicketResolvedByAi(ticketId: String, aiDiagnosis: String) {
        if (ticketId.isBlank()) return
        viewModelScope.launch {
            repository.updateSupportTicketByUser(
                ticketId = ticketId,
                status = "ai_resolved",
                sentToWhatsApp = false,
                aiDiagnosis = aiDiagnosis
            )
            _statusBanner.value = "Marked resolved by Gemini Support."
        }
    }

    fun markSupportTicketSentToWhatsApp(ticketId: String, aiDiagnosis: String) {
        if (ticketId.isBlank()) return
        viewModelScope.launch {
            repository.updateSupportTicketByUser(
                ticketId = ticketId,
                status = "escalated",
                sentToWhatsApp = true,
                aiDiagnosis = aiDiagnosis
            )
        }
    }

    // --- Profile Identity (Display Name & Username) & Admin Controls ---

    fun updateProfileIdentity(displayName: String, username: String) {
        val cleanName = displayName.trim()
        val cleanUsername = username.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "").take(40)
        if (cleanName.isBlank()) {
            _statusBanner.value = "Display name cannot be empty."
            return
        }
        viewModelScope.launch {
            val cfg = currentConfigOrDefault()
            val updated = cfg.copy(
                displayName = cleanName,
                username = cleanUsername,
                email = userEmail.ifBlank { cfg.email }
            )
            repository.saveWorkspaceConfig(updated).onSuccess {
                _statusBanner.value = "Profile updated."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    fun updateUsername(username: String) {
        val cleanUsername = username.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "").take(40)
        if (cleanUsername.length < 3) {
            _statusBanner.value = "Username must be at least 3 characters (letters, numbers, ., _, -)."
            return
        }
        viewModelScope.launch {
            val cfg = currentConfigOrDefault()
            repository.saveWorkspaceConfig(cfg.copy(username = cleanUsername)).onSuccess {
                _statusBanner.value = "Username @$cleanUsername saved."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to save username"
            }
        }
    }

    fun updateDisplayName(displayName: String) {
        updateProfileIdentity(displayName, currentConfigOrDefault().username)
    }

    fun setUserAdminRole(targetUid: String, makeAdmin: Boolean) {
        if (!isMainAdminUser) return
        viewModelScope.launch {
            repository.updateUserRoleByMainAdmin(targetUid, if (makeAdmin) "admin" else "member")
                .onSuccess {
                    _statusBanner.value = if (makeAdmin) "Promoted user to Admin." else "Changed user back to Member."
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Failed to update user role"
                }
        }
    }

    fun assignTicketToAdmins(ticketId: String, sharedAdminUids: List<String>) {
        if (!isMainAdminUser) return
        viewModelScope.launch {
            repository.assignSupportTicketAdmins(ticketId, sharedAdminUids)
                .onSuccess { _statusBanner.value = "Updated ticket admin assignment." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to assign ticket" }
        }
    }

    fun adminReplyTicket(ticketId: String, status: String, reply: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            repository.updateSupportTicketByAdmin(ticketId, status, reply).onSuccess {
                _statusBanner.value = "Ticket updated ($status)."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to update ticket"
            }
        }
    }

    // --- Immersive Glassmorphism Appearance & Wallpaper Controls ---

    fun updateAppearance(transform: (AppearancePreferencesState) -> AppearancePreferencesState) {
        viewModelScope.launch {
            appearanceStore.update(transform)
            _customBgRefreshKey.value += 1
        }
    }

    fun updateGlobalWallpaper(transform: (WallpaperConfig) -> WallpaperConfig) {
        updateAppearance { st -> st.copy(globalWallpaper = transform(st.globalWallpaper)) }
    }

    fun updatePerScreenWallpaper(screenKey: String, transform: (WallpaperConfig) -> WallpaperConfig) {
        updateAppearance { st ->
            val current = st.perScreenWallpapers[screenKey] ?: st.globalWallpaper
            st.copy(perScreenWallpapers = st.perScreenWallpapers + (screenKey to transform(current)))
        }
    }

    fun clearPerScreenWallpaper(screenKey: String) {
        updateAppearance { st ->
            st.copy(perScreenWallpapers = st.perScreenWallpapers - screenKey)
        }
    }

    fun saveThemePreset(name: String) {
        val clean = name.trim().ifBlank { "Custom Glass" }
        val st = appearanceState.value
        val preset = SavedThemePreset(
            id = "preset_${System.currentTimeMillis()}",
            name = clean,
            darkModePreference = st.darkModePreference,
            accentPalette = st.themePalette,
            glassOpacity = st.glassOpacity,
            glassBlurDp = st.glassBlurDp,
            wallpaper = st.globalWallpaper,
            partColors = st.partColors
        )
        updateAppearance { cur ->
            cur.copy(savedPresets = (listOf(preset) + cur.savedPresets).take(10))
        }
        _statusBanner.value = "Saved preset \"$clean\"."
    }

    fun applyThemePreset(preset: SavedThemePreset) {
        updateAppearance { cur ->
            cur.copy(
                darkModePreference = preset.darkModePreference,
                themePalette = preset.accentPalette,
                glassOpacity = preset.glassOpacity,
                glassBlurDp = preset.glassBlurDp,
                globalWallpaper = preset.wallpaper,
                partColors = preset.partColors
            )
        }
        _statusBanner.value = "Applied theme preset \"${preset.name}\"."
    }

    fun deleteThemePreset(presetId: String) {
        updateAppearance { cur ->
            cur.copy(savedPresets = cur.savedPresets.filterNot { it.id == presetId })
        }
    }

    fun resetAppearancePreferences() {
        viewModelScope.launch {
            appearanceStore.resetAppearance()
            _customBgRefreshKey.value += 1
            _statusBanner.value = "Appearance reset to default glass theme."
        }
    }

    fun toggleDashboardWidget(widgetId: String) {
        updateAppearance { cur ->
            cur.copy(
                dashboardWidgets = cur.dashboardWidgets.map { w ->
                    if (w.id == widgetId) w.copy(visible = !w.visible) else w
                }
            )
        }
    }

    fun moveDashboardWidget(widgetId: String, direction: Int) {
        updateAppearance { cur ->
            val list = cur.dashboardWidgets.toMutableList()
            val idx = list.indexOfFirst { it.id == widgetId }
            val targetIdx = idx + direction
            if (idx >= 0 && targetIdx in list.indices) {
                val moved = list.removeAt(idx)
                list.add(targetIdx, moved)
            }
            cur.copy(dashboardWidgets = list)
        }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        updateAppearance { it.copy(monitoringEnabled = enabled) }
        _statusBanner.value = if (enabled) "Behavioural intelligence monitoring resumed." else "Monitoring paused. Existing insights remain available."
    }

    fun setRetentionDays(days: Int) {
        val clamped = days.coerceIn(7, 365)
        updateAppearance { it.copy(retentionDays = clamped) }
        viewModelScope.launch(Dispatchers.IO) {
            intelligenceRepo.enforceRetention(clamped)
        }
        _statusBanner.value = "Data retention set to $clamped days."
    }

    // --- Evidence-Based AI Intelligence Pipeline ---

    fun refreshIntelligencePipeline(runBriefingIfMissing: Boolean = false) {
        intelligenceJob?.cancel()
        intelligenceJob = viewModelScope.launch(Dispatchers.IO) {
            _intelligenceState.value = _intelligenceState.value.copy(isRefreshing = true)
            try {
                val pref = appearanceState.value
                val result = insightManager.refreshIntelligence(
                    habits = habits.value,
                    notes = notes.value,
                    focusSessions = focusSessions.value,
                    diaryEntries = diaryEntries.value,
                    appRules = appRules.value,
                    sleepInsights = _sleepInsights.value,
                    monitoringEnabled = pref.monitoringEnabled,
                    retentionDays = pref.retentionDays
                )
                latestPipelineResult = result
                _intelligenceState.value = _intelligenceState.value.copy(
                    isRefreshing = false,
                    usageAccessGranted = result.usageAccessGranted,
                    analytics = result.analytics,
                    profile = result.profile,
                    patterns = result.patterns,
                    insights = result.insights
                )
                if (runBriefingIfMissing && _intelligenceState.value.briefing == null) {
                    generateDailyAiBriefing()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "refreshIntelligencePipeline error", e)
                _intelligenceState.value = _intelligenceState.value.copy(isRefreshing = false)
            }
        }
    }

    fun generateDailyAiBriefing() {
        viewModelScope.launch {
            _intelligenceState.value = _intelligenceState.value.copy(
                briefingLoading = true,
                briefingError = null
            )
            val pipeline = latestPipelineResult ?: withContext(Dispatchers.IO) {
                val pref = appearanceState.value
                insightManager.refreshIntelligence(
                    habits = habits.value,
                    notes = notes.value,
                    focusSessions = focusSessions.value,
                    diaryEntries = diaryEntries.value,
                    appRules = appRules.value,
                    sleepInsights = _sleepInsights.value,
                    monitoringEnabled = pref.monitoringEnabled,
                    retentionDays = pref.retentionDays
                ).also { latestPipelineResult = it }
            }
            val persona = currentConfigOrDefault().aiCoachPersona
            insightManager.generateAiDailyBriefing(pipeline, persona)
                .onSuccess { res ->
                    _intelligenceState.value = _intelligenceState.value.copy(
                        briefing = res,
                        briefingLoading = false
                    )
                }
                .onFailure { err ->
                    _intelligenceState.value = _intelligenceState.value.copy(
                        briefingLoading = false,
                        briefingError = err.localizedMessage ?: "Could not reach Gemini AI briefing."
                    )
                }
        }
    }

    fun generatePeriodReport(periodTitle: String) {
        viewModelScope.launch {
            _intelligenceState.value = _intelligenceState.value.copy(periodReportLoading = true)
            val pipeline = latestPipelineResult ?: withContext(Dispatchers.IO) {
                val pref = appearanceState.value
                insightManager.refreshIntelligence(
                    habits = habits.value,
                    notes = notes.value,
                    focusSessions = focusSessions.value,
                    diaryEntries = diaryEntries.value,
                    appRules = appRules.value,
                    sleepInsights = _sleepInsights.value,
                    monitoringEnabled = pref.monitoringEnabled,
                    retentionDays = pref.retentionDays
                ).also { latestPipelineResult = it }
            }
            insightManager.generatePeriodReport(periodTitle, pipeline)
                .onSuccess { report ->
                    _intelligenceState.value = _intelligenceState.value.copy(
                        periodReport = report,
                        periodReportLoading = false
                    )
                }
                .onFailure { err ->
                    _intelligenceState.value = _intelligenceState.value.copy(
                        periodReport = "Unable to generate $periodTitle report: ${err.localizedMessage}",
                        periodReportLoading = false
                    )
                }
        }
    }

    fun askIntelligenceQuestion(question: String) {
        val clean = question.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _intelligenceState.value = _intelligenceState.value.copy(qaLoading = true)
            val pipeline = latestPipelineResult ?: withContext(Dispatchers.IO) {
                val pref = appearanceState.value
                insightManager.refreshIntelligence(
                    habits = habits.value,
                    notes = notes.value,
                    focusSessions = focusSessions.value,
                    diaryEntries = diaryEntries.value,
                    appRules = appRules.value,
                    sleepInsights = _sleepInsights.value,
                    monitoringEnabled = pref.monitoringEnabled,
                    retentionDays = pref.retentionDays
                ).also { latestPipelineResult = it }
            }
            insightManager.askIntelligenceQuestion(clean, pipeline)
                .onSuccess { ans ->
                    val entry = IntelligenceQaMessage(
                        id = "qa_${System.currentTimeMillis()}",
                        question = clean,
                        answer = ans.answer,
                        citedEvidence = ans.citedEvidence
                    )
                    _intelligenceState.value = _intelligenceState.value.copy(
                        qaMessages = (_intelligenceState.value.qaMessages + entry).takeLast(12),
                        qaLoading = false
                    )
                }
                .onFailure { err ->
                    val entry = IntelligenceQaMessage(
                        id = "qa_${System.currentTimeMillis()}",
                        question = clean,
                        answer = err.localizedMessage ?: "Failed to query Gemini Intelligence.",
                        isError = true
                    )
                    _intelligenceState.value = _intelligenceState.value.copy(
                        qaMessages = (_intelligenceState.value.qaMessages + entry).takeLast(12),
                        qaLoading = false
                    )
                }
        }
    }

    fun verifyOrEditPattern(patternId: String, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            intelligenceRepo.updatePatternFeedback(patternId, note)
        }
        _statusBanner.value = "Pattern saved to verified profile."
    }

    fun deletePattern(patternId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            intelligenceRepo.deletePattern(patternId)
        }
    }

    fun editAiMemory(memoryId: String, updatedSummary: String) {
        viewModelScope.launch(Dispatchers.IO) {
            intelligenceRepo.memoryManager.updateMemory(memoryId, updatedSummary)
        }
        _statusBanner.value = "AI Memory updated."
    }

    fun deleteAiMemory(memoryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            intelligenceRepo.memoryManager.deleteMemory(memoryId)
        }
    }

    fun clearAllIntelligenceData() {
        viewModelScope.launch(Dispatchers.IO) {
            intelligenceRepo.clearAllIntelligenceData()
            latestPipelineResult = null
            _intelligenceState.value = IntelligenceUiState(
                monitoringEnabled = appearanceState.value.monitoringEnabled,
                retentionDays = appearanceState.value.retentionDays
            )
        }
        _statusBanner.value = "Cleared local activity telemetry, patterns, and AI memory."
    }

    fun dismissInsight(insightId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            intelligenceRepo.dismissInsight(insightId)
        }
    }

    private fun unwrapFirestoreMessage(error: Throwable, fallback: String): String {
        val firestoreErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        return firestoreErr?.message ?: error.localizedMessage ?: fallback
    }

    private companion object {
        const val TAG = "FocusPulseVM"
    }
}

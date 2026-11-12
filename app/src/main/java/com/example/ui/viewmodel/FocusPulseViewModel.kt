package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.FocusAiRepository
import com.example.data.model.AppLimitRule
import com.example.data.model.BlockedSite
import com.example.data.model.DiaryEntry
import com.example.data.model.FocusSession
import com.example.data.model.HabitItem
import com.example.data.model.InventoryItem
import com.example.data.model.NoteItem
import com.example.data.model.ReorderRecord
import com.example.data.model.UserWorkspaceConfig
import com.example.data.repository.FocusPulseRepository
import com.example.system.AppUsageInfo
import com.example.system.BatteryTelemetry
import com.example.system.DeviceTelemetryManager
import com.example.system.FocusGuardPreferences
import com.example.system.SleepAlarmScheduler
import com.example.system.SleepCircadianInsights
import com.example.system.SystemPermissionsState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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

class FocusPulseViewModel(
    application: Application,
    private val repository: FocusPulseRepository,
    private val aiRepository: FocusAiRepository,
    private val currentUserId: String,
    displayName: String
) : AndroidViewModel(application) {

    val currentUid: String get() = currentUserId

    private val telemetryManager = DeviceTelemetryManager(application)
    private val guardPrefs = FocusGuardPreferences(application)
    private val alarmScheduler = SleepAlarmScheduler(application)

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

    val diaryEntries: StateFlow<List<DiaryEntry>> =
        repository.observeDiaryEntries(currentUserId)
            .catch { Log.w(TAG, "observeDiaryEntries error", it); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val focusSessions: StateFlow<List<FocusSession>> =
        repository.observeFocusSessions(currentUserId)
            .catch { Log.w(TAG, "observeFocusSessions error", it); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // --- Real-Time Device Telemetry ---
    private val _batteryTelemetry = MutableStateFlow(BatteryTelemetry())
    val batteryTelemetry: StateFlow<BatteryTelemetry> = _batteryTelemetry.asStateFlow()

    private val _deviceApps = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val deviceApps: StateFlow<List<AppUsageInfo>> = _deviceApps.asStateFlow()

    private val _permissions = MutableStateFlow(SystemPermissionsState())
    val permissions: StateFlow<SystemPermissionsState> = _permissions.asStateFlow()

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

    private var buddyJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureWorkspaceConfig(displayName)
        }
        guardPrefs.touchUserActivity()
        refreshSystemTelemetry()
        startSyncBridgeLoop()
        startTickerLoop()
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

    fun refreshSystemTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            val perms = telemetryManager.checkPermissions()
            val battery = telemetryManager.readBatteryTelemetry()
            val apps = telemetryManager.queryAppsAndUsage()
            withContext(Dispatchers.Main) {
                _permissions.value = perms
                _batteryTelemetry.value = battery
                _deviceApps.value = apps
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

    // --- Shop Inventory & Automated Reordering ---

    fun saveInventoryItem(item: InventoryItem) {
        viewModelScope.launch {
            repository.upsertInventoryItem(item).onSuccess { savedId ->
                val savedItem = item.copy(id = savedId)
                checkAndTriggerAutoReorder(savedItem)
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to save inventory item"
            }
        }
    }

    fun adjustInventoryStock(item: InventoryItem, delta: Long, recordSaleIfNegative: Boolean = false) {
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
                _statusBanner.value = "Restocked +${reorder.quantity} units of ${reorder.itemName}."
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
        val historyPairs = _buddyMessages.value.map { msg ->
            (if (msg.isUser) "user" else "model") to msg.text
        }
        val userBubble = BuddyMessage("u_${System.currentTimeMillis()}", true, trimmed)
        _buddyMessages.value = _buddyMessages.value + userBubble

        val cfg = currentConfigOrDefault()
        val lowStockCount = inventory.value.count { it.isLowStock }
        val totalRev = inventory.value.sumOf { it.totalRevenue }
        val todayEntry = diaryEntries.value.firstOrNull { it.dateKey == todayDateKey() }
        val contextSummary = """
            - Screen time today: ${String.format("%.1f", onlineHoursToday())}h across ${_deviceApps.value.size} tracked apps
            - Habits completed today: ${habits.value.count { it.completedToday >= it.targetPerDay }}/${habits.value.size}
            - Sleep schedule: Bed ${String.format("%02d:%02d", cfg.bedtimeHour, cfg.bedtimeMinute)}, Wake ${String.format("%02d:%02d", cfg.wakeHour, cfg.wakeMinute)}
            - Inventory SKUs: ${inventory.value.size} ($lowStockCount low stock), Revenue $${String.format("%.2f", totalRev)}
            - Today's diary: ${todayEntry?.activitiesText?.ifBlank { "No activities logged yet" } ?: "Not started"}
            - Active Dashboard: ${cfg.activeDashboardId}
        """.trimIndent()

        buddyJob = viewModelScope.launch {
            _buddyState.value = AiBuddyUiState.Loading
            var finalResponse = ""
            try {
                aiRepository.streamBuddyReply(
                    userMessage = trimmed,
                    historyPairs = historyPairs,
                    contextSummary = contextSummary,
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
    fun openHealthConnectSettings() = telemetryManager.openHealthConnectSettings()

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

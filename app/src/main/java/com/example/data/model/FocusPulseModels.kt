package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserWorkspaceConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val activeDashboardId: String = "command_center",
    val themePalette: String = "emerald",
    val darkModePreference: String = "dark",
    val cardStyle: String = "elevated",
    val cornerRadiusDp: Long = 18L,
    val compactDensity: Boolean = false,
    val appLayoutStyle: String = "bento_grid",
    val navBarStyle: String = "bottom_bar",
    val partColors: Map<String, String> = emptyMap(),
    val backgroundPreset: String = "none",
    val backgroundOverlayAlpha: Double = 0.65,
    val cardOpacity: Double = 0.90,
    val enabledWidgets: List<String> = listOf(
        "focus_pulse",
        "doomscroll_shield",
        "sleep_optimizer",
        "habits_streak",
        "deep_work",
        "inventory_alerts",
        "diary_ai",
        "battery_guard"
    ),
    val bedtimeHour: Long = 22L,
    val bedtimeMinute: Long = 30L,
    val wakeHour: Long = 6L,
    val wakeMinute: Long = 30L,
    val sleepLockEnabled: Boolean = true,
    val deepWorkMinutes: Long = 45L,
    val deepWorkDndEnabled: Boolean = true,
    val deepWorkActiveUntilMs: Long = 0L,
    val inactivityAlertHours: Long = 3L,
    val inactivityFullScreenAlarm: Boolean = false,
    val aiCoachPersona: String = "balanced",
    val lastActiveDevice: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    private fun sanitizePartColors(): Map<String, String> {
        val allowed = setOf(
            "background", "surface", "primary", "secondary", "tertiary",
            "text", "mutedText", "navBar", "border", "buttonText"
        )
        return partColors.entries
            .filter { (k, v) -> k in allowed && v.trim().matches(Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")) }
            .associate { (k, v) -> k to v.trim().uppercase() }
    }

    fun toCreateMap(uid: String, deviceName: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "displayName" to displayName.take(80),
        "activeDashboardId" to activeDashboardId,
        "themePalette" to themePalette,
        "darkModePreference" to darkModePreference,
        "cardStyle" to cardStyle,
        "cornerRadiusDp" to cornerRadiusDp.coerceIn(0L, 32L),
        "compactDensity" to compactDensity,
        "appLayoutStyle" to appLayoutStyle,
        "navBarStyle" to navBarStyle,
        "partColors" to sanitizePartColors(),
        "backgroundPreset" to backgroundPreset,
        "backgroundOverlayAlpha" to backgroundOverlayAlpha.coerceIn(0.05, 0.98),
        "cardOpacity" to cardOpacity.coerceIn(0.35, 1.0),
        "enabledWidgets" to enabledWidgets.take(20),
        "bedtimeHour" to bedtimeHour.coerceIn(0L, 23L),
        "bedtimeMinute" to bedtimeMinute.coerceIn(0L, 59L),
        "wakeHour" to wakeHour.coerceIn(0L, 23L),
        "wakeMinute" to wakeMinute.coerceIn(0L, 59L),
        "sleepLockEnabled" to sleepLockEnabled,
        "deepWorkMinutes" to deepWorkMinutes.coerceIn(5L, 480L),
        "deepWorkDndEnabled" to deepWorkDndEnabled,
        "deepWorkActiveUntilMs" to deepWorkActiveUntilMs.coerceAtLeast(0L),
        "inactivityAlertHours" to inactivityAlertHours.coerceIn(1L, 48L),
        "inactivityFullScreenAlarm" to inactivityFullScreenAlarm,
        "aiCoachPersona" to aiCoachPersona,
        "lastActiveDevice" to deviceName.take(100),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(deviceName: String): Map<String, Any> = mapOf(
        "displayName" to displayName.take(80),
        "activeDashboardId" to activeDashboardId,
        "themePalette" to themePalette,
        "darkModePreference" to darkModePreference,
        "cardStyle" to cardStyle,
        "cornerRadiusDp" to cornerRadiusDp.coerceIn(0L, 32L),
        "compactDensity" to compactDensity,
        "appLayoutStyle" to appLayoutStyle,
        "navBarStyle" to navBarStyle,
        "partColors" to sanitizePartColors(),
        "backgroundPreset" to backgroundPreset,
        "backgroundOverlayAlpha" to backgroundOverlayAlpha.coerceIn(0.05, 0.98),
        "cardOpacity" to cardOpacity.coerceIn(0.35, 1.0),
        "enabledWidgets" to enabledWidgets.take(20),
        "bedtimeHour" to bedtimeHour.coerceIn(0L, 23L),
        "bedtimeMinute" to bedtimeMinute.coerceIn(0L, 59L),
        "wakeHour" to wakeHour.coerceIn(0L, 23L),
        "wakeMinute" to wakeMinute.coerceIn(0L, 59L),
        "sleepLockEnabled" to sleepLockEnabled,
        "deepWorkMinutes" to deepWorkMinutes.coerceIn(5L, 480L),
        "deepWorkDndEnabled" to deepWorkDndEnabled,
        "deepWorkActiveUntilMs" to deepWorkActiveUntilMs.coerceAtLeast(0L),
        "inactivityAlertHours" to inactivityAlertHours.coerceIn(1L, 48L),
        "inactivityFullScreenAlarm" to inactivityFullScreenAlarm,
        "aiCoachPersona" to aiCoachPersona,
        "lastActiveDevice" to deviceName.take(100),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class AppLimitRule(
    @DocumentId val id: String = "",
    val userId: String = "",
    val packageName: String = "",
    val appName: String = "",
    val category: String = "social",
    val dailyLimitMinutes: Long = 30L,
    val todayUsedMinutes: Long = 0L,
    val blockDuringSleep: Boolean = true,
    val blockDuringDeepWork: Boolean = true,
    val isLocked: Boolean = false,
    val lockedUntilMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "packageName" to packageName.take(180),
        "appName" to appName.take(100),
        "category" to category,
        "dailyLimitMinutes" to dailyLimitMinutes.coerceIn(1L, 1440L),
        "todayUsedMinutes" to todayUsedMinutes.coerceIn(0L, 1440L),
        "blockDuringSleep" to blockDuringSleep,
        "blockDuringDeepWork" to blockDuringDeepWork,
        "isLocked" to isLocked,
        "lockedUntilMillis" to lockedUntilMillis.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "packageName" to packageName.take(180),
        "appName" to appName.take(100),
        "category" to category,
        "dailyLimitMinutes" to dailyLimitMinutes.coerceIn(1L, 1440L),
        "todayUsedMinutes" to todayUsedMinutes.coerceIn(0L, 1440L),
        "blockDuringSleep" to blockDuringSleep,
        "blockDuringDeepWork" to blockDuringDeepWork,
        "isLocked" to isLocked,
        "lockedUntilMillis" to lockedUntilMillis.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class BlockedSite(
    @DocumentId val id: String = "",
    val userId: String = "",
    val domain: String = "",
    val label: String = "",
    val blockDuringDeepWork: Boolean = true,
    val blockAlways: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "domain" to domain.trim().lowercase().take(150),
        "label" to label.trim().take(80),
        "blockDuringDeepWork" to blockDuringDeepWork,
        "blockAlways" to blockAlways,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "domain" to domain.trim().lowercase().take(150),
        "label" to label.trim().take(80),
        "blockDuringDeepWork" to blockDuringDeepWork,
        "blockAlways" to blockAlways,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class HabitItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = "focus",
    val targetPerDay: Long = 1L,
    val completedToday: Long = 0L,
    val streakDays: Long = 0L,
    val lastCompletedDate: String = "",
    val reminderTime: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.trim().take(120),
        "category" to category,
        "targetPerDay" to targetPerDay.coerceIn(1L, 100L),
        "completedToday" to completedToday.coerceIn(0L, 500L),
        "streakDays" to streakDays.coerceIn(0L, 36500L),
        "lastCompletedDate" to lastCompletedDate.take(20),
        "reminderTime" to reminderTime.take(16),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "title" to title.trim().take(120),
        "category" to category,
        "targetPerDay" to targetPerDay.coerceIn(1L, 100L),
        "completedToday" to completedToday.coerceIn(0L, 500L),
        "streakDays" to streakDays.coerceIn(0L, 36500L),
        "lastCompletedDate" to lastCompletedDate.take(20),
        "reminderTime" to reminderTime.take(16),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class NoteItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "note",
    val isPinned: Boolean = false,
    val isCompleted: Boolean = false,
    val reminderAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.trim().take(160),
        "content" to content.take(10000),
        "category" to category,
        "isPinned" to isPinned,
        "isCompleted" to isCompleted,
        "reminderAtMillis" to reminderAtMillis.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "title" to title.trim().take(160),
        "content" to content.take(10000),
        "category" to category,
        "isPinned" to isPinned,
        "isCompleted" to isCompleted,
        "reminderAtMillis" to reminderAtMillis.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class InventoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sku: String = "",
    val name: String = "",
    val category: String = "General",
    val stockQty: Long = 0L,
    val lowStockThreshold: Long = 5L,
    val reorderQty: Long = 20L,
    val autoReorder: Boolean = true,
    val unitCost: Double = 0.0,
    val unitPrice: Double = 0.0,
    val unitsSold: Long = 0L,
    val supplierName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isLowStock: Boolean
        get() = stockQty <= lowStockThreshold

    val totalValuation: Double
        get() = stockQty * unitCost

    val retailValuation: Double
        get() = stockQty * unitPrice

    val totalRevenue: Double
        get() = unitsSold * unitPrice

    val totalCogs: Double
        get() = unitsSold * unitCost

    val grossProfit: Double
        get() = totalRevenue - totalCogs

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "sku" to sku.trim().take(64),
        "name" to name.trim().take(120),
        "category" to category.trim().take(60),
        "stockQty" to stockQty.coerceIn(0L, 10000000L),
        "lowStockThreshold" to lowStockThreshold.coerceIn(0L, 1000000L),
        "reorderQty" to reorderQty.coerceIn(1L, 1000000L),
        "autoReorder" to autoReorder,
        "unitCost" to unitCost.coerceIn(0.0, 10000000.0),
        "unitPrice" to unitPrice.coerceIn(0.0, 10000000.0),
        "unitsSold" to unitsSold.coerceIn(0L, 100000000L),
        "supplierName" to supplierName.trim().take(100),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "sku" to sku.trim().take(64),
        "name" to name.trim().take(120),
        "category" to category.trim().take(60),
        "stockQty" to stockQty.coerceIn(0L, 10000000L),
        "lowStockThreshold" to lowStockThreshold.coerceIn(0L, 1000000L),
        "reorderQty" to reorderQty.coerceIn(1L, 1000000L),
        "autoReorder" to autoReorder,
        "unitCost" to unitCost.coerceIn(0.0, 10000000.0),
        "unitPrice" to unitPrice.coerceIn(0.0, 10000000.0),
        "unitsSold" to unitsSold.coerceIn(0L, 100000000L),
        "supplierName" to supplierName.trim().take(100),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class ReorderRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val sku: String = "",
    val quantity: Long = 1L,
    val totalCost: Double = 0.0,
    val supplierName: String = "",
    val status: String = "ordered",
    val isAutoTriggered: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "itemId" to itemId.take(128),
        "itemName" to itemName.take(120),
        "sku" to sku.take(64),
        "quantity" to quantity.coerceIn(1L, 1000000L),
        "totalCost" to totalCost.coerceIn(0.0, 100000000.0),
        "supplierName" to supplierName.take(100),
        "status" to status,
        "isAutoTriggered" to isAutoTriggered,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class DiaryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val activitiesText: String = "",
    val moodScore: Long = 3L,
    val energyScore: Long = 3L,
    val onlineHours: Double = 0.0,
    val voiceTranscribed: Boolean = false,
    val aiScore: Long = -1L,
    val aiFeedback: String = "",
    val aiRecommendations: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "dateKey" to dateKey.take(20),
        "activitiesText" to activitiesText.take(10000),
        "moodScore" to moodScore.coerceIn(1L, 5L),
        "energyScore" to energyScore.coerceIn(1L, 5L),
        "onlineHours" to onlineHours.coerceIn(0.0, 24.0),
        "voiceTranscribed" to voiceTranscribed,
        "aiScore" to aiScore.coerceIn(-1L, 100L),
        "aiFeedback" to aiFeedback.take(6000),
        "aiRecommendations" to aiRecommendations.take(10),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "activitiesText" to activitiesText.take(10000),
        "moodScore" to moodScore.coerceIn(1L, 5L),
        "energyScore" to energyScore.coerceIn(1L, 5L),
        "onlineHours" to onlineHours.coerceIn(0.0, 24.0),
        "voiceTranscribed" to voiceTranscribed,
        "aiScore" to aiScore.coerceIn(-1L, 100L),
        "aiFeedback" to aiFeedback.take(6000),
        "aiRecommendations" to aiRecommendations.take(10),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class FocusSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val durationMinutes: Long = 25L,
    val mode: String = "deep_work",
    val completed: Boolean = true,
    val distractionsBlocked: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.trim().take(120),
        "durationMinutes" to durationMinutes.coerceIn(1L, 720L),
        "mode" to mode,
        "completed" to completed,
        "distractionsBlocked" to distractionsBlocked.coerceIn(0L, 10000L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

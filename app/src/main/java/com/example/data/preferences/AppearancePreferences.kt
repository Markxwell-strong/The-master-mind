package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.appearanceDataStore by preferencesDataStore(name = "master_mind_appearance")

data class WallpaperConfig(
    val sourceType: String = "built_in", // built_in, gallery, gradient
    val builtInId: String = "obsidian_aurora",
    val cropScale: Float = 1.0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val brightness: Float = 0.95f,
    val blurDp: Float = 6f,
    val overlayOpacity: Float = 0.36f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sourceType", sourceType)
        put("builtInId", builtInId)
        put("cropScale", cropScale.toDouble())
        put("offsetX", offsetX.toDouble())
        put("offsetY", offsetY.toDouble())
        put("brightness", brightness.toDouble())
        put("blurDp", blurDp.toDouble())
        put("overlayOpacity", overlayOpacity.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject?): WallpaperConfig {
            if (obj == null) return WallpaperConfig()
            return WallpaperConfig(
                sourceType = obj.optString("sourceType", "built_in"),
                builtInId = obj.optString("builtInId", "obsidian_aurora"),
                cropScale = obj.optDouble("cropScale", 1.0).toFloat().coerceIn(1f, 2.5f),
                offsetX = obj.optDouble("offsetX", 0.0).toFloat().coerceIn(-1f, 1f),
                offsetY = obj.optDouble("offsetY", 0.0).toFloat().coerceIn(-1f, 1f),
                brightness = obj.optDouble("brightness", 0.95).toFloat().coerceIn(0.45f, 1.45f),
                blurDp = obj.optDouble("blurDp", 6.0).toFloat().coerceIn(0f, 32f),
                overlayOpacity = obj.optDouble("overlayOpacity", 0.36).toFloat().coerceIn(0.05f, 0.90f)
            )
        }
    }
}

data class DashboardWidgetConfig(
    val id: String,
    val title: String,
    val visible: Boolean = true
)

data class SavedThemePreset(
    val id: String,
    val name: String,
    val darkModePreference: String,
    val accentPalette: String,
    val glassOpacity: Float,
    val glassBlurDp: Float,
    val wallpaper: WallpaperConfig,
    val partColors: Map<String, String> = emptyMap()
)

data class AppearancePreferencesState(
    val darkModePreference: String = "dark",
    val themePalette: String = "emerald",
    val partColors: Map<String, String> = emptyMap(),
    val glassOpacity: Float = 0.38f,
    val glassBlurDp: Float = 12f,
    val cornerRadiusDp: Int = 20,
    val compactDensity: Boolean = false,
    val appLayoutStyle: String = "bento_grid",
    val globalWallpaper: WallpaperConfig = WallpaperConfig(),
    val perScreenWallpapers: Map<String, WallpaperConfig> = emptyMap(),
    val savedPresets: List<SavedThemePreset> = emptyList(),
    val dashboardWidgets: List<DashboardWidgetConfig> = defaultDashboardWidgets(),
    val monitoringEnabled: Boolean = true,
    val retentionDays: Int = 30
)

fun defaultDashboardWidgets(): List<DashboardWidgetConfig> = listOf(
    DashboardWidgetConfig("productivity_score", "Daily Productivity Score", true),
    DashboardWidgetConfig("ai_daily_insight", "AI Daily Insight", true),
    DashboardWidgetConfig("habits_today", "Today's Habit Completion", true),
    DashboardWidgetConfig("focus_screentime", "Focus & Screen-Time Summary", true),
    DashboardWidgetConfig("pending_tasks", "Pending Tasks & Reminders", true),
    DashboardWidgetConfig("weekly_summary", "Weekly Performance Summary", true),
    DashboardWidgetConfig("recent_activity", "Recent Activity Timeline", true),
    DashboardWidgetConfig("quick_modules", "Sleep Shield & Business Ops", true)
)

class AppearancePreferencesStore(private val context: Context) {

    private object Keys {
        val DARK_MODE = stringPreferencesKey("dark_mode_preference")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val PART_COLORS = stringPreferencesKey("part_colors_json")
        val GLASS_OPACITY = floatPreferencesKey("glass_opacity")
        val GLASS_BLUR = floatPreferencesKey("glass_blur_dp")
        val CORNER_RADIUS = intPreferencesKey("corner_radius_dp")
        val COMPACT_DENSITY = booleanPreferencesKey("compact_density")
        val APP_LAYOUT = stringPreferencesKey("app_layout_style")
        val GLOBAL_WALLPAPER = stringPreferencesKey("global_wallpaper_json")
        val PER_SCREEN_WALLPAPERS = stringPreferencesKey("per_screen_wallpapers_json")
        val SAVED_PRESETS = stringPreferencesKey("saved_presets_json")
        val DASHBOARD_WIDGETS = stringPreferencesKey("dashboard_widgets_json")
        val MONITORING_ENABLED = booleanPreferencesKey("intelligence_monitoring_enabled")
        val RETENTION_DAYS = intPreferencesKey("intelligence_retention_days")
    }

    val state: Flow<AppearancePreferencesState> = context.appearanceDataStore.data.map { prefs ->
        fromPrefs(prefs)
    }

    private fun fromPrefs(prefs: Preferences): AppearancePreferencesState {
        return AppearancePreferencesState(
            darkModePreference = prefs[Keys.DARK_MODE] ?: "dark",
            themePalette = prefs[Keys.THEME_PALETTE] ?: "emerald",
            partColors = decodeStringMap(prefs[Keys.PART_COLORS]),
            glassOpacity = (prefs[Keys.GLASS_OPACITY] ?: 0.38f).coerceIn(0.16f, 0.88f),
            glassBlurDp = (prefs[Keys.GLASS_BLUR] ?: 12f).coerceIn(0f, 28f),
            cornerRadiusDp = (prefs[Keys.CORNER_RADIUS] ?: 20).coerceIn(8, 28),
            compactDensity = prefs[Keys.COMPACT_DENSITY] ?: false,
            appLayoutStyle = prefs[Keys.APP_LAYOUT] ?: "bento_grid",
            globalWallpaper = decodeWallpaper(prefs[Keys.GLOBAL_WALLPAPER]),
            perScreenWallpapers = decodePerScreenWallpapers(prefs[Keys.PER_SCREEN_WALLPAPERS]),
            savedPresets = decodePresets(prefs[Keys.SAVED_PRESETS]),
            dashboardWidgets = decodeWidgets(prefs[Keys.DASHBOARD_WIDGETS]),
            monitoringEnabled = prefs[Keys.MONITORING_ENABLED] ?: true,
            retentionDays = (prefs[Keys.RETENTION_DAYS] ?: 30).coerceIn(7, 365)
        )
    }

    suspend fun update(transform: (AppearancePreferencesState) -> AppearancePreferencesState) {
        context.appearanceDataStore.edit { prefs ->
            val current = fromPrefs(prefs)
            val next = transform(current)
            prefs[Keys.DARK_MODE] = next.darkModePreference
            prefs[Keys.THEME_PALETTE] = next.themePalette
            prefs[Keys.PART_COLORS] = encodeStringMap(next.partColors)
            prefs[Keys.GLASS_OPACITY] = next.glassOpacity.coerceIn(0.16f, 0.88f)
            prefs[Keys.GLASS_BLUR] = next.glassBlurDp.coerceIn(0f, 28f)
            prefs[Keys.CORNER_RADIUS] = next.cornerRadiusDp.coerceIn(8, 28)
            prefs[Keys.COMPACT_DENSITY] = next.compactDensity
            prefs[Keys.APP_LAYOUT] = next.appLayoutStyle
            prefs[Keys.GLOBAL_WALLPAPER] = next.globalWallpaper.toJson().toString()
            prefs[Keys.PER_SCREEN_WALLPAPERS] = encodePerScreenWallpapers(next.perScreenWallpapers)
            prefs[Keys.SAVED_PRESETS] = encodePresets(next.savedPresets)
            prefs[Keys.DASHBOARD_WIDGETS] = encodeWidgets(next.dashboardWidgets)
            prefs[Keys.MONITORING_ENABLED] = next.monitoringEnabled
            prefs[Keys.RETENTION_DAYS] = next.retentionDays.coerceIn(7, 365)
        }
    }

    suspend fun resetAppearance() {
        context.appearanceDataStore.edit { prefs ->
            val current = fromPrefs(prefs)
            val defaults = AppearancePreferencesState(
                savedPresets = current.savedPresets,
                dashboardWidgets = current.dashboardWidgets,
                monitoringEnabled = current.monitoringEnabled,
                retentionDays = current.retentionDays
            )
            prefs[Keys.DARK_MODE] = defaults.darkModePreference
            prefs[Keys.THEME_PALETTE] = defaults.themePalette
            prefs[Keys.PART_COLORS] = "{}"
            prefs[Keys.GLASS_OPACITY] = defaults.glassOpacity
            prefs[Keys.GLASS_BLUR] = defaults.glassBlurDp
            prefs[Keys.CORNER_RADIUS] = defaults.cornerRadiusDp
            prefs[Keys.COMPACT_DENSITY] = false
            prefs[Keys.APP_LAYOUT] = "bento_grid"
            prefs[Keys.GLOBAL_WALLPAPER] = WallpaperConfig().toJson().toString()
            prefs[Keys.PER_SCREEN_WALLPAPERS] = "{}"
        }
    }

    private fun decodeWallpaper(raw: String?): WallpaperConfig {
        if (raw.isNullOrBlank()) return WallpaperConfig()
        return try {
            WallpaperConfig.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            WallpaperConfig()
        }
    }

    private fun decodePerScreenWallpapers(raw: String?): Map<String, WallpaperConfig> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = mutableMapOf<String, WallpaperConfig>()
            obj.keys().forEach { k ->
                out[k] = WallpaperConfig.fromJson(obj.optJSONObject(k))
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun encodePerScreenWallpapers(map: Map<String, WallpaperConfig>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v.toJson()) }
        return obj.toString()
    }

    private fun decodeStringMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = mutableMapOf<String, String>()
            obj.keys().forEach { k -> out[k] = obj.optString(k) }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun encodeStringMap(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun decodePresets(raw: String?): List<SavedThemePreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                SavedThemePreset(
                    id = obj.optString("id"),
                    name = obj.optString("name", "Preset"),
                    darkModePreference = obj.optString("darkModePreference", "dark"),
                    accentPalette = obj.optString("accentPalette", "emerald"),
                    glassOpacity = obj.optDouble("glassOpacity", 0.38).toFloat(),
                    glassBlurDp = obj.optDouble("glassBlurDp", 12.0).toFloat(),
                    wallpaper = WallpaperConfig.fromJson(obj.optJSONObject("wallpaper")),
                    partColors = decodeStringMap(obj.optString("partColors"))
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodePresets(list: List<SavedThemePreset>): String {
        val arr = JSONArray()
        list.take(12).forEach { preset ->
            arr.put(
                JSONObject().apply {
                    put("id", preset.id)
                    put("name", preset.name)
                    put("darkModePreference", preset.darkModePreference)
                    put("accentPalette", preset.accentPalette)
                    put("glassOpacity", preset.glassOpacity.toDouble())
                    put("glassBlurDp", preset.glassBlurDp.toDouble())
                    put("wallpaper", preset.wallpaper.toJson())
                    put("partColors", encodeStringMap(preset.partColors))
                }
            )
        }
        return arr.toString()
    }

    private fun decodeWidgets(raw: String?): List<DashboardWidgetConfig> {
        val defaults = defaultDashboardWidgets()
        if (raw.isNullOrBlank()) return defaults
        return try {
            val arr = JSONArray(raw)
            val parsed = (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id")
                val defaultTitle = defaults.firstOrNull { it.id == id }?.title ?: id
                DashboardWidgetConfig(
                    id = id,
                    title = obj.optString("title", defaultTitle),
                    visible = obj.optBoolean("visible", true)
                )
            }
            val missing = defaults.filter { def -> parsed.none { it.id == def.id } }
            (parsed + missing).ifEmpty { defaults }
        } catch (_: Exception) {
            defaults
        }
    }

    private fun encodeWidgets(widgets: List<DashboardWidgetConfig>): String {
        val arr = JSONArray()
        widgets.forEach { w ->
            arr.put(
                JSONObject().apply {
                    put("id", w.id)
                    put("title", w.title)
                    put("visible", w.visible)
                }
            )
        }
        return arr.toString()
    }
}

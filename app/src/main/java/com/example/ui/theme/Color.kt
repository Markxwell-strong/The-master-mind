package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class FocusPaletteSpec(
    val id: String,
    val label: String,
    val primaryDark: Color,
    val secondaryDark: Color,
    val tertiaryDark: Color,
    val primaryLight: Color,
    val secondaryLight: Color,
    val tertiaryLight: Color
)

val FocusPalettes = listOf(
    FocusPaletteSpec(
        id = "emerald",
        label = "Obsidian Emerald",
        primaryDark = Color(0xFF10B981),
        secondaryDark = Color(0xFF38BDF8),
        tertiaryDark = Color(0xFFF59E0B),
        primaryLight = Color(0xFF047857),
        secondaryLight = Color(0xFF0284C7),
        tertiaryLight = Color(0xFFD97706)
    ),
    FocusPaletteSpec(
        id = "cobalt",
        label = "Electric Cobalt",
        primaryDark = Color(0xFF60A5FA),
        secondaryDark = Color(0xFF34D399),
        tertiaryDark = Color(0xFFA78BFA),
        primaryLight = Color(0xFF1D4ED8),
        secondaryLight = Color(0xFF059669),
        tertiaryLight = Color(0xFF7C3AED)
    ),
    FocusPaletteSpec(
        id = "amber",
        label = "Solar Amber",
        primaryDark = Color(0xFFFBBF24),
        secondaryDark = Color(0xFFFB7185),
        tertiaryDark = Color(0xFF2DD4BF),
        primaryLight = Color(0xFFB45309),
        secondaryLight = Color(0xFFBE123C),
        tertiaryLight = Color(0xFF0F766E)
    ),
    FocusPaletteSpec(
        id = "crimson",
        label = "Crimson Focus",
        primaryDark = Color(0xFFFB7185),
        secondaryDark = Color(0xFFF59E0B),
        tertiaryDark = Color(0xFF38BDF8),
        primaryLight = Color(0xFFBE123C),
        secondaryLight = Color(0xFFB45309),
        tertiaryLight = Color(0xFF0369A1)
    ),
    FocusPaletteSpec(
        id = "amethyst",
        label = "Amethyst Night",
        primaryDark = Color(0xFFC084FC),
        secondaryDark = Color(0xFF2DD4BF),
        tertiaryDark = Color(0xFFF472B6),
        primaryLight = Color(0xFF7E22CE),
        secondaryLight = Color(0xFF0D9488),
        tertiaryLight = Color(0xFFBE185D)
    ),
    FocusPaletteSpec(
        id = "slate",
        label = "Nordic Slate",
        primaryDark = Color(0xFF94A3B8),
        secondaryDark = Color(0xFF22D3EE),
        tertiaryDark = Color(0xFF4ADE80),
        primaryLight = Color(0xFF334155),
        secondaryLight = Color(0xFF0891B2),
        tertiaryLight = Color(0xFF15803D)
    )
)

fun paletteById(id: String): FocusPaletteSpec =
    FocusPalettes.firstOrNull { it.id == id } ?: FocusPalettes.first()

enum class MasterMindColorPart(val key: String, val label: String, val hint: String) {
    BACKGROUND("background", "Whole Background", "App backdrop behind every screen"),
    SURFACE("surface", "Cards & Panels", "All dashboard & feature cards"),
    PRIMARY("primary", "Primary Buttons & Accents", "Main actions, rings & active tabs"),
    SECONDARY("secondary", "Charts & Secondary Accents", "Analytics bars, sparklines & pills"),
    TERTIARY("tertiary", "Highlights & Alarms", "Alert highlights & status tags"),
    TEXT("text", "Main Headings & Text", "Titles, labels & body copy"),
    MUTED_TEXT("mutedText", "Subtitles & Captions", "Secondary descriptions & hints"),
    NAV_BAR("navBar", "Navigation Bar / Dock", "Bottom bar, floating dock or side rail"),
    CARD_BORDER("border", "Borders & Dividers", "Card outlines & subtle separators"),
    BUTTON_TEXT("buttonText", "Button Label Text", "Text & icons inside primary buttons")
}

val MasterMindSwatchPalette: List<Color> = listOf(
    // Dark & Neutral Backdrops
    Color(0xFF000000),
    Color(0xFF070D1A),
    Color(0xFF0F172A),
    Color(0xFF141124),
    Color(0xFF0B1F1C),
    Color(0xFF1F1215),
    Color(0xFF1E293B),
    // Light & Warm Surfaces
    Color(0xFFFFFFFF),
    Color(0xFFF8FAFC),
    Color(0xFFEDE9FE),
    Color(0xFFECFDF5),
    Color(0xFFFFF7ED),
    Color(0xFFE2E8F0),
    Color(0xFF94A3B8),
    // Rich Accents
    Color(0xFF10B981),
    Color(0xFF06B6D4),
    Color(0xFF3B82F6),
    Color(0xFF6366F1),
    Color(0xFFA855F7),
    Color(0xFFEC4899),
    Color(0xFFF43F5E),
    Color(0xFFF97316),
    Color(0xFFF59E0B),
    Color(0xFFEAB308),
    Color(0xFF84CC16),
    Color(0xFF14B8A6),
    Color(0xFF0EA5E9),
    Color(0xFF475569)
)

fun parseHexColorOrNull(hex: String?): Color? {
    val clean = hex?.trim().orEmpty()
    if (!clean.matches(Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$"))) return null
    return try {
        val parsed = android.graphics.Color.parseColor(clean)
        Color(parsed)
    } catch (_: Exception) {
        null
    }
}

fun Color.toHexRgb(): String {
    val rgb = toArgb() and 0x00FFFFFF
    return String.format("#%06X", rgb)
}

data class TabAppearancePreset(
    val route: String,
    val primaryDark: Color,
    val secondaryDark: Color,
    val tertiaryDark: Color,
    val bgDark: Color,
    val surfaceDark: Color,
    val primaryLight: Color,
    val secondaryLight: Color,
    val tertiaryLight: Color,
    val bgLight: Color,
    val surfaceLight: Color
)

private val TabAppearancePresets = listOf(
    TabAppearancePreset(
        route = "dashboard",
        primaryDark = Color(0xFF10B981),
        secondaryDark = Color(0xFF38BDF8),
        tertiaryDark = Color(0xFFF59E0B),
        bgDark = Color(0xFF070D1A),
        surfaceDark = Color(0xFF0F192E),
        primaryLight = Color(0xFF047857),
        secondaryLight = Color(0xFF0284C7),
        tertiaryLight = Color(0xFFD97706),
        bgLight = Color(0xFFF3F8F6),
        surfaceLight = Color(0xFFFFFFFF)
    ),
    TabAppearancePreset(
        route = "shield",
        primaryDark = Color(0xFFFB7185),
        secondaryDark = Color(0xFFF59E0B),
        tertiaryDark = Color(0xFF38BDF8),
        bgDark = Color(0xFF160911),
        surfaceDark = Color(0xFF23101B),
        primaryLight = Color(0xFFBE123C),
        secondaryLight = Color(0xFFB45309),
        tertiaryLight = Color(0xFF0369A1),
        bgLight = Color(0xFFFFF5F7),
        surfaceLight = Color(0xFFFFFFFF)
    ),
    TabAppearancePreset(
        route = "habits",
        primaryDark = Color(0xFFC084FC),
        secondaryDark = Color(0xFF2DD4BF),
        tertiaryDark = Color(0xFFF472B6),
        bgDark = Color(0xFF100A1F),
        surfaceDark = Color(0xFF1B1233),
        primaryLight = Color(0xFF7E22CE),
        secondaryLight = Color(0xFF0D9488),
        tertiaryLight = Color(0xFFBE185D),
        bgLight = Color(0xFFF7F4FF),
        surfaceLight = Color(0xFFFFFFFF)
    ),
    TabAppearancePreset(
        route = "ops",
        primaryDark = Color(0xFFFBBF24),
        secondaryDark = Color(0xFF34D399),
        tertiaryDark = Color(0xFFFB7185),
        bgDark = Color(0xFF151007),
        surfaceDark = Color(0xFF21190B),
        primaryLight = Color(0xFFB45309),
        secondaryLight = Color(0xFF059669),
        tertiaryLight = Color(0xFFBE123C),
        bgLight = Color(0xFFFFFBEB),
        surfaceLight = Color(0xFFFFFFFF)
    ),
    TabAppearancePreset(
        route = "diary",
        primaryDark = Color(0xFF38BDF8),
        secondaryDark = Color(0xFF818CF8),
        tertiaryDark = Color(0xFF34D399),
        bgDark = Color(0xFF06131F),
        surfaceDark = Color(0xFF0D2033),
        primaryLight = Color(0xFF0284C7),
        secondaryLight = Color(0xFF4F46E5),
        tertiaryLight = Color(0xFF059669),
        bgLight = Color(0xFFF0F9FF),
        surfaceLight = Color(0xFFFFFFFF)
    ),
    TabAppearancePreset(
        route = "settings",
        primaryDark = Color(0xFF818CF8),
        secondaryDark = Color(0xFF2DD4BF),
        tertiaryDark = Color(0xFFFBBF24),
        bgDark = Color(0xFF0B1120),
        surfaceDark = Color(0xFF141D33),
        primaryLight = Color(0xFF4338CA),
        secondaryLight = Color(0xFF0D9488),
        tertiaryLight = Color(0xFFB45309),
        bgLight = Color(0xFFF4F6FB),
        surfaceLight = Color(0xFFFFFFFF)
    ),
    TabAppearancePreset(
        route = "profile",
        primaryDark = Color(0xFF2DD4BF),
        secondaryDark = Color(0xFF60A5FA),
        tertiaryDark = Color(0xFFF472B6),
        bgDark = Color(0xFF061617),
        surfaceDark = Color(0xFF0E2426),
        primaryLight = Color(0xFF0F766E),
        secondaryLight = Color(0xFF1D4ED8),
        tertiaryLight = Color(0xFFBE185D),
        bgLight = Color(0xFFF0FDFA),
        surfaceLight = Color(0xFFFFFFFF)
    )
)

fun tabAppearanceForRoute(route: String?): TabAppearancePreset =
    TabAppearancePresets.firstOrNull { it.route == route } ?: TabAppearancePresets.first()

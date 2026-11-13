package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.UserWorkspaceConfig
import com.example.data.preferences.AppearancePreferencesState
import com.example.data.preferences.WallpaperConfig

data class FocusUiTokens(
    val isLightTheme: Boolean = false,
    val cornerRadius: Dp = 20.dp,
    val compactDensity: Boolean = false,
    val appLayoutStyle: String = "bento_grid",
    val glassOpacity: Float = 0.38f,
    val glassBlurDp: Float = 12f,
    val glassSurfaceColor: Color = Color(0x61111C33),
    val glassElevatedColor: Color = Color(0x781A2742),
    val glassSpecularBorderTop: Color = Color(0x47FFFFFF),
    val glassSpecularBorderBottom: Color = Color(0x18FFFFFF),
    val resolvedBackgroundColor: Color = Color(0xFF070D1A),
    val resolvedNavBarColor: Color = Color(0x880B1324),
    val resolvedBorderColor: Color = Color(0x3DFFFFFF)
)

val LocalFocusUiTokens = staticCompositionLocalOf { FocusUiTokens() }

object ThemeManager {
    fun resolveUseDark(
        darkModePreference: String,
        wallpaper: WallpaperConfig,
        systemDark: Boolean
    ): Boolean {
        return when (darkModePreference) {
            "light" -> false
            "dark", "amoled" -> true
            else -> {
                if (wallpaper.sourceType == "built_in") {
                    !BackgroundManager.wallpaperById(wallpaper.builtInId).isLightBackdrop
                } else {
                    systemDark
                }
            }
        }
    }
}

@Composable
fun MasterMindGlassTheme(
    appearance: AppearancePreferencesState,
    activeRoute: String? = "home",
    content: @Composable () -> Unit
) {
    val screenKey = BackgroundManager.normalizeScreenKey(activeRoute)
    val wallpaper = BackgroundManager.resolveForScreen(appearance, screenKey)
    val systemDark = isSystemInDarkTheme()
    val useDark = ThemeManager.resolveUseDark(appearance.darkModePreference, wallpaper, systemDark)
    val isAmoled = appearance.darkModePreference == "amoled"

    val palette = paletteById(appearance.themePalette)
    val tabPreset = tabAppearanceForRoute(screenKey)
    val useDynamicTabAccent = appearance.themePalette == "emerald"

    val targetPrimary = parseHexColorOrNull(appearance.partColors[MasterMindColorPart.PRIMARY.key])
        ?: if (useDynamicTabAccent) {
            if (useDark) tabPreset.primaryDark else tabPreset.primaryLight
        } else {
            if (useDark) palette.primaryDark else palette.primaryLight
        }

    val targetSecondary = parseHexColorOrNull(appearance.partColors[MasterMindColorPart.SECONDARY.key])
        ?: if (useDynamicTabAccent) {
            if (useDark) tabPreset.secondaryDark else tabPreset.secondaryLight
        } else {
            if (useDark) palette.secondaryDark else palette.secondaryLight
        }

    val targetTertiary = parseHexColorOrNull(appearance.partColors[MasterMindColorPart.TERTIARY.key])
        ?: if (useDynamicTabAccent) {
            if (useDark) tabPreset.tertiaryDark else tabPreset.tertiaryLight
        } else {
            if (useDark) palette.tertiaryDark else palette.tertiaryLight
        }

    val defaultBg = when {
        !useDark -> tabPreset.bgLight
        isAmoled -> Color(0xFF000000)
        else -> tabPreset.bgDark
    }
    val customBgTarget = parseHexColorOrNull(appearance.partColors[MasterMindColorPart.BACKGROUND.key]) ?: defaultBg
    val customSurf = parseHexColorOrNull(appearance.partColors[MasterMindColorPart.SURFACE.key])

    // Enforce high-contrast legible typography regardless of wallpaper brightness
    val targetText = parseHexColorOrNull(appearance.partColors[MasterMindColorPart.TEXT.key])
        ?: if (useDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val targetMuted = parseHexColorOrNull(appearance.partColors[MasterMindColorPart.MUTED_TEXT.key])
        ?: if (useDark) Color(0xFFCBD5E1) else Color(0xFF334155)
    val targetButtonText = parseHexColorOrNull(appearance.partColors[MasterMindColorPart.BUTTON_TEXT.key])
        ?: if (useDark) Color(0xFF041410) else Color.White

    // Ensure card opacity never drops below readable floor when wallpaper brightness is extreme
    val minSafeGlassAlpha = if (wallpaper.brightness > 1.12f && useDark) {
        0.54f
    } else if (!useDark && wallpaper.brightness < 0.85f) {
        0.64f
    } else {
        0.24f
    }
    val effectiveGlassAlpha = appearance.glassOpacity.coerceIn(minSafeGlassAlpha, 0.90f)

    val baseSurfaceTint = customSurf ?: if (useDark) {
        if (isAmoled) Color(0xFF0A0F1A) else blendThemeColors(Color(0xFF0F172A), targetPrimary, 0.08f)
    } else {
        blendThemeColors(Color(0xFFFFFFFF), targetPrimary, 0.04f)
    }

    val targetNavBar = parseHexColorOrNull(appearance.partColors[MasterMindColorPart.NAV_BAR.key])
        ?: baseSurfaceTint.copy(alpha = (effectiveGlassAlpha + 0.24f).coerceIn(0.45f, 0.92f))

    val targetBorder = parseHexColorOrNull(appearance.partColors[MasterMindColorPart.CARD_BORDER.key])
        ?: if (useDark) Color.White.copy(alpha = 0.18f) else Color(0xFF0F172A).copy(alpha = 0.14f)

    val animSpec = tween<Color>(durationMillis = 360)
    val animBg by animateColorAsState(customBgTarget, animSpec, label = "bgAnim")
    val animPrimary by animateColorAsState(targetPrimary, animSpec, label = "primaryAnim")
    val animSecondary by animateColorAsState(targetSecondary, animSpec, label = "secondaryAnim")
    val animTertiary by animateColorAsState(targetTertiary, animSpec, label = "tertiaryAnim")
    val animNavBar by animateColorAsState(targetNavBar, animSpec, label = "navAnim")
    val animBorder by animateColorAsState(targetBorder, animSpec, label = "borderAnim")

    val glassSurface = baseSurfaceTint.copy(alpha = effectiveGlassAlpha)
    val glassElevated = baseSurfaceTint.copy(alpha = (effectiveGlassAlpha + 0.14f).coerceIn(0.32f, 0.94f))
    val glassHighest = baseSurfaceTint.copy(alpha = (effectiveGlassAlpha + 0.24f).coerceIn(0.40f, 0.96f))

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = animPrimary,
            onPrimary = targetButtonText,
            primaryContainer = animPrimary.copy(alpha = 0.24f),
            onPrimaryContainer = Color(0xFFE2F8F0),
            secondary = animSecondary,
            onSecondary = Color(0xFF04131E),
            secondaryContainer = animSecondary.copy(alpha = 0.22f),
            onSecondaryContainer = Color(0xFFE0F2FE),
            tertiary = animTertiary,
            onTertiary = Color(0xFF1D1202),
            tertiaryContainer = animTertiary.copy(alpha = 0.22f),
            onTertiaryContainer = Color(0xFFFEF3C7),
            background = animBg,
            onBackground = targetText,
            surface = glassSurface,
            onSurface = targetText,
            surfaceVariant = glassElevated,
            onSurfaceVariant = targetMuted,
            surfaceContainerLowest = animBg,
            surfaceContainerLow = glassSurface,
            surfaceContainer = glassElevated,
            surfaceContainerHigh = glassHighest,
            outline = animBorder,
            outlineVariant = animBorder.copy(alpha = 0.65f),
            error = Color(0xFFFCA5A5),
            onError = Color(0xFF450A0A),
            errorContainer = Color(0x997F1D1D),
            onErrorContainer = Color(0xFFFEE2E2)
        )
    } else {
        lightColorScheme(
            primary = animPrimary,
            onPrimary = targetButtonText,
            primaryContainer = animPrimary.copy(alpha = 0.16f),
            onPrimaryContainer = animPrimary,
            secondary = animSecondary,
            onSecondary = Color.White,
            secondaryContainer = animSecondary.copy(alpha = 0.16f),
            onSecondaryContainer = animSecondary,
            tertiary = animTertiary,
            onTertiary = Color.White,
            tertiaryContainer = animTertiary.copy(alpha = 0.16f),
            onTertiaryContainer = animTertiary,
            background = animBg,
            onBackground = targetText,
            surface = glassSurface,
            onSurface = targetText,
            surfaceVariant = glassElevated,
            onSurfaceVariant = targetMuted,
            surfaceContainerLowest = animBg,
            surfaceContainerLow = glassSurface,
            surfaceContainer = glassElevated,
            surfaceContainerHigh = glassHighest,
            outline = animBorder,
            outlineVariant = animBorder.copy(alpha = 0.7f),
            error = Color(0xFFDC2626),
            onError = Color.White,
            errorContainer = Color(0xD9FEE2E2),
            onErrorContainer = Color(0xFF7F1D1D)
        )
    }

    val radius = appearance.cornerRadiusDp.coerceIn(8, 28).dp
    val shapes = Shapes(
        small = RoundedCornerShape((radius * 0.6f).coerceAtLeast(6.dp)),
        medium = RoundedCornerShape(radius),
        large = RoundedCornerShape((radius * 1.25f).coerceAtMost(32.dp))
    )

    val tokens = FocusUiTokens(
        isLightTheme = !useDark,
        cornerRadius = radius,
        compactDensity = appearance.compactDensity,
        appLayoutStyle = appearance.appLayoutStyle,
        glassOpacity = effectiveGlassAlpha,
        glassBlurDp = appearance.glassBlurDp,
        glassSurfaceColor = glassSurface,
        glassElevatedColor = glassElevated,
        glassSpecularBorderTop = if (useDark) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.78f),
        glassSpecularBorderBottom = if (useDark) Color.White.copy(alpha = 0.08f) else Color(0xFF0F172A).copy(alpha = 0.10f),
        resolvedBackgroundColor = animBg,
        resolvedNavBarColor = animNavBar,
        resolvedBorderColor = animBorder
    )

    CompositionLocalProvider(LocalFocusUiTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FocusTypography,
            shapes = shapes,
            content = content
        )
    }
}

@Composable
fun MasterMindTheme(
    config: UserWorkspaceConfig,
    activeRoute: String? = "home",
    content: @Composable () -> Unit
) {
    MasterMindGlassTheme(
        appearance = AppearancePreferencesState(
            darkModePreference = config.darkModePreference,
            themePalette = config.themePalette,
            partColors = config.partColors,
            glassOpacity = config.cardOpacity.toFloat().coerceIn(0.24f, 0.88f),
            cornerRadiusDp = config.cornerRadiusDp.toInt().coerceIn(8, 28),
            compactDensity = config.compactDensity,
            appLayoutStyle = config.appLayoutStyle
        ),
        activeRoute = activeRoute,
        content = content
    )
}

@Composable
fun MasterMindBackgroundHost(
    backgroundRefreshKey: Int,
    appearanceState: AppearancePreferencesState = AppearancePreferencesState(),
    activeRoute: String? = "home",
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    GlassBackgroundHost(
        appearanceState = appearanceState,
        activeRoute = activeRoute,
        backgroundRefreshKey = backgroundRefreshKey,
        modifier = modifier,
        content = content
    )
}

internal fun blendThemeColors(base: Color, tint: Color, ratio: Float): Color {
    val inv = 1f - ratio
    return Color(
        red = base.red * inv + tint.red * ratio,
        green = base.green * inv + tint.green * ratio,
        blue = base.blue * inv + tint.blue * ratio,
        alpha = base.alpha
    )
}

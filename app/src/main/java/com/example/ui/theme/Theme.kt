package com.example.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.UserWorkspaceConfig
import com.example.system.BackgroundMediaStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FocusUiTokens(
    val cardStyle: String = "elevated",
    val cornerRadius: Dp = 18.dp,
    val compactDensity: Boolean = false,
    val appLayoutStyle: String = "bento_grid",
    val navBarStyle: String = "bottom_bar",
    val backgroundPreset: String = "none",
    val backgroundOverlayAlpha: Float = 0.65f,
    val cardOpacity: Float = 0.90f,
    val resolvedBackgroundColor: Color = Color(0xFF070D1A),
    val resolvedSurfaceColor: Color = Color(0xFF111C33),
    val resolvedNavBarColor: Color = Color(0xFF0D1526),
    val resolvedBorderColor: Color = Color(0xFF1B2944)
)

val LocalFocusUiTokens = staticCompositionLocalOf { FocusUiTokens() }

@Composable
fun FocusPulseTheme(
    paletteId: String = "emerald",
    darkModePreference: String = "dark",
    cardStyle: String = "elevated",
    cornerRadiusDp: Int = 18,
    compactDensity: Boolean = false,
    content: @Composable () -> Unit
) {
    MasterMindTheme(
        config = UserWorkspaceConfig(
            themePalette = paletteId,
            darkModePreference = darkModePreference,
            cardStyle = cardStyle,
            cornerRadiusDp = cornerRadiusDp.toLong(),
            compactDensity = compactDensity
        ),
        content = content
    )
}

@Composable
fun MasterMindTheme(
    config: UserWorkspaceConfig,
    activeRoute: String? = "dashboard",
    content: @Composable () -> Unit
) {
    val palette = paletteById(config.themePalette)
    val tabPreset = tabAppearanceForRoute(activeRoute)
    val useDynamicTabAccent = config.themePalette == "emerald"
    val useDark = when (config.darkModePreference) {
        "light" -> false
        "dark", "amoled" -> true
        else -> isSystemInDarkTheme()
    }
    val isAmoled = config.darkModePreference == "amoled"

    val defaultBg = when {
        !useDark -> tabPreset.bgLight
        isAmoled -> Color(0xFF000000)
        else -> tabPreset.bgDark
    }
    val defaultSurf = when {
        !useDark -> tabPreset.surfaceLight
        isAmoled -> Color(0xFF080C14)
        else -> tabPreset.surfaceDark
    }
    val defaultSurfLow = when {
        !useDark -> blendColors(tabPreset.surfaceLight, tabPreset.primaryLight, 0.03f)
        isAmoled -> Color(0xFF0A101B)
        else -> blendColors(tabPreset.surfaceDark, tabPreset.primaryDark, 0.06f)
    }
    val defaultSurfHigh = when {
        !useDark -> blendColors(tabPreset.surfaceLight, tabPreset.primaryLight, 0.07f)
        isAmoled -> Color(0xFF101829)
        else -> blendColors(tabPreset.surfaceDark, tabPreset.primaryDark, 0.12f)
    }
    val defaultSurfHighest = when {
        !useDark -> blendColors(tabPreset.surfaceLight, tabPreset.primaryLight, 0.12f)
        isAmoled -> Color(0xFF162136)
        else -> blendColors(tabPreset.surfaceDark, tabPreset.primaryDark, 0.18f)
    }

    val targetPrimary = parseHexColorOrNull(config.partColors[MasterMindColorPart.PRIMARY.key])
        ?: if (useDynamicTabAccent) {
            if (useDark) tabPreset.primaryDark else tabPreset.primaryLight
        } else {
            if (useDark) palette.primaryDark else palette.primaryLight
        }

    val targetSecondary = parseHexColorOrNull(config.partColors[MasterMindColorPart.SECONDARY.key])
        ?: if (useDynamicTabAccent) {
            if (useDark) tabPreset.secondaryDark else tabPreset.secondaryLight
        } else {
            if (useDark) palette.secondaryDark else palette.secondaryLight
        }

    val targetTertiary = parseHexColorOrNull(config.partColors[MasterMindColorPart.TERTIARY.key])
        ?: if (useDynamicTabAccent) {
            if (useDark) tabPreset.tertiaryDark else tabPreset.tertiaryLight
        } else {
            if (useDark) palette.tertiaryDark else palette.tertiaryLight
        }

    val targetBg = parseHexColorOrNull(config.partColors[MasterMindColorPart.BACKGROUND.key]) ?: defaultBg
    val customSurf = parseHexColorOrNull(config.partColors[MasterMindColorPart.SURFACE.key])
    val targetText = parseHexColorOrNull(config.partColors[MasterMindColorPart.TEXT.key])
        ?: if (useDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val targetMuted = parseHexColorOrNull(config.partColors[MasterMindColorPart.MUTED_TEXT.key])
        ?: if (useDark) Color(0xFF94A3B8) else Color(0xFF475569)
    val targetNavBar = parseHexColorOrNull(config.partColors[MasterMindColorPart.NAV_BAR.key])
        ?: (customSurf ?: defaultSurf)
    val targetBorder = parseHexColorOrNull(config.partColors[MasterMindColorPart.CARD_BORDER.key])
        ?: if (useDark) blendColors(Color(0xFF2B3D5E), targetPrimary, 0.22f)
        else blendColors(Color(0xFFCBD5E1), targetPrimary, 0.18f)
    val targetButtonText = parseHexColorOrNull(config.partColors[MasterMindColorPart.BUTTON_TEXT.key])
        ?: if (useDark) Color(0xFF041410) else Color.White

    // Smooth per-tab animated theme transitions
    val animSpec = tween<Color>(durationMillis = 360)
    val customBg by animateColorAsState(targetBg, animSpec, label = "bgAnim")
    val customPrimary by animateColorAsState(targetPrimary, animSpec, label = "primaryAnim")
    val customSecondary by animateColorAsState(targetSecondary, animSpec, label = "secondaryAnim")
    val customTertiary by animateColorAsState(targetTertiary, animSpec, label = "tertiaryAnim")
    val customNavBar by animateColorAsState(targetNavBar, animSpec, label = "navAnim")
    val customBorder by animateColorAsState(targetBorder, animSpec, label = "borderAnim")

    val cardAlpha = config.cardOpacity.toFloat().coerceIn(0.35f, 1.0f)
    val hasVisualBg = config.backgroundPreset != "none"
    val effectiveCardAlpha = if (hasVisualBg) cardAlpha else cardAlpha.coerceAtLeast(0.92f)

    val surfBaseTarget = customSurf ?: defaultSurf
    val surfBase by animateColorAsState(surfBaseTarget, animSpec, label = "surfBaseAnim")
    val surfLow by animateColorAsState(
        (customSurf ?: defaultSurfLow).copy(alpha = effectiveCardAlpha),
        animSpec,
        label = "surfLowAnim"
    )
    val surfHigh by animateColorAsState(
        (customSurf?.let { blendColors(it, Color.White, if (useDark) 0.08f else 0.04f) }
            ?: defaultSurfHigh).copy(alpha = effectiveCardAlpha),
        animSpec,
        label = "surfHighAnim"
    )
    val surfHighest by animateColorAsState(
        customSurf?.let { blendColors(it, Color.White, if (useDark) 0.14f else 0.08f) }
            ?: defaultSurfHighest,
        animSpec,
        label = "surfHighestAnim"
    )

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = customPrimary,
            onPrimary = targetButtonText,
            primaryContainer = customPrimary.copy(alpha = 0.20f),
            onPrimaryContainer = customPrimary,
            secondary = customSecondary,
            onSecondary = Color(0xFF04131E),
            secondaryContainer = customSecondary.copy(alpha = 0.18f),
            onSecondaryContainer = customSecondary,
            tertiary = customTertiary,
            onTertiary = Color(0xFF1D1202),
            tertiaryContainer = customTertiary.copy(alpha = 0.20f),
            onTertiaryContainer = customTertiary,
            background = customBg,
            onBackground = targetText,
            surface = surfBase.copy(alpha = effectiveCardAlpha),
            onSurface = targetText,
            surfaceVariant = surfHigh,
            onSurfaceVariant = targetMuted,
            surfaceContainerLowest = customBg,
            surfaceContainerLow = surfLow,
            surfaceContainer = surfHigh,
            surfaceContainerHigh = surfHighest,
            outline = customBorder,
            outlineVariant = customBorder.copy(alpha = 0.65f),
            error = Color(0xFFF87171),
            onError = Color(0xFF2A0707),
            errorContainer = Color(0xFF451218),
            onErrorContainer = Color(0xFFFCA5A5)
        )
    } else {
        lightColorScheme(
            primary = customPrimary,
            onPrimary = targetButtonText,
            primaryContainer = customPrimary.copy(alpha = 0.14f),
            onPrimaryContainer = customPrimary,
            secondary = customSecondary,
            onSecondary = Color.White,
            secondaryContainer = customSecondary.copy(alpha = 0.14f),
            onSecondaryContainer = customSecondary,
            tertiary = customTertiary,
            onTertiary = Color.White,
            tertiaryContainer = customTertiary.copy(alpha = 0.15f),
            onTertiaryContainer = customTertiary,
            background = customBg,
            onBackground = targetText,
            surface = surfBase.copy(alpha = effectiveCardAlpha),
            onSurface = targetText,
            surfaceVariant = surfHigh,
            onSurfaceVariant = targetMuted,
            surfaceContainerLowest = customBg,
            surfaceContainerLow = surfLow,
            surfaceContainer = surfHigh,
            surfaceContainerHigh = surfHighest,
            outline = customBorder,
            outlineVariant = customBorder.copy(alpha = 0.7f),
            error = Color(0xFFDC2626),
            onError = Color.White,
            errorContainer = Color(0xFFFEE2E2),
            onErrorContainer = Color(0xFF991B1B)
        )
    }

    val radius = config.cornerRadiusDp.toInt().coerceIn(4, 32).dp
    val shapes = Shapes(
        small = RoundedCornerShape((radius * 0.6f).coerceAtLeast(4.dp)),
        medium = RoundedCornerShape(radius),
        large = RoundedCornerShape((radius * 1.25f).coerceAtMost(32.dp))
    )

    val tokens = FocusUiTokens(
        cardStyle = config.cardStyle,
        cornerRadius = radius,
        compactDensity = config.compactDensity,
        appLayoutStyle = config.appLayoutStyle,
        navBarStyle = config.navBarStyle,
        backgroundPreset = config.backgroundPreset,
        backgroundOverlayAlpha = config.backgroundOverlayAlpha.toFloat().coerceIn(0.05f, 0.98f),
        cardOpacity = effectiveCardAlpha,
        resolvedBackgroundColor = customBg,
        resolvedSurfaceColor = surfLow,
        resolvedNavBarColor = customNavBar,
        resolvedBorderColor = customBorder
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
fun MasterMindBackgroundHost(
    backgroundRefreshKey: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalFocusUiTokens.current
    var customBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(tokens.backgroundPreset, backgroundRefreshKey) {
        customBitmap = if (tokens.backgroundPreset == "custom_photo") {
            withContext(Dispatchers.IO) {
                BackgroundMediaStorage.loadBitmap(context)
            }
        } else {
            null
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val baseBg = tokens.resolvedBackgroundColor

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        baseBg,
                        blendColors(baseBg, primary, 0.09f),
                        blendColors(baseBg, secondary, 0.07f)
                    )
                )
            )
    ) {
        when (tokens.backgroundPreset) {
            "pulse_nebula" -> {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_pulse),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(baseBg.copy(alpha = tokens.backgroundOverlayAlpha))
                )
            }
            "custom_photo" -> {
                val bmp = customBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(baseBg.copy(alpha = tokens.backgroundOverlayAlpha))
                    )
                }
            }
        }
        content()
    }
}

private fun blendColors(base: Color, tint: Color, ratio: Float): Color {
    val inv = 1f - ratio
    return Color(
        red = base.red * inv + tint.red * ratio,
        green = base.green * inv + tint.green * ratio,
        blue = base.blue * inv + tint.blue * ratio,
        alpha = base.alpha
    )
}

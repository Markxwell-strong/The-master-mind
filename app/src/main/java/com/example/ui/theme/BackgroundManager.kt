package com.example.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.AppearancePreferencesState
import com.example.data.preferences.WallpaperConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class BuiltInWallpaper(
    val id: String,
    val title: String,
    val subtitle: String,
    @DrawableRes val drawableRes: Int,
    val isLightBackdrop: Boolean = false
)

object BackgroundManager {
    const val SCOPE_GLOBAL = "global"

    val builtInWallpapers: List<BuiltInWallpaper> = listOf(
        BuiltInWallpaper(
            id = "obsidian_aurora",
            title = "Obsidian Aurora",
            subtitle = "Midnight navy with emerald glass waves",
            drawableRes = R.drawable.img_wall_obsidian_aurora,
            isLightBackdrop = false
        ),
        BuiltInWallpaper(
            id = "cosmic_velvet",
            title = "Cosmic Velvet",
            subtitle = "Indigo glass spheres with violet glow",
            drawableRes = R.drawable.img_wall_cosmic_velvet,
            isLightBackdrop = false
        ),
        BuiltInWallpaper(
            id = "solar_dusk",
            title = "Solar Dusk",
            subtitle = "Warm espresso slate with amber horizon",
            drawableRes = R.drawable.img_wall_solar_dusk,
            isLightBackdrop = false
        ),
        BuiltInWallpaper(
            id = "morning_mist",
            title = "Morning Mist",
            subtitle = "Airy pearl daylight sage horizon",
            drawableRes = R.drawable.img_wall_morning_mist,
            isLightBackdrop = true
        ),
        BuiltInWallpaper(
            id = "pulse_nebula",
            title = "Neural Nebula",
            subtitle = "Signature Master Mind cosmic field",
            drawableRes = R.drawable.img_hero_pulse,
            isLightBackdrop = false
        )
    )

    fun wallpaperById(id: String): BuiltInWallpaper =
        builtInWallpapers.firstOrNull { it.id == id } ?: builtInWallpapers.first()

    fun resolveForScreen(
        state: AppearancePreferencesState,
        screenKey: String
    ): WallpaperConfig {
        val normalized = normalizeScreenKey(screenKey)
        return state.perScreenWallpapers[normalized] ?: state.globalWallpaper
    }

    fun normalizeScreenKey(route: String?): String = when (route) {
        "home", "dashboard" -> "home"
        "activities", "habits", "shield" -> "activities"
        "intelligence" -> "intelligence"
        "journal", "diary" -> "journal"
        "settings" -> "settings"
        "profile" -> "profile"
        "business", "ops" -> "business"
        else -> "home"
    }

    private fun wallpaperDir(context: Context): File =
        File(context.filesDir, "wallpapers").apply { if (!exists()) mkdirs() }

    fun galleryFile(context: Context, scopeKey: String = SCOPE_GLOBAL): File {
        val safe = scopeKey.replace(Regex("[^a-zA-Z0-9_-]"), "")
            .ifBlank { SCOPE_GLOBAL }
        return File(wallpaperDir(context), "wall_$safe.jpg")
    }

    fun hasGalleryImage(context: Context, scopeKey: String = SCOPE_GLOBAL): Boolean =
        galleryFile(context, scopeKey).exists()

    fun saveGalleryImage(context: Context, uri: Uri, scopeKey: String = SCOPE_GLOBAL): Boolean {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (maxDim / sampleSize > 1920) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return false

            val outFile = galleryFile(context, scopeKey)
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun deleteGalleryImage(context: Context, scopeKey: String = SCOPE_GLOBAL): Boolean {
        val f = galleryFile(context, scopeKey)
        return if (f.exists()) f.delete() else true
    }

    fun loadGalleryBitmap(context: Context, scopeKey: String = SCOPE_GLOBAL): Bitmap? {
        val screenFile = galleryFile(context, scopeKey)
        val target = when {
            screenFile.exists() -> screenFile
            galleryFile(context, SCOPE_GLOBAL).exists() -> galleryFile(context, SCOPE_GLOBAL)
            else -> File(context.filesDir, "custom_master_bg.jpg") // legacy migration fallback
        }
        if (!target.exists()) return null
        return try {
            BitmapFactory.decodeFile(target.absolutePath)
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun ImmersiveWallpaperCanvas(
    wallpaper: WallpaperConfig,
    screenKey: String,
    refreshKey: Int,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false
) {
    val context = LocalContext.current
    val tokens = LocalFocusUiTokens.current
    var galleryBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(wallpaper.sourceType, screenKey, refreshKey) {
        galleryBitmap = if (wallpaper.sourceType == "gallery") {
            withContext(Dispatchers.IO) {
                BackgroundManager.loadGalleryBitmap(context, screenKey)
            }
        } else {
            null
        }
    }

    val brightnessOffset = (wallpaper.brightness - 1.0f) * 255f
    val brightnessFilter = remember(wallpaper.brightness) {
        val matrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, brightnessOffset,
                0f, 1f, 0f, 0f, brightnessOffset,
                0f, 0f, 1f, 0f, brightnessOffset,
                0f, 0f, 0f, 1f, 0f
            )
        )
        ColorFilter.colorMatrix(matrix)
    }

    val imageModifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
            scaleX = wallpaper.cropScale
            scaleY = wallpaper.cropScale
            translationX = wallpaper.offsetX * size.width * 0.28f
            translationY = wallpaper.offsetY * size.height * 0.28f
        }
        .let { mod ->
            if (wallpaper.blurDp > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mod.blur(wallpaper.blurDp.dp)
            } else {
                mod
            }
        }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val baseBg = tokens.resolvedBackgroundColor
    val scrimColor = if (tokens.isLightTheme) {
        Color(0xFFF8FAFC)
    } else {
        Color(0xFF050A14)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        baseBg,
                        blendThemeColors(baseBg, primary, 0.16f),
                        blendThemeColors(baseBg, secondary, 0.12f)
                    )
                )
            )
    ) {
        when (wallpaper.sourceType) {
            "gallery" -> {
                val bmp = galleryBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = if (isPreview) "Wallpaper preview" else null,
                        contentScale = ContentScale.Crop,
                        colorFilter = brightnessFilter,
                        modifier = imageModifier
                    )
                } else {
                    val fallback = BackgroundManager.wallpaperById(wallpaper.builtInId)
                    Image(
                        painter = painterResource(id = fallback.drawableRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = brightnessFilter,
                        modifier = imageModifier
                    )
                }
            }
            "built_in" -> {
                val builtIn = BackgroundManager.wallpaperById(wallpaper.builtInId)
                Image(
                    painter = painterResource(id = builtIn.drawableRes),
                    contentDescription = if (isPreview) builtIn.title else null,
                    contentScale = ContentScale.Crop,
                    colorFilter = brightnessFilter,
                    modifier = imageModifier
                )
            }
            else -> Unit // ambient gradient
        }

        // Fallback soft diffusion layer when blur requested on API < 31
        val legacyBlurBoost = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && wallpaper.blurDp > 1f) {
            (wallpaper.blurDp / 32f) * 0.22f
        } else 0f

        val effectiveOverlay = (wallpaper.overlayOpacity + legacyBlurBoost).coerceIn(0.05f, 0.92f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            scrimColor.copy(alpha = (effectiveOverlay * 0.72f).coerceIn(0.05f, 0.90f)),
                            scrimColor.copy(alpha = effectiveOverlay),
                            scrimColor.copy(alpha = (effectiveOverlay * 1.15f).coerceIn(0.08f, 0.92f))
                        )
                    )
                )
        )
    }
}

@Composable
fun GlassBackgroundHost(
    appearanceState: AppearancePreferencesState,
    activeRoute: String?,
    backgroundRefreshKey: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val screenKey = BackgroundManager.normalizeScreenKey(activeRoute)
    val wallpaper = BackgroundManager.resolveForScreen(appearanceState, screenKey)
    Box(modifier = modifier.fillMaxSize()) {
        ImmersiveWallpaperCanvas(
            wallpaper = wallpaper,
            screenKey = screenKey,
            refreshKey = backgroundRefreshKey
        )
        content()
    }
}

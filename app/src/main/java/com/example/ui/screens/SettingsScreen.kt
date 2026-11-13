package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserWorkspaceConfig
import com.example.data.preferences.WallpaperConfig
import com.example.system.ProfileMediaStorage
import com.example.system.SupportHelper
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTopBar
import com.example.ui.components.MasterMindLayoutOptions
import com.example.ui.components.SectionHeaderRow
import com.example.ui.theme.BackgroundManager
import com.example.ui.theme.FocusPalettes
import com.example.ui.theme.ImmersiveWallpaperCanvas
import com.example.ui.theme.MasterMindColorPart
import com.example.ui.theme.MasterMindSwatchPalette
import com.example.ui.theme.parseHexColorOrNull
import com.example.ui.theme.toHexRgb
import com.example.ui.viewmodel.FocusPulseViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val WallpaperScopes = listOf(
    BackgroundManager.SCOPE_GLOBAL to "All Screens",
    "home" to "Home",
    "activities" to "Activities",
    "intelligence" to "Intelligence",
    "journal" to "Journal",
    "settings" to "Settings",
    "business" to "Business Ops"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: FocusPulseViewModel,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBusiness: () -> Unit,
    onOpenSupport: () -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configState by viewModel.workspaceConfig.collectAsStateWithLifecycle()
    val appearance by viewModel.appearanceState.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsFeed.collectAsStateWithLifecycle()
    val bgRefreshKey by viewModel.customBgRefreshKey.collectAsStateWithLifecycle()
    val profilePhotoRefreshKey by viewModel.profilePhotoRefreshKey.collectAsStateWithLifecycle()

    val config = (configState as? UiState.Success)?.data ?: UserWorkspaceConfig()
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val uid = viewModel.currentUid
    LaunchedEffect(profilePhotoRefreshKey, uid) {
        profileBitmap = withContext(Dispatchers.IO) {
            ProfileMediaStorage.loadBitmap(context, uid)
        }
    }
    val profileInitial = config.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"

    var selectedScope by remember { mutableStateOf(BackgroundManager.SCOPE_GLOBAL) }
    val activeScopeWallpaper: WallpaperConfig = remember(appearance, selectedScope) {
        if (selectedScope == BackgroundManager.SCOPE_GLOBAL) {
            appearance.globalWallpaper
        } else {
            appearance.perScreenWallpapers[selectedScope] ?: appearance.globalWallpaper
        }
    }
    val hasPerScreenOverride = selectedScope != BackgroundManager.SCOPE_GLOBAL &&
        appearance.perScreenWallpapers.containsKey(selectedScope)

    var hasScopedGalleryPhoto by remember(selectedScope, bgRefreshKey) {
        mutableStateOf(BackgroundManager.hasGalleryImage(context, selectedScope))
    }

    fun updateActiveWallpaper(transform: (WallpaperConfig) -> WallpaperConfig) {
        if (selectedScope == BackgroundManager.SCOPE_GLOBAL) {
            viewModel.updateGlobalWallpaper(transform)
        } else {
            viewModel.updatePerScreenWallpaper(selectedScope, transform)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    BackgroundManager.saveGalleryImage(context, uri, selectedScope)
                }
                if (saved) {
                    hasScopedGalleryPhoto = true
                    updateActiveWallpaper { it.copy(sourceType = "gallery") }
                    viewModel.showStatusMessage(
                        if (selectedScope == BackgroundManager.SCOPE_GLOBAL) "Custom global wallpaper applied."
                        else "Custom wallpaper set for ${selectedScope.replaceFirstChar { it.uppercase() }}."
                    )
                } else {
                    viewModel.showStatusMessage("Could not load selected picture.")
                }
            }
        }
    }

    // Draft sliders for smooth real-time preview before DataStore commit
    var zoomDraft by remember(selectedScope, activeScopeWallpaper.cropScale) {
        mutableFloatStateOf(activeScopeWallpaper.cropScale)
    }
    var offsetXDraft by remember(selectedScope, activeScopeWallpaper.offsetX) {
        mutableFloatStateOf(activeScopeWallpaper.offsetX)
    }
    var offsetYDraft by remember(selectedScope, activeScopeWallpaper.offsetY) {
        mutableFloatStateOf(activeScopeWallpaper.offsetY)
    }
    var brightnessDraft by remember(selectedScope, activeScopeWallpaper.brightness) {
        mutableFloatStateOf(activeScopeWallpaper.brightness)
    }
    var blurDraft by remember(selectedScope, activeScopeWallpaper.blurDp) {
        mutableFloatStateOf(activeScopeWallpaper.blurDp)
    }
    var overlayDraft by remember(selectedScope, activeScopeWallpaper.overlayOpacity) {
        mutableFloatStateOf(activeScopeWallpaper.overlayOpacity)
    }
    var glassOpacityDraft by remember(appearance.glassOpacity) {
        mutableFloatStateOf(appearance.glassOpacity)
    }
    var radiusDraft by remember(appearance.cornerRadiusDp) {
        mutableFloatStateOf(appearance.cornerRadiusDp.toFloat())
    }

    val livePreviewWallpaper = activeScopeWallpaper.copy(
        cropScale = zoomDraft,
        offsetX = offsetXDraft,
        offsetY = offsetYDraft,
        brightness = brightnessDraft,
        blurDp = blurDraft,
        overlayOpacity = overlayDraft
    )

    // Color-by-parts state
    var selectedPart by remember { mutableStateOf(MasterMindColorPart.PRIMARY) }
    val fallbackColorForSelected = when (selectedPart) {
        MasterMindColorPart.BACKGROUND -> MaterialTheme.colorScheme.background
        MasterMindColorPart.SURFACE -> MaterialTheme.colorScheme.surface
        MasterMindColorPart.PRIMARY -> MaterialTheme.colorScheme.primary
        MasterMindColorPart.SECONDARY -> MaterialTheme.colorScheme.secondary
        MasterMindColorPart.TERTIARY -> MaterialTheme.colorScheme.tertiary
        MasterMindColorPart.TEXT -> MaterialTheme.colorScheme.onBackground
        MasterMindColorPart.MUTED_TEXT -> MaterialTheme.colorScheme.onSurfaceVariant
        MasterMindColorPart.NAV_BAR -> MaterialTheme.colorScheme.surface
        MasterMindColorPart.CARD_BORDER -> MaterialTheme.colorScheme.outline
        MasterMindColorPart.BUTTON_TEXT -> MaterialTheme.colorScheme.onPrimary
    }
    val activePartColor = parseHexColorOrNull(appearance.partColors[selectedPart.key])
        ?: fallbackColorForSelected

    var rSlider by remember(selectedPart, activePartColor) { mutableFloatStateOf(activePartColor.red) }
    var gSlider by remember(selectedPart, activePartColor) { mutableFloatStateOf(activePartColor.green) }
    var bSlider by remember(selectedPart, activePartColor) { mutableFloatStateOf(activePartColor.blue) }
    var hexDraft by remember(selectedPart, activePartColor) { mutableStateOf(activePartColor.toHexRgb()) }

    LaunchedEffect(appearance.partColors[selectedPart.key], selectedPart) {
        val current = parseHexColorOrNull(appearance.partColors[selectedPart.key]) ?: fallbackColorForSelected
        rSlider = current.red
        gSlider = current.green
        bSlider = current.blue
        hexDraft = current.toHexRgb()
    }

    var presetNameDraft by remember { mutableStateOf("") }
    var showClearIntelConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassTopBar(
                title = "Glass & Intelligence Settings",
                subtitle = "Wallpapers, presets, widgets, privacy & support",
                profileInitial = profileInitial,
                profileBitmap = profileBitmap,
                unreadNotifications = notifications.size,
                onOpenProfile = onOpenProfile,
                onOpenNotifications = onOpenNotifications
            )
        }

        // 1. Immersive Wallpaper & Glassmorphism Studio
        item {
            GlassCard(accentBorder = true) {
                SectionHeaderRow(
                    title = "Background & Glass Studio",
                    subtitle = "Apply globally or customize each screen independently",
                    icon = Icons.Default.Wallpaper
                )

                Text("Target Screen Scope", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WallpaperScopes.forEach { (scopeKey, label) ->
                        val isCustom = scopeKey != BackgroundManager.SCOPE_GLOBAL &&
                            appearance.perScreenWallpapers.containsKey(scopeKey)
                        FilterChip(
                            selected = selectedScope == scopeKey,
                            onClick = { selectedScope = scopeKey },
                            label = { Text(if (isCustom) "$label •" else label) },
                            modifier = Modifier.testTag("wallpaper_scope_$scopeKey")
                        )
                    }
                }

                if (hasPerScreenOverride) {
                    OutlinedButton(
                        onClick = { viewModel.clearPerScreenWallpaper(selectedScope) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Inherit Global Wallpaper for ${selectedScope.replaceFirstChar { it.uppercase() }}")
                    }
                }

                // Live Preview Frame
                Text("Live Glass Preview", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                ) {
                    ImmersiveWallpaperCanvas(
                        wallpaper = livePreviewWallpaper,
                        screenKey = selectedScope,
                        refreshKey = bgRefreshKey,
                        isPreview = true,
                        modifier = Modifier.fillMaxSize()
                    )
                    GlassCard(
                        contentPadding = 12.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "THE MASTER MIND Glass",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Blur ${blurDraft.roundToInt()}dp • Scrim ${(overlayDraft * 100).roundToInt()}% • Zoom ${String.format("%.1fx", zoomDraft)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Built-in Wallpaper Library
                Text("Built-In Wallpaper Library", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(BackgroundManager.builtInWallpapers, key = { it.id }) { wall ->
                        val isSelected = activeScopeWallpaper.sourceType == "built_in" &&
                            activeScopeWallpaper.builtInId == wall.id
                        Column(
                            modifier = Modifier
                                .width(116.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    updateActiveWallpaper {
                                        it.copy(sourceType = "built_in", builtInId = wall.id)
                                    }
                                }
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Image(
                                painter = painterResource(id = wall.drawableRes),
                                contentDescription = wall.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Text(
                                text = wall.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Gallery & Ambient Gradient buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassButton(
                        text = if (hasScopedGalleryPhoto) "Change Gallery Photo" else "Pick Gallery Photo",
                        icon = Icons.Default.Image,
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_background_photo_button")
                    )
                    if (hasScopedGalleryPhoto) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        BackgroundManager.deleteGalleryImage(context, selectedScope)
                                    }
                                    hasScopedGalleryPhoto = false
                                    updateActiveWallpaper { it.copy(sourceType = "built_in") }
                                    viewModel.showStatusMessage("Gallery photo removed.")
                                }
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("clear_background_photo_button")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove photo")
                        }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = activeScopeWallpaper.sourceType == "gradient",
                        onClick = { updateActiveWallpaper { it.copy(sourceType = "gradient") } },
                        label = { Text("Ambient Gradient") }
                    )
                    if (hasScopedGalleryPhoto) {
                        FilterChip(
                            selected = activeScopeWallpaper.sourceType == "gallery",
                            onClick = { updateActiveWallpaper { it.copy(sourceType = "gallery") } },
                            label = { Text("Saved Gallery Photo") }
                        )
                    }
                }

                HorizontalDivider()

                // Position, Crop, Brightness, Blur, Overlay & Glass Opacity Sliders
                Text("Crop Zoom (${String.format("%.2fx", zoomDraft)})", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = zoomDraft,
                    onValueChange = { zoomDraft = it },
                    onValueChangeFinished = { updateActiveWallpaper { it.copy(cropScale = zoomDraft) } },
                    valueRange = 1.0f..2.2f
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pan X (${(offsetXDraft * 100).roundToInt()}%)", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = offsetXDraft,
                            onValueChange = { offsetXDraft = it },
                            onValueChangeFinished = { updateActiveWallpaper { it.copy(offsetX = offsetXDraft) } },
                            valueRange = -1.0f..1.0f
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pan Y (${(offsetYDraft * 100).roundToInt()}%)", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = offsetYDraft,
                            onValueChange = { offsetYDraft = it },
                            onValueChangeFinished = { updateActiveWallpaper { it.copy(offsetY = offsetYDraft) } },
                            valueRange = -1.0f..1.0f
                        )
                    }
                }

                Text("Wallpaper Brightness (${(brightnessDraft * 100).roundToInt()}%)", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = brightnessDraft,
                    onValueChange = { brightnessDraft = it },
                    onValueChangeFinished = { updateActiveWallpaper { it.copy(brightness = brightnessDraft) } },
                    valueRange = 0.45f..1.45f
                )

                Text("Wallpaper Blur (${blurDraft.roundToInt()}dp)", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = blurDraft,
                    onValueChange = { blurDraft = it },
                    onValueChangeFinished = { updateActiveWallpaper { it.copy(blurDp = blurDraft) } },
                    valueRange = 0f..28f
                )

                Text("Dimming Overlay Opacity (${(overlayDraft * 100).roundToInt()}%)", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = overlayDraft,
                    onValueChange = { overlayDraft = it },
                    onValueChangeFinished = { updateActiveWallpaper { it.copy(overlayOpacity = overlayDraft) } },
                    valueRange = 0.05f..0.90f
                )

                Text("Frosted Glass Card Opacity (${(glassOpacityDraft * 100).roundToInt()}%)", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = glassOpacityDraft,
                    onValueChange = { glassOpacityDraft = it },
                    onValueChangeFinished = {
                        viewModel.updateAppearance { it.copy(glassOpacity = glassOpacityDraft) }
                    },
                    valueRange = 0.20f..0.88f
                )
            }
        }

        // 2. Theme Mode, Presets & Reset
        item {
            GlassCard {
                SectionHeaderRow(
                    title = "Theme Presets & Brightness Mode",
                    subtitle = "Save and switch complete glass styles cleanly",
                    icon = Icons.Default.BookmarkAdd,
                    trailing = {
                        TextButton(
                            onClick = { viewModel.resetAppearancePreferences() },
                            modifier = Modifier.testTag("reset_appearance_button")
                        ) {
                            Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("dark" to "Dark", "amoled" to "AMOLED", "light" to "Light", "system" to "System").forEach { (id, lbl) ->
                        FilterChip(
                            selected = appearance.darkModePreference == id,
                            onClick = {
                                viewModel.updateAppearance { it.copy(darkModePreference = id) }
                            },
                            label = { Text(lbl) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = presetNameDraft,
                        onValueChange = { presetNameDraft = it },
                        label = { Text("New Preset Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            viewModel.saveThemePreset(presetNameDraft.ifBlank { "My Glass Preset" })
                            presetNameDraft = ""
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("save_theme_preset_button")
                    ) {
                        Text("Save")
                    }
                }

                if (appearance.savedPresets.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(appearance.savedPresets, key = { it.id }) { preset ->
                            Surface(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TextButton(onClick = { viewModel.applyThemePreset(preset) }) {
                                        Text(preset.name, fontWeight = FontWeight.SemiBold)
                                    }
                                    IconButton(onClick = { viewModel.deleteThemePreset(preset.id) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete preset")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Accent Palettes & Color-By-Parts Studio
        item {
            GlassCard(accentBorder = appearance.partColors.isNotEmpty()) {
                SectionHeaderRow(
                    title = "Accent & Color By Parts",
                    subtitle = "Tabs shift subtly by default; fine-tune any UI element",
                    icon = Icons.Default.ColorLens,
                    trailing = {
                        if (appearance.partColors.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    viewModel.updateAppearance { it.copy(partColors = emptyMap()) }
                                },
                                modifier = Modifier.testTag("reset_all_colors_button")
                            ) {
                                Text("Clear Colors")
                            }
                        }
                    }
                )

                Text("Accent Palette", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FocusPalettes.forEach { pal ->
                        FilterChip(
                            selected = appearance.themePalette == pal.id,
                            onClick = { viewModel.updateAppearance { it.copy(themePalette = pal.id) } },
                            label = { Text(pal.label) }
                        )
                    }
                }

                HorizontalDivider()

                Text("Target Part", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MasterMindColorPart.entries.forEach { part ->
                        val hasCustom = appearance.partColors.containsKey(part.key)
                        val dotColor = parseHexColorOrNull(appearance.partColors[part.key])
                        FilterChip(
                            selected = selectedPart == part,
                            onClick = { selectedPart = part },
                            label = { Text(part.label) },
                            leadingIcon = if (dotColor != null) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), CircleShape)
                                    )
                                }
                            } else null,
                            trailingIcon = if (hasCustom) {
                                { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MasterMindSwatchPalette.forEach { swatch ->
                        val swatchHex = swatch.toHexRgb()
                        val isCurrent = activePartColor.toHexRgb().equals(swatchHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (isCurrent) 3.dp else 1.dp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable {
                                    rSlider = swatch.red
                                    gSlider = swatch.green
                                    bSlider = swatch.blue
                                    hexDraft = swatchHex
                                    viewModel.updateAppearance { st ->
                                        st.copy(partColors = st.partColors + (selectedPart.key to swatchHex))
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCurrent) {
                                val lum = swatch.red * 0.299f + swatch.green * 0.587f + swatch.blue * 0.114f
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected $swatchHex",
                                    tint = if (lum > 0.55f) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Text("RGB Fine-Tune (${Color(rSlider, gSlider, bSlider).toHexRgb()})", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("R", modifier = Modifier.width(20.dp), fontWeight = FontWeight.Bold)
                    Slider(
                        value = rSlider,
                        onValueChange = {
                            rSlider = it
                            hexDraft = Color(it, gSlider, bSlider).toHexRgb()
                        },
                        onValueChangeFinished = {
                            val hex = Color(rSlider, gSlider, bSlider).toHexRgb()
                            viewModel.updateAppearance { st -> st.copy(partColors = st.partColors + (selectedPart.key to hex)) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("G", modifier = Modifier.width(20.dp), fontWeight = FontWeight.Bold)
                    Slider(
                        value = gSlider,
                        onValueChange = {
                            gSlider = it
                            hexDraft = Color(rSlider, it, bSlider).toHexRgb()
                        },
                        onValueChangeFinished = {
                            val hex = Color(rSlider, gSlider, bSlider).toHexRgb()
                            viewModel.updateAppearance { st -> st.copy(partColors = st.partColors + (selectedPart.key to hex)) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("B", modifier = Modifier.width(20.dp), fontWeight = FontWeight.Bold)
                    Slider(
                        value = bSlider,
                        onValueChange = {
                            bSlider = it
                            hexDraft = Color(rSlider, gSlider, it).toHexRgb()
                        },
                        onValueChangeFinished = {
                            val hex = Color(rSlider, gSlider, bSlider).toHexRgb()
                            viewModel.updateAppearance { st -> st.copy(partColors = st.partColors + (selectedPart.key to hex)) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hexDraft,
                        onValueChange = { hexDraft = it.take(9) },
                        label = { Text("Hex Code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val parsed = parseHexColorOrNull(hexDraft)
                            if (parsed != null) {
                                val hex = parsed.toHexRgb()
                                viewModel.updateAppearance { st -> st.copy(partColors = st.partColors + (selectedPart.key to hex)) }
                            } else {
                                viewModel.showStatusMessage("Use #RRGGBB format, e.g. #10B981")
                            }
                        }
                    ) { Text("Apply") }
                    if (appearance.partColors.containsKey(selectedPart.key)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.updateAppearance { st ->
                                    st.copy(partColors = st.partColors - selectedPart.key)
                                }
                            }
                        ) { Text("Reset") }
                    }
                }
            }
        }

        // 4. Layout, Corner Roundness & Widget Order
        item {
            GlassCard {
                SectionHeaderRow(
                    title = "Layout & Dashboard Widgets",
                    subtitle = "Customize board layout, card shape & widget ordering",
                    icon = Icons.Default.ViewModule
                )

                Text("Board Layout Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MasterMindLayoutOptions.forEach { layoutOpt ->
                        FilterChip(
                            selected = appearance.appLayoutStyle == layoutOpt.id,
                            onClick = {
                                viewModel.updateAppearance { it.copy(appLayoutStyle = layoutOpt.id) }
                            },
                            label = { Text(layoutOpt.title) }
                        )
                    }
                }

                Text("Card Corner Roundness: ${radiusDraft.roundToInt()}dp", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = radiusDraft,
                    onValueChange = { radiusDraft = it },
                    onValueChangeFinished = {
                        viewModel.updateAppearance { it.copy(cornerRadiusDp = radiusDraft.roundToInt()) }
                    },
                    valueRange = 8f..28f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Compact Spacing Density", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Tighter spacing to reveal more glass panels at once",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appearance.compactDensity,
                        onCheckedChange = { compact ->
                            viewModel.updateAppearance { it.copy(compactDensity = compact) }
                        }
                    )
                }

                HorizontalDivider()
                Text("Home Dashboard Widgets", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                appearance.dashboardWidgets.forEachIndexed { idx, w ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = w.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.moveDashboardWidget(w.id, -1) },
                            enabled = idx > 0
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = { viewModel.moveDashboardWidget(w.id, 1) },
                            enabled = idx < appearance.dashboardWidgets.lastIndex
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                        }
                        Switch(
                            checked = w.visible,
                            onCheckedChange = { viewModel.toggleDashboardWidget(w.id) }
                        )
                    }
                }
            }
        }

        // 5. AI Intelligence, Privacy & Retention Controls
        item {
            GlassCard {
                SectionHeaderRow(
                    title = "AI Intelligence & Data Privacy",
                    subtitle = "Pause collection anytime or adjust local retention window",
                    icon = Icons.Default.PrivacyTip
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Evidence-Based Activity Monitoring", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Tracks permitted UsageStats app durations, launches, habits & focus logs on-device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appearance.monitoringEnabled,
                        onCheckedChange = { viewModel.setMonitoringEnabled(it) },
                        modifier = Modifier.testTag("monitoring_enabled_switch")
                    )
                }

                Text("Local Telemetry Retention Window", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 90, 365).forEach { days ->
                        FilterChip(
                            selected = appearance.retentionDays == days,
                            onClick = { viewModel.setRetentionDays(days) },
                            label = { Text("$days days") }
                        )
                    }
                }

                Text("AI Coaching Persona", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("balanced", "strict", "zen", "business").forEach { persona ->
                        FilterChip(
                            selected = config.aiCoachPersona == persona,
                            onClick = { viewModel.updateWorkspaceConfig { it.copy(aiCoachPersona = persona) } },
                            label = { Text(persona.replaceFirstChar { it.uppercase() }) },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp)) }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showClearIntelConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("clear_intelligence_data_button")
                ) {
                    Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear Stored Patterns, Memory & Telemetry")
                }
            }
        }

        // 6. Business Ops, Permissions, Profile & Developer Support
        item {
            GlassCard {
                SectionHeaderRow(
                    title = "Modules, Permissions & Support",
                    subtitle = "Access Business Ops, permissions wizard, profile & developer help",
                    icon = Icons.Default.Tune
                )

                GlassButton(
                    text = "Open Stock & Profit Table",
                    icon = Icons.Default.Inventory2,
                    onClick = onOpenBusiness,
                    tonal = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_open_business_button")
                )

                OutlinedButton(
                    onClick = { viewModel.openPermissionsPrompt() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_permissions_button")
                ) {
                    Icon(Icons.Default.Security, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Review System Permissions & Usage Access")
                }

                OutlinedButton(
                    onClick = onOpenProfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_open_profile_button")
                ) {
                    Icon(Icons.Default.Person, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Profile Picture, Admin & Cloud Sync")
                }

                Button(
                    onClick = onOpenSupport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_support_triage_button")
                ) {
                    Icon(Icons.Default.SupportAgent, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("AI Troubleshooting & Developer Support")
                }

                OutlinedButton(
                    onClick = { SupportHelper.openDeveloperWhatsApp(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_whatsapp_support_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Direct WhatsApp (${SupportHelper.DEVELOPER_WHATSAPP_RAW})")
                }

                OutlinedButton(
                    onClick = onSignOut,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out & Switch Account")
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    if (showClearIntelConfirm) {
        AlertDialog(
            onDismissRequest = { showClearIntelConfirm = false },
            title = { Text("Clear Local AI Memory?") },
            text = {
                Text("This removes locally recorded usage summaries, behavioural patterns, active insights, and AI memory entries. Cloud-synced habits, notes, diary entries, and shop inventory remain safe.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllIntelligenceData()
                        showClearIntelConfirm = false
                    }
                ) { Text("Clear Local Data") }
            },
            dismissButton = {
                TextButton(onClick = { showClearIntelConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

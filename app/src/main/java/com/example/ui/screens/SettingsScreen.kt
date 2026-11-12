package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Wallpaper
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserWorkspaceConfig
import com.example.system.BackgroundMediaStorage
import com.example.system.SupportHelper
import com.example.ui.components.MasterMindAdaptiveBoard
import com.example.ui.components.MasterMindLayoutOptions
import com.example.ui.components.PulseCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.theme.FocusPalettes
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: FocusPulseViewModel,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configState by viewModel.workspaceConfig.collectAsStateWithLifecycle()
    val bgRefreshKey by viewModel.customBgRefreshKey.collectAsStateWithLifecycle()
    val config = (configState as? UiState.Success)?.data ?: UserWorkspaceConfig()

    var hasSavedPhoto by remember(bgRefreshKey) {
        mutableStateOf(BackgroundMediaStorage.hasCustomBackground(context))
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    BackgroundMediaStorage.savePickedImage(context, uri)
                }
                if (saved) {
                    hasSavedPhoto = true
                    viewModel.notifyCustomBackgroundChanged("custom_photo")
                    viewModel.showStatusMessage("Custom app background updated.")
                } else {
                    viewModel.showStatusMessage("Could not load selected picture.")
                }
            }
        }
    }

    var selectedPart by remember { mutableStateOf(MasterMindColorPart.PRIMARY) }

    val fallbackColorForSelected = when (selectedPart) {
        MasterMindColorPart.BACKGROUND -> MaterialTheme.colorScheme.background
        MasterMindColorPart.SURFACE -> MaterialTheme.colorScheme.surfaceContainerLow
        MasterMindColorPart.PRIMARY -> MaterialTheme.colorScheme.primary
        MasterMindColorPart.SECONDARY -> MaterialTheme.colorScheme.secondary
        MasterMindColorPart.TERTIARY -> MaterialTheme.colorScheme.tertiary
        MasterMindColorPart.TEXT -> MaterialTheme.colorScheme.onBackground
        MasterMindColorPart.MUTED_TEXT -> MaterialTheme.colorScheme.onSurfaceVariant
        MasterMindColorPart.NAV_BAR -> MaterialTheme.colorScheme.surface
        MasterMindColorPart.CARD_BORDER -> MaterialTheme.colorScheme.outline
        MasterMindColorPart.BUTTON_TEXT -> MaterialTheme.colorScheme.onPrimary
    }

    val activePartColor = parseHexColorOrNull(config.partColors[selectedPart.key])
        ?: fallbackColorForSelected

    var rSlider by remember(selectedPart, activePartColor) { mutableFloatStateOf(activePartColor.red) }
    var gSlider by remember(selectedPart, activePartColor) { mutableFloatStateOf(activePartColor.green) }
    var bSlider by remember(selectedPart, activePartColor) { mutableFloatStateOf(activePartColor.blue) }
    var hexDraft by remember(selectedPart, activePartColor) { mutableStateOf(activePartColor.toHexRgb()) }

    LaunchedEffect(config.partColors[selectedPart.key], selectedPart) {
        val current = parseHexColorOrNull(config.partColors[selectedPart.key]) ?: fallbackColorForSelected
        rSlider = current.red
        gSlider = current.green
        bSlider = current.blue
        hexDraft = current.toHexRgb()
    }

    val blocks: List<@Composable (Modifier) -> Unit> = listOf(
        // 1. Developer Support Card (WhatsApp)
        { mod ->
            PulseCard(modifier = mod, accentBorder = true) {
                SectionHeaderRow(
                    title = "Developer Support",
                    subtitle = "Direct WhatsApp help for issues, bugs or custom requests",
                    icon = Icons.Default.SupportAgent
                )
                Text(
                    text = "Need help or spotted an issue? Message the developer directly on WhatsApp (${SupportHelper.DEVELOPER_WHATSAPP_RAW}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { SupportHelper.openDeveloperWhatsApp(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_whatsapp_support_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Chat with Developer on WhatsApp")
                }
            }
        },

        // 2. Whole-App Background & Custom Photo Studio
        { mod ->
            PulseCard(modifier = mod) {
                SectionHeaderRow(
                    title = "Whole-App Background Studio",
                    subtitle = "Set a solid gradient, nebula art or your own gallery photo",
                    icon = Icons.Default.Wallpaper
                )
                Text("Brightness Mode", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("dark" to "Dark", "amoled" to "AMOLED Black", "light" to "Light", "system" to "System").forEach { (id, lbl) ->
                        FilterChip(
                            selected = config.darkModePreference == id,
                            onClick = { viewModel.updateWorkspaceConfig { it.copy(darkModePreference = id) } },
                            label = { Text(lbl) }
                        )
                    }
                }

                Text("Background Source", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = config.backgroundPreset == "none",
                        onClick = { viewModel.notifyCustomBackgroundChanged("none") },
                        label = { Text("Solid Gradient") }
                    )
                    FilterChip(
                        selected = config.backgroundPreset == "pulse_nebula",
                        onClick = { viewModel.notifyCustomBackgroundChanged("pulse_nebula") },
                        label = { Text("Nebula Art") }
                    )
                    FilterChip(
                        selected = config.backgroundPreset == "custom_photo",
                        onClick = {
                            if (hasSavedPhoto) {
                                viewModel.notifyCustomBackgroundChanged("custom_photo")
                            } else {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                        label = { Text("Custom Photo") },
                        leadingIcon = { Icon(Icons.Default.Image, null, Modifier.size(16.dp)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_background_photo_button")
                    ) {
                        Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (hasSavedPhoto) "Change Picture" else "Pick Custom Picture")
                    }

                    if (hasSavedPhoto) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        BackgroundMediaStorage.clearCustomBackground(context)
                                    }
                                    hasSavedPhoto = false
                                    viewModel.notifyCustomBackgroundChanged("none")
                                    viewModel.showStatusMessage("Custom background removed.")
                                }
                            },
                            modifier = Modifier.testTag("clear_background_photo_button")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove picture")
                        }
                    }
                }

                if (config.backgroundPreset != "none") {
                    var overlayAlphaDraft by remember(config.backgroundOverlayAlpha) {
                        mutableFloatStateOf(config.backgroundOverlayAlpha.toFloat())
                    }
                    Text(
                        text = "Backdrop Scrim Dimming (${(overlayAlphaDraft * 100).roundToInt()}%)",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = overlayAlphaDraft,
                        onValueChange = { overlayAlphaDraft = it },
                        onValueChangeFinished = {
                            viewModel.updateWorkspaceConfig { it.copy(backgroundOverlayAlpha = overlayAlphaDraft.toDouble()) }
                        },
                        valueRange = 0.08f..0.92f
                    )
                }

                var cardOpacityDraft by remember(config.cardOpacity) {
                    mutableFloatStateOf(config.cardOpacity.toFloat())
                }
                Text(
                    text = "Card Glass Opacity (${(cardOpacityDraft * 100).roundToInt()}%)",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = cardOpacityDraft,
                    onValueChange = { cardOpacityDraft = it },
                    onValueChangeFinished = {
                        viewModel.updateWorkspaceConfig { it.copy(cardOpacity = cardOpacityDraft.toDouble()) }
                    },
                    valueRange = 0.40f..1.0f
                )
            }
        },

        // 3. Color Everywhere (By Parts) Studio
        { mod ->
            PulseCard(modifier = mod, accentBorder = config.partColors.isNotEmpty()) {
                SectionHeaderRow(
                    title = "Color By Parts Studio",
                    subtitle = "Tabs shift colors per view by default; customize any part below",
                    icon = Icons.Default.ColorLens,
                    trailing = {
                        if (config.partColors.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.resetAllPartColors() },
                                modifier = Modifier.testTag("reset_all_colors_button")
                            ) {
                                Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset All")
                            }
                        }
                    }
                )

                Text("Base Preset Palette", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FocusPalettes.forEach { pal ->
                        FilterChip(
                            selected = config.themePalette == pal.id,
                            onClick = { viewModel.updateWorkspaceConfig { it.copy(themePalette = pal.id) } },
                            label = { Text(pal.label) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Select Element To Recolor", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MasterMindColorPart.entries.forEach { part ->
                        val hasCustom = config.partColors.containsKey(part.key)
                        val dotColor = parseHexColorOrNull(config.partColors[part.key])
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
                            } else null,
                            modifier = Modifier.testTag("color_part_chip_${part.key}")
                        )
                    }
                }

                // Live Preview Box for selected part
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Editing: ${selectedPart.label}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = selectedPart.hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Preview")
                        }
                    }
                }

                Text("Quick Color Swatches", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
                                    viewModel.updatePartColor(selectedPart.key, swatchHex)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCurrent) {
                                val lum = (swatch.red * 0.299f + swatch.green * 0.587f + swatch.blue * 0.114f)
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected color $swatchHex",
                                    tint = if (lum > 0.55f) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // RGB fine-tune sliders
                Text("Fine-Tune Shades (${Color(rSlider, gSlider, bSlider).toHexRgb()})", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("R", modifier = Modifier.width(20.dp), fontWeight = FontWeight.Bold)
                    Slider(
                        value = rSlider,
                        onValueChange = { r ->
                            rSlider = r
                            val hex = Color(r, gSlider, bSlider).toHexRgb()
                            hexDraft = hex
                        },
                        onValueChangeFinished = {
                            viewModel.updatePartColor(selectedPart.key, Color(rSlider, gSlider, bSlider).toHexRgb())
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("G", modifier = Modifier.width(20.dp), fontWeight = FontWeight.Bold)
                    Slider(
                        value = gSlider,
                        onValueChange = { g ->
                            gSlider = g
                            val hex = Color(rSlider, g, bSlider).toHexRgb()
                            hexDraft = hex
                        },
                        onValueChangeFinished = {
                            viewModel.updatePartColor(selectedPart.key, Color(rSlider, gSlider, bSlider).toHexRgb())
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("B", modifier = Modifier.width(20.dp), fontWeight = FontWeight.Bold)
                    Slider(
                        value = bSlider,
                        onValueChange = { b ->
                            bSlider = b
                            val hex = Color(rSlider, gSlider, b).toHexRgb()
                            hexDraft = hex
                        },
                        onValueChangeFinished = {
                            viewModel.updatePartColor(selectedPart.key, Color(rSlider, gSlider, bSlider).toHexRgb())
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
                                viewModel.updatePartColor(selectedPart.key, parsed.toHexRgb())
                            } else {
                                viewModel.showStatusMessage("Use #RRGGBB format, e.g. #10B981")
                            }
                        }
                    ) {
                        Text("Apply")
                    }
                    if (config.partColors.containsKey(selectedPart.key)) {
                        OutlinedButton(onClick = { viewModel.updatePartColor(selectedPart.key, null) }) {
                            Text("Reset")
                        }
                    }
                }
            }
        },

        // 4. App Layout & Navigation Architecture
        { mod ->
            PulseCard(modifier = mod) {
                SectionHeaderRow(
                    title = "App Layout & Navigation Shell",
                    subtitle = "Switch freely between grids, carousels, tiles or streams",
                    icon = Icons.Default.ViewModule
                )
                Text("Board Layout Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MasterMindLayoutOptions.forEach { layoutOpt ->
                        FilterChip(
                            selected = config.appLayoutStyle == layoutOpt.id,
                            onClick = { viewModel.updateWorkspaceConfig { it.copy(appLayoutStyle = layoutOpt.id) } },
                            label = { Text(layoutOpt.title) },
                            modifier = Modifier.testTag("layout_style_${layoutOpt.id}")
                        )
                    }
                }

                Text("Navigation Shell Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "bottom_bar" to "Docked Bar",
                        "floating_dock" to "Floating Capsule",
                        "side_rail" to "Side Rail"
                    ).forEach { (id, lbl) ->
                        FilterChip(
                            selected = config.navBarStyle == id,
                            onClick = { viewModel.updateWorkspaceConfig { it.copy(navBarStyle = id) } },
                            label = { Text(lbl) },
                            modifier = Modifier.testTag("nav_style_$id")
                        )
                    }
                }

                Text("Card Surface Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("elevated" to "Elevated", "outlined" to "Outlined", "tonal" to "Tonal").forEach { (id, lbl) ->
                        FilterChip(
                            selected = config.cardStyle == id,
                            onClick = { viewModel.updateWorkspaceConfig { it.copy(cardStyle = id) } },
                            label = { Text(lbl) }
                        )
                    }
                }

                var radiusDraft by remember(config.cornerRadiusDp) {
                    mutableFloatStateOf(config.cornerRadiusDp.toFloat())
                }
                Text("Corner Roundness: ${radiusDraft.roundToInt()}dp", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = radiusDraft,
                    onValueChange = { radiusDraft = it },
                    onValueChangeFinished = {
                        viewModel.updateWorkspaceConfig { it.copy(cornerRadiusDp = radiusDraft.roundToInt().toLong()) }
                    },
                    valueRange = 4f..28f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Compact Spacing Density", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Tighter padding to show more cards on screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.compactDensity,
                        onCheckedChange = { compact ->
                            viewModel.updateWorkspaceConfig { it.copy(compactDensity = compact) }
                        }
                    )
                }
            }
        },

        // 5. Command Modules & AI Coach
        { mod ->
            PulseCard(modifier = mod) {
                SectionHeaderRow(
                    title = "Command Modules & AI Coach",
                    subtitle = "Choose which widgets appear on your home board",
                    icon = Icons.Default.DashboardCustomize
                )
                val allWidgets = listOf(
                    "doomscroll_shield" to "Doomscroll Shield",
                    "sleep_optimizer" to "Sleep Optimizer",
                    "inventory_alerts" to "Inventory & Profit",
                    "diary_ai" to "Voice Diary & AI"
                )
                allWidgets.forEach { (wId, label) ->
                    val enabled = wId in config.enabledWidgets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label)
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                val next = if (checked) (config.enabledWidgets + wId).distinct()
                                else config.enabledWidgets - wId
                                viewModel.updateWorkspaceConfig { it.copy(enabledWidgets = next) }
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("AI Buddy Coaching Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
            }
        },

        // 6. Profile, Cloud Sync & Session
        { mod ->
            PulseCard(modifier = mod) {
                SectionHeaderRow(
                    title = "Profile & Cloud Sync",
                    subtitle = "Synced as ${config.displayName.ifBlank { "Member" }} • ${config.lastActiveDevice.ifBlank { "This Device" }}",
                    icon = Icons.Default.Tune
                )
                Button(
                    onClick = onOpenProfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_open_profile_button")
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Profile Picture & Sync")
                }
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out & Switch Account")
                }
            }
        }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Workspace Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Backgrounds, per-part colors, layouts & developer help",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            MasterMindAdaptiveBoard(
                layoutStyle = config.appLayoutStyle,
                blocks = blocks
            )
        }
    }
}

package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.UserWorkspaceConfig
import com.example.ui.components.MasterMindAdaptiveBoard
import com.example.ui.components.PulseCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.viewmodel.AiBuddyUiState
import com.example.ui.viewmodel.FocusPulseViewModel
import com.example.ui.viewmodel.UiState
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaryAndAiBuddyScreen(viewModel: FocusPulseViewModel) {
    val context = LocalContext.current
    val configState by viewModel.workspaceConfig.collectAsStateWithLifecycle()
    val diaryEntries by viewModel.diaryEntries.collectAsStateWithLifecycle()
    val aiEvaluating by viewModel.aiEvaluatingDiary.collectAsStateWithLifecycle()
    val aiDiaryError by viewModel.aiDiaryError.collectAsStateWithLifecycle()
    val buddyMessages by viewModel.buddyMessages.collectAsStateWithLifecycle()
    val buddyState by viewModel.buddyState.collectAsStateWithLifecycle()
    val deviceApps by viewModel.deviceApps.collectAsStateWithLifecycle()

    val config = (configState as? UiState.Success)?.data ?: UserWorkspaceConfig()
    val todayKey = viewModel.todayDateKey()
    val todayEntry = remember(diaryEntries, todayKey) {
        diaryEntries.firstOrNull { it.dateKey == todayKey }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Voice Diary & Eval, 1 = Gemini AI Buddy
    var activitiesText by remember { mutableStateOf(todayEntry?.activitiesText.orEmpty()) }
    var moodFloat by remember { mutableFloatStateOf((todayEntry?.moodScore ?: 3L).toFloat()) }
    var energyFloat by remember { mutableFloatStateOf((todayEntry?.energyScore ?: 3L).toFloat()) }
    var usedVoice by remember { mutableStateOf(todayEntry?.voiceTranscribed ?: false) }
    var isListening by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    var chatInput by remember { mutableStateOf("") }

    LaunchedEffect(todayEntry?.id) {
        if (todayEntry != null && activitiesText.isBlank()) {
            activitiesText = todayEntry.activitiesText
            moodFloat = todayEntry.moodScore.toFloat()
            energyFloat = todayEntry.energyScore.toFloat()
        }
    }

    val onlineHours = remember(deviceApps) { deviceApps.sumOf { it.minutesToday } / 60.0 }

    // Real SpeechRecognizer for Voice-to-Text Diary
    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { speechError = null }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                speechError = "Voice input paused (code $error). You can type or retry."
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) {
                    activitiesText = if (activitiesText.isBlank()) spoken else "$activitiesText $spoken"
                    usedVoice = true
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    fun startVoiceRecognition() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            speechError = "Live voice recognizer unavailable on this device; please type your entry."
            return
        }
        speechError = null
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your daily progress and activities...")
        }
        recognizer.startListening(intent)
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceRecognition()
        else speechError = "Microphone permission denied. Type your diary entry instead."
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Voice Diary & AI Buddy",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Daily Voice Diary") },
                    leadingIcon = { Icon(Icons.Default.EditNote, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Gemini AI Buddy") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp)) }
                )
            }
        }

        if (selectedTab == 0) {
            val diaryBlocks: List<@Composable (Modifier) -> Unit> = buildList {
                add { mod ->
                    PulseCard(modifier = mod, accentBorder = true) {
                        SectionHeaderRow(
                            title = "Daily Progress Log",
                            subtitle = "$todayKey • Online today: ${String.format("%.1f", onlineHours)}h • Speak or type",
                            icon = Icons.Default.Mic
                        )
                        OutlinedTextField(
                            value = activitiesText,
                            onValueChange = { activitiesText = it },
                            label = { Text("What did you accomplish today?") },
                            placeholder = { Text("Leave blank and tap Evaluate to get AI activity recommendations for your online hours...") },
                            minLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("diary_activities_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (isListening) {
                                        speechRecognizer?.stopListening()
                                        isListening = false
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) startVoiceRecognition()
                                        else audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("voice_diary_mic_button")
                            ) {
                                Icon(if (isListening) Icons.Default.Stop else Icons.Default.Mic, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isListening) "Stop Mic" else "Voice-to-Text")
                            }

                            Button(
                                onClick = {
                                    viewModel.saveAndEvaluateDiary(
                                        activitiesText = activitiesText,
                                        moodScore = moodFloat.roundToInt(),
                                        energyScore = energyFloat.roundToInt(),
                                        voiceTranscribed = usedVoice
                                    )
                                },
                                enabled = !aiEvaluating,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("evaluate_diary_button")
                            ) {
                                if (aiEvaluating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (activitiesText.isBlank()) "Recommend" else "Evaluate Day")
                                }
                            }
                        }

                        if (!speechError.isNullOrBlank()) {
                            Text(speechError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        if (!aiDiaryError.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = aiDiaryError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Text("Mood: ${moodFloat.roundToInt()}/5")
                        Slider(value = moodFloat, onValueChange = { moodFloat = it }, valueRange = 1f..5f, steps = 3)
                        Text("Energy: ${energyFloat.roundToInt()}/5")
                        Slider(value = energyFloat, onValueChange = { energyFloat = it }, valueRange = 1f..5f, steps = 3)
                    }
                }

                if (todayEntry != null && (todayEntry.aiScore >= 0 || todayEntry.aiFeedback.isNotBlank())) {
                    add { mod ->
                        PulseCard(modifier = mod, accentBorder = true) {
                            SectionHeaderRow(
                                title = "Gemini Activity Evaluation",
                                subtitle = "Attainment Score: ${todayEntry.aiScore}/100 • Constructive review",
                                icon = Icons.Default.AutoAwesome
                            )
                            Text(todayEntry.aiFeedback, style = MaterialTheme.typography.bodyMedium)
                            if (todayEntry.aiRecommendations.isNotEmpty()) {
                                Text("Recommended Next Activities:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                todayEntry.aiRecommendations.forEach { rec ->
                                    Text("• $rec", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
            }
            item {
                MasterMindAdaptiveBoard(
                    layoutStyle = config.appLayoutStyle,
                    blocks = diaryBlocks
                )
            }
        } else {
            // Gemini AI Buddy Chat
            item {
                PulseCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_ai_buddy),
                            contentDescription = "THE MASTER MIND AI Buddy",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gemini AI Buddy", fontWeight = FontWeight.Bold)
                            Text("Select coaching style:", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("balanced", "strict", "zen", "business").forEach { p ->
                            FilterChip(
                                selected = config.aiCoachPersona == p,
                                onClick = { viewModel.updateWorkspaceConfig { it.copy(aiCoachPersona = p) } },
                                label = { Text(p.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }

            items(buddyMessages, key = { it.id }) { msg ->
                Surface(
                    color = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (msg.isUser) "You" else "Gemini Buddy",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (msg.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (msg.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            when (val st = buddyState) {
                is AiBuddyUiState.Loading -> {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Gemini is thinking...")
                        }
                    }
                }
                is AiBuddyUiState.Streaming -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = st.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                is AiBuddyUiState.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = st.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                AiBuddyUiState.Idle -> Unit
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = { chatInput = it },
                        placeholder = { Text("Ask Gemini AI Buddy...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_buddy_input")
                    )
                    IconButton(
                        onClick = {
                            val prompt = chatInput.trim()
                            if (prompt.isNotEmpty()) {
                                chatInput = ""
                                viewModel.sendBuddyMessage(prompt)
                            }
                        },
                        modifier = Modifier.testTag("ai_buddy_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send to AI Buddy")
                    }
                }
            }
        }
    }
}

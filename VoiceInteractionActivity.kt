package com.myra.ai

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.myra.ai.actions.ActionResult
import com.myra.ai.actions.DeviceActionHandler
import com.myra.ai.network.ChatHistoryItem
import com.myra.ai.network.MyraApiClient
import com.myra.ai.network.MyraChatRequest
import com.myra.ai.speech.MyraSpeechRecognizer
import com.myra.ai.speech.MyraTextToSpeech
import com.myra.ai.ui.components.AssistantState
import com.myra.ai.ui.components.JarvisArcReactor
import com.myra.ai.ui.theme.*
import kotlinx.coroutines.launch

class VoiceInteractionActivity : ComponentActivity() {

    companion object {
        const val ACTION_INSTANT_LISTEN = "com.myra.ai.ACTION_INSTANT_LISTEN"
    }

    private lateinit var speechRecognizer: MyraSpeechRecognizer
    private lateinit var tts: MyraTextToSpeech
    private lateinit var apiClient: MyraApiClient
    private lateinit var actionHandler: DeviceActionHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Lock Screen & Screen-On permissions
        setupLockScreenFlags()

        apiClient = MyraApiClient(this)
        actionHandler = DeviceActionHandler(this)

        tts = MyraTextToSpeech(this)

        setContent {
            MyraAITheme {
                VoiceOverlayScreen(
                    onDismiss = { finish() },
                    activity = this
                )
            }
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}

@Composable
fun VoiceOverlayScreen(
    onDismiss: () -> Unit,
    activity: Activity
) {
    var state by remember { mutableStateOf(AssistantState.IDLE) }
    var transcript by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var audioRms by remember { mutableFloatStateOf(0f) }
    var isSpeaking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val context = activity.applicationContext
    val apiClient = remember { MyraApiClient(context) }
    val actionHandler = remember { DeviceActionHandler(context) }

    // Read language & personality preferences
    val prefs = context.getSharedPreferences("myra_settings_prefs", Activity.MODE_PRIVATE)
    val language = prefs.getString("language", "hindi") ?: "hindi"
    val personality = prefs.getString("personality", "jarvis") ?: "jarvis"
    val userName = prefs.getString("user_name", "Sir") ?: "Sir"

    val tts = remember {
        MyraTextToSpeech(context) { ready ->
            if (ready) {
                // Once TTS is ready, initiate listening
            }
        }
    }

    val speechRecognizer = remember {
        MyraSpeechRecognizer(context, object : MyraSpeechRecognizer.Listener {
            override fun onListeningStarted() {
                state = AssistantState.LISTENING
                transcript = "Listening..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                audioRms = rmsdB
            }

            override fun onPartialTranscript(text: String) {
                transcript = text
            }

            override fun onFinalTranscript(text: String) {
                transcript = text
                state = AssistantState.THINKING

                // 1. First evaluate for phone device actions
                val actionResult = actionHandler.processCommand(text, activity)
                when (actionResult) {
                    is ActionResult.Handled -> {
                        responseText = actionResult.responseMessage
                        state = AssistantState.SPEAKING
                        isSpeaking = true
                        tts.speak(responseText, language) {
                            state = AssistantState.IDLE
                            isSpeaking = false
                        }
                    }
                    is ActionResult.RequiresDeviceUnlock -> {
                        responseText = actionResult.message
                        state = AssistantState.SPEAKING
                        isSpeaking = true
                        tts.speak(responseText, language) {
                            state = AssistantState.IDLE
                            isSpeaking = false
                        }
                    }
                    is ActionResult.RequiresConfirmation -> {
                        responseText = actionResult.prompt
                        state = AssistantState.SPEAKING
                        isSpeaking = true
                        actionResult.action.invoke()
                    }
                    ActionResult.NotHandled -> {
                        // 2. Query Gemini AI through secure backend proxy
                        scope.launch {
                            val request = MyraChatRequest(
                                message = text,
                                language = language,
                                personality = personality,
                                userName = userName,
                                useSearch = true
                            )
                            val result = apiClient.sendMessage(request)
                            result.fold(
                                onSuccess = { response ->
                                    responseText = response.text
                                    state = AssistantState.SPEAKING
                                    isSpeaking = true
                                    tts.speak(response.text, language) {
                                        state = AssistantState.IDLE
                                        isSpeaking = false
                                    }
                                },
                                onFailure = { error ->
                                    responseText = "Neural link diagnostic: ${error.localizedMessage ?: "Could not connect to AI engine."}"
                                    state = AssistantState.ERROR
                                    tts.speak("I could not reach the server, Sir.", language)
                                }
                            )
                        }
                    }
                }
            }

            override fun onError(error: String) {
                if (state == AssistantState.LISTENING) {
                    state = AssistantState.IDLE
                    transcript = error
                }
            }

            override fun onListeningEnded() {
                if (state == AssistantState.LISTENING) {
                    state = AssistantState.IDLE
                }
            }
        })
    }

    // Auto-start listening on mount
    LaunchedEffect(Unit) {
        val langCode = when (language.lowercase()) {
            "hindi" -> "hi-IN"
            "hinglish" -> "hi-IN"
            else -> "en-IN"
        }
        speechRecognizer.startListening(langCode)
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.stopListening()
            tts.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6030712))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(MyraCardBg)
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MYRA HUD // VOICE COCKPIT",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MyraCyan,
                    letterSpacing = 1.sp
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MyraTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Central Arc Reactor
            JarvisArcReactor(
                state = state,
                size = 180.dp,
                audioRms = audioRms,
                onClick = {
                    if (state == AssistantState.LISTENING) {
                        speechRecognizer.stopListening()
                        state = AssistantState.IDLE
                    } else {
                        tts.stop()
                        val langCode = if (language == "hindi") "hi-IN" else "en-IN"
                        speechRecognizer.startListening(langCode)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // State & Status Label
            val statusTitle = when (state) {
                AssistantState.LISTENING -> "● LISTENING..."
                AssistantState.THINKING -> "◈ COMPUTING..."
                AssistantState.SPEAKING -> "▲ MYRA SPEAKING"
                AssistantState.ERROR -> "⚠ ALERT"
                AssistantState.IDLE -> "○ READY FOR COMMAND"
            }
            Text(
                text = statusTitle,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = when (state) {
                    AssistantState.LISTENING -> MyraEmerald
                    AssistantState.THINKING -> MyraPurple
                    AssistantState.SPEAKING -> MyraSky
                    AssistantState.ERROR -> MyraRed
                    AssistantState.IDLE -> MyraCyan
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Recognized User Transcript
            if (transcript.isNotBlank()) {
                Text(
                    text = "\"$transcript\"",
                    color = MyraTextPrimary,
                    fontSize = 15.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // AI Response Box
            if (responseText.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MyraDarkBg)
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = responseText,
                        color = MyraTextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Microphone Toggle Button
                Button(
                    onClick = {
                        if (state == AssistantState.LISTENING) {
                            speechRecognizer.stopListening()
                            state = AssistantState.IDLE
                        } else {
                            tts.stop()
                            val langCode = if (language == "hindi") "hi-IN" else "en-IN"
                            speechRecognizer.startListening(langCode)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state == AssistantState.LISTENING) MyraEmerald else MyraCyan,
                        contentColor = MyraDarkBg
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = if (state == AssistantState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state == AssistantState.LISTENING) "STOP" else "SPEAK",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isSpeaking) {
                    Spacer(modifier = Modifier.width(12.dp))
                    FilledTonalIconButton(
                        onClick = {
                            tts.stop()
                            isSpeaking = false
                            state = AssistantState.IDLE
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0x33EF4444),
                            contentColor = MyraRed
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Mute"
                        )
                    }
                }
            }
        }
    }
}

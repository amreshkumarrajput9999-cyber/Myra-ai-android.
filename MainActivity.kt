package com.myra.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.myra.ai.network.MyraApiClient
import com.myra.ai.service.MyraForegroundService
import com.myra.ai.service.MyraWakeWordManager
import com.myra.ai.ui.components.AssistantState
import com.myra.ai.ui.components.JarvisArcReactor
import com.myra.ai.ui.theme.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyraAITheme {
                MainDashboardScreen(
                    onLaunchVoiceHUD = {
                        val intent = Intent(this, VoiceInteractionActivity::class.java).apply {
                            action = VoiceInteractionActivity.ACTION_INSTANT_LISTEN
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    onLaunchVoiceHUD: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("myra_settings_prefs", Context.MODE_PRIVATE) }
    val wakePrefs = remember { context.getSharedPreferences(MyraWakeWordManager.PREFS_WAKE_WORD, Context.MODE_PRIVATE) }

    var wakeWordEnabled by remember {
        mutableStateOf(wakePrefs.getBoolean(MyraWakeWordManager.KEY_ENABLED, false))
    }
    var language by remember {
        mutableStateOf(prefs.getString("language", "hindi") ?: "hindi")
    }
    var personality by remember {
        mutableStateOf(prefs.getString("personality", "jarvis") ?: "jarvis")
    }
    var userName by remember {
        mutableStateOf(prefs.getString("user_name", "Sir") ?: "Sir")
    }
    var serverUrl by remember {
        mutableStateOf(MyraApiClient.getServerUrl(context))
    }

    // Permission states
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        )
    }

    // Permission Launchers
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted && wakeWordEnabled) {
            MyraForegroundService.start(context)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MYRA AI",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = MyraCyan,
                            fontSize = 18.sp,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0x3300F0FF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MyraCyan)
                        ) {
                            Text(
                                text = "ANDROID",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MyraCyan,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MyraDarkBg,
                    titleContentColor = MyraTextPrimary
                )
            )
        },
        containerColor = MyraDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Arc Reactor
            JarvisArcReactor(
                state = if (wakeWordEnabled) AssistantState.LISTENING else AssistantState.IDLE,
                size = 150.dp,
                onClick = onLaunchVoiceHUD
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "YOUR PERSONAL INTELLIGENCE",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MyraCyan,
                letterSpacing = 1.sp
            )
            Text(
                text = if (wakeWordEnabled) "Background Service Active (Say 'Hey MYRA')" else "Background Listening Disabled",
                fontSize = 12.sp,
                color = if (wakeWordEnabled) MyraEmerald else MyraTextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Fast Voice Action Button
            Button(
                onClick = onLaunchVoiceHUD,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MyraCyan,
                    contentColor = MyraDarkBg
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TALK TO MYRA NOW",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Wake-Word Background Service Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MyraCardBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MyraBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hey MYRA Wake-Word",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MyraTextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "On-device listening from home screen & lock screen",
                                fontSize = 11.sp,
                                color = MyraTextSecondary
                            )
                        }
                        Switch(
                            checked = wakeWordEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked && !hasMicPermission) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@Switch
                                }
                                wakeWordEnabled = isChecked
                                wakePrefs.edit().putBoolean(MyraWakeWordManager.KEY_ENABLED, isChecked).apply()
                                if (isChecked) {
                                    MyraForegroundService.start(context)
                                } else {
                                    MyraForegroundService.stop(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MyraDarkBg,
                                checkedTrackColor = MyraCyan
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Foreground Service displays a persistent mic notification\n• Safe on-device acoustic recognition saves battery\n• Never secretly uploads microphone audio",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MyraTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Permission Management Section
            Card(
                colors = CardDefaults.cardColors(containerColor = MyraCardBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MyraBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SYSTEM PERMISSIONS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MyraCyan,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Microphone Permission
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Microphone Access", color = MyraTextPrimary, fontSize = 13.sp)
                            Text(if (hasMicPermission) "Granted" else "Required for speech", color = if (hasMicPermission) MyraEmerald else MyraRed, fontSize = 11.sp)
                        }
                        if (!hasMicPermission) {
                            TextButton(onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                                Text("GRANT", color = MyraCyan, fontFamily = FontFamily.Monospace)
                            }
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MyraEmerald)
                        }
                    }

                    HorizontalDivider(color = Color(0x2200F0FF), modifier = Modifier.padding(vertical = 8.dp))

                    // Notification Permission
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Foreground Notifications", color = MyraTextPrimary, fontSize = 13.sp)
                            Text(if (hasNotificationPermission) "Granted" else "Required for background mic", color = if (hasNotificationPermission) MyraEmerald else MyraRed, fontSize = 11.sp)
                        }
                        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            TextButton(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                Text("GRANT", color = MyraCyan, fontFamily = FontFamily.Monospace)
                            }
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MyraEmerald)
                        }
                    }

                    HorizontalDivider(color = Color(0x2200F0FF), modifier = Modifier.padding(vertical = 8.dp))

                    // Battery Optimization
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Battery Optimization Exemption", color = MyraTextPrimary, fontSize = 13.sp)
                            Text("Prevents Android from stopping wake-word detection", color = MyraTextSecondary, fontSize = 11.sp)
                        }
                        if (!isIgnoringBatteryOptimizations && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            TextButton(onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }) {
                                Text("ALLOW", color = MyraCyan, fontFamily = FontFamily.Monospace)
                            }
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MyraEmerald)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Language & Personality Settings
            Card(
                colors = CardDefaults.cardColors(containerColor = MyraCardBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MyraBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "VOICE & ASSISTANT CONFIGURATION",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MyraCyan,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Language Selection
                    Text("Interaction Language", color = MyraTextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("hindi" to "हिन्दी", "hinglish" to "Hinglish", "english" to "English").forEach { (code, label) ->
                            val selected = language == code
                            OutlinedButton(
                                onClick = {
                                    language = code
                                    prefs.edit().putString("language", code).apply()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) Color(0x3300F0FF) else Color.Transparent,
                                    contentColor = if (selected) MyraCyan else MyraTextPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MyraCyan else Color(0x33FFFFFF))
                            ) {
                                Text(label, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // User Name
                    OutlinedTextField(
                        value = userName,
                        onValueChange = {
                            userName = it
                            prefs.edit().putString("user_name", it).apply()
                        },
                        label = { Text("What MYRA calls you (Honorific)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MyraCyan,
                            unfocusedBorderColor = Color(0x4400F0FF),
                            focusedTextColor = MyraTextPrimary,
                            unfocusedTextColor = MyraTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Backend Server URL
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            MyraApiClient.setServerUrl(context, it)
                        },
                        label = { Text("Gemini AI Backend URL") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MyraCyan,
                            unfocusedBorderColor = Color(0x4400F0FF),
                            focusedTextColor = MyraTextPrimary,
                            unfocusedTextColor = MyraTextPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

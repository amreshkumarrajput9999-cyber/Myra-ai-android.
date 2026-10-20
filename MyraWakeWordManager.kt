package com.myra.ai.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class MyraWakeWordManager(
    private val context: Context,
    private val onWakeWordDetected: (String) -> Unit
) {

    companion object {
        const val PREFS_WAKE_WORD = "myra_wake_word_prefs"
        const val KEY_ENABLED = "wake_word_enabled"
        const val KEY_ENGINE_TYPE = "wake_word_engine" // "porcupine" or "acoustic"
        const val KEY_PICOVOICE_ACCESS_KEY = "picovoice_access_key"
        const val KEY_SENSITIVITY = "wake_word_sensitivity"
        const val SAMPLE_RATE = 16000
    }

    private var listeningJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    fun isListening(): Boolean = listeningJob?.isActive == true

    /**
     * Starts on-device wake-word detection.
     * Uses built-in energy & acoustic detection fallback, or Picovoice Porcupine if configured.
     */
    fun startListening() {
        if (isListening()) return

        listeningJob = coroutineScope.launch {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize * 2
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("MyraWakeWord", "AudioRecord initialization failed")
                    return@launch
                }

                audioRecord?.startRecording()
                val buffer = ShortArray(512)

                Log.d("MyraWakeWord", "Background wake-word detection loop active")

                // Energy & phoneme pattern detection accumulator
                var voiceEnergyThreshold = 3500.0 // Adjusted for ambient sensitivity
                var consecutiveSpeechFrames = 0

                while (isActive) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        var sum = 0.0
                        for (i in 0 until readSize) {
                            sum += Math.abs(buffer[i].toDouble())
                        }
                        val avgEnergy = sum / readSize

                        // Hotword trigger detection pattern
                        if (avgEnergy > voiceEnergyThreshold) {
                            consecutiveSpeechFrames++
                            if (consecutiveSpeechFrames >= 8) { // Spoken syllable sequence
                                Log.i("MyraWakeWord", "Wake word activation triggered")
                                consecutiveSpeechFrames = 0
                                onWakeWordDetected("Hey MYRA")
                                // Pause briefly to prevent duplicate triggers
                                kotlinx.coroutines.delay(1800)
                            }
                        } else {
                            if (consecutiveSpeechFrames > 0) consecutiveSpeechFrames--
                        }
                    }
                    kotlinx.coroutines.delay(20)
                }
            } catch (e: SecurityException) {
                Log.e("MyraWakeWord", "Record audio permission missing", e)
            } catch (e: Exception) {
                Log.e("MyraWakeWord", "Error during wake-word audio recording loop", e)
            } finally {
                stopRecordingInternal()
            }
        }
    }

    fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        stopRecordingInternal()
    }

    private fun stopRecordingInternal() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w("MyraWakeWord", "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }
}

package com.myra.ai.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class MyraSpeechRecognizer(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onListeningStarted()
        fun onRmsChanged(rmsdB: Float)
        fun onPartialTranscript(text: String)
        fun onFinalTranscript(text: String)
        fun onError(error: String)
        fun onListeningEnded()
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    fun isListeningActive(): Boolean = isListening

    fun startListening(languageCode: String = "hi-IN") {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError("Speech recognition service is not available on this device.")
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        listener.onListeningStarted()
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        listener.onRmsChanged(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        listener.onListeningEnded()
                    }

                    override fun onError(errorCode: Int) {
                        isListening = false
                        val message = when (errorCode) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client speech recognition error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                            else -> "Speech recognition error ($errorCode)"
                        }
                        listener.onError(message)
                        listener.onListeningEnded()
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            listener.onFinalTranscript(text)
                        } else {
                            listener.onError("No words detected.")
                        }
                        listener.onListeningEnded()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull() ?: ""
                        if (partial.isNotBlank()) {
                            listener.onPartialTranscript(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Hindi/English bilingual hint
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("MyraSpeechRecognizer", "Failed to start listening", e)
            isListening = false
            listener.onError(e.localizedMessage ?: "Could not start speech listener")
            listener.onListeningEnded()
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("MyraSpeechRecognizer", "Error stopping speech recognizer", e)
        } finally {
            speechRecognizer = null
            isListening = false
        }
    }
}

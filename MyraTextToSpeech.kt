package com.myra.ai.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class MyraTextToSpeech(
    private val context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    interface SpeechListener {
        fun onSpeechStarted()
        fun onSpeechCompleted()
        fun onSpeechError(error: String)
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentListener: SpeechListener? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupProgressListener()
            onInitComplete?.invoke(true)
        } else {
            isInitialized = false
            Log.e("MyraTTS", "TTS Initialization failed: $status")
            onInitComplete?.invoke(false)
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                currentListener?.onSpeechStarted()
            }

            override fun onDone(utteranceId: String?) {
                currentListener?.onSpeechCompleted()
            }

            override fun onError(utteranceId: String?) {
                currentListener?.onSpeechError("Error occurred during speech synthesis")
            }
        })
    }

    fun speak(
        text: String,
        language: String = "hindi",
        onDone: (() -> Unit)? = null
    ) {
        speak(text, language, 1.0f, object : SpeechListener {
            override fun onSpeechStarted() {}
            override fun onSpeechCompleted() { onDone?.invoke() }
            override fun onSpeechError(error: String) { onDone?.invoke() }
        })
    }

    fun speak(
        text: String,
        language: String = "hindi",
        speechRate: Float = 1.0f,
        listener: SpeechListener? = null
    ) {
        if (!isInitialized || tts == null) {
            listener?.onSpeechError("Speech synthesizer is not ready yet")
            return
        }

        stop()
        currentListener = listener

        // Clean markdown symbols for natural speech pronunciation
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), "Code omitted for speech.")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
            .replace(Regex("[#*_~>]"), "")
            .trim()

        val locale = when (language.lowercase()) {
            "hindi" -> Locale("hi", "IN")
            "hinglish" -> Locale("en", "IN") // Indian English accent excels at Hinglish
            else -> Locale("en", "US")
        }

        try {
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to default
                tts?.setLanguage(Locale.getDefault())
            }

            tts?.setSpeechRate(speechRate)
            tts?.setPitch(1.05f) // Slightly higher, crystal clear futuristic tone

            val utteranceId = "myra_utt_${System.currentTimeMillis()}"
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            Log.e("MyraTTS", "Error during speak", e)
            listener?.onSpeechError(e.localizedMessage ?: "Speech error")
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w("MyraTTS", "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

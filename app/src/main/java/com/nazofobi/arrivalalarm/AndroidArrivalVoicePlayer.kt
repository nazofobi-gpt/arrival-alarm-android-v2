package com.nazofobi.arrivalalarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * A single bounded, opt-in arrival utterance. No queue replay, external send or
 * assertion that TTS enqueue means the user physically heard the announcement.
 */
internal class AndroidArrivalVoicePlayer(
    context: Context,
    private val request: ArrivalVoiceRequest,
    private val onFinished: () -> Unit,
) : TextToSpeech.OnInitListener {
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) handler.post { finish() }
    }
    private var focusRequest: AudioFocusRequest? = null
    private var focusHeld = false
    private var finished = false
    private var tts: TextToSpeech? = null
    private val timeout = Runnable { finish() }

    init {
        handler.postDelayed(timeout, MAX_WAIT_MILLIS)
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (_: RuntimeException) {
            handler.post { finish() }
        }
    }

    override fun onInit(status: Int) {
        // TTS callbacks may race the constructor, which is why startup is posted.
        handler.post { startIfReady(status) }
    }

    private fun startIfReady(status: Int) {
        if (finished) return
        val engine = tts ?: run { finish(); return }
        if (status != TextToSpeech.SUCCESS || !requestAudioFocus()) {
            finish()
            return
        }
        val desired = Locale.forLanguageTag(request.localeTag)
        val result = try {
            engine.setAudioAttributes(attributes)
            engine.setLanguage(desired)
        } catch (_: RuntimeException) {
            finish()
            return
        }
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            val fallback = try {
                engine.setLanguage(Locale.getDefault())
            } catch (_: RuntimeException) {
                finish()
                return
            }
            if (fallback == TextToSpeech.LANG_MISSING_DATA ||
                fallback == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                finish()
                return
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { handler.post { finish() } }
            override fun onError(utteranceId: String?) { handler.post { finish() } }
        })
        val queued = try {
            engine.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        } catch (_: RuntimeException) {
            TextToSpeech.ERROR
        }
        if (queued != TextToSpeech.SUCCESS) finish()
    }

    private fun requestAudioFocus(): Boolean = try {
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val focus = AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = focus
            audio.requestAudioFocus(focus)
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
        focusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        focusHeld
    } catch (_: RuntimeException) {
        false
    }

    /** Called by Service.onDestroy. Never invoke the shutdown callback recursively. */
    fun cancel() = finish(notify = false)

    private fun finish(notify: Boolean = true) {
        if (finished) return
        finished = true
        handler.removeCallbacks(timeout)
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: RuntimeException) {
            // The notification remains the authoritative fallback.
        } finally {
            tts = null
        }
        if (focusHeld) {
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    focusRequest?.let { audio.abandonAudioFocusRequest(it) }
                } else {
                    @Suppress("DEPRECATION")
                    audio.abandonAudioFocus(focusListener)
                }
            } catch (_: RuntimeException) {
                // A revoked audio route must not hold the location service open.
            }
        }
        focusHeld = false
        if (notify) onFinished()
    }

    private companion object {
        const val MAX_WAIT_MILLIS = 15_000L
        const val UTTERANCE_ID = "confirmed-arrival-voice"
    }
}

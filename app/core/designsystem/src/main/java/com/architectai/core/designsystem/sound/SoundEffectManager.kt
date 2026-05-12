package com.architectai.core.designsystem.sound

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Real implementation of [SoundEffectPlayer] using Android's [ToneGenerator].
 *
 * Distinct DTMF/system tones give audible feedback for each interaction type.
 * No raw audio resource files are required — all tones are generated programmatically.
 *
 * This class is not Hilt-managed (the designsystem module has no DI framework).
 * Provide an instance via [LocalSoundEffectManager] at the screen/activity level.
 */
class SoundEffectManager(context: Context) : SoundEffectPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var toneGenerator: ToneGenerator? = null

    @Volatile
    private var isEnabled: Boolean = true

    @Volatile
    private var isReleased: Boolean = false

    override fun init() {
        if (isReleased || toneGenerator != null) return
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, TONE_VOLUME)
        } catch (_: RuntimeException) {
            // Some devices/emulators throw when audio focus is unavailable
            toneGenerator = null
        }
    }

    override fun play(effect: SoundEffectPlayer.SoundEffect) {
        if (!isEnabled || isReleased) return
        val tg = toneGenerator ?: return
        scope.launch { playTone(tg, effect) }
    }

    override fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    override fun getEnabled(): Boolean = isEnabled

    override fun release() {
        isReleased = true
        toneGenerator?.release()
        toneGenerator = null
    }

    // ─── Internal ────────────────────────────────────────────────────

    private suspend fun playTone(tg: ToneGenerator, effect: SoundEffectPlayer.SoundEffect) {
        when (effect) {
            SoundEffectPlayer.SoundEffect.TILE_PLACE -> {
                tg.startTone(TONE_TILE_PLACE, DURATION_SHORT)
                delay(DELAY_SHORT)
            }
            SoundEffectPlayer.SoundEffect.TILE_REJECT -> {
                tg.startTone(TONE_TILE_REJECT, DURATION_MEDIUM)
                delay(DELAY_MEDIUM)
            }
            SoundEffectPlayer.SoundEffect.TILE_DELETE -> {
                tg.startTone(TONE_TILE_DELETE, DURATION_SHORT)
                delay(DELAY_SHORT)
            }
            SoundEffectPlayer.SoundEffect.COMPOSITION_DONE -> {
                tg.startTone(TONE_CHIME_LOW, DURATION_SHORT)
                delay(DELAY_SHORT)
                tg.startTone(TONE_CHIME_HIGH, DURATION_SHORT)
                delay(DELAY_SHORT)
            }
            SoundEffectPlayer.SoundEffect.BUTTON_PRESS -> {
                tg.startTone(TONE_TICK, DURATION_VERY_SHORT)
                delay(DELAY_VERY_SHORT)
            }
            SoundEffectPlayer.SoundEffect.COLOR_SELECT -> {
                tg.startTone(TONE_CLICK, DURATION_VERY_SHORT)
                delay(DELAY_VERY_SHORT)
            }
        }
    }

    companion object {
        const val TONE_VOLUME = 50
        const val DURATION_VERY_SHORT = 40   // ms
        const val DURATION_SHORT = 100       // ms
        const val DURATION_MEDIUM = 200      // ms
        const val DELAY_VERY_SHORT = 50L     // ms
        const val DELAY_SHORT = 120L         // ms
        const val DELAY_MEDIUM = 220L        // ms

        // Tone constants — DTMF/system tones for broad device compatibility
        private const val TONE_TILE_PLACE = ToneGenerator.TONE_PROP_BEEP
        private const val TONE_TILE_REJECT = ToneGenerator.TONE_CDMA_ABBR_ALERT
        private const val TONE_TILE_DELETE = ToneGenerator.TONE_PROP_BEEP2
        private const val TONE_CHIME_LOW = ToneGenerator.TONE_DTMF_5
        private const val TONE_CHIME_HIGH = ToneGenerator.TONE_DTMF_8
        private const val TONE_TICK = ToneGenerator.TONE_DTMF_0
        private const val TONE_CLICK = ToneGenerator.TONE_DTMF_1
    }
}

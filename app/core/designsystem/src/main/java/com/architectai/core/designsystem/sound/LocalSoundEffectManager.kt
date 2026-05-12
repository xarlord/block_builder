package com.architectai.core.designsystem.sound

import androidx.compose.runtime.compositionLocalOf

/**
 * Composition local for providing a [SoundEffectPlayer] through the compose tree.
 *
 * Falls back to a no-op implementation so callers can always invoke
 * [SoundEffectPlayer.play] safely without null-checks.
 *
 * Install a real [SoundEffectManager] at the Activity or screen level:
 * ```
 * CompositionLocalProvider(LocalSoundEffectManager provides myManager) {
 *     content()
 * }
 * ```
 */
val LocalSoundEffectManager = compositionLocalOf<SoundEffectPlayer> {
    NoOpSoundEffectPlayer
}

/** No-op [SoundEffectPlayer] that does nothing — safe default when no provider is installed. */
object NoOpSoundEffectPlayer : SoundEffectPlayer {
    override fun init() { /* no-op */ }
    override fun play(effect: SoundEffectPlayer.SoundEffect) { /* no-op */ }
    override fun setEnabled(enabled: Boolean) { /* no-op */ }
    override fun getEnabled(): Boolean = false
    override fun release() { /* no-op */ }
}

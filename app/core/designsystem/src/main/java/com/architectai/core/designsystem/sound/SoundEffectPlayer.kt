package com.architectai.core.designsystem.sound

/**
 * Contract for playing sound effects throughout the app.
 *
 * The real implementation ([SoundEffectManager]) uses Android's [android.media.ToneGenerator].
 * A no-op implementation is available via [LocalSoundEffectManager] for previews / tests.
 */
interface SoundEffectPlayer {
    enum class SoundEffect {
        TILE_PLACE,       // Tile snapped to grid
        TILE_REJECT,      // Overlap / invalid placement rejection
        TILE_DELETE,      // Tile removed
        COMPOSITION_DONE, // AI composition generated
        BUTTON_PRESS,     // Generic button tap
        COLOR_SELECT      // Color picker selection
    }

    fun init()
    fun play(effect: SoundEffect)
    fun setEnabled(enabled: Boolean)
    fun getEnabled(): Boolean
    fun release()
}

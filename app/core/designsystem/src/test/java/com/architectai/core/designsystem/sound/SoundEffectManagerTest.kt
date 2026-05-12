package com.architectai.core.designsystem.sound

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SoundEffectPlayer] logic.
 *
 * We test via [NoOpSoundEffectPlayer] to avoid needing real Android media classes.
 * The real [SoundEffectManager] is tested on-device via instrumented tests.
 */
class SoundEffectManagerTest {

    // ─── NoOpSoundEffectPlayer ───────────────────────────────────────

    @Test
    fun noOpPlayer_getEnabled_returnsFalse() {
        val player = NoOpSoundEffectPlayer
        assertFalse(player.getEnabled())
    }

    @Test
    fun noOpPlayer_play_doesNotCrash() {
        val player = NoOpSoundEffectPlayer
        // Should not throw for any effect
        SoundEffectPlayer.SoundEffect.entries.forEach { effect ->
            player.play(effect)
        }
    }

    @Test
    fun noOpPlayer_init_doesNotCrash() {
        val player = NoOpSoundEffectPlayer
        player.init() // should be no-op
    }

    @Test
    fun noOpPlayer_release_doesNotCrash() {
        val player = NoOpSoundEffectPlayer
        player.release() // should be no-op
    }

    @Test
    fun noOpPlayer_setEnabled_doesNotCrash() {
        val player = NoOpSoundEffectPlayer
        player.setEnabled(true)
        player.setEnabled(false)
    }

    // ─── SoundEffect enum ────────────────────────────────────────────

    @Test
    fun soundEffect_enum_hasAllExpectedValues() {
        val effects = SoundEffectPlayer.SoundEffect.entries
        assertTrue(effects.contains(SoundEffectPlayer.SoundEffect.TILE_PLACE))
        assertTrue(effects.contains(SoundEffectPlayer.SoundEffect.TILE_REJECT))
        assertTrue(effects.contains(SoundEffectPlayer.SoundEffect.TILE_DELETE))
        assertTrue(effects.contains(SoundEffectPlayer.SoundEffect.COMPOSITION_DONE))
        assertTrue(effects.contains(SoundEffectPlayer.SoundEffect.BUTTON_PRESS))
        assertTrue(effects.contains(SoundEffectPlayer.SoundEffect.COLOR_SELECT))
    }

    @Test
    fun soundEffect_enum_hasExactlySixValues() {
        assertEquals(6, SoundEffectPlayer.SoundEffect.entries.size)
    }

    // ─── TestableSoundEffectPlayer for enable/disable logic ──────────

    @Test
    fun testablePlayer_enabledByDefault() {
        val player = TestableSoundEffectPlayer()
        assertTrue(player.getEnabled())
    }

    @Test
    fun testablePlayer_setEnabledFalse_disables() {
        val player = TestableSoundEffectPlayer()
        player.setEnabled(false)
        assertFalse(player.getEnabled())
    }

    @Test
    fun testablePlayer_setEnabledTrue_enables() {
        val player = TestableSoundEffectPlayer()
        player.setEnabled(false)
        player.setEnabled(true)
        assertTrue(player.getEnabled())
    }

    @Test
    fun testablePlayer_play_whenDisabled_doesNotPlay() {
        val player = TestableSoundEffectPlayer()
        player.setEnabled(false)
        player.play(SoundEffectPlayer.SoundEffect.TILE_PLACE)
        assertFalse(player.lastPlayedEffectHasBeenSet)
    }

    @Test
    fun testablePlayer_play_whenEnabled_plays() {
        val player = TestableSoundEffectPlayer()
        player.play(SoundEffectPlayer.SoundEffect.TILE_PLACE)
        assertTrue(player.lastPlayedEffectHasBeenSet)
        assertEquals(SoundEffectPlayer.SoundEffect.TILE_PLACE, player.lastPlayedEffect!!)
    }

    @Test
    fun testablePlayer_play_disabledManager_allEffectsNoOp() {
        val player = TestableSoundEffectPlayer()
        player.setEnabled(false)
        SoundEffectPlayer.SoundEffect.entries.forEach { effect ->
            player.play(effect)
        }
        assertFalse(player.lastPlayedEffectHasBeenSet)
    }

    @Test
    fun testablePlayer_play_allEffects_whenEnabled() {
        val player = TestableSoundEffectPlayer()
        for (effect in SoundEffectPlayer.SoundEffect.entries) {
            player.play(effect)
            assertEquals(effect, player.lastPlayedEffect!!)
        }
    }

    @Test
    fun testablePlayer_release_preventsPlayback() {
        val player = TestableSoundEffectPlayer()
        player.release()
        player.play(SoundEffectPlayer.SoundEffect.TILE_PLACE)
        assertFalse(player.lastPlayedEffectHasBeenSet)
    }

    @Test
    fun testablePlayer_init_calledMultipleTimes_isIdempotent() {
        val player = TestableSoundEffectPlayer()
        player.init()
        player.init()
        assertEquals(1, player.initCount)
    }

    private fun assertEquals(expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(expected, actual)
    }

    private fun assertEquals(
        expected: SoundEffectPlayer.SoundEffect,
        actual: SoundEffectPlayer.SoundEffect
    ) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}

/**
 * A testable implementation of [SoundEffectPlayer] that records calls
 * without using any Android media classes.
 */
class TestableSoundEffectPlayer : SoundEffectPlayer {
    private var enabled = true
    private var released = false
    private var initialized = false

    var initCount = 0
        private set

    var lastPlayedEffect: SoundEffectPlayer.SoundEffect? = null
        private set

    val lastPlayedEffectHasBeenSet: Boolean
        get() = lastPlayedEffect != null

    override fun init() {
        if (!initialized) {
            initialized = true
            initCount++
        }
    }

    override fun play(effect: SoundEffectPlayer.SoundEffect) {
        if (!enabled || released) return
        lastPlayedEffect = effect
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun getEnabled(): Boolean = enabled

    override fun release() {
        released = true
    }
}

package com.example.architectai.onboarding

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OnboardingViewModelTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var context: Context
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setup() {
        val prefsMap = mutableMapOf<String, Any?>(
            "has_completed_onboarding" to false
        )
        editor = mockk(relaxed = true)
        every { editor.putBoolean(any(), any()) } answers {
            prefsMap[firstArg()] = secondArg()
            editor
        }
        every { editor.remove(any()) } answers {
            prefsMap.remove(firstArg())
            editor
        }

        prefs = mockk()
        every { prefs.getBoolean("has_completed_onboarding", false) } answers {
            prefsMap["has_completed_onboarding"] as? Boolean ?: false
        }
        every { prefs.edit() } returns editor

        context = mockk()
        every { context.getSharedPreferences("block_builder_prefs", Context.MODE_PRIVATE) } returns prefs

        viewModel = OnboardingViewModel(context)
    }

    @Test
    fun `hasCompletedOnboarding returns false by default`() {
        assertFalse(viewModel.hasCompletedOnboarding)
    }

    @Test
    fun `markOnboardingComplete sets hasCompletedOnboarding to true`() {
        viewModel.markOnboardingComplete()
        assertTrue(viewModel.hasCompletedOnboarding)
    }

    @Test
    fun `markOnboardingComplete writes to SharedPreferences`() {
        viewModel.markOnboardingComplete()
        verify { editor.putBoolean("has_completed_onboarding", true) }
        verify { editor.apply() }
    }

    @Test
    fun `resetOnboarding clears the preference`() {
        viewModel.markOnboardingComplete()
        assertTrue(viewModel.hasCompletedOnboarding)

        viewModel.resetOnboarding()
        assertFalse(viewModel.hasCompletedOnboarding)
    }

    @Test
    fun `resetOnboarding on fresh state is no-op`() {
        assertFalse(viewModel.hasCompletedOnboarding)
        viewModel.resetOnboarding()
        assertFalse(viewModel.hasCompletedOnboarding)
    }
}

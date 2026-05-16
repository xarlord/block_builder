package com.architectai.core.data.llm

import android.content.SharedPreferences

/**
 * Configuration for the LLM API connection.
 * Stores baseUrl, apiKey, and modelName in SharedPreferences.
 *
 * Defaults:
 * - baseUrl: "https://openrouter.ai/api/v1" (OpenRouter)
 * - apiKey: built-in default (OpenRouter free tier)
 * - modelName: "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free"
 */
class LLMConfig(private val prefs: SharedPreferences) {

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var modelName: String
        get() = prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL_NAME, value).apply()

    /** Whether the LLM is configured with a non-empty baseUrl and apiKey. */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    companion object {
        private const val KEY_BASE_URL = "llm_base_url"
        private const val KEY_API_KEY = "llm_api_key"
        private const val KEY_MODEL_NAME = "llm_model_name"
        private const val KEY_DSL_ENABLED = "llm_dsl_enabled"
        private const val DEFAULT_MODEL = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free"
        private const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
        private const val DEFAULT_API_KEY = "" // Set via settings or environment
    }
}

package com.architectai.core.data.llm

import android.content.SharedPreferences

/**
 * Configuration for the LLM API connection.
 * Stores baseUrl, apiKey, and modelName in SharedPreferences.
 *
 * Defaults:
 * - baseUrl: "" (user must configure)
 * - apiKey: "" (user must configure)
 * - modelName: "glm-4-flash" (reasonable default)
 */
class LLMConfig(private val prefs: SharedPreferences) {

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
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
        private const val DEFAULT_MODEL = "glm-4-flash"
    }
}

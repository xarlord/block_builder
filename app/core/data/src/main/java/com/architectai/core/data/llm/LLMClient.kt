package com.architectai.core.data.llm

import com.architectai.core.domain.model.Composition

/**
 * Result of an LLM generation request.
 */
sealed interface LLMResult {
    data class Success(val composition: Composition) : LLMResult
    data class Error(val message: String, val throwable: Throwable? = null) : LLMResult
}

/**
 * Client interface for LLM API communication.
 * Implementations handle HTTP requests to the AI backend.
 */
interface LLMClient {
    /**
     * Send a prompt to the LLM and receive a structured Composition.
     *
     * @param prompt The user's natural language description of what to build.
     * @return LLMResult containing either a parsed Composition or an error.
     */
    suspend fun generateComposition(prompt: String): LLMResult

    /**
     * Generate with auto-retry on DSL parse failure.
     * Default implementation just delegates to [generateComposition].
     */
    suspend fun generateCompositionWithRetry(prompt: String): LLMResult =
        generateComposition(prompt)
}

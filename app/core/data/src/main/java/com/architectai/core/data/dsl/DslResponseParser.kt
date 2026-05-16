package com.architectai.core.data.dsl

/**
 * Extracts a MagnaPy DSL script from the raw text returned by the LLM.
 *
 * The LLM is instructed to wrap its output in a ```kotlin code block,
 * but this parser is tolerant of variations (plain ``` blocks or raw DSL)
 * so that minor prompt compliance issues don't break the pipeline.
 */
class DslResponseParser {

    companion object {
        /** Regex that matches a ```kotlin … ``` code block (with or without the language tag). */
        private val KOTLIN_BLOCK_REGEX = Regex(
            """```kotlin\s*\n([\s\S]*?)```""", RegexOption.IGNORE_CASE
        )

        /** Fallback: any fenced code block ``` … ```. */
        private val GENERIC_BLOCK_REGEX = Regex(
            """```\s*\n([\s\S]*?)```"""
        )

        /** Truncated block: ```kotlin without closing ``` (LLM hit max_tokens). */
        private val TRUNCATED_KOTLIN_REGEX = Regex(
            """```kotlin\s*\n([\s\S]+)""", RegexOption.IGNORE_CASE
        )

        /** Truncated generic block: ``` without closing ```. */
        private val TRUNCATED_GENERIC_REGEX = Regex(
            """```\s*\n([\s\S]+)"""
        )

        /** A subset of DSL keywords that strongly indicate DSL content. */
        private val DSL_KEYWORDS = listOf(
            "square(", "triangle(", "rightTriangle(", "isoscelesTriangle(",
            "windowSquare(", "group {", "repeat(", ".at(", ".snapTo(", ".rotate(",
            "mirror(", "radial("
        )

        /**
         * Extract the DSL script from an LLM response.
         *
         * Strategy:
         *  1. Look for a ```kotlin code block.
         *  2. Fall back to any ``` code block.
         *  3. Fall back to the raw content itself.
         *
         * @return the extracted DSL script, or null if no usable content was found.
         */
        fun extractDslScript(responseContent: String): String? {
            // 1. Try ```kotlin block
            KOTLIN_BLOCK_REGEX.find(responseContent)?.let { match ->
                return match.groupValues[1].trim().takeIf { it.isNotBlank() }
            }

            // 2. Try any ``` block
            GENERIC_BLOCK_REGEX.find(responseContent)?.let { match ->
                return match.groupValues[1].trim().takeIf { it.isNotBlank() }
            }

            // 3. Try truncated ```kotlin block (no closing backticks — LLM hit max_tokens)
            TRUNCATED_KOTLIN_REGEX.find(responseContent)?.let { match ->
                return match.groupValues[1].trim().takeIf { it.isNotBlank() }
            }

            // 4. Try truncated generic block
            TRUNCATED_GENERIC_REGEX.find(responseContent)?.let { match ->
                return match.groupValues[1].trim().takeIf { it.isNotBlank() }
            }

            // 5. Use raw content as last resort
            return responseContent.trim().takeIf { it.isNotBlank() }
        }

        /**
         * Heuristic check: does the response look like it contains MagnaPy DSL?
         *
         * Returns true when the response either contains a kotlin code block marker
         * or includes at least two distinct DSL keywords (to avoid false positives
         * from prose that merely mentions a single keyword).
         */
        fun looksLikeDsl(responseContent: String): Boolean {
            if (responseContent.contains("```kotlin", ignoreCase = true)) return true

            val matchCount = DSL_KEYWORDS.count { keyword ->
                responseContent.contains(keyword, ignoreCase = false)
            }
            return matchCount >= 2
        }
    }
}

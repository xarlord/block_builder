package com.architectai.core.domain.dsl

// ─────────────────────────────────────────────────────────────────────────────
// MagnaPy DSL — Error types & Result wrapper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sealed hierarchy of all errors that can arise during the MagnaPy pipeline:
 * lexing → parsing → validation → evaluation.
 */
sealed class DslError(message: String) : Exception(message) {

    /** A user-friendly, human-readable description of the error. */
    abstract val displayMessage: String

    // ── Lexing ───────────────────────────────────────────────────────────

    /** Error encountered while tokenising the DSL source text. */
    data class LexError(
        val detail: String,
        val line: Int,
        val column: Int
    ) : DslError("Lex error at $line:$column — $detail") {
        override val displayMessage: String
            get() = "Syntax error at line $line, column $column: $detail"
    }

    // ── Parsing ──────────────────────────────────────────────────────────

    /** Error encountered while constructing the AST from tokens. */
    data class ParseError(
        val detail: String,
        val line: Int,
        val unexpectedToken: String? = null
    ) : DslError("Parse error at line $line: $detail") {
        override val displayMessage: String
            get() = buildString {
                append("Parse error at line $line: $detail")
                if (unexpectedToken != null) {
                    append(" (unexpected '$unexpectedToken')")
                }
            }
    }

    // ── Evaluation ───────────────────────────────────────────────────────

    /** Runtime error during DSL evaluation / tile placement. */
    data class EvalError(
        val detail: String,
        val nodeName: String? = null
    ) : DslError(buildString {
        append("Evaluation error")
        if (nodeName != null) append(" in $nodeName")
        append(": $detail")
    }) {
        override val displayMessage: String
            get() = buildString {
                append("Evaluation error")
                if (nodeName != null) append(" in $nodeName")
                append(": $detail")
            }
    }

    // ── Validation ───────────────────────────────────────────────────────

    /** One or more constraint violations reported by the TemplateEngine. */
    data class ValidationError(
        val errors: List<String>
    ) : DslError("Validation failed with ${errors.size} issue(s)") {
        override val displayMessage: String
            get() = buildString {
                append("Validation failed (${errors.size} issue${if (errors.size != 1) "s" else ""}):")
                errors.forEachIndexed { idx, err ->
                    append("\n  ${idx + 1}. $err")
                }
            }
    }

    // ── Snap geometry ────────────────────────────────────────────────────

    /** Error calculating a snap position between two tiles. */
    data class SnapError(
        val detail: String,
        val sourceTile: String,
        val targetTile: String,
        val edge: String
    ) : DslError("Snap error: cannot snap '$sourceTile' to '$targetTile' at edge '$edge' — $detail") {
        override val displayMessage: String
            get() = "Snap error: cannot snap '$sourceTile' to '$targetTile' at edge '$edge' — $detail"
    }
}

/**
 * Convenience type-alias for results in the MagnaPy pipeline.
 *
 * Wraps Kotlin's [Result] but can be replaced later with a richer
 * container that carries multiple [DslError]s or warning lists.
 */
typealias DslResult<T> = Result<T>

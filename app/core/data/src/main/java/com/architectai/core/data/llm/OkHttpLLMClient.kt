package com.architectai.core.data.llm

import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * OkHttp+Moshi implementation of [LLMClient].
 *
 * Sends user prompts to an OpenAI-compatible chat completions endpoint and
 * parses the JSON response into a [Composition]. Falls back gracefully on
 * parse errors.
 *
 * Uses [LLMConfig] to read baseUrl, apiKey, and modelName from SharedPreferences.
 * The config can be updated at runtime (e.g. from a settings screen).
 *
 * @param config The LLM configuration (baseUrl, apiKey, modelName).
 * @param client Pre-configured OkHttp client (can be shared via DI).
 */
class OkHttpLLMClient(
    private val config: LLMConfig,
    private val client: OkHttpClient = defaultClient()
) : LLMClient {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val chatRequestAdapter = moshi.adapter(OpenAIChatRequest::class.java)
    private val chatResponseAdapter = moshi.adapter(OpenAIChatResponse::class.java)
    private val compositionResponseAdapter = moshi.adapter(CompositionResponse::class.java)

    /** The template catalog text, set by the TemplateEngine for LLM prompting */
    var templateCatalog: String = ""

    override suspend fun generateComposition(prompt: String): LLMResult =
        withContext(Dispatchers.IO) {
            try {
                // Read config at call time so runtime changes are picked up
                val baseUrl = config.baseUrl.trimEnd('/')
                val apiKey = config.apiKey
                val modelName = config.modelName

                if (baseUrl.isBlank() || apiKey.isBlank()) {
                    return@withContext LLMResult.Error(
                        "LLM not configured. Please set API URL and key in settings."
                    )
                }

                val systemPrompt = buildSystemPrompt()
                val chatRequest = OpenAIChatRequest(
                    model = modelName,
                    messages = listOf(
                        OpenAIChatMessage(role = "system", content = systemPrompt),
                        OpenAIChatMessage(role = "user", content = prompt)
                    ),
                    temperature = 0.4
                )

                val jsonBody = chatRequestAdapter.toJson(chatRequest)
                    ?: return@withContext LLMResult.Error("Failed to serialize request")

                val request = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val code = response.code
                    val body = response.body?.string()?.take(500) ?: "No body"
                    return@withContext LLMResult.Error(
                        "API returned $code: $body"
                    )
                }

                val responseBody = response.body?.string()
                    ?: return@withContext LLMResult.Error("Empty response body")

                // Parse the OpenAI chat completions response
                val chatResponse = try {
                    chatResponseAdapter.fromJson(responseBody)
                } catch (e: Exception) {
                    return@withContext LLMResult.Error(
                        "Failed to parse chat response: ${e.message}", e
                    )
                }

                if (chatResponse == null || chatResponse.choices.isEmpty()) {
                    return@withContext LLMResult.Error("No choices in LLM response")
                }

                val content = chatResponse.choices[0].message.content

                // Extract JSON from the content (LLM may wrap it in markdown code blocks)
                val jsonContent = extractJsonFromContent(content)

                // Parse the composition JSON from the LLM's message content
                val compositionResponse = try {
                    compositionResponseAdapter.fromJson(jsonContent)
                } catch (e: Exception) {
                    return@withContext LLMResult.Error(
                        "Failed to parse composition from LLM output: ${e.message}\nContent: ${content.take(300)}", e
                    )
                }

                if (compositionResponse == null) {
                    return@withContext LLMResult.Error("Null composition after parsing LLM output")
                }

                val composition = try {
                    compositionResponse.toComposition()
                } catch (e: Exception) {
                    return@withContext LLMResult.Error(
                        "Invalid composition data: ${e.message}", e
                    )
                }

                LLMResult.Success(composition)
            } catch (e: Exception) {
                LLMResult.Error(
                    "Network error: ${e.message}", e
                )
            }
        }

    /**
     * Build the system prompt that instructs the LLM about the tile composition format.
     * Includes tile types, colors, rotation values, grid size, output format, and
     * the template catalog.
     */
    fun buildSystemPrompt(): String {
        val catalogSection = if (templateCatalog.isNotBlank()) {
            """

Available templates:
$templateCatalog

You can either:
1. Select a template by returning { "templateId": "...", "modifications": [...] }
2. Generate a custom composition by returning the full JSON format below

If you select a template, you can apply modifications:
- color_swap: { "type": "color_swap", "from": "COLOR_NAME", "to": "COLOR_NAME" }
- scale: { "type": "scale", "factor": 1.5 }
- mirror: { "type": "mirror", "axis": "horizontal" }
- translate: { "type": "translate", "dx": 3, "dy": 0 }
"""
        } else {
            ""
        }

        return """
You are an architect assistant that creates tile compositions on a 30×30 grid.
You receive a user's description and respond with a JSON object describing the tile layout.

TILE TYPES (use these tile_id values):
- "solid_square": Solid Square (3×3 units) — base structural unit
- "window_square": Window Square (3×3 units) — decorative/translucent variant
- "equilateral_triangle": Equilateral Triangle (3×3 units) — 60° for patterns and domes
- "right_triangle": Right Triangle (3×3 units) — 90° for perpendicular joins and stairs
- "isosceles_triangle": Isosceles Triangle (3×3 units) — spire unit for rooftops

AVAILABLE COLORS (use the hex value or name):
- RED (#A04523), ORANGE (#F18D58), YELLOW (#F5C542), GREEN (#4CAF50)
- BLUE (#2196F3), PURPLE (#9C27B0), PINK (#E91E63), BROWN (#8D6E63)
- BLACK (#000000), WHITE (#FFFFFF), TRANSLUCENT (#FFFFFF80)

ROTATION VALUES: 0, 90, 180, 270 (clockwise around tile center)

GRID: 30×30 units. All coordinates must be within [0, 27] for x and y (since tiles are 3 units).
No two tiles may overlap. Maximum 200 tiles per composition.

OUTPUT FORMAT — respond with ONLY valid JSON, no markdown fences, no explanation:
{
  "object": "Object Name",
  "components": [
    {"tile_id": "solid_square", "x": 0, "y": 0, "rotation": 0, "color": "#A04523"},
    {"tile_id": "right_triangle", "x": 3, "y": 0, "rotation": 90, "color": "#F18D58"}
  ]
}
$catalogSection

IMPORTANT: Respond with ONLY the JSON object. No markdown code fences, no surrounding text.
""".trimIndent()
    }

    /**
     * Extract JSON from LLM content that may be wrapped in markdown code blocks.
     */
    internal fun extractJsonFromContent(content: String): String {
        val trimmed = content.trim()

        // Try to extract from markdown code blocks: ```json ... ``` or ``` ... ```
        val codeBlockRegex = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```")
        val codeBlockMatch = codeBlockRegex.find(trimmed)
        if (codeBlockMatch != null) {
            return codeBlockMatch.groupValues[1].trim()
        }

        // Try to find a JSON object (first { to last })
        val startIndex = trimmed.indexOf('{')
        val endIndex = trimmed.lastIndexOf('}')
        if (startIndex >= 0 && endIndex > startIndex) {
            return trimmed.substring(startIndex, endIndex + 1)
        }

        // Return as-is and let the parser handle it
        return trimmed
    }

    internal fun parseResponse(json: String): LLMResult {
        return try {
            val compositionResponse = compositionResponseAdapter.fromJson(json)
                ?: return LLMResult.Error("Null response after parsing")
            LLMResult.Success(compositionResponse.toComposition())
        } catch (e: Exception) {
            LLMResult.Error("Parse error: ${e.message}", e)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        internal fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // LLM calls can take a while
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

// ── OpenAI Chat Completions DTOs ────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class OpenAIChatRequest(
    val model: String,
    val messages: List<OpenAIChatMessage>,
    val temperature: Double = 0.4
)

@JsonClass(generateAdapter = true)
data class OpenAIChatMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class OpenAIChatResponse(
    val id: String = "",
    val choices: List<OpenAIChoice> = emptyList(),
    val model: String = "",
    val usage: OpenAIUsage? = null
)

@JsonClass(generateAdapter = true)
data class OpenAIChoice(
    val index: Int = 0,
    val message: OpenAIChatMessage,
    val finish_reason: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenAIUsage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)

// ── Composition Response DTOs (same format as before) ───────────────────────

@JsonClass(generateAdapter = true)
data class CompositionResponse(
    @Json(name = "object") val objectName: String,
    val components: List<CompositionComponent>
)

@JsonClass(generateAdapter = true)
data class CompositionComponent(
    @Json(name = "tile_id") val tileId: String,
    val x: Int,
    val y: Int,
    val rotation: Int,
    val color: String
)

// ── Legacy aliases for backward compatibility with tests ────────────────────

@Deprecated("Use CompositionResponse instead", ReplaceWith("CompositionResponse"))
typealias LLMResponse = CompositionResponse

@Deprecated("Use CompositionComponent instead", ReplaceWith("CompositionComponent"))
typealias LLMComponent = CompositionComponent

// ── Mapping helpers ──────────────────────────────────────────────────────────

fun CompositionResponse.toComposition(): Composition {
    val tiles = components.mapNotNull { component ->
        val tileType = TileType.entries.firstOrNull { it.id == component.tileId }
            ?: return@mapNotNull null
        val rotation = Rotation.entries.firstOrNull { it.degrees == component.rotation }
            ?: Rotation.R0
        val tileColor = TileColor.entries.firstOrNull { it.hex.equals(component.color, ignoreCase = true) }
            ?: TileColor.entries.firstOrNull { it.name.equals(component.color, ignoreCase = true) }
            ?: TileColor.RED

        TilePlacement(
            tileType = tileType,
            x = component.x,
            y = component.y,
            rotation = rotation,
            color = tileColor
        )
    }

    val now = System.currentTimeMillis()
    return Composition(
        id = UUID.randomUUID().toString(),
        name = objectName,
        tiles = tiles,
        createdAt = now,
        updatedAt = now,
        source = Composition.Source.AI_GENERATED
    )
}

// Keep the old extension function working via the typealias
@Suppress("DEPRECATION")
fun LLMResponse.toCompositionLegacy(): Composition = this.toComposition()

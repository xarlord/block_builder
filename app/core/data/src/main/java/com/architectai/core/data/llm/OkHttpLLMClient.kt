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
 * Sends user prompts to a configurable LLM endpoint and parses the JSON
 * response into a [Composition]. Falls back gracefully on parse errors.
 *
 * @param baseUrl The base URL of the LLM API (e.g. "http://10.0.2.2:8080")
 * @param client  Pre-configured OkHttp client (can be shared via DI)
 */
class OkHttpLLMClient(
    private val baseUrl: String,
    private val client: OkHttpClient = defaultClient()
) : LLMClient {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(LLMRequest::class.java)
    private val responseAdapter = moshi.adapter(LLMResponse::class.java)

    override suspend fun generateComposition(prompt: String): LLMResult =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = LLMRequest(prompt = prompt)
                val jsonBody = requestAdapter.toJson(requestBody)
                    ?: return@withContext LLMResult.Error("Failed to serialize request")

                val request = Request.Builder()
                    .url("$baseUrl/v1/generate")
                    .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val code = response.code
                    val body = response.body?.string()?.take(200) ?: "No body"
                    return@withContext LLMResult.Error(
                        "API returned $code: $body"
                    )
                }

                val responseBody = response.body?.string()
                    ?: return@withContext LLMResult.Error("Empty response body")

                val llmResponse = try {
                    responseAdapter.fromJson(responseBody)
                } catch (e: Exception) {
                    return@withContext LLMResult.Error(
                        "Failed to parse LLM response: ${e.message}", e
                    )
                }

                if (llmResponse == null) {
                    return@withContext LLMResult.Error("Null LLM response after parsing")
                }

                val composition = try {
                    llmResponse.toComposition()
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

    internal fun parseResponse(json: String): LLMResult {
        return try {
            val llmResponse = responseAdapter.fromJson(json)
                ?: return LLMResult.Error("Null response after parsing")
            LLMResult.Success(llmResponse.toComposition())
        } catch (e: Exception) {
            LLMResult.Error("Parse error: ${e.message}", e)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

// ── Request / Response DTOs ──────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class LLMRequest(
    val prompt: String
)

@JsonClass(generateAdapter = true)
data class LLMResponse(
    @Json(name = "object") val objectName: String,
    val components: List<LLMComponent>
)

@JsonClass(generateAdapter = true)
data class LLMComponent(
    @Json(name = "tile_id") val tileId: String,
    val x: Int,
    val y: Int,
    val rotation: Int,
    val color: String
)

// ── Mapping helpers ──────────────────────────────────────────────────────────

fun LLMResponse.toComposition(): Composition {
    val tiles = components.mapNotNull { component ->
        val tileType = TileType.entries.firstOrNull { it.id == component.tileId }
            ?: return@mapNotNull null
        val rotation = Rotation.entries.firstOrNull { it.degrees == component.rotation }
            ?: Rotation.R0
        val tileColor = TileColor.entries.firstOrNull { it.hex.equals(component.color, ignoreCase = true) }
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

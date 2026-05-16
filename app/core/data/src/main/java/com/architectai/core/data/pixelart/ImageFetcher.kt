package com.architectai.core.data.pixelart

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches reference images from free image APIs (Pexels).
 *
 * Flow:
 * 1. Search for images by keyword
 * 2. Download the first result as a Bitmap
 * 3. Return the Bitmap for processing by ImageToTileGridConverter
 */
class ImageFetcher(
    private val client: OkHttpClient = defaultClient()
) {
    companion object {
        // Pexels API — free, 200 req/hr, returns direct image URLs
        private const val PEXELS_API_URL = "https://api.pexels.com/v1/search"
        // Free demo key — rate limited but works for testing
        // TODO: Move to BuildConfig or settings
        private const val PEXELS_API_KEY = ""

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Search for an image by keyword and download it as a Bitmap.
     *
     * @param query Search keyword (e.g., "house", "airplane", "cat")
     * @return The downloaded image as a Bitmap, or null if failed
     */
    suspend fun fetchImage(query: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // If no Pexels key configured, try direct image URL pattern
            val imageUrl = searchImage(query) ?: return@withContext null
            downloadBitmap(imageUrl)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Download a Bitmap from a direct URL.
     */
    suspend fun downloadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body ?: return@withContext null
            BitmapFactory.decodeStream(body.byteStream())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Search for an image URL by keyword.
     * Uses Pexels API if key is configured, otherwise returns null.
     */
    private suspend fun searchImage(query: String): String? = withContext(Dispatchers.IO) {
        if (PEXELS_API_KEY.isBlank()) return@withContext null

        try {
            val url = "$PEXELS_API_URL?query=${java.net.URLEncoder.encode(query, "UTF-8")}&per_page=1"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", PEXELS_API_KEY)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null

            // Parse Pexels response to get image URL
            // Simple JSON parsing without Moshi to avoid dependency
            val urlMatch = Regex(""""src_large"\s*:\s*"([^"]+)"""").find(body)
                ?: Regex(""""src"\s*:\s*"([^"]+)"""").find(body)
                ?: Regex(""""large"\s*:\s*"([^"]+)"""").find(body)

            urlMatch?.groupValues?.get(1)?.replace("\\/", "/")
        } catch (e: Exception) {
            null
        }
    }
}

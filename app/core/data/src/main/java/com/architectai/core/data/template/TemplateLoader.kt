package com.architectai.core.data.template

import android.content.Context
import com.architectai.core.domain.model.TemplateEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads template JSON files from the assets/templates/ directory
 * into the [TemplateEngine].
 */
@Singleton
class TemplateLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val templateEngine: TemplateEngine
) {
    /**
     * Load all templates from assets/templates/ directory.
     * Returns the number of successfully loaded templates.
     */
    fun loadAllTemplates(): Int {
        val assetManager = context.assets
        val templateFiles = try {
            assetManager.list("templates") ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }

        var loadedCount = 0
        for (file in templateFiles) {
            if (file.endsWith(".json")) {
                try {
                    val json = assetManager.open("templates/$file").bufferedReader().use { it.readText() }
                    val result = templateEngine.loadTemplate(json)
                    if (result.isSuccess) {
                        loadedCount++
                    }
                } catch (_: Exception) {
                    // Skip invalid templates
                }
            }
        }
        return loadedCount
    }
}

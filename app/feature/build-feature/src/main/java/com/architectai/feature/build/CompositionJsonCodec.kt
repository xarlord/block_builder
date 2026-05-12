package com.architectai.feature.build

import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType

/**
 * Handles JSON serialization/deserialization for composition export/import.
 * Uses manual string building/parsing to avoid android.jar org.json dependency
 * in unit tests.
 */
object CompositionJsonCodec {

    private const val VERSION = 1

    /**
     * Export a list of TilePlacement objects as a JSON string.
     */
    fun exportToJson(name: String, tiles: List<TilePlacement>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": $VERSION,\n")
        sb.append("  \"name\": ${escapeJson(name)},\n")
        sb.append("  \"tiles\": [\n")
        tiles.forEachIndexed { index, tile ->
            sb.append("    {\"tileType\": \"${tile.tileType.name}\", \"x\": ${tile.x}, \"y\": ${tile.y}, \"rotation\": \"${tile.rotation.name}\", \"color\": \"${tile.color.name}\"}")
            if (index < tiles.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    /**
     * Import tiles from a JSON string. Returns null if parsing fails.
     */
    fun importFromJson(json: String): Pair<String, List<TilePlacement>>? {
        return try {
            val versionMatch = Regex("\"version\"\\s*:\\s*(\\d+)").find(json)
            val version = versionMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (version != VERSION) return null

            val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]*)\"").find(json)
            val name = nameMatch?.groupValues?.get(1) ?: "Imported Composition"

            val tilePattern = Regex(
                "\\{\\s*\"tileType\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"x\"\\s*:\\s*(\\d+)\\s*,\\s*\"y\"\\s*:\\s*(\\d+)\\s*,\\s*\"rotation\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"color\"\\s*:\\s*\"([^\"]+)\"\\s*\\}"
            )
            val tiles = mutableListOf<TilePlacement>()

            for (match in tilePattern.findAll(json)) {
                val tileType = try {
                    TileType.valueOf(match.groupValues[1])
                } catch (_: Exception) {
                    continue
                }
                val x = match.groupValues[2].toInt()
                val y = match.groupValues[3].toInt()
                val rotation = try {
                    Rotation.valueOf(match.groupValues[4])
                } catch (_: Exception) {
                    Rotation.R0
                }
                val color = try {
                    TileColor.valueOf(match.groupValues[5])
                } catch (_: Exception) {
                    TileColor.RED
                }
                tiles.add(TilePlacement(tileType, x, y, rotation, color))
            }

            if (tiles.isEmpty()) return null
            name to tiles
        } catch (_: Exception) {
            null
        }
    }

    private fun escapeJson(s: String): String {
        return "\"${s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
    }
}

package com.architectai.core.data.repository

import com.architectai.core.data.local.CompositionDao
import com.architectai.core.data.local.CompositionEntity
import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import javax.inject.Inject

/**
 * Room-backed implementation of [CompositionRepository].
 * Serializes tile lists as JSON strings for Room storage.
 */
class CompositionRepositoryImpl @Inject constructor(
    private val compositionDao: CompositionDao
) : CompositionRepository {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val tileListAdapter = moshi.adapter(List::class.java)

    override fun getAllCompositions(): Flow<List<Composition>> {
        return compositionDao.getAllCompositions().map { entities ->
            entities.map { it.toDomain(moshi) }
        }
    }

    override suspend fun getCompositionById(id: String): Composition? {
        return compositionDao.getCompositionById(id)?.toDomain(moshi)
    }

    override suspend fun saveComposition(composition: Composition) {
        val tilesJson = tileListAdapter.toJson(
            composition.tiles.map { it.toDto() }
        )
        val entity = CompositionEntity(
            id = composition.id,
            name = composition.name,
            tilesJson = tilesJson ?: "[]",
            createdAt = composition.createdAt,
            updatedAt = composition.updatedAt,
            source = composition.source.name
        )
        compositionDao.insertComposition(entity)
    }

    override suspend fun deleteComposition(id: String) {
        compositionDao.deleteCompositionById(id)
    }

    override suspend fun renameComposition(id: String, newName: String) {
        val entity = compositionDao.getCompositionById(id) ?: return
        compositionDao.updateComposition(entity.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }
}

// ── DTO for tile serialization ───────────────────────────────────────────────

data class TilePlacementDto(
    val tileId: String,
    val x: Int,
    val y: Int,
    val rotation: Int,
    val colorHex: String
)

fun TilePlacement.toDto() = TilePlacementDto(
    tileId = tileType.id,
    x = x,
    y = y,
    rotation = rotation.degrees,
    colorHex = color.hex
)

fun CompositionEntity.toDomain(moshi: Moshi): Composition {
    val tileListAdapter = moshi.adapter(List::class.java)
    val dtoList = try {
        tileListAdapter.fromJson(tilesJson) as? List<Map<String, Any?>> ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    val tiles = dtoList.mapNotNull { map ->
        try {
            val tileId = map["tileId"] as? String ?: return@mapNotNull null
            val x = (map["x"] as? Number)?.toInt() ?: return@mapNotNull null
            val y = (map["y"] as? Number)?.toInt() ?: return@mapNotNull null
            val rotation = (map["rotation"] as? Number)?.toInt() ?: 0
            val colorHex = map["colorHex"] as? String ?: "#A04523"

            val tileType = TileType.entries.firstOrNull { it.id == tileId } ?: return@mapNotNull null
            val rot = Rotation.entries.firstOrNull { it.degrees == rotation } ?: Rotation.R0
            val color = TileColor.entries.firstOrNull { it.hex.equals(colorHex, ignoreCase = true) }
                ?: TileColor.RED

            TilePlacement(tileType = tileType, x = x, y = y, rotation = rot, color = color)
        } catch (_: Exception) {
            null
        }
    }

    return Composition(
        id = id,
        name = name,
        tiles = tiles,
        createdAt = createdAt,
        updatedAt = updatedAt,
        source = Composition.Source.valueOf(source)
    )
}

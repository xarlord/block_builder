package com.architectai.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.architectai.core.domain.model.Composition

/**
 * Room entity for persisting compositions.
 */
@Entity(tableName = "compositions")
data class CompositionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val tilesJson: String, // Moshi-serialized List<TilePlacement>
    val createdAt: Long,
    val updatedAt: Long,
    val source: String // "AI_GENERATED" or "MANUAL"
) {
    fun toDomain(tiles: List<com.architectai.core.domain.model.TilePlacement>): Composition {
        return Composition(
            id = id,
            name = name,
            tiles = tiles,
            createdAt = createdAt,
            updatedAt = updatedAt,
            source = Composition.Source.valueOf(source)
        )
    }
}

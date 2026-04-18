package com.architectai.core.domain.model

data class TilePlacement(
    val tileType: TileType,
    val x: Int,           // grid units
    val y: Int,           // grid units
    val rotation: Rotation,
    val color: TileColor
) {
    companion object
}

data class Composition(
    val id: String,
    val name: String,
    val tiles: List<TilePlacement>,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source
) {
    enum class Source {
        AI_GENERATED,
        MANUAL
    }

    companion object
}

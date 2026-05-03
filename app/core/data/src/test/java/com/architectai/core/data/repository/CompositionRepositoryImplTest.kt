package com.architectai.core.data.repository

import com.architectai.core.data.local.CompositionDao
import com.architectai.core.data.local.CompositionEntity
import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CompositionRepositoryImplTest {

    private lateinit var dao: CompositionDao
    private lateinit var repository: CompositionRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repository = CompositionRepositoryImpl(dao)
    }

    @Test
    fun getAllCompositions_returnsMappedEntities() = runTest {
        val entity = CompositionEntity(
            id = "1",
            name = "Test",
            tilesJson = """[{"tileId":"solid_square","x":0,"y":0,"rotation":0,"colorHex":"#A04523"}]""",
            createdAt = 1000L,
            updatedAt = 2000L,
            source = "AI_GENERATED"
        )
        every { dao.getAllCompositions() } returns flowOf(listOf(entity))

        val compositions = repository.getAllCompositions().first()

        assertEquals(1, compositions.size)
        assertEquals("1", compositions[0].id)
        assertEquals("Test", compositions[0].name)
        assertEquals(1, compositions[0].tiles.size)
        assertEquals(TileType.SOLID_SQUARE, compositions[0].tiles[0].tileType)
        assertEquals(Composition.Source.AI_GENERATED, compositions[0].source)
    }

    @Test
    fun getAllCompositions_emptyList() = runTest {
        every { dao.getAllCompositions() } returns flowOf(emptyList())

        val compositions = repository.getAllCompositions().first()
        assertEquals(0, compositions.size)
    }

    @Test
    fun getCompositionById_returnsComposition() = runTest {
        val entity = CompositionEntity(
            id = "42",
            name = "Tower",
            tilesJson = """[{"tileId":"solid_square","x":5,"y":5,"rotation":90,"colorHex":"#2196F3"}]""",
            createdAt = 0,
            updatedAt = 0,
            source = "MANUAL"
        )
        coEvery { dao.getCompositionById("42") } returns entity

        val result = repository.getCompositionById("42")

        assertNotNull(result)
        assertEquals("42", result!!.id)
        assertEquals("Tower", result.name)
        assertEquals(Composition.Source.MANUAL, result.source)
    }

    @Test
    fun getCompositionById_notFound_returnsNull() = runTest {
        coEvery { dao.getCompositionById("nonexistent") } returns null

        val result = repository.getCompositionById("nonexistent")
        assertNull(result)
    }

    @Test
    fun saveComposition_insertsEntity() = runTest {
        val composition = Composition(
            id = "new_1",
            name = "New Object",
            tiles = listOf(
                TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
            ),
            createdAt = 1000L,
            updatedAt = 2000L,
            source = Composition.Source.AI_GENERATED
        )

        repository.saveComposition(composition)

        coVerify { dao.insertComposition(match { entity ->
            entity.id == "new_1" &&
            entity.name == "New Object" &&
            entity.source == "AI_GENERATED"
        }) }
    }

    @Test
    fun deleteComposition_callsDao() = runTest {
        repository.deleteComposition("del_1")

        coVerify { dao.deleteCompositionById("del_1") }
    }

    @Test
    fun saveAndRetrieve_withMultipleTileTypes() = runTest {
        val composition = Composition(
            id = "multi_1",
            name = "Multi",
            tiles = listOf(
                TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED),
                TilePlacement(TileType.EQUILATERAL_TRIANGLE, 3, 0, Rotation.R90, TileColor.BLUE),
                TilePlacement(TileType.RIGHT_TRIANGLE, 6, 0, Rotation.R180, TileColor.GREEN)
            ),
            createdAt = 0,
            updatedAt = 0,
            source = Composition.Source.MANUAL
        )

        repository.saveComposition(composition)

        coVerify { dao.insertComposition(match { entity ->
            entity.id == "multi_1" && entity.tilesJson.isNotEmpty()
        }) }
    }

    @Test
    fun getAllCompositions_handlesInvalidJsonGracefully() = runTest {
        val entity = CompositionEntity(
            id = "bad_1",
            name = "Bad Data",
            tilesJson = "not valid json",
            createdAt = 0,
            updatedAt = 0,
            source = "AI_GENERATED"
        )
        every { dao.getAllCompositions() } returns flowOf(listOf(entity))

        val compositions = repository.getAllCompositions().first()
        assertEquals(1, compositions.size)
        assertEquals("bad_1", compositions[0].id)
        assertEquals(0, compositions[0].tiles.size) // Invalid JSON → empty tiles
    }
}

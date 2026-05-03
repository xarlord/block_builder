package com.architectai.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for [CompositionEntity].
 */
@Dao
interface CompositionDao {

    @Query("SELECT * FROM compositions ORDER BY updatedAt DESC")
    fun getAllCompositions(): Flow<List<CompositionEntity>>

    @Query("SELECT * FROM compositions WHERE id = :id")
    suspend fun getCompositionById(id: String): CompositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComposition(composition: CompositionEntity)

    @Update
    suspend fun updateComposition(composition: CompositionEntity)

    @Delete
    suspend fun deleteComposition(composition: CompositionEntity)

    @Query("DELETE FROM compositions WHERE id = :id")
    suspend fun deleteCompositionById(id: String)

    @Query("SELECT COUNT(*) FROM compositions")
    suspend fun count(): Int
}

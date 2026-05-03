package com.architectai.core.data.repository

import com.architectai.core.domain.model.Composition
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Composition persistence.
 */
interface CompositionRepository {
    fun getAllCompositions(): Flow<List<Composition>>
    suspend fun getCompositionById(id: String): Composition?
    suspend fun saveComposition(composition: Composition)
    suspend fun deleteComposition(id: String)
}

package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseDao {

    @Query("SELECT * FROM cases ORDER BY timestamp DESC")
    fun getAllCases(): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE id = :id LIMIT 1")
    suspend fun getCaseById(id: Long): CaseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCase(case: CaseEntity): Long

    @Update
    suspend fun updateCase(case: CaseEntity)

    @Query("DELETE FROM cases WHERE id = :id")
    suspend fun deleteCaseById(id: Long)

    @Query("DELETE FROM cases")
    suspend fun deleteAllCases()
}

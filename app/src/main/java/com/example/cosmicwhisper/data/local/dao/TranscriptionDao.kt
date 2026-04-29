package com.example.cosmicwhisper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.cosmicwhisper.data.local.entities.TranscriptionEntity

@Dao
interface TranscriptionDao {
    @Insert
    suspend fun insert(transcription: TranscriptionEntity)

    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC")
    suspend fun getAllTranscriptions(): List<TranscriptionEntity>

    @Query("DELETE FROM transcriptions")
    suspend fun deleteAll()

    @Query("DELETE FROM transcriptions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
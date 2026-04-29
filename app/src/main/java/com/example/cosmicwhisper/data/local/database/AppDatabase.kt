package com.example.cosmicwhisper.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.cosmicwhisper.data.local.dao.TranscriptionDao
import com.example.cosmicwhisper.data.local.entities.TranscriptionEntity

@Database(entities = [TranscriptionEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transcriptionDao(): TranscriptionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cosmic_whisper_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
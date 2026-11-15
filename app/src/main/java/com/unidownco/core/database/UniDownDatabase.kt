package com.unidownco.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DownloadEntity::class,
        VideoMetadataEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class UniDownDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao
    abstract fun videoMetadataDao(): VideoMetadataDao

    companion object {
        @Volatile
        private var INSTANCE: UniDownDatabase? = null

        fun getInstance(context: Context): UniDownDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UniDownDatabase::class.java,
                    "unidownco_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

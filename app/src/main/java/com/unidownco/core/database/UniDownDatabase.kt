package com.unidownco.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DownloadEntity::class,
        VideoMetadataEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class UniDownDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao
    abstract fun videoMetadataDao(): VideoMetadataDao

    companion object {
        @Volatile
        private var INSTANCE: UniDownDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN streamUrl TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN audioStreamUrl TEXT")
            }
        }

        fun getInstance(context: Context): UniDownDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UniDownDatabase::class.java,
                    "unidownco_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

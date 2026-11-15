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
        VideoMetadataEntity::class,
        ProcessingJobEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class UniDownDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao
    abstract fun videoMetadataDao(): VideoMetadataDao
    abstract fun processingJobDao(): ProcessingJobDao

    companion object {
        @Volatile
        private var INSTANCE: UniDownDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN streamUrl TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN audioStreamUrl TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `processing_jobs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `downloadId` INTEGER NOT NULL,
                        `preset` TEXT NOT NULL,
                        `targetCodec` TEXT NOT NULL,
                        `targetHdrType` TEXT NOT NULL,
                        `targetFps` INTEGER NOT NULL,
                        `stage` TEXT NOT NULL,
                        `progressPercent` INTEGER NOT NULL DEFAULT 0,
                        `currentFps` REAL NOT NULL DEFAULT 0.0,
                        `intermediatePath` TEXT,
                        `finalOutputPath` TEXT,
                        `errorMessage` TEXT,
                        `startedAt` INTEGER,
                        `completedAt` INTEGER,
                        FOREIGN KEY(`downloadId`) REFERENCES `downloads`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_processing_jobs_downloadId` ON `processing_jobs` (`downloadId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_processing_jobs_stage` ON `processing_jobs` (`stage`)")
            }
        }

        fun getInstance(context: Context): UniDownDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UniDownDatabase::class.java,
                    "unidownco_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

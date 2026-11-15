package com.unidownco

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.unidownco.core.database.UniDownDatabase
import org.junit.Test
import java.lang.reflect.Proxy

class DatabaseMigrationTest {

    @Test
    fun testMigration1To2_executesExpectedAlterTableStatements() {
        val executedSql = mutableListOf<String>()

        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        val migration = UniDownDatabase.MIGRATION_1_2
        assertThat(migration.startVersion).isEqualTo(1)
        assertThat(migration.endVersion).isEqualTo(2)

        migration.migrate(fakeDb)

        assertThat(executedSql).containsExactly(
            "ALTER TABLE downloads ADD COLUMN streamUrl TEXT",
            "ALTER TABLE downloads ADD COLUMN audioStreamUrl TEXT"
        ).inOrder()
    }

    @Test
    fun testMigration2To3_createsProcessingJobsTableAndIndices() {
        val executedSql = mutableListOf<String>()

        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        val migration = UniDownDatabase.MIGRATION_2_3
        assertThat(migration.startVersion).isEqualTo(2)
        assertThat(migration.endVersion).isEqualTo(3)

        migration.migrate(fakeDb)

        assertThat(executedSql).hasSize(3)
        assertThat(executedSql[0]).contains("CREATE TABLE IF NOT EXISTS `processing_jobs`")
        assertThat(executedSql[0]).contains("FOREIGN KEY(`downloadId`) REFERENCES `downloads`(`id`) ON DELETE CASCADE")
        assertThat(executedSql[1]).isEqualTo("CREATE INDEX IF NOT EXISTS `index_processing_jobs_downloadId` ON `processing_jobs` (`downloadId`)")
        assertThat(executedSql[2]).isEqualTo("CREATE INDEX IF NOT EXISTS `index_processing_jobs_stage` ON `processing_jobs` (`stage`)")
    }
}

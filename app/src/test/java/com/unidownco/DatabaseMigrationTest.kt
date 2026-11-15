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
}

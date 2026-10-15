package com.paperadda.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migrations, hand-written so they stay reviewable.
 *
 * Naming rule for future migrations: if [MIGRATION_7_8] is the last entry, the
 * next one is MIGRATION_8_9 and the constant goes in [ALL_MIGRATIONS].
 *
 * The generated index names must match what Room itself produces for
 * `@Entity(indices = ...)` — `index_<table>_<columns>`. Room validates index
 * names on open, so a typo here fails fast in tests rather than in the field.
 */
object Migrations {

    /**
     * Version 7 → 8: indices backing the two "ORDER BY <timestamp> DESC"
     * queries the paper DAO serves (saved + downloaded libraries).
     *
     * Additive only — no column or table is touched, so every row a user
     * already has survives untouched.
     */
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_saved_papers_savedAt " +
                    "ON saved_papers (savedAt)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_downloaded_papers_downloadedAt " +
                    "ON downloaded_papers (downloadedAt)"
            )
        }
    }

    /** Every migration, oldest first — the order [AppDatabase] hands to Room. */
    val ALL: List<Migration> = listOf(MIGRATION_7_8)
}

package com.paperadda.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SavedPaperEntity::class,
        DownloadedPaperEntity::class,
        NoteEntity::class,
        SubjectEntity::class,
        PaperEntity::class
    ],
    version = 8,
    // Schema JSON is written to android/app/schemas/ (see room.schemaLocation in
    // app/build.gradle.kts). It is the input for Room's auto-migrations and for
    // MigrationTestHelper, which is the only way to test that a schema change
    // keeps user data. Without it, "fix the migration" is guesswork.
    exportSchema = true
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun paperDao(): PaperDao
    abstract fun noteDao(): NoteDao
    abstract fun subjectCatalogDao(): SubjectCatalogDao
    abstract fun paperCatalogDao(): PaperCatalogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "paperadda_database"
                )
                    .addMigrations(*Migrations.ALL.toTypedArray())
                    // Last resort only, and reachable only for schemas older
                    // than v7: those builds ran with exportSchema = false, so
                    // no JSON exists to write a real 6→7 migration from, and a
                    // crash on open is worse than a rebuilt cache. Two things
                    // keep that safe: the catalog and notes are re-synced from
                    // Supabase within seconds of launch, and
                    // DownloadedPaperReconciler rebuilds the download index
                    // from the PDFs still on disk. From v7 on, every change
                    // ships a real migration, so this branch stays untouched.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

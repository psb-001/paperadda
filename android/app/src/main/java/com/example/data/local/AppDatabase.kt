package com.example.data.local

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
    version = 6,
    exportSchema = false
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
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

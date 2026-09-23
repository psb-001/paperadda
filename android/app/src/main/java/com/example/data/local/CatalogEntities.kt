package com.example.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/** Offline cache of the admin-published catalog (mirrors Supabase). */
@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val branchCode: String,
    val academicYear: Int,
    val paperCount: Int,
    val iconName: String
)

@Entity(tableName = "papers")
@TypeConverters(StringListConverter::class)
data class PaperEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subjectName: String,
    val branchCode: String,
    val year: String,
    val examType: String,
    val fileFormat: String,
    val fileSize: String,
    val duration: String,
    val maxMarks: Int,
    val sampleQuestions: List<String>,
    val storagePath: String = ""
)

class StringListConverter {
    private val sep = "␟"

    @TypeConverter
    fun fromList(values: List<String>): String = values.joinToString(sep)

    @TypeConverter
    fun toList(raw: String): List<String> =
        if (raw.isEmpty()) emptyList() else raw.split(sep)
}

@Dao
interface SubjectCatalogDao {
    @Query("SELECT * FROM subjects")
    suspend fun getAll(): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SubjectEntity>)

    @Query("DELETE FROM subjects")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<SubjectEntity>) {
        clear()
        insertAll(items)
    }
}

@Dao
interface PaperCatalogDao {
    @Query("SELECT * FROM papers")
    suspend fun getAll(): List<PaperEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PaperEntity>)

    @Query("DELETE FROM papers")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<PaperEntity>) {
        clear()
        insertAll(items)
    }
}

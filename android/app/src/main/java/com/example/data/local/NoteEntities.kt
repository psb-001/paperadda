package com.example.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Admin-published study notes. The note body is a PDF on Supabase Storage
 * ([storagePath]); [content] is only an optional short description for the
 * list row. Users only read here — publishing happens on the admin portal.
 * Field names mirror the backend `notes` table 1:1.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String = "", // optional short description (not the PDF body)
    val subjectName: String = "",
    val branchCode: String = "",
    val academicYear: Int = 1, // 1 = First Year ... 4 = Final Year
    val fileUrl: String = "", // legacy public URL (unused when storagePath set)
    val storagePath: String = "", // key into the `papers` storage bucket; empty = no PDF yet
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteEntity>)

    @Query("DELETE FROM notes")
    suspend fun clear()
}

package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaperDao {
    @Query("SELECT * FROM saved_papers ORDER BY savedAt DESC")
    fun getAllSavedPapers(): Flow<List<SavedPaperEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_papers WHERE paperId = :paperId)")
    fun isPaperSavedFlow(paperId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_papers WHERE paperId = :paperId)")
    suspend fun isPaperSaved(paperId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedPaper(paper: SavedPaperEntity)

    @Query("DELETE FROM saved_papers WHERE paperId = :paperId")
    suspend fun deleteSavedPaper(paperId: String)

    @Query("SELECT * FROM downloaded_papers ORDER BY downloadedAt DESC")
    fun getAllDownloadedPapers(): Flow<List<DownloadedPaperEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_papers WHERE paperId = :paperId)")
    fun isPaperDownloadedFlow(paperId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedPaper(paper: DownloadedPaperEntity)
}

package com.paperadda.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_papers",
    // The library list is `ORDER BY savedAt DESC`; the index keeps that a
    // lookup instead of a full scan-and-sort as the list grows.
    indices = [Index(value = ["savedAt"])]
)
data class SavedPaperEntity(
    @PrimaryKey val paperId: String,
    val title: String,
    val subjectName: String,
    val branchCode: String,
    val year: String,
    val fileFormat: String = "PDF",
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "downloaded_papers",
    indices = [Index(value = ["downloadedAt"])]
)
data class DownloadedPaperEntity(
    @PrimaryKey val paperId: String,
    val title: String,
    val subjectName: String,
    val branchCode: String,
    val year: String,
    val filePath: String,
    val fileSize: String,
    val downloadedAt: Long = System.currentTimeMillis()
)

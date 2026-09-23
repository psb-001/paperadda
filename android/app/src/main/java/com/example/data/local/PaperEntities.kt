package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_papers")
data class SavedPaperEntity(
    @PrimaryKey val paperId: String,
    val title: String,
    val subjectName: String,
    val branchCode: String,
    val year: String,
    val fileFormat: String = "PDF",
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_papers")
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

package com.example.data.repository

import com.example.data.local.NoteDao
import com.example.data.local.NoteEntity
import com.example.data.remote.SupabaseRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Published-notes repository. Local Room table mirrors the admin-published
 * remote list (full replace on sync, so admin deletions propagate too).
 * The note body lives in Supabase Storage ([NoteEntity.storagePath]);
 * [NoteEntity.content] is only an optional list description.
 * Starts empty — content only appears after an admin publishes it.
 */
class NoteRepository(
    private val noteDao: NoteDao
) {
    val notes: Flow<List<NoteEntity>> = noteDao.getAllNotes()

    /** Mirror the remote list locally. False when offline — old cache kept. */
    suspend fun refreshFromRemote(remote: SupabaseRemoteDataSource): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val remoteNotes = remote.fetchNotes()
                noteDao.clear()
                noteDao.insertAll(remoteNotes.map {
                    NoteEntity(
                        title = it.title,
                        content = it.content,
                        subjectName = it.subjectName,
                        branchCode = it.branchCode,
                        academicYear = it.academicYear,
                        fileUrl = it.fileUrl,
                        storagePath = it.storagePath
                    )
                })
                true
            } catch (e: Exception) {
                SupabaseRemoteDataSource.logSyncError(e)
                false
            }
        }
}

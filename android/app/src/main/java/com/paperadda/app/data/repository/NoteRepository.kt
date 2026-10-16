package com.paperadda.app.data.repository

import com.paperadda.app.data.local.NoteDao
import com.paperadda.app.data.local.NoteEntity
import com.paperadda.app.data.remote.SupabaseRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Published-notes repository. Local Room table mirrors the admin-published
 * remote list (full replace on sync, so admin deletions propagate too).
 * The note body lives in Supabase Storage ([NoteEntity.storagePath]);
 * [NoteEntity.content] is only an optional list description.
 */
class NoteRepository(
    private val noteDao: NoteDao
) {
    val notes: Flow<List<NoteEntity>> = noteDao.getAllNotes()

    /**
     * Mirrors the remote notes list into Room. Throws [SupabaseException] so
     * callers surface a specific failure instead of a generic boolean —
     * matching [PaperRepository.refreshFromRemote].
     */
    suspend fun refreshFromRemote(remote: SupabaseRemoteDataSource) = withContext(Dispatchers.IO) {
        val remoteNotes = remote.fetchNotes()
        noteDao.clear()
        noteDao.insertAll(remoteNotes.map {
            NoteEntity(
                id = it.id ?: 0L,
                title = it.title,
                content = it.content,
                subjectName = it.subjectName,
                branchCode = it.branchCode,
                academicYear = it.academicYear,
                fileUrl = it.fileUrl,
                storagePath = it.storagePath
            )
        })
    }
}

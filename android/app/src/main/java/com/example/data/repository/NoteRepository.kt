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
 */
class NoteRepository(
    private val noteDao: NoteDao
) {
    val notes: Flow<List<NoteEntity>> = noteDao.getAllNotes()

    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (noteDao.count() == 0) {
            noteDao.insertAll(adminSnapshot)
        }
    }

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

    companion object {
        // Local stand-in for the admin-published list (replaced by backend).
        // First-year note uses BRANCH_COMMON; storagePath is empty until an
        // admin uploads the real PDF — the UI falls back to the description.
        private val adminSnapshot = listOf(
            NoteEntity(
                title = "Maths I — Important Formulas",
                content = "Differentiation shortcuts, integration rules, and common identities for the first-unit test.",
                subjectName = "Engineering Mathematics I",
                branchCode = "COMMON",
                academicYear = 1
            ),
            NoteEntity(
                title = "Signals & Systems — Formula Sheet",
                content = "Fourier transform pairs, LTI convolution, and the Nyquist rate in one page.",
                subjectName = "Signals and Systems",
                branchCode = "ENTC",
                academicYear = 2
            ),
            NoteEntity(
                title = "DSA — Sorting Cheat Sheet",
                content = "Complexity of quick, merge, heap, and counting sort at a glance.",
                subjectName = "Data Structures & Algorithms",
                branchCode = "CE",
                academicYear = 2
            ),
            NoteEntity(
                title = "ML Foundations — Key Definitions",
                content = "Bias–variance tradeoff and SVM margin formulation, condensed.",
                subjectName = "Machine Learning Foundations",
                branchCode = "AIML",
                academicYear = 2
            ),
            NoteEntity(
                title = "Deep Learning — Activation Functions",
                content = "Sigmoid, ReLU, and Softmax behaviour and when to use each.",
                subjectName = "Deep Learning & Neural Networks",
                branchCode = "AIML",
                academicYear = 3
            ),
            NoteEntity(
                title = "Operating Systems — Process vs Thread",
                content = "Address-space differences and the four Coffman deadlock conditions.",
                subjectName = "Operating Systems",
                branchCode = "CE",
                academicYear = 3
            ),
            NoteEntity(
                title = "Digital Communication — Quick Revision",
                content = "PCM pipeline, Nyquist sampling, and ASK/FSK/PSK comparison.",
                subjectName = "Digital Communication",
                branchCode = "ENTC",
                academicYear = 4
            ),
            NoteEntity(
                title = "Cloud — Service Models Compared",
                content = "IaaS vs PaaS vs SaaS with virtualization types side by side.",
                subjectName = "Cloud Computing",
                branchCode = "IT",
                academicYear = 4
            )
        )
    }
}

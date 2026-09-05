package com.scanrift.android.service.backup

import android.content.ContentResolver
import android.net.Uri
import com.scanrift.android.core.log.Log
import com.scanrift.android.di.IoDispatcher
import com.scanrift.android.di.SnapshotJson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/**
 * Whole-library backup and restore, replacing iOS's iCloud sync on Android.
 *
 * The file goes wherever the user picks through the Storage Access Framework — which
 * can be a Google Drive, OneDrive or Dropbox folder, so the cloud round trip happens
 * in whatever they already use rather than in something this app has to run.
 *
 * All IO and parsing lives here rather than in the UI, so the whole thing is testable
 * against a fake [ContentResolver].
 */
@Singleton
class BackupService @Inject constructor(
    private val contentResolver: ContentResolver,
    private val builder: SnapshotBuilder,
    private val merger: SnapshotMerger,
    @param:SnapshotJson private val json: Json,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun writeSnapshot(uri: Uri, now: Long): Result<Int> = withContext(io) {
        runCatching {
            val snapshot = builder.build(now)
            // "wt" truncates; a bare "w" leaves trailing bytes from a longer previous
            // file, producing JSON that parses as far as the old tail and then fails.
            contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                json.encodeToStream(snapshot, stream)
            } ?: error("Could not open $uri for writing")

            val itemCount = snapshot.entries.size + snapshot.decks.size +
                snapshot.lists.size + snapshot.gameRecords.size
            Log.backup.i("Wrote snapshot with %d items", itemCount)
            itemCount
        }.onFailure { Log.backup.e(it, "Backup failed") }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun readAndMerge(uri: Uri): Result<RestoreResult> = withContext(io) {
        runCatching {
            val snapshot = contentResolver.openInputStream(uri)?.use { stream ->
                json.decodeFromStream<CollectionSnapshot>(stream)
            } ?: error("Could not open $uri for reading")

            merger.merge(snapshot).also { Log.backup.i("Restore: %s", it.summary) }
        }.onFailure { Log.backup.e(it, "Restore failed") }
    }

    /** Parses without writing anything, so the UI can preview a file before merging. */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun peek(uri: Uri): Result<CollectionSnapshot> = withContext(io) {
        runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                json.decodeFromStream<CollectionSnapshot>(stream)
            } ?: error("Could not open $uri for reading")
        }
    }
}

package com.staninna.aufmachen.mail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.staninna.aufmachen.AppContainer
import com.staninna.aufmachen.R
import com.staninna.aufmachen.data.HeaderEntity
import com.staninna.aufmachen.data.SyncStateEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import kotlin.coroutines.coroutineContext

/** Streams the Takeout .mbox from the SAF uri in settings into the headers table. */
object MboxImporter {
    const val SOURCE = HeaderEntity.SOURCE_MBOX
    private const val BATCH = 1000

    /** [onProgress] gets (messages read, estimated total). Returns the message count. */
    suspend fun run(c: AppContainer, context: Context, onProgress: suspend (Int, Int) -> Unit = { _, _ -> }): Int =
        withContext(Dispatchers.IO) {
            val uriText = c.settings.get().mboxUri
                ?: throw MailFailure(MailText.get(R.string.mail_err_mbox), retryable = false)
            val uri = Uri.parse(uriText)
            val sync = c.db.sync()
            val now = System.currentTimeMillis()
            try {
                val size = fileSize(context, uri)
                c.db.headers().deleteSource(SOURCE)
                var st = SyncStateEntity(source = SOURCE, folder = null, startedAt = now)
                sync.upsert(st)

                val input = context.contentResolver.openInputStream(uri)
                    ?: throw MailFailure(MailText.get(R.string.mail_err_mbox), retryable = false)
                var bytes = 0L
                var count = 0
                var sinceRescore = 0
                val batch = ArrayList<HeaderEntity>(BATCH)

                suspend fun flush() {
                    if (batch.isEmpty()) return
                    coroutineContext.ensureActive()
                    c.db.headers().insertAll(batch)
                    count += batch.size
                    sinceRescore += batch.size
                    batch.clear()
                    // Estimate the total from the byte share read so far.
                    val est = if (size > 0 && bytes > 0) maxOf(count.toLong(), size * count / bytes).toInt() else count
                    st = st.copy(processed = count, total = est)
                    sync.upsert(st)
                    onProgress(count, est)
                    if (sinceRescore >= maxOf(20_000, count / 4)) {
                        c.repo.rescore()
                        sinceRescore = 0
                    }
                }

                BufferedInputStream(input, 1 shl 20).use { stream ->
                    for ((index, rec) in MboxReader.read(stream, onBytes = { bytes = it })) {
                        batch += rec.fields.toEntity(
                            source = SOURCE, uid = index.toLong(), seen = rec.seen, flagged = rec.flagged,
                            important = rec.important, sent = rec.sent, category = rec.category,
                            syncedAt = now, initialScan = true,
                        )
                        if (batch.size >= BATCH) flush()
                    }
                }
                flush()
                sync.upsert(st.copy(initialDone = true, processed = count, total = count,
                    lastSyncAt = System.currentTimeMillis(), lastError = null))
                c.repo.rescore()
                count
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = if (e is MailFailure) e
                else MailFailure(MailText.get(R.string.mail_err_mbox), retryable = false, cause = e)
                val st = sync.get(SOURCE) ?: SyncStateEntity(SOURCE)
                sync.upsert(st.copy(lastError = failure.message))
                throw failure
            }
        }

    private fun fileSize(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cur ->
            if (cur.moveToFirst() && !cur.isNull(0)) cur.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)
}

package com.staninna.aufmachen.mail

import androidx.room.withTransaction
import com.staninna.aufmachen.AppContainer
import com.staninna.aufmachen.R
import com.staninna.aufmachen.data.HeaderEntity
import com.staninna.aufmachen.data.SyncStateEntity
import com.sun.mail.gimap.GmailFolder
import com.sun.mail.gimap.GmailMessage
import com.sun.mail.gimap.GmailRawSearchTerm
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Message
import javax.mail.UIDFolder
import kotlin.coroutines.coroutineContext

data class ScanProgress(val processed: Int, val total: Int, val readingCategories: Boolean = false)

/** [startedAt] is the syncedAt stamp of the rows written; the watchdog reads rows since then. */
data class SyncOutcome(val newMails: Int, val startedAt: Long, val wasInitial: Boolean)

/**
 * Header-only IMAP reader. Port of `read_imap` from mail_triage.py.
 *
 * What goes over the wire per batch: `UID FETCH <uids> (UID)` to build message objects, then
 * `FETCH <seqs> (FLAGS UID [X-GM-LABELS] BODY.PEEK[HEADER.FIELDS (From To Cc Subject Date
 * List-Unsubscribe List-Unsubscribe-Post List-Id Precedence Content-Type)])`. javax.mail builds the
 * last item from the header names in the FetchProfile. We never add ENVELOPE, CONTENT_INFO or any
 * body item, and the folder is opened with EXAMINE (READ_ONLY), so no body is fetched and no flag
 * can change.
 */
object ImapScanner {
    const val SOURCE = HeaderEntity.SOURCE_IMAP
    const val BATCH = 500
    private const val RESCORE_MIN = 5000
    private const val SEARCH_CHUNK = 2000

    private val HEADERS = listOf(
        "From", "To", "Cc", "Subject", "Date", "List-Unsubscribe", "List-Unsubscribe-Post",
        "List-Id", "Precedence", "Content-Type",
    )

    /** Runs the first scan (resuming if needed) or an incremental sync, depending on stored state. */
    suspend fun run(c: AppContainer, onProgress: suspend (ScanProgress) -> Unit = {}): SyncOutcome =
        withContext(Dispatchers.IO) {
            val cfg = c.settings.get().imap
            val password = c.secrets.imapPassword
            if (cfg == null || password.isNullOrEmpty()) {
                throw MailFailure(MailText.get(R.string.mail_err_not_configured), retryable = false)
            }
            val startedAt = System.currentTimeMillis()
            try {
                MailConnection.open(cfg, password).use { conn ->
                    scan(c, conn, startedAt, onProgress)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = MailErrors.toFailure(e)
                val st = c.db.sync().get(SOURCE) ?: SyncStateEntity(SOURCE)
                c.db.sync().upsert(st.copy(lastError = failure.message))
                throw failure
            }
        }

    private suspend fun scan(
        c: AppContainer, conn: ImapConnection, startedAt: Long, onProgress: suspend (ScanProgress) -> Unit,
    ): SyncOutcome {
        val folder = conn.folder
        val sync = c.db.sync()
        val uv = folder.uidValidity
        var st = sync.get(SOURCE) ?: SyncStateEntity(SOURCE)
        if (st.uidValidity != null && st.uidValidity != uv) {
            // Mailbox was rebuilt on the server: UIDs mean something else now. Start over.
            c.db.headers().deleteSource(SOURCE)
            st = SyncStateEntity(SOURCE)
        }
        st = st.copy(folder = folder.fullName, uidValidity = uv, lastError = null)
        sync.upsert(st)
        return if (st.initialDone) incremental(c, conn, st, startedAt) else initial(c, conn, st, startedAt, onProgress)
    }

    // ------------------------------------------------------------------ first scan

    private suspend fun initial(
        c: AppContainer, conn: ImapConnection, start: SyncStateEntity, now: Long,
        onProgress: suspend (ScanProgress) -> Unit,
    ): SyncOutcome {
        val folder = conn.folder
        val sync = c.db.sync()
        val all = allUids(folder)
        val have = c.db.headers().uids(SOURCE).toHashSet()
        // Newest first so the lists fill with recent mail within minutes.
        val todo = all.filter { it !in have }.sortedDescending()
        val total = all.size
        var processed = total - todo.size
        var st = start.copy(total = total, processed = processed, startedAt = start.startedAt ?: now)
        sync.upsert(st)
        onProgress(ScanProgress(processed, total))

        var sinceRescore = 0
        var inserted = 0
        for (batch in todo.chunked(BATCH)) {
            coroutineContext.ensureActive()
            val rows = fetchBatch(conn, batch.toLongArray(), now, initialScan = true)
            c.db.headers().insertAll(rows)
            processed += batch.size
            inserted += rows.size
            sinceRescore += rows.size
            st = st.copy(processed = processed)
            sync.upsert(st)
            onProgress(ScanProgress(processed, total))
            // Rescore every ~5000 mails so lists fill live. The gap grows with the table because
            // each rescore reads every stored header.
            if (sinceRescore >= maxOf(RESCORE_MIN, inserted / 4)) {
                c.repo.rescore()
                sinceRescore = 0
            }
        }

        if (conn.gmail) {
            onProgress(ScanProgress(processed, total, readingCategories = true))
            gmailPass(c, folder as GmailFolder)
        }
        st = st.copy(
            initialDone = true, highestUid = all.maxOrNull() ?: 0, processed = total,
            lastSyncAt = System.currentTimeMillis(), lastError = null,
        )
        sync.upsert(st)
        c.repo.rescore()
        return SyncOutcome(inserted, now, wasInitial = true)
    }

    // ------------------------------------------------------------------ incremental

    private suspend fun incremental(
        c: AppContainer, conn: ImapConnection, start: SyncStateEntity, now: Long,
    ): SyncOutcome {
        val folder = conn.folder
        val sync = c.db.sync()
        var st = start
        // UID FETCH <highest+1>:* (UID). A range ending in * always returns the last message even
        // when it is below the start, hence the filter.
        val fresh = folder.getMessagesByUID(st.highestUid + 1, UIDFolder.LASTUID)
            .filterNotNull().map { folder.getUID(it) }.filter { it > st.highestUid }.sorted()
        var added = 0
        // Ascending, so highestUid can advance per batch and an interrupted sync resumes cleanly.
        for (batch in fresh.chunked(BATCH)) {
            coroutineContext.ensureActive()
            val rows = fetchBatch(conn, batch.toLongArray(), now, initialScan = false)
            c.db.headers().insertAll(rows)
            added += rows.size
            st = st.copy(highestUid = batch.last())
            sync.upsert(st)
        }
        // No category pass here: X-GM-RAW cannot be limited to a UID range through javax.mail's
        // search terms, and searching the whole mailbox on every refresh is not cheap. New mails
        // keep category "" until the next full rescan. Read, starred, important, sent and
        // attachment state come straight from FLAGS, labels and Content-Type.
        sync.upsert(st.copy(lastSyncAt = System.currentTimeMillis(), lastError = null))
        return SyncOutcome(added, now, wasInitial = false)
    }

    // ------------------------------------------------------------------ fetching

    private fun profile(gmail: Boolean) = FetchProfile().apply {
        add(FetchProfile.Item.FLAGS)
        add(UIDFolder.FetchProfileItem.UID)
        if (gmail) add(GmailFolder.FetchProfileItem.LABELS)
        HEADERS.forEach { add(it) }
    }

    /** UID FETCH 1:* (UID): all UIDs, ascending. Message objects are dropped right after. */
    private fun allUids(folder: IMAPFolder): List<Long> =
        folder.getMessagesByUID(1, UIDFolder.LASTUID).filterNotNull().map { folder.getUID(it) }.sorted()

    private fun fetchBatch(conn: ImapConnection, uids: LongArray, syncedAt: Long, initialScan: Boolean): List<HeaderEntity> {
        val folder = conn.folder
        val msgs = folder.getMessagesByUID(uids).filterNotNull()
        folder.fetch(msgs.toTypedArray(), profile(conn.gmail))
        val today = LocalDate.now()
        val out = ArrayList<HeaderEntity>(msgs.size)
        for (m in msgs) {
            val fields = HeaderParser.parse({ name -> m.getHeader(name)?.firstOrNull() }, today)
            val labels = if (conn.gmail) (m as GmailMessage).labels.orEmpty().map { it.lowercase() } else emptyList()
            out += fields.toEntity(
                source = SOURCE, uid = folder.getUID(m),
                seen = m.isSet(Flags.Flag.SEEN), flagged = m.isSet(Flags.Flag.FLAGGED),
                important = "\\important" in labels, sent = "\\sent" in labels,
                category = "", syncedAt = syncedAt, initialScan = initialScan,
            )
            // The folder keeps every message object it ever created. Free the cached headers.
            (m as IMAPMessage).invalidateHeaders()
        }
        return out
    }

    // ------------------------------------------------------------------ Gmail extras

    /**
     * Second half of `read_imap`: categories, attachments, read/starred/important state from
     * X-GM-RAW searches. Searches run first (network), then one transaction applies the results.
     */
    private suspend fun gmailPass(c: AppContainer, folder: GmailFolder) {
        val cats = listOf("promotions", "social", "updates", "forums").associateWith { gmSearch(folder, "category:$it") }
        val attach = gmSearch(folder, "has:attachment")
        val read = gmSearch(folder, "is:read")
        val starred = gmSearch(folder, "is:starred")
        val important = gmSearch(folder, "is:important")
        val dao = c.db.headers()
        c.db.withTransaction {
            val raw = c.db.openHelper.writableDatabase
            raw.execSQL("UPDATE headers SET category='primary' WHERE source='imap'")
            for ((cat, uids) in cats) uids.chunked(500).forEach { dao.setCategory(SOURCE, it, cat) }
            // Like the prototype: reset a column only when the search found anything.
            if (attach.isNotEmpty()) {
                raw.execSQL("UPDATE headers SET attach=0 WHERE source='imap'")
                attach.chunked(500).forEach { dao.setAttach(SOURCE, it, true) }
            }
            if (read.isNotEmpty()) {
                raw.execSQL("UPDATE headers SET seen=0 WHERE source='imap'")
                read.chunked(500).forEach { dao.setSeen(SOURCE, it, true) }
            }
            if (starred.isNotEmpty()) {
                raw.execSQL("UPDATE headers SET flagged=0 WHERE source='imap'")
                starred.chunked(500).forEach { dao.setFlagged(SOURCE, it, true) }
            }
            if (important.isNotEmpty()) {
                raw.execSQL("UPDATE headers SET important=0 WHERE source='imap'")
                important.chunked(500).forEach { dao.setImportant(SOURCE, it, true) }
            }
        }
    }

    /** SEARCH X-GM-RAW "<query>", mapped to UIDs (prototype `gm_search`). */
    private fun gmSearch(folder: GmailFolder, query: String): List<Long> {
        val hits: Array<Message> = folder.search(GmailRawSearchTerm(query))
        if (hits.isEmpty()) return emptyList()
        val fp = FetchProfile().apply { add(UIDFolder.FetchProfileItem.UID) }
        val out = ArrayList<Long>(hits.size)
        for (chunk in hits.asList().chunked(SEARCH_CHUNK)) {
            folder.fetch(chunk.toTypedArray(), fp)
            chunk.forEach { out += folder.getUID(it) }
        }
        return out
    }
}

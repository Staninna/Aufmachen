package com.staninna.aufmachen.mail

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate

/** One mbox message: parsed headers plus the Takeout flags derived from X-Gmail-Labels. */
data class MboxRecord(
    val fields: HeaderFields,
    val seen: Boolean,
    val flagged: Boolean,
    val important: Boolean,
    val sent: Boolean,
    val category: String,
)

/**
 * Streaming mbox reader (port of `read_mbox`). Memory stays flat on multi-GB files:
 * only each message's header block is kept, body lines are read and dropped.
 */
object MboxReader {
    private const val MAX_HEADER_LINE = 16 * 1024
    private const val MAX_HEADER_BLOCK = 256 * 1024
    private val CATEGORIES = listOf("promotions", "social", "updates", "forums")

    /**
     * Lazily yields (running index, record) for each message. Index is 0-based and only counts
     * messages with a usable header block. [onBytes] gets the bytes consumed so far after each message.
     * A sequence, so the caller can run suspending database writes between messages.
     */
    fun read(
        input: InputStream,
        today: LocalDate = LocalDate.now(),
        onBytes: (Long) -> Unit = {},
    ): Sequence<Pair<Int, MboxRecord>> = sequence {
        val lines = LineReader(input)
        val line = ByteArrayOutputStream()
        val block = ByteArrayOutputStream()
        var index = 0
        var inHeaders = false
        var inMessage = false

        while (true) {
            // Headers need whole lines. Body lines only need the first 5 bytes ("From ").
            val max = if (inHeaders) MAX_HEADER_LINE else 5
            if (!lines.readLine(line, max)) break
            val b = line.toByteArray()
            if (startsWithFrom(b)) {
                if (inMessage) {
                    val rec = record(block.toByteArray(), today)
                    block.reset()
                    onBytes(lines.bytesRead)
                    if (rec != null) yield(index++ to rec)
                }
                inMessage = true
                inHeaders = true
                continue
            }
            if (!inMessage || !inHeaders) continue
            val blank = b.isEmpty() || (b.size == 1 && b[0] == '\r'.code.toByte())
            if (blank) {
                inHeaders = false
            } else if (block.size() < MAX_HEADER_BLOCK) {
                block.write(b)
                block.write('\n'.code)
            }
        }
        if (inMessage) {
            val rec = record(block.toByteArray(), today)
            onBytes(lines.bytesRead)
            if (rec != null) yield(index++ to rec)
        }
    }

    private fun startsWithFrom(b: ByteArray) =
        b.size >= 5 && b[0] == 'F'.code.toByte() && b[1] == 'r'.code.toByte() && b[2] == 'o'.code.toByte() &&
            b[3] == 'm'.code.toByte() && b[4] == ' '.code.toByte()

    /** Null when the block holds no recognisable header (a stray "From " line inside a body). */
    internal fun record(block: ByteArray, today: LocalDate): MboxRecord? {
        val map = HeaderParser.headerMap(block)
        if (listOf("from", "subject", "date", "to").none { it in map }) return null
        val fields = HeaderParser.parse({ map[it.lowercase()] }, today)
        val labels = HeaderParser.decode(map["x-gmail-labels"]).split(",")
            .map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        var cat = ""
        for (c in CATEGORIES) if ("category $c" in labels) cat = c
        if (cat.isEmpty() && labels.isNotEmpty()) cat = "primary"
        return MboxRecord(
            fields = fields,
            seen = "unread" !in labels,
            flagged = "starred" in labels,
            important = "important" in labels,
            sent = "sent" in labels,
            category = cat,
        )
    }

    /** Line splitter on '\n' that keeps at most `max` bytes per line (without the '\n'). */
    private class LineReader(private val input: InputStream) {
        private val buf = ByteArray(1 shl 16)
        private var pos = 0
        private var lim = 0
        var bytesRead = 0L
            private set

        fun readLine(out: ByteArrayOutputStream, max: Int): Boolean {
            out.reset()
            var any = false
            while (true) {
                if (pos >= lim) {
                    lim = input.read(buf, 0, buf.size)
                    pos = 0
                    if (lim <= 0) { lim = 0; return any }
                }
                any = true
                var i = pos
                while (i < lim && buf[i] != '\n'.code.toByte()) i++
                val take = minOf(i - pos, max - out.size())
                if (take > 0) out.write(buf, pos, take)
                bytesRead += (i - pos) + (if (i < lim) 1 else 0)
                if (i < lim) { pos = i + 1; return true }
                pos = lim
            }
        }
    }
}

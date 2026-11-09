package com.staninna.aufmachen.mail

import com.staninna.aufmachen.data.HeaderEntity
import com.staninna.aufmachen.engine.baseDomain
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.mail.internet.InternetAddress
import javax.mail.internet.MailDateFormat
import javax.mail.internet.MimeUtility

/** What `record_from_headers` of the prototype yields, before flags and labels are added. */
data class HeaderFields(
    val dateEpochDay: Long?,
    val fromAddr: String,
    val fromName: String,
    val fromDomain: String,
    val toAddrs: String,
    val subject: String,
    val unsubHeader: String,
    val unsubOneClick: Boolean,
    val bulk: Boolean,
    val attach: Boolean,
) {
    fun toEntity(
        source: String, uid: Long, seen: Boolean, flagged: Boolean, important: Boolean, sent: Boolean,
        category: String, syncedAt: Long, initialScan: Boolean,
    ) = HeaderEntity(
        source = source, uid = uid, dateEpochDay = dateEpochDay, fromAddr = fromAddr, fromName = fromName,
        fromDomain = fromDomain, toAddrs = toAddrs, subject = subject, unsubHeader = unsubHeader,
        unsubOneClick = unsubOneClick, bulk = bulk, attach = attach, seen = seen, flagged = flagged,
        important = important, sent = sent, category = category, syncedAt = syncedAt, initialScan = initialScan,
    )
}

/** Port of `record_from_headers`, `decode` and `parse_date` from mail_triage.py. */
object HeaderParser {
    private val ADDR_RE = Regex("[^\\s<>,;\"()]+@[^\\s<>,;\"()]+")

    /** Parses a raw header block (as bytes, ends at the first blank line). */
    fun parseBlock(block: ByteArray, today: LocalDate = LocalDate.now()): HeaderFields {
        val map = headerMap(block)
        return parse({ map[it.lowercase()] }, today)
    }

    /** [get] returns the raw (possibly RFC 2047 encoded) value of a header, or null. */
    fun parse(get: (String) -> String?, today: LocalDate = LocalDate.now()): HeaderFields {
        fun raw(name: String) = get(name)?.let { MimeUtility.unfold(it) }?.trim()

        var name = ""
        var addr = ""
        raw("From")?.let { from ->
            val a = firstAddress(from)
            addr = a.second
            name = a.first
        }
        addr = addr.lowercase()

        val to = (addrList(raw("To")) + addrList(raw("Cc"))).joinToString(",")
        val toCut = if (to.length <= 2000) to else to.take(2000).substringBeforeLast(',', "")

        val prec = raw("Precedence")?.lowercase().orEmpty()
        val ctype = raw("Content-Type")?.lowercase().orEmpty()
        val unsub = raw("List-Unsubscribe").orEmpty()
        val post = raw("List-Unsubscribe-Post").orEmpty()

        return HeaderFields(
            dateEpochDay = parseDate(raw("Date"), today)?.toEpochDay(),
            fromAddr = addr,
            fromName = name.take(120),
            fromDomain = if (addr.contains('@')) baseDomain(addr.substringAfterLast('@')) else "",
            toAddrs = toCut,
            subject = decode(raw("Subject")).take(300),
            unsubHeader = unsub,
            unsubOneClick = post.contains("List-Unsubscribe=One-Click", ignoreCase = true),
            bulk = prec == "bulk" || prec == "list" || prec == "junk" || !raw("List-Id").isNullOrEmpty(),
            attach = ctype.contains("multipart/mixed"),
        )
    }

    /** RFC 2047 decode, falling back to the raw text (prototype `decode`). */
    fun decode(value: String?): String {
        if (value == null) return ""
        return try {
            MimeUtility.decodeText(MimeUtility.unfold(value)).trim()
        } catch (e: Exception) {
            value.trim()
        }
    }

    private fun firstAddress(header: String): Pair<String, String> {
        try {
            val a = InternetAddress.parseHeader(header, false).firstOrNull { !it.isGroup }
            if (a != null) return decode(a.personal) to a.address.orEmpty()
        } catch (e: Exception) {
            // fall through to the regex
        }
        val addr = ADDR_RE.find(header)?.value.orEmpty()
        return "" to addr
    }

    private fun addrList(header: String?): List<String> {
        if (header.isNullOrBlank()) return emptyList()
        val parsed = try {
            InternetAddress.parseHeader(header, false).flatMap { a ->
                if (a.isGroup) a.getGroup(false)?.map { it.address }.orEmpty() else listOf(a.address)
            }
        } catch (e: Exception) {
            ADDR_RE.findAll(header).map { it.value }.toList()
        }
        return parsed.filter { !it.isNullOrEmpty() && it.contains('@') }.map { it.lowercase() }
    }

    /** Date in the message's own offset; null when unparsable, before 1995, or > 2 days ahead. */
    fun parseDate(value: String?, today: LocalDate = LocalDate.now()): LocalDate? {
        if (value.isNullOrBlank()) return null
        val cleaned = value.replace(Regex("\\([^)]*\\)"), " ").trim().replace(Regex("\\s+"), " ")
        val d: LocalDate = try {
            OffsetDateTime.parse(cleaned, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDate()
        } catch (e: Exception) {
            try {
                val date = MailDateFormat().parse(cleaned) ?: return null
                date.toInstant().atOffset(ZoneOffset.UTC).toLocalDate()
            } catch (e2: Exception) {
                return null
            }
        }
        if (d.year < 1995 || d.isAfter(today.plusDays(2))) return null
        return d
    }

    /** Unfolds a header block into lowercase-name to first-value. */
    fun headerMap(block: ByteArray): Map<String, String> {
        val text = decodeBytes(block)
        val out = LinkedHashMap<String, String>()
        var curName: String? = null
        val cur = StringBuilder()
        fun flush() {
            val n = curName ?: return
            out.putIfAbsent(n, cur.toString().trim())
            curName = null
            cur.setLength(0)
        }
        for (line in text.split('\n')) {
            val l = line.trimEnd('\r')
            if (l.isEmpty()) break
            if ((l[0] == ' ' || l[0] == '\t') && curName != null) {
                cur.append(l)
                continue
            }
            val i = l.indexOf(':')
            if (i <= 0 || l.startsWith("From ")) { flush(); continue }
            flush()
            curName = l.substring(0, i).trim().lowercase()
            cur.append(l.substring(i + 1))
        }
        flush()
        return out
    }

    private fun decodeBytes(b: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString()
    } catch (e: CharacterCodingException) {
        String(b, Charsets.ISO_8859_1)
    }
}

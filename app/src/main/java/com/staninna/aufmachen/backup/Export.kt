package com.staninna.aufmachen.backup

import android.content.Context
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.model.Action
import com.staninna.aufmachen.model.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.URLEncoder
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/** A ratio from 0 to 1, shown as a percentage. */
data class Pct(val ratio: Double)

/** The Services sheet of the prototype, minus the saved-password columns, plus status, address, proof date. */
object ServiceExport {
    val HEADER = listOf(
        "Rank", "Tier", "Score", "Action", "Service", "Category", "Sender name", "Mails", "Last 12m",
        "Per year", "First", "Last", "Days since", "Active years", "Read %", "Important %", "Starred",
        "You sent", "Security", "Welcome", "Orders/inv", "Billing", "Bill months", "Marketing",
        "Newsletter %", "Promo %", "Updates %", "Social %", "Attachments", "Regularity", "Senders",
        "Why", "Example subject", "Gmail", "Unsubscribe link", "Status", "Address", "Proof date",
    )

    fun actionText(a: Action) = when (a) {
        Action.CHANGE_FIRST -> "Change email FIRST + check 2FA/recovery"
        Action.DORMANT_DELETE -> "Dormant: delete account (or ignore)"
        Action.CHANGE -> "Change email"
        Action.UNSUBSCRIBE -> "Unsubscribe"
        Action.CHANGE_LOW_OR_DELETE -> "Change email (low prio) or delete account"
        Action.IGNORE -> "Ignore"
    }

    fun tierText(t: Tier) = "${t.level} " + t.name.lowercase().replaceFirstChar { it.uppercase() }

    /** [proofDates] maps a service domain (any member of a group) to its latest proof date. */
    fun rows(views: List<ServiceView>, proofDates: Map<String, String>): List<List<Any?>> =
        views.mapIndexed { i, v ->
            val s = v.stats
            val why = v.why.filter { kotlin.math.abs(it.points) >= 3 }
                .sortedByDescending { kotlin.math.abs(it.points) }.take(4)
                .joinToString(", ") { "${it.key} ${if (it.points >= 0) "+" else "-"}${kotlin.math.abs(it.points).roundToInt()}" }
            val proof = (listOf(s.domain) + v.domains).firstNotNullOfOrNull { proofDates[it] }
            listOf(
                i + 1, tierText(v.tier), s.score, actionText(v.action), s.domain, s.category,
                v.displayNames.joinToString("; ").take(80), s.mails, s.last12, s.perYearRate,
                s.firstEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
                s.lastEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
                s.daysSince, s.activeYears, Pct(s.readPct), Pct(s.importantPct), s.starred, s.youSent,
                s.security, s.welcome, s.orders, s.billing, s.billMonths, s.marketing,
                Pct(s.newsletterPct), Pct(s.promoPct), Pct(s.updatesPct), Pct(s.socialPct), s.attachments,
                s.regularity, v.senderAddrs.size, why, s.exampleSubject,
                "https://mail.google.com/mail/u/0/#search/" +
                    URLEncoder.encode("from:${s.domain}", "UTF-8").replace("+", "%20"),
                v.unsubLinks.firstOrNull().orEmpty(), v.status.name, v.newAddress, proof,
            )
        }

    suspend fun load(context: Context): List<List<Any?>> = withContext(Dispatchers.Default) {
        val c = context.container
        val views = c.repo.services.first()
        val proofDates = HashMap<String, String>()
        c.db.proofs().all().sortedBy { it.createdAt }.forEach { p ->
            proofDates[p.domain] = p.receivedDate ?: java.time.Instant.ofEpochMilli(p.createdAt).toString().take(10)
        }
        rows(views, proofDates)
    }

    // ------------------------------------------------------------------ CSV

    private fun csvText(v: Any?): String = when (v) {
        null -> ""
        is Pct -> ((v.ratio * 1000).roundToInt() / 10.0).toString()
        else -> v.toString()
    }

    fun csvField(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** RFC 4180: comma separated, CRLF line ends, quotes doubled. */
    fun toCsv(header: List<String>, rows: List<List<Any?>>): String = buildString {
        append(header.joinToString(",") { csvField(it) }).append("\r\n")
        rows.forEach { r -> append(r.joinToString(",") { csvField(csvText(it)) }).append("\r\n") }
    }

    fun writeCsv(out: OutputStream, header: List<String>, rows: List<List<Any?>>) {
        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // BOM so Excel reads UTF-8
        out.write(toCsv(header, rows).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    // ----------------------------------------------------------------- XLSX

    fun colName(i: Int): String {
        var n = i
        val sb = StringBuilder()
        do {
            sb.insert(0, 'A' + n % 26)
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    fun xmlEscape(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 0x20 && ch != '\n' && ch != '\r' && ch != '\t' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun cell(ref: String, v: Any?, style: Int?): String {
        val st = style?.let { " s=\"$it\"" }.orEmpty()
        return when (v) {
            null -> ""
            is Pct -> "<c r=\"$ref\" s=\"2\"><v>${v.ratio}</v></c>"
            is Int, is Long, is Short -> "<c r=\"$ref\"$st><v>$v</v></c>"
            is Double -> if (v.isNaN() || v.isInfinite()) "" else "<c r=\"$ref\"$st><v>$v</v></c>"
            else -> "<c r=\"$ref\"$st t=\"inlineStr\"><is><t xml:space=\"preserve\">${xmlEscape(v.toString())}</t></is></c>"
        }
    }

    fun sheetXml(header: List<String>, rows: List<List<Any?>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        append("<row r=\"1\">")
        header.forEachIndexed { c, h -> append(cell("${colName(c)}1", h, 1)) }
        append("</row>")
        rows.forEachIndexed { r, row ->
            append("<row r=\"${r + 2}\">")
            row.forEachIndexed { c, v -> append(cell("${colName(c)}${r + 2}", v, null)) }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private const val DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    private const val CONTENT_TYPES = DECL +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
        "</Types>"

    private const val ROOT_RELS = DECL +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    private const val WORKBOOK = DECL +
        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
        "<sheets><sheet name=\"Services\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"

    private const val WORKBOOK_RELS = DECL +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
        "</Relationships>"

    // xf 0 plain, xf 1 bold header, xf 2 percent
    private const val STYLES = DECL +
        "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
        "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
        "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"3\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
        "<xf numFmtId=\"9\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "</cellXfs></styleSheet>"

    fun writeXlsx(out: OutputStream, header: List<String>, rows: List<List<Any?>>) {
        val zip = ZipOutputStream(out)
        fun part(name: String, body: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(body.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        part("[Content_Types].xml", CONTENT_TYPES)
        part("_rels/.rels", ROOT_RELS)
        part("xl/workbook.xml", WORKBOOK)
        part("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
        part("xl/styles.xml", STYLES)
        part("xl/worksheets/sheet1.xml", sheetXml(header, rows))
        zip.finish()
        out.flush()
    }
}

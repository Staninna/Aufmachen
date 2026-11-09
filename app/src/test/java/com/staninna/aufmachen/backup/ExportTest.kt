package com.staninna.aufmachen.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ExportTest {
    private val header = listOf("Service", "Why", "Mails")

    @Test fun csvQuotesCommasQuotesAndNewlines() {
        val csv = ServiceExport.toCsv(
            header,
            listOf(listOf("a,b", "say \"hi\"", 3), listOf("line1\nline2", "plain", null)),
        )
        assertEquals(
            "Service,Why,Mails\r\n" +
                "\"a,b\",\"say \"\"hi\"\"\",3\r\n" +
                "\"line1\nline2\",plain,\r\n",
            csv,
        )
    }

    @Test fun csvPercentIsRounded() {
        assertTrue(ServiceExport.toCsv(listOf("p"), listOf(listOf(Pct(0.4567)))).contains("45.7"))
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                m[e.name] = String(z.readBytes(), Charsets.UTF_8)
            }
        }
        return m
    }

    private fun parse(xml: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))

    @Test fun xlsxHasRequiredPartsAndValidXml() {
        val out = ByteArrayOutputStream()
        ServiceExport.writeXlsx(out, header, listOf(listOf("a & <b>", "x", 5), listOf("c", null, 2.5)))
        val parts = unzip(out.toByteArray())
        listOf(
            "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels",
            "xl/worksheets/sheet1.xml", "xl/styles.xml",
        ).forEach { assertTrue("missing $it", it in parts) }
        parts.values.forEach { parse(it) } // throws when not well-formed
        val sheet = parse(parts.getValue("xl/worksheets/sheet1.xml"))
        val rows = sheet.getElementsByTagName("row")
        assertEquals(3, rows.length)
        val headCells = (rows.item(0) as org.w3c.dom.Element).getElementsByTagName("c")
        assertEquals(3, headCells.length)
        assertEquals("1", (headCells.item(0) as org.w3c.dom.Element).getAttribute("s"))
        assertEquals("Service", headCells.item(0).textContent)
        assertTrue(parts.getValue("xl/worksheets/sheet1.xml").contains("<v>5</v>"))
        assertEquals("a & <b>", (rows.item(1) as org.w3c.dom.Element).getElementsByTagName("t").item(0).textContent)
    }

    @Test fun columnNames() {
        assertEquals("A", ServiceExport.colName(0))
        assertEquals("Z", ServiceExport.colName(25))
        assertEquals("AA", ServiceExport.colName(26))
        assertEquals("AM", ServiceExport.colName(38))
    }
}

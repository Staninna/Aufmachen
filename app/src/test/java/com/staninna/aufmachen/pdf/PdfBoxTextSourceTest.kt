package com.staninna.aufmachen.pdf

import androidx.test.core.app.ApplicationProvider
import com.staninna.aufmachen.engine.ProofCandidate
import com.staninna.aufmachen.engine.ProofMatcher
import com.staninna.aufmachen.engine.TutaPdfParser
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfBoxTextSourceTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun buildPdf(title: String?, lines: List<String>, link: String?): String {
        PDFBoxResourceLoader.init(context)
        val file = tmp.newFile("tuta.pdf")
        PDDocument().use { doc ->
            title?.let { doc.documentInformation.title = it }
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.setFont(PDType1Font.HELVETICA, 12f)
                var y = 780f
                for (l in lines) {
                    cs.beginText(); cs.newLineAtOffset(50f, y); cs.showText(l); cs.endText()
                    y -= 20f
                }
            }
            if (link != null) {
                val a = PDAnnotationLink()
                a.rectangle = PDRectangle(50f, 600f, 200f, 20f)
                a.action = PDActionURI().apply { uri = link }
                page.annotations.add(a)
            }
            doc.save(file)
        }
        return file.absolutePath
    }

    @Test fun roundTripThroughParserAndMatcher() {
        val path = buildPdf(
            "me@example.com - Tuta",
            listOf(
                "Tuta Team",
                "to: me.other@example.org",
                "Thu, Nov 5, 2026 • 22:14",
                "To",
                "me.other@example.org",
                "Reply to",
                "no-reply@tutanota.de",
                "Welcome to your new mailbox.",
            ),
            "https://tuta.com/support",
        )
        val x = PdfBoxTextSource(context).extract(path)!!
        assertEquals("me@example.com - Tuta", x.title)
        assertEquals(listOf("https://tuta.com/support"), x.links)

        val f = TutaPdfParser.parse(x.title, x.text, x.links)!!
        assertEquals("me@example.com", f.account)
        assertEquals("Tuta Team", f.displayName)
        assertEquals("me.other@example.org", f.to)
        assertEquals("Thu, Nov 5, 2026 • 22:14", f.dateLine)
        assertEquals("no-reply@tutanota.de", f.replyTo)
        assertEquals("Welcome to your new mailbox.", f.bodyText.trim())
        assertEquals(listOf("https://tuta.com/support"), f.links)

        val ranked = ProofMatcher.rank(
            f,
            listOf(
                ProofCandidate("decoy", setOf("example.net"), listOf("Decoy Bank")),
                ProofCandidate("tuta", setOf("tutanota.de", "tuta.com"), listOf("Tuta Team")),
            ),
        )
        assertEquals("tuta", ranked[0].groupKey)
        assertTrue(ranked[0].score > ProofMatcher.HIGH_CONFIDENCE)
        assertEquals("decoy", ranked[1].groupKey)
        assertTrue(ranked[1].score < ranked[0].score)
    }

    @Test fun unreadableFileIsNull() {
        val f = tmp.newFile("junk.pdf").also { it.writeText("not a pdf") }
        assertNull(PdfBoxTextSource(context).extract(f.absolutePath))
        assertNull(PdfBoxTextSource(context).extract(tmp.root.absolutePath + "/missing.pdf"))
    }

    @Test fun pdfWithoutTextHasEmptyText() {
        val path = buildPdf(null, emptyList(), null)
        val x = PdfBoxTextSource(context).extract(path)
        assertNotNull(x)
        assertTrue(x!!.text.isBlank())
    }
}

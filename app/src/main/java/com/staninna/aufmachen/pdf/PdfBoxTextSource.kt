package com.staninna.aufmachen.pdf

import android.content.Context
import com.staninna.aufmachen.engine.PdfExtract
import com.staninna.aufmachen.engine.PdfTextSource
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** Text layer, title and URI link annotations of a PDF. No OCR. */
class PdfBoxTextSource(context: Context) : PdfTextSource {
    init {
        PDFBoxResourceLoader.init(context.applicationContext ?: context)
    }

    override fun extract(path: String): PdfExtract? {
        val doc = try {
            PDDocument.load(File(path))
        } catch (e: Exception) {
            return null
        }
        return try {
            val title = doc.documentInformation?.title
            val text = PDFTextStripper().apply { sortByPosition = true }.getText(doc).orEmpty()
            val links = LinkedHashSet<String>()
            for (page in doc.pages) {
                val annotations = try { page.annotations } catch (e: Exception) { continue }
                for (a in annotations) {
                    val uri = ((a as? PDAnnotationLink)?.action as? PDActionURI)?.uri
                    if (!uri.isNullOrBlank()) links += uri
                }
            }
            PdfExtract(title, text, links.toList())
        } catch (e: Exception) {
            null
        } finally {
            try { doc.close() } catch (_: Exception) {}
        }
    }
}

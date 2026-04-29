package com.example.cosmicwhisper.domain.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    private const val PAGE_WIDTH = 595 // A4 width in points
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 40f

    fun exportTranscriptionToPdf(context: Context, text: String, fileName: String): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val metaPaint = TextPaint().apply {
            color = Color.DKGRAY
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }

        val bodyPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        var currentY = MARGIN

        // Title
        canvas.drawText("Cosmic Whisper Transcription", MARGIN, currentY + 24f, titlePaint)
        currentY += 40f

        // Metadata
        val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Date: $date", MARGIN, currentY + 12f, metaPaint)
        currentY += 30f

        // Horizontal Line
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, linePaint)
        currentY += 20f

        // Body Text with Wrapping
        val textWidth = (PAGE_WIDTH - 2 * MARGIN).toInt()
        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, bodyPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.2f)
            .setIncludePad(false)
            .build()

        // Handle multi-page if needed
        // For simplicity in this version, we focus on the first page, but we could loop lines
        staticLayout.draw(canvas.apply { 
            save()
            translate(MARGIN, currentY)
        })
        canvas.restore()

        pdfDocument.finishPage(page)

        val directory = File(context.filesDir, "Library")
        if (!directory.exists()) directory.mkdirs()

        val file = File(directory, "$fileName.pdf")
        return try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }
}

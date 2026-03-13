package com.sanship.utils

import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDFont
import java.awt.Color

/**
 * Utility functions for PDF generation
 */
object PDFUtils {

    fun drawText(
        contentStream: PDPageContentStream,
        text: String,
        x: Float,
        y: Float,
        font: PDFont,
        fontSize: Float,
        color: Color = Color.BLACK
    ) {
        contentStream.beginText()
        contentStream.setFont(font, fontSize)
        contentStream.setNonStrokingColor(color)
        contentStream.newLineAtOffset(x, y)
        contentStream.showText(text)
        contentStream.endText()
    }

    fun drawRightAlignedText(
        contentStream: PDPageContentStream,
        text: String,
        x: Float,
        y: Float,
        font: PDFont,
        fontSize: Float,
        color: Color = Color.BLACK
    ) {
        val width = font.getStringWidth(text) / 1000 * fontSize
        drawText(contentStream, text, x - width, y, font, fontSize, color)
    }

    fun drawCenteredText(
        contentStream: PDPageContentStream,
        text: String,
        centerX: Float,
        y: Float,
        font: PDFont,
        fontSize: Float,
        color: Color = Color.BLACK
    ) {
        val width = font.getStringWidth(text) / 1000 * fontSize
        drawText(contentStream, text, centerX - (width / 2), y, font, fontSize, color)
    }

    fun drawLine(
        contentStream: PDPageContentStream,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Color = Color.BLACK,
        lineWidth: Float = 1f
    ) {
        contentStream.setStrokingColor(color)
        contentStream.setLineWidth(lineWidth)
        contentStream.moveTo(x1, y1)
        contentStream.lineTo(x2, y2)
        contentStream.stroke()
    }

    fun drawRect(
        contentStream: PDPageContentStream,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        strokeColor: Color = Color.BLACK,
        fillColor: Color? = null,
        lineWidth: Float = 1f
    ) {
        if (fillColor != null) {
            contentStream.setNonStrokingColor(fillColor)
            contentStream.addRect(x, y, width, height)
            contentStream.fill()
        }
        
        contentStream.setStrokingColor(strokeColor)
        contentStream.setLineWidth(lineWidth)
        contentStream.addRect(x, y, width, height)
        contentStream.stroke()
    }
    
    /**
     * Draws text wrapped within a given width.
     * Returns the y-coordinate after the last line drawn.
     */
    fun drawWrappedText(
        contentStream: PDPageContentStream,
        text: String,
        x: Float,
        topY: Float,
        maxWidth: Float,
        font: PDFont,
        fontSize: Float,
        lineSpacing: Float = 10f,
        maxLines: Int = Int.MAX_VALUE,
        color: Color = Color.BLACK
    ): Float {
        var currentY = topY
        
        // 1. Split into physical lines (e.g. from newline chars)
        val paragraphs = text.split("\n")
        var linesDrawn = 0
        
        contentStream.setNonStrokingColor(color)
        contentStream.setFont(font, fontSize)
        
        for (paragraph in paragraphs) {
            // 2. Wrap each paragraph
            val wrappedLines = getWrappedText(paragraph, font, fontSize, maxWidth)
            
            for (line in wrappedLines) {
                if (linesDrawn >= maxLines) break
                
                contentStream.beginText()
                contentStream.newLineAtOffset(x, currentY)
                contentStream.showText(line)
                contentStream.endText()
                
                currentY -= lineSpacing
                linesDrawn++
            }
            if (linesDrawn >= maxLines) break
        }
        
        return currentY
    }

    // Helper for wrapping text inside a cell
    fun getWrappedText(text: String, font: PDFont, fontSize: Float, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = StringBuilder()
        
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            // calculate width safely
            val width = try {
                font.getStringWidth(testLine) / 1000 * fontSize
            } catch (e: Exception) { 0f }
            
            if (width > maxWidth) {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                } else {
                    // Word itself is longer than line
                    lines.add(word) 
                    currentLine = StringBuilder("")
                }
            } else {
                if (currentLine.isNotEmpty()) currentLine.append(" ")
                currentLine.append(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }
    
    fun drawImage(
        document: org.apache.pdfbox.pdmodel.PDDocument,
        contentStream: PDPageContentStream,
        imagePath: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        try {
            var file = java.io.File(imagePath)
            if (!file.exists()) {
                // Try classpath
                val resource = this::class.java.classLoader.getResource(imagePath)
                if (resource != null) {
                    file = java.io.File(resource.toURI())
                }
            }
            
            if (file.exists()) {
                val image = org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromFile(file.absolutePath, document)
                contentStream.drawImage(image, x, y, width, height)
            } else {
                println("Image not found: $imagePath")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

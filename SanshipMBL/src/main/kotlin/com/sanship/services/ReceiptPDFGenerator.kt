package com.sanship.services

import com.sanship.utils.PDFUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import java.awt.Color
import java.text.DecimalFormat
import java.io.File

object ReceiptPDFGenerator {

    data class ReceiptData(
        val receiptNo: String,
        val date: String,
        val receivedFrom: String,
        val amount: Double,
        val mode: String, // Bank/Cash
        val narration: String,
        val jobNo: String? = null
    )

    private val df = DecimalFormat("#,##0.00")

    fun generateReceiptPDF(data: ReceiptData, outputPath: String) {
        val document = PDDocument()
        try {
            // Use A5 or Half Page layout if possible? 
            // For simplicity, sticking to A4 with top margin or centered box.
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)

            val contentStream = PDPageContentStream(document, page)
            val pageWidth = PDRectangle.A4.width
            val pageHeight = PDRectangle.A4.height
            val margin = 50f
            
            val boxHeight = 400f // Half page receipt
            val startY = pageHeight - margin
            
            // Draw Main Border
            PDFUtils.drawRect(contentStream, margin, startY - boxHeight, pageWidth - 2 * margin, boxHeight)
            
            // Header
            var curY = startY - 30f
            val midX = pageWidth / 2
            
            PDFUtils.drawCenteredText(contentStream, "RECEIPT VOUCHER", midX, curY, PDType1Font.HELVETICA_BOLD, 18f)
            curY -= 25f
            PDFUtils.drawCenteredText(contentStream, "SANSHIP LOGISTICS", midX, curY, PDType1Font.HELVETICA_BOLD, 12f)
            curY -= 40f
            
            // Info Row (Receipt No | Date)
            PDFUtils.drawText(contentStream, "Receipt No: ${data.receiptNo}", margin + 20f, curY, PDType1Font.HELVETICA_BOLD, 10f)
            PDFUtils.drawRightAlignedText(contentStream, "Date: ${data.date}", pageWidth - margin - 20f, curY, PDType1Font.HELVETICA_BOLD, 10f)
            
            curY -= 30f
            PDFUtils.drawLine(contentStream, margin, curY, pageWidth - margin, curY)
            curY -= 30f
            
            // Body
            val labelX = margin + 20f
            val valX = margin + 120f
            
            PDFUtils.drawText(contentStream, "Received with thanks from:", labelX, curY, PDType1Font.HELVETICA, 10f)
            curY -= 20f
            PDFUtils.drawText(contentStream, data.receivedFrom, valX, curY, PDType1Font.HELVETICA_BOLD, 12f)
            
            curY -= 30f
            PDFUtils.drawText(contentStream, "The Sum of Rupees:", labelX, curY, PDType1Font.HELVETICA, 10f)
            curY -= 20f
            PDFUtils.drawText(contentStream, "INR ${convertAmountToWords(data.amount)} Only", valX, curY, PDType1Font.HELVETICA_BOLD_OBLIQUE, 11f)
            
            curY -= 30f
            PDFUtils.drawText(contentStream, "By Cash / Bank:", labelX, curY, PDType1Font.HELVETICA, 10f)
            PDFUtils.drawText(contentStream, data.mode, valX, curY, PDType1Font.HELVETICA_BOLD, 10f)
            
            curY -= 20f
            PDFUtils.drawText(contentStream, "Narration:", labelX, curY, PDType1Font.HELVETICA, 10f)
            PDFUtils.drawText(contentStream, data.narration, valX, curY, PDType1Font.HELVETICA, 10f)
            
            if (data.jobNo != null) {
                curY -= 20f
                PDFUtils.drawText(contentStream, "Against Job:", labelX, curY, PDType1Font.HELVETICA, 10f)
                PDFUtils.drawText(contentStream, data.jobNo, valX, curY, PDType1Font.HELVETICA, 10f)
            }
            
            curY -= 40f
            // Amount Box
            PDFUtils.drawRect(contentStream, margin + 20f, curY - 30f, 150f, 30f)
            PDFUtils.drawText(contentStream, "₹ ${df.format(data.amount)}/-", margin + 30f, curY - 20f, PDType1Font.HELVETICA_BOLD, 14f)
            
            // Signature
            PDFUtils.drawRightAlignedText(contentStream, "Authorized Signatory", pageWidth - margin - 20f, curY - 20f, PDType1Font.HELVETICA, 10f)
            
            contentStream.close()
            document.save(outputPath)
            println("✓ Receipt PDF generated: $outputPath")
            
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            document.close()
        }
    }

    private fun convertAmountToWords(amount: Double): String {
        return amount.toInt().toString() // Placeholder
    }
}

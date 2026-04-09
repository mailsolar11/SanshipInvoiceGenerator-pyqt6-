package com.sanship.services

import com.sanship.utils.PDFUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import java.text.DecimalFormat

object AccountingVoucherPdfService {

    data class VoucherData(
        val title: String,       // e.g. "RECEIPT VOUCHER"
        val voucherNo: String,
        val date: String,
        val mainLabel: String,   // e.g. "Received From:" or "Paid To:"
        val mainValue: String,
        val amount: Double,
        val mode: String,        // e.g. "Cash / Bank"
        val narration: String,
        val jobNo: String? = null,
        val isJournal: Boolean = false,
        // For Journal entries:
        val debits: List<Pair<String, Double>> = emptyList(),
        val credits: List<Pair<String, Double>> = emptyList()
    )

    private val df = DecimalFormat("#,##0.00")

    fun generateVoucherPDF(data: VoucherData, outputPath: String) {
        val document = PDDocument()
        try {
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)

            val contentStream = PDPageContentStream(document, page)
            val pageWidth = PDRectangle.A4.width
            val pageHeight = PDRectangle.A4.height
            val margin = 40f
            
            // Bold outer border (draw multiple rectangles for thickness)
            for (i in 0..2) {
                PDFUtils.drawRect(contentStream, margin + i, margin + i, pageWidth - 2 * margin - 2 * i, pageHeight - 2 * margin - 2 * i)
            }
            
            var curY = pageHeight - margin - 50f
            val midX = pageWidth / 2
            
            // Header
            PDFUtils.drawCenteredText(contentStream, "SANSHIP LOGISTICS", midX, curY, PDType1Font.HELVETICA_BOLD, 22f)
            curY -= 30f
            PDFUtils.drawCenteredText(contentStream, data.title, midX, curY, PDType1Font.HELVETICA_BOLD, 16f)
            curY -= 30f
            
            PDFUtils.drawLine(contentStream, margin, curY, pageWidth - margin, curY)
            curY -= 40f
            
            // Left Aligned Content Details
            val labelX = margin + 30f
            val valueX = margin + 200f
            
            val lineSpacing = 40f
            val labelFont = PDType1Font.HELVETICA_BOLD
            val valueFont = PDType1Font.HELVETICA
            val fontSize = 12f
            
            // Voucher No & Date
            PDFUtils.drawText(contentStream, "Voucher No:", labelX, curY, labelFont, fontSize)
            PDFUtils.drawText(contentStream, data.voucherNo, valueX, curY, valueFont, fontSize)
            curY -= lineSpacing
            
            PDFUtils.drawText(contentStream, "Date:", labelX, curY, labelFont, fontSize)
            PDFUtils.drawText(contentStream, data.date, valueX, curY, valueFont, fontSize)
            curY -= lineSpacing
            
            if (!data.isJournal) {
                // Main Party
                PDFUtils.drawText(contentStream, data.mainLabel, labelX, curY, labelFont, fontSize)
                PDFUtils.drawText(contentStream, data.mainValue, valueX, curY, valueFont, fontSize)
                curY -= lineSpacing
                
                // Mode
                PDFUtils.drawText(contentStream, "Mode (Cash/Bank):", labelX, curY, labelFont, fontSize)
                PDFUtils.drawText(contentStream, data.mode, valueX, curY, valueFont, fontSize)
                curY -= lineSpacing
                
                // Amount In Words
                PDFUtils.drawText(contentStream, "Amount in Words:", labelX, curY, labelFont, fontSize)
                val words = "INR " + convertAmountToWords(data.amount.toLong()) + " Only"
                PDFUtils.drawText(contentStream, words, valueX, curY, PDType1Font.HELVETICA_OBLIQUE, fontSize)
                curY -= lineSpacing
                
            } else {
                // Journal specific layout
                PDFUtils.drawText(contentStream, "Debits:", labelX, curY, labelFont, fontSize)
                curY -= 20f
                data.debits.forEach { (acc, amt) ->
                    PDFUtils.drawText(contentStream, "- $acc", labelX + 20f, curY, valueFont, 11f)
                    PDFUtils.drawText(contentStream, df.format(amt), valueX + 150f, curY, valueFont, 11f)
                    curY -= 20f
                }
                curY -= 10f
                PDFUtils.drawText(contentStream, "Credits:", labelX, curY, labelFont, fontSize)
                curY -= 20f
                data.credits.forEach { (acc, amt) ->
                    PDFUtils.drawText(contentStream, "- $acc", labelX + 20f, curY, valueFont, 11f)
                    PDFUtils.drawText(contentStream, df.format(amt), valueX + 150f, curY, valueFont, 11f)
                    curY -= 20f
                }
                curY -= 20f
            }
            
            // Narration
            PDFUtils.drawText(contentStream, "Narration:", labelX, curY, labelFont, fontSize)
            
            // Wrap narration text
            val wrappedNarration = PDFUtils.getWrappedText(data.narration, valueFont, fontSize, pageWidth - valueX - margin - 20f)
            if (wrappedNarration.isEmpty()) {
                PDFUtils.drawText(contentStream, "-", valueX, curY, valueFont, fontSize)
            } else {
                wrappedNarration.forEachIndexed { index, line ->
                    if (index > 0) curY -= 15f
                    PDFUtils.drawText(contentStream, line, valueX, curY, valueFont, fontSize)
                }
            }
            curY -= lineSpacing
            
            // Job No
            if (data.jobNo != null && data.jobNo.isNotBlank()) {
                PDFUtils.drawText(contentStream, "Against Job No:", labelX, curY, labelFont, fontSize)
                PDFUtils.drawText(contentStream, data.jobNo, valueX, curY, valueFont, fontSize)
                curY -= lineSpacing
            }
            
            curY -= 20f
            
            // Amount Box at bottom left
            PDFUtils.drawRect(contentStream, labelX, curY - 30f, 200f, 40f)
            PDFUtils.drawText(contentStream, "Rs. ${df.format(data.amount)}/-", labelX + 10f, curY - 15f, PDType1Font.HELVETICA_BOLD, 18f)
            
            // Authorised Signatory at bottom right
            PDFUtils.drawText(contentStream, "Authorised Signatory", pageWidth - margin - 150f, curY - 15f, labelFont, 12f)
            
            contentStream.close()
            document.save(outputPath)
            
        } finally {
            document.close()
        }
    }

    private fun convertAmountToWords(number: Long): String {
        if (number == 0L) return "Zero"
        
        val ones = arrayOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
        val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")
        
        var num = number
        val parts = mutableListOf<String>()
        
        if (num >= 10000000) {
            parts.add("${convertAmountToWords(num / 10000000)} Crore")
            num %= 10000000
        }
        if (num >= 100000) {
            parts.add("${convertAmountToWords(num / 100000)} Lakh")
            num %= 100000
        }
        if (num >= 1000) {
            parts.add("${convertAmountToWords(num / 1000)} Thousand")
            num %= 1000
        }
        if (num >= 100) {
            parts.add("${ones[num.toInt() / 100]} Hundred")
            num %= 100
        }
        if (num >= 20) {
            val tensPart = tens[num.toInt() / 10]
            val onesPart = ones[(num % 10).toInt()]
            parts.add(if (onesPart.isEmpty()) tensPart else "$tensPart $onesPart")
        } else if (num > 0) {
            parts.add(ones[num.toInt()])
        }
        
        return parts.joinToString(" ")
    }
}

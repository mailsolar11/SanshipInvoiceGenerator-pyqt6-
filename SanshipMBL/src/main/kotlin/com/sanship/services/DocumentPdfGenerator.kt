package com.sanship.services

import com.sanship.utils.PDFUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.PDPageContentStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

object DocumentPdfGenerator {
    
    /** Strip any character that Helvetica/WinAnsiEncoding cannot render (tabs, control chars, etc.) */
    private fun sanitize(text: String): String =
        text.replace("\t", "  ").replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "").trim()

    fun generateArrivalNotice(jobData: Map<String, String>, outputPath: String) {
        val document = PDDocument()
        try {
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val stream = PDPageContentStream(document, page)

            val margin = 50f
            var yPos = page.mediaBox.height - margin

            // Header
            PDFUtils.drawText(stream, "ARRIVAL NOTICE", margin, yPos, PDType1Font.HELVETICA_BOLD, 24f)
            yPos -= 30f
            PDFUtils.drawText(stream, "Date: ${SimpleDateFormat("dd-MMM-yyyy").format(Date())}", margin, yPos, PDType1Font.HELVETICA, 12f)
            yPos -= 40f

            // Consignee
            PDFUtils.drawText(stream, "To Consignee:", margin, yPos, PDType1Font.HELVETICA_BOLD, 12f)
            yPos -= 15f
            PDFUtils.drawText(stream, sanitize(jobData["consignee"] ?: "N/A"), margin, yPos, PDType1Font.HELVETICA, 12f)
            yPos -= 40f

            PDFUtils.drawText(stream, "Dear Sir/Madam,", margin, yPos, PDType1Font.HELVETICA, 12f)
            yPos -= 20f
            PDFUtils.drawText(stream, "Please be informed that the following shipment is expected to arrive.", margin, yPos, PDType1Font.HELVETICA, 12f)
            yPos -= 30f

            // Details Table
            val labels = listOf("Job No:", "MBL No:", "Vessel / Flight:", "POL:", "POD:", "ETA:", "Weight:", "Volume:", "Packages:")
            val values = listOf(
                sanitize(jobData["job_no"] ?: ""),
                sanitize(jobData["mbl_no"] ?: ""),
                sanitize(jobData["vessel_flight"] ?: ""),
                sanitize(jobData["pol"] ?: ""),
                sanitize(jobData["pod"] ?: ""),
                sanitize(jobData["eta"] ?: ""),
                sanitize("${jobData["gross_weight"] ?: ""} / ${jobData["net_weight"] ?: ""}"),
                sanitize(jobData["volume_cbm"] ?: ""),
                sanitize(jobData["packages"] ?: "")
            )

            for (i in labels.indices) {
                PDFUtils.drawText(stream, labels[i], margin, yPos, PDType1Font.HELVETICA_BOLD, 12f)
                PDFUtils.drawText(stream, values[i], margin + 120f, yPos, PDType1Font.HELVETICA, 12f)
                yPos -= 20f
            }

            yPos -= 30f
            PDFUtils.drawText(stream, "Please arrange for the surrender of Original Bills of Lading and payment of all", margin, yPos, PDType1Font.HELVETICA, 12f)
            yPos -= 15f
            PDFUtils.drawText(stream, "outstanding charges prior to collection of the Delivery Order.", margin, yPos, PDType1Font.HELVETICA, 12f)

            yPos -= 60f
            PDFUtils.drawText(stream, "Authorized Signatory", margin, yPos, PDType1Font.HELVETICA_BOLD, 12f)

            stream.close()
            document.save(File(outputPath))
        } finally {
            document.close()
        }
    }

    fun generateDeliveryOrder(jobData: Map<String, String>, outputPath: String) {
        val document = PDDocument()
        try {
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val stream = PDPageContentStream(document, page)

            val margin = 50f
            var yPos = page.mediaBox.height - margin
            val contentWidth = page.mediaBox.width - 2 * margin

            // Header
            PDFUtils.drawText(stream, "DELIVERY ORDER", margin, yPos, PDType1Font.HELVETICA_BOLD, 24f)
            yPos -= 30f
            PDFUtils.drawText(stream, "Date: ${SimpleDateFormat("dd-MMM-yyyy").format(Date())}", margin, yPos, PDType1Font.HELVETICA, 12f)
            yPos -= 40f

            // Consignee
            PDFUtils.drawText(stream, "To The Master / Agent:", margin, yPos, PDType1Font.HELVETICA_BOLD, 12f)
            yPos -= 15f
            PDFUtils.drawText(stream, jobData["consignee"] ?: "N/A", margin, yPos, PDType1Font.HELVETICA, 12f)
            yPos -= 40f

            PDFUtils.drawText(stream, "Dear Sir/Madam,", margin, yPos, PDType1Font.HELVETICA, 12f)
            yPos -= 20f
            PDFUtils.drawText(stream, "Please deliver the following cargo to the bearer of this order.", margin, yPos, PDType1Font.HELVETICA, 12f)
            yPos -= 30f

            // Details Table
            val labels = listOf("Job No:", "MBL No:", "Vessel / Flight:", "POL:", "POD:", "ETA:", "Weight:", "Volume:", "Packages:")
            val values = listOf(
                sanitize(jobData["job_no"] ?: ""),
                sanitize(jobData["mbl_no"] ?: ""),
                sanitize(jobData["vessel_flight"] ?: ""),
                sanitize(jobData["pol"] ?: ""),
                sanitize(jobData["pod"] ?: ""),
                sanitize(jobData["eta"] ?: ""),
                sanitize("${jobData["gross_weight"] ?: ""} / ${jobData["net_weight"] ?: ""}"),
                sanitize(jobData["volume_cbm"] ?: ""),
                sanitize(jobData["packages"] ?: "")
            )

            for (i in labels.indices) {
                PDFUtils.drawText(stream, labels[i], margin, yPos, PDType1Font.HELVETICA_BOLD, 12f)
                PDFUtils.drawText(stream, values[i], margin + 120f, yPos, PDType1Font.HELVETICA, 12f)
                yPos -= 20f
            }

            yPos -= 30f
            PDFUtils.drawText(stream, "All charges have been collected.", margin, yPos, PDType1Font.HELVETICA, 12f)

            yPos -= 60f
            PDFUtils.drawText(stream, "Authorized Signatory", margin, yPos, PDType1Font.HELVETICA_BOLD, 12f)

            stream.close()
            document.save(File(outputPath))
        } finally {
            document.close()
        }
    }
}

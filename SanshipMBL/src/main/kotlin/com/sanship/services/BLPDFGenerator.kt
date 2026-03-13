package com.sanship.services

import com.sanship.models.BLData
import com.sanship.models.Container
import com.sanship.models.HBLInstruction
import com.sanship.utils.PDFUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import java.awt.Color
import java.io.File
import java.text.DecimalFormat

object BLPDFGenerator {

    fun generateBL(data: BLData, outputPath: String) {
        val document = PDDocument()
        try {
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)

            val contentStream = PDPageContentStream(document, page)
            val pageWidth = PDRectangle.A4.width
            val pageHeight = PDRectangle.A4.height
            val margin = 30f
            
            // Draw Layout Frames
            drawBLLayout(contentStream, data.instruction, margin, pageHeight - margin, pageWidth)
            
            // Draw Cargo Details
            drawCargoDetails(contentStream, data, margin, pageHeight - margin - 350f, pageWidth)
            
            contentStream.close()
            document.save(outputPath)
            println("✓ BL PDF generated: $outputPath")
        } finally {
            document.close()
        }
    }

    private fun drawBLLayout(stream: PDPageContentStream, hbl: HBLInstruction, x: Float, topY: Float, pageWidth: Float) {
        val width = pageWidth - 2 * x
        val midX = pageWidth / 2
        
        // 1. Shipper (Top Left)
        PDFUtils.drawRect(stream, x, topY - 80f, midX - x, 80f)
        PDFUtils.drawText(stream, "Shipper", x + 5f, topY - 12f, PDType1Font.HELVETICA_BOLD, 8f)
        drawwrappedText(stream, hbl.shipperText, x + 5f, topY - 25f, PDType1Font.HELVETICA, 8f, 250f)

        // 2. BL No & Ref (Top Right)
        PDFUtils.drawRect(stream, midX, topY - 80f, midX - x, 80f)
        PDFUtils.drawText(stream, "Bill of Lading No:", midX + 5f, topY - 12f, PDType1Font.HELVETICA_BOLD, 8f)
        PDFUtils.drawText(stream, hbl.hblNo, midX + 5f, topY - 25f, PDType1Font.HELVETICA_BOLD, 12f)
        
        PDFUtils.drawText(stream, "Booking Ref:", midX + 5f, topY - 45f, PDType1Font.HELVETICA_BOLD, 8f)
        PDFUtils.drawText(stream, hbl.mblNo, midX + 5f, topY - 58f, PDType1Font.HELVETICA, 10f)

        // 3. Consignee (Left)
        var curY = topY - 80f
        PDFUtils.drawRect(stream, x, curY - 80f, midX - x, 80f)
        PDFUtils.drawText(stream, "Consignee", x + 5f, curY - 12f, PDType1Font.HELVETICA_BOLD, 8f)
        drawwrappedText(stream, hbl.consigneeText, x + 5f, curY - 25f, PDType1Font.HELVETICA, 8f, 250f)
        
        // 4. Notify Party (Right) -> Doing Forwarding Agent / References here often, but standard BL puts Notify below Consignee
        // Let's put Delivery Agent or Notify on Right? Standard layout usually has Notify below Consignee on Left too.
        // Let's stick to standard: Consignee Left, Notify Left below it.
        // Right side usually has Agent details.
        
        PDFUtils.drawRect(stream, midX, curY - 80f, midX - x, 80f)
        PDFUtils.drawText(stream, "Delivery Agent", midX + 5f, curY - 12f, PDType1Font.HELVETICA_BOLD, 8f)
        drawwrappedText(stream, hbl.deliveryAgentText, midX + 5f, curY - 25f, PDType1Font.HELVETICA, 8f, 250f)

        curY -= 80f // Now at topY - 160
        
        // 5. Notify Party (Left)
        PDFUtils.drawRect(stream, x, curY - 80f, midX - x, 80f)
        PDFUtils.drawText(stream, "Notify Party", x + 5f, curY - 12f, PDType1Font.HELVETICA_BOLD, 8f)
        drawwrappedText(stream, hbl.notifyPartyText, x + 5f, curY - 25f, PDType1Font.HELVETICA, 8f, 250f)
        
        // 6. Ports (Right)
        PDFUtils.drawRect(stream, midX, curY - 80f, midX - x, 80f)
        // Split into 4 quadrants
        val qH = 40f
        val qW = (midX - x) / 2
        
        // Pre-Carriage | Place of Receipt
        PDFUtils.drawLine(stream, midX, curY - qH, pageWidth - x, curY - qH)
        PDFUtils.drawLine(stream, midX + qW, curY, midX + qW, curY - 80f)
        
        PDFUtils.drawText(stream, "Pre-Carriage By", midX + 5f, curY - 10f, PDType1Font.HELVETICA_BOLD, 7f)
        PDFUtils.drawText(stream, "Place of Receipt", midX + qW + 5f, curY - 10f, PDType1Font.HELVETICA_BOLD, 7f)
        PDFUtils.drawText(stream, hbl.placeOfReceipt, midX + qW + 5f, curY - 25f, PDType1Font.HELVETICA, 8f)
        
        // Vessel | Load Port
        val row2Y = curY - 40f
        PDFUtils.drawText(stream, "Vessel / Voy", midX + 5f, row2Y - 10f, PDType1Font.HELVETICA_BOLD, 7f)
        // PDFUtils.drawText(stream, hbl.vessel, midX + 5f, row2Y - 25f, PDType1Font.HELVETICA, 8f) // Vessel in Job?
        
        PDFUtils.drawText(stream, "Port of Loading", midX + qW + 5f, row2Y - 10f, PDType1Font.HELVETICA_BOLD, 7f)
        PDFUtils.drawText(stream, hbl.portOfLoading, midX + qW + 5f, row2Y - 25f, PDType1Font.HELVETICA, 8f)
        
        curY -= 80f // Now at topY - 240
        
        // 7. Discharge / Delivery
        PDFUtils.drawRect(stream, x, curY - 40f, midX - x, 40f) // Port of Discharge
        PDFUtils.drawText(stream, "Port of Discharge", x + 5f, curY - 10f, PDType1Font.HELVETICA_BOLD, 7f)
        PDFUtils.drawText(stream, hbl.portOfDischarge, x + 5f, curY - 25f, PDType1Font.HELVETICA, 9f)

        PDFUtils.drawRect(stream, midX, curY - 40f, midX - x, 40f) // Place of Delivery
        PDFUtils.drawText(stream, "Place of Delivery", midX + 5f, curY - 10f, PDType1Font.HELVETICA_BOLD, 7f)
        PDFUtils.drawText(stream, hbl.placeOfDelivery, midX + 5f, curY - 25f, PDType1Font.HELVETICA, 9f)
        
        // Header for Cargo Table
        curY -= 40f
        PDFUtils.drawRect(stream, x, curY - 20f, width, 20f)
        val col1 = x + 100f
        val col2 = x + 180f
        val col3 = pageWidth - x - 150f
        val col4 = pageWidth - x - 80f
        
        PDFUtils.drawText(stream, "Marks & Nos", x + 5f, curY - 14f, PDType1Font.HELVETICA_BOLD, 8f)
        PDFUtils.drawText(stream, "No of Pkgs", col1 + 5f, curY - 14f, PDType1Font.HELVETICA_BOLD, 8f)
        PDFUtils.drawText(stream, "Description of Packages & Goods", col2 + 5f, curY - 14f, PDType1Font.HELVETICA_BOLD, 8f)
        PDFUtils.drawText(stream, "Gross Weight", col3 + 5f, curY - 14f, PDType1Font.HELVETICA_BOLD, 8f)
        PDFUtils.drawText(stream, "Measurement", col4 + 5f, curY - 14f, PDType1Font.HELVETICA_BOLD, 8f)
    }

    private fun drawCargoDetails(stream: PDPageContentStream, data: BLData, x: Float, y: Float, pageWidth: Float) {
        // Draw the main body text
        val col1 = x + 100f
        val col2 = x + 180f
        val col3 = pageWidth - x - 150f
        val col4 = pageWidth - x - 80f
        
        var curY = y
        
        // 1. Main Marks & Desc
        drawwrappedText(stream, data.instruction.marksAndNumbers, x + 5f, curY, PDType1Font.HELVETICA, 8f, 90f)
        
        // Total Pkgs
        PDFUtils.drawText(stream, "${data.totalPackages} PKGS", col1 + 5f, curY, PDType1Font.HELVETICA_BOLD, 9f)
        
        // Description
        drawwrappedText(stream, data.instruction.descriptionOfGoods, col2 + 5f, curY, PDType1Font.HELVETICA, 9f, 250f)
        
        // Totals
        PDFUtils.drawText(stream, "${data.totalGrossWeight} KGS", col3 + 5f, curY, PDType1Font.HELVETICA_BOLD, 9f)
        PDFUtils.drawText(stream, "${data.totalVolume} CBM", col4 + 5f, curY, PDType1Font.HELVETICA_BOLD, 9f)
        
        curY -= 100f // Space for main desc
        
        // 2. Container Details
        PDFUtils.drawText(stream, "CONTAINER DETIALS:", col2 + 5f, curY, PDType1Font.HELVETICA_BOLD, 8f)
        curY -= 12f
        
        data.containers.forEach { c ->
             val line = "${c.containerNo} / ${c.sealNo} / ${c.containerType} / ${c.packages} PKGS / ${c.grossWeight} KGS"
             PDFUtils.drawText(stream, line, col2 + 5f, curY, PDType1Font.HELVETICA, 8f)
             curY -= 10f
        }
        
        // 3. Shipped on Board Date
        val bottomY = 150f
        PDFUtils.drawText(stream, "SHIPPED ON BOARD DATE: ${data.instruction.shippedOnBoardDate}", x + 200f, bottomY + 50f, PDType1Font.HELVETICA_BOLD, 10f)
        
        // 4. By Line
        PDFUtils.drawRightAlignedText(stream, "For SANSHIP LOGISTICS", pageWidth - x, bottomY + 20f, PDType1Font.HELVETICA_BOLD, 10f)
        PDFUtils.drawRightAlignedText(stream, "As Agents", pageWidth - x, bottomY + 5f, PDType1Font.HELVETICA, 8f)
    }

    private fun drawwrappedText(stream: PDPageContentStream, text: String, x: Float, y: Float, font: PDType1Font, fontSize: Float, width: Float) {
        val lines = PDFUtils.getWrappedText(text, font, fontSize, width)
        var curY = y
        lines.forEach { line ->
            PDFUtils.drawText(stream, line, x, curY, font, fontSize)
            curY -= (fontSize + 2f)
        }
    }
}

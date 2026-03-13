package com.sanship.utils

import com.sanship.data.MblData
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import java.awt.Color

// ===========================================================================
//  STATIONERY LAYOUT VECTOR MAP
//  (0,0) is Bottom-Left.
// ===========================================================================
object StationeryLayout {

    // --- Grid X-Coordinates (Global Left Shift -20f) ---
    const val START_X = 30f
    const val WIDTH = 545f
    const val END_X = START_X + WIDTH

    const val CENTER_X = START_X + (WIDTH / 2)

    // Gap padding (+4f) preserved from previous request
    const val Q1_X = START_X + (WIDTH * 0.30f) + 4f
    const val Q3_X = START_X + (WIDTH * 0.75f)

    // --- Header Section Y ---
    const val CONSIGNOR_Y = 780f
    const val CONSIGNEE_Y = 695f
    const val NOTIFY_Y = 610f

    // Top Right Boxes
    const val BOX_X = END_X - 130f - 5f
    const val MTD_Y = 785f
    const val REF_Y = 765f

    // --- Routing Grid Y ---
    const val ROW_1_Y = 528f
    const val DELIVERY_AGENT_Y = 540f
    const val ROW_2_Y = 493f
    const val ROW_3_Y = 455f

    // --- Cargo Table X (UPDATED) ---
    const val COL_CONT_X = START_X          // 30f

    // Container ends at 110f (Width 80f). Marks starts here.
    const val COL_MARKS_X = 110f

    // Marks ends at 220f. Description starts here.
    const val COL_DESC_X = 220f

    const val COL_GROSS_X = END_X - 130f
    const val COL_MEASURE_X = END_X - 60f

    // --- Cargo Y ---
    const val CARGO_START_Y = 415f

    // --- Footer Y ---
    const val FOOTER_TOP_Y = 110f
    const val FOOTER_MID_Y = 80f
}

// ===========================================================================
//  GHOST GENERATOR (Data Only)
// ===========================================================================
object PrintStationeryGenerator {

    fun generatePrintOverlay(filePath: String, data: MblData) {
        val document = PDDocument()
        drawTextLayer(document, data)
        if (data.cargoItems.size > 1) {
            drawAnnexurePage(document, data)
        }
        try {
            document.save(filePath)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            document.close()
        }
    }

    private fun drawTextLayer(document: PDDocument, data: MblData) {
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        val contentStream = PDPageContentStream(document, page)

        val layout = StationeryLayout
        val pad = 4f

        // ============================
        // 1. HEADER SECTION
        // ============================
        drawSmartWrappedText(contentStream, data.consignor, layout.START_X + pad, layout.CONSIGNOR_Y, 260f, maxLines = 7)
        drawSmartWrappedText(contentStream, data.consignee, layout.START_X + pad, layout.CONSIGNEE_Y, 260f, maxLines = 7)
        drawSmartWrappedText(contentStream, data.notifyAddress, layout.START_X + pad, layout.NOTIFY_Y, 260f, maxLines = 7)

        // ============================
        // 2. TOP RIGHT (MTD & REF)
        // ============================
        drawSmartWrappedText(contentStream, data.mtdNumber, layout.BOX_X + pad, layout.MTD_Y, 130f, maxLines = 2)
        drawSmartWrappedText(contentStream, data.refNumber, layout.BOX_X + pad, layout.REF_Y, 130f, maxLines = 2)

        // ============================
        // 3. ROUTING GRID
        // ============================
        // Row 1
        drawSmartWrappedText(contentStream, data.preCarriage, layout.START_X + pad, layout.ROW_1_Y, (layout.Q1_X - layout.START_X - pad), maxLines = 2)
        drawSmartWrappedText(contentStream, data.placeReceipt, layout.Q1_X + pad, layout.ROW_1_Y, (layout.CENTER_X - layout.Q1_X - pad), maxLines = 2)

        // Delivery Agent
        drawSmartWrappedText(contentStream, data.deliveryAgent, layout.CENTER_X + pad, layout.DELIVERY_AGENT_Y, (layout.END_X - layout.CENTER_X - pad), fontSize = 7f, maxLines = 5)

        // Row 2
        val vesselWidth = 100f
        drawSmartWrappedText(contentStream, data.vessel, layout.START_X + pad, layout.ROW_2_Y, vesselWidth, maxLines = 2)
        drawText(contentStream, data.voyNumber, layout.START_X + vesselWidth + 10f, layout.ROW_2_Y)
        drawSmartWrappedText(contentStream, data.portLoading, layout.Q1_X + pad, layout.ROW_2_Y, (layout.CENTER_X - layout.Q1_X - pad), maxLines = 2)

        // Row 3
        drawSmartWrappedText(contentStream, data.portDischarge, layout.START_X + pad, layout.ROW_3_Y, (layout.Q1_X - layout.START_X - pad), maxLines = 2)
        drawSmartWrappedText(contentStream, data.placeDelivery, layout.Q1_X + pad, layout.ROW_3_Y, (layout.CENTER_X - layout.Q1_X - pad), maxLines = 2)
        drawSmartWrappedText(contentStream, data.mode, layout.CENTER_X + pad, layout.ROW_3_Y, (layout.Q3_X - layout.CENTER_X - pad), maxLines = 1)
        drawSmartWrappedText(contentStream, data.route, layout.Q3_X + pad, layout.ROW_3_Y, (layout.END_X - layout.Q3_X - pad), maxLines = 1)

        // ============================
        // 4. CARGO SECTION
        // ============================
        val safeMaxLines = 27

        // Goods Description
        val descWidth = layout.COL_GROSS_X - layout.COL_DESC_X - pad
        drawSmartWrappedText(contentStream, data.goodsDescription, layout.COL_DESC_X, layout.CARGO_START_Y, descWidth, maxLines = safeMaxLines)

        // Marks & Numbers
        drawSmartWrappedText(contentStream, data.marksNumbers, 112f, layout.CARGO_START_Y, 106f, maxLines = safeMaxLines)

        if (data.cargoItems.isNotEmpty()) {
            val item = data.cargoItems[0]

            // Container No
            drawSmartWrappedText(contentStream, item.containerNo, layout.COL_CONT_X + pad, layout.CARGO_START_Y, (layout.COL_MARKS_X - layout.COL_CONT_X - pad), isBold = true, maxLines = safeMaxLines)

            // Weights
            drawSmartWrappedText(contentStream, item.grossWeight, layout.COL_GROSS_X + pad, layout.CARGO_START_Y, (layout.COL_MEASURE_X - layout.COL_GROSS_X - pad), maxLines = safeMaxLines)
            drawSmartWrappedText(contentStream, item.measurement, layout.COL_MEASURE_X + pad, layout.CARGO_START_Y, (layout.END_X - layout.COL_MEASURE_X - pad), maxLines = safeMaxLines)

            // Seals
            var sealY = layout.CARGO_START_Y - 35f
            drawText(contentStream, "Custom Seal:", layout.START_X + pad, sealY, true, 5f)
            sealY -= 10f
            drawSmartWrappedText(contentStream, data.mainCustomsSeal, layout.START_X + pad, sealY, (layout.COL_MARKS_X - layout.START_X - pad), fontSize = 5f)

            sealY -= 20f
            drawText(contentStream, "Agent Seal:", layout.START_X + pad, sealY, true, 5f)
            sealY -= 10f
            drawSmartWrappedText(contentStream, data.mainAgentSeal, layout.START_X + pad, sealY, (layout.COL_MARKS_X - layout.START_X - pad), fontSize = 5f)

            if (data.cargoItems.size > 1) {
                sealY -= 20f
                drawText(contentStream, "AND ${data.cargoItems.size - 1} OTHERS", layout.START_X + pad, sealY, true, 5f)
                sealY -= 10f
                drawText(contentStream, "(SEE ANNEXURE)", layout.START_X + pad, sealY, true, 5f)
            }
        }

        // ============================
        // 5. FOOTER SECTION
        // ============================

        // --- REQUESTED UPDATE: All 4 footer fields aligned at 80f ---
        val footerDataY = 80f

        // 1. Freight & Charges Amount
        drawSmartWrappedText(contentStream, data.freightAmount, layout.START_X + pad, footerDataY, (layout.Q1_X - layout.START_X - pad), maxLines = 3)

        // 2. Freight Payable at
        drawSmartWrappedText(contentStream, data.freightPayableAt, layout.Q1_X + pad, footerDataY, (layout.CENTER_X - layout.Q1_X - pad), maxLines = 3)

        // 3. Number of Original MTD(s)
        drawSmartWrappedText(contentStream, data.originalMtds, layout.CENTER_X + pad, footerDataY, (layout.Q3_X - layout.CENTER_X - pad), maxLines = 3)

        // 4. Place and Date of Issue
        drawSmartWrappedText(contentStream, data.placeDateIssue, layout.Q3_X + pad, footerDataY, (layout.END_X - layout.Q3_X - pad), maxLines = 3)

        // 5. Other Particulars (Moved down to 50f to avoid overlap with the data above)
        drawSmartWrappedText(contentStream, data.otherParticulars, layout.START_X + pad, 50f, 300f, maxLines = 4)

        contentStream.close()
    }

    // --- ANNEXURE PAGE (Unchanged) ---
    private fun drawAnnexurePage(document: PDDocument, data: MblData) {
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        val contentStream = PDPageContentStream(document, page)

        val startX = 25f
        val startY = 800f

        drawTextCentered(contentStream, "ANNEXURE FOR BL NO - ${data.mtdNumber}", startX, startX + 535f, startY, fontSize = 11f, isBold = true)

        var y = startY - 30f
        val cols = floatArrayOf(startX, startX + 25, startX + 95, startX + 155, startX + 205, startX + 255, startX + 340, startX + 425, startX + 485, startX + 535f)

        val headers = listOf("Sr.no", "Container No", "No of Pkgs", "Net Wgt", "Gross Wgt", "A-Seal no", "C-Seal no", "SB No", "Date")

        contentStream.setStrokingColor(Color.BLACK); contentStream.setLineWidth(1f)
        contentStream.addRect(startX, y - 20f, 535f, 20f); contentStream.stroke()

        for (i in headers.indices) {
            drawText(contentStream, headers[i], cols[i] + 2, y - 13, isBold = true, fontSize = 7f)
            if (i > 0) drawLine(contentStream, cols[i], y, cols[i], y - 20f, true)
        }
        y -= 20f

        data.cargoItems.forEachIndexed { index, item ->
            contentStream.addRect(startX, y - 20f, 535f, 20f); contentStream.stroke()
            val rowData = listOf((index + 1).toString(), item.containerNo, item.pkgCount, item.netWeight, item.grossWeight, item.customsSeal, item.agentSeal, item.sbNumber, item.sbDate)
            for (i in rowData.indices) {
                drawSmartWrappedText(contentStream, rowData[i], cols[i] + 2, y - 13, (cols[i+1] - cols[i] - 4), fontSize = 7f, maxLines = 1)
                if (i > 0) drawLine(contentStream, cols[i], y, cols[i], y - 20f, true)
            }
            y -= 20f
        }

        contentStream.addRect(startX, y - 20f, 535f, 20f); contentStream.stroke()
        drawText(contentStream, "TOTAL", cols[1] + 5, y - 13, true)
        val totalNet = data.cargoItems.sumOf { it.netWeight.toDoubleOrNull() ?: 0.0 }
        val totalGross = data.cargoItems.sumOf { it.grossWeight.toDoubleOrNull() ?: 0.0 }
        val totalPkgs = data.cargoItems.sumOf { it.pkgCount.toIntOrNull() ?: 0 }

        drawText(contentStream, totalPkgs.toString(), cols[2] + 2, y - 13, true)
        drawText(contentStream, totalNet.toString(), cols[3] + 2, y - 13, true)
        drawText(contentStream, totalGross.toString(), cols[4] + 2, y - 13, true)

        for (i in 1 until cols.size) drawLine(contentStream, cols[i], y, cols[i], y - 20f, true)

        contentStream.close()
    }

    // --- HELPER FUNCTIONS ---
    private fun drawLine(s: PDPageContentStream, sx: Float, sy: Float, ex: Float, ey: Float = sy, isVertical: Boolean = false) {
        s.moveTo(sx, sy); s.lineTo(if (isVertical) sx else ex, if (isVertical) ey else sy); s.stroke()
    }

    private fun drawText(s: PDPageContentStream, t: String?, x: Float, y: Float, isBold: Boolean = false, fontSize: Float = 8f) {
        if (t.isNullOrEmpty()) return
        s.beginText()
        s.newLineAtOffset(x, y)
        s.setFont(if(isBold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA, fontSize)
        s.setNonStrokingColor(Color.BLACK)
        s.showText(t)
        s.endText()
    }

    private fun drawTextCentered(s: PDPageContentStream, t: String, sx: Float, ex: Float, y: Float, fontSize: Float = 9f, isBold: Boolean = false) {
        val font = if (isBold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
        val w = font.getStringWidth(t)/1000*fontSize
        s.beginText(); s.setFont(font, fontSize); s.newLineAtOffset(sx + (ex-sx-w)/2, y); s.showText(t); s.endText()
    }

    private fun drawSmartWrappedText(
        stream: PDPageContentStream,
        text: String?,
        x: Float,
        startY: Float,
        allowedWidth: Float,
        fontSize: Float = 7f,
        leading: Float = 11f,
        isBold: Boolean = false,
        maxLines: Int = 100
    ) {
        if (text.isNullOrEmpty()) return
        val font = if (isBold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
        stream.setFont(font, fontSize)
        stream.setNonStrokingColor(Color.BLACK)
        fun getStrWidth(s: String): Float = font.getStringWidth(s) / 1000 * fontSize

        val sanitized = text.replace("\r", "")
        val paragraphs = sanitized.split("\n")
        var currentY = startY
        var linesPrinted = 0

        for (p in paragraphs) {
            val words = p.split(" ")
            var line = ""
            for (word in words) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                if (getStrWidth(testLine) < allowedWidth) {
                    line = testLine
                } else {
                    if (line.isNotEmpty()) {
                        if (linesPrinted >= maxLines) return
                        stream.beginText(); stream.newLineAtOffset(x, currentY); stream.showText(line); stream.endText()
                        currentY -= leading
                        linesPrinted++
                    }
                    if (getStrWidth(word) < allowedWidth) {
                        line = word
                    } else {
                        var remainingWord = word
                        while (getStrWidth(remainingWord) > allowedWidth) {
                            if (linesPrinted >= maxLines) return
                            var splitIndex = 1
                            while (splitIndex < remainingWord.length && getStrWidth(remainingWord.substring(0, splitIndex + 1)) < allowedWidth) splitIndex++
                            val chunk = remainingWord.substring(0, splitIndex)
                            stream.beginText(); stream.newLineAtOffset(x, currentY); stream.showText(chunk); stream.endText()
                            currentY -= leading
                            linesPrinted++
                            remainingWord = remainingWord.substring(splitIndex)
                        }
                        line = remainingWord
                    }
                }
            }
            if (line.isNotEmpty()) {
                if (linesPrinted >= maxLines) return
                stream.beginText(); stream.newLineAtOffset(x, currentY); stream.showText(line); stream.endText()
                currentY -= leading
                linesPrinted++
            }
        }
    }
}
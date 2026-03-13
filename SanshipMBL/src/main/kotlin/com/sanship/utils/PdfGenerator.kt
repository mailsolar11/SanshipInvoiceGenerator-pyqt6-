package com.sanship.utils

import com.sanship.data.MblData
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import java.awt.Color
import javax.imageio.ImageIO

object PdfGenerator {

    fun generatePdf(filePath: String, data: MblData) {
        val document = PDDocument()
        drawMainPage(document, data)
        if (data.cargoItems.size > 1) {
            drawAnnexurePage(document, data)
        }
        saveDocument(document, filePath)
    }

    private fun saveDocument(document: PDDocument, filePath: String) {
        try {
            document.save(filePath)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            document.close()
        }
    }

    private fun drawMainPage(document: PDDocument, data: MblData) {
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        val contentStream = PDPageContentStream(document, page)

        // --- GRID SYSTEM DEFINITION ---
        val startX = 25f
        val width = 545f
        val endX = startX + width
        val topY = 810f
        val bottomY = 30f

        // Horizontal Dividers
        val consignorBottomY = 720f
        val consigneeBottomY = 630f
        val legalSplitY = 585f
        val notifyBottomY = 540f
        val routingRow1Bottom = 500f
        val routingRow2Bottom = 460f
        val routingRow3Bottom = 420f
        val footerTopY = 140f
        val footerMidY = 90f

        val centerX = startX + (width / 2)
        val q1 = startX + (width * 0.25f)
        val q3 = startX + (width * 0.75f)

        // --- UPDATED CARGO COLUMNS ---
        // Container End (and Marks Start) at 110f
        val col_Cont_End = 110f

        // Marks End (and Description Start) at 220f
        // REMOVED: col_Pkgs_End (Was 260f)
        val col_Marks_End = 220f

        // Description Ends (Gross Weight Start)
        val col_Desc_End = endX - 130f // 440f
        val col_Gross_End = endX - 60f

        val f_Payable_Start = q1
        val f_Originals_Start = centerX
        val f_Date_Start = q3
        val f_Legal_Split = startX + (width * 0.60f)

        // --- DRAW BORDERS & STRUCTURE ---
        drawWatermark(document, contentStream)

        // Title
        drawTextCentered(contentStream, "MULTI-MODAL TRANSPORT DOCUMENT", startX, endX, 825f, fontSize = 13f, isBold = true)
        contentStream.setNonStrokingColor(Color.RED)
        drawText(contentStream, "NON-NEGOTIABLE", endX - 120f, 825f, true, fontSize = 11f)
        contentStream.setNonStrokingColor(Color.BLACK)

        contentStream.setStrokingColor(Color.BLACK)
        contentStream.setLineWidth(1f)

        // Outer Box
        contentStream.addRect(startX, bottomY, width, topY - bottomY); contentStream.stroke()

        // Header Split Lines
        drawLine(contentStream, startX, consignorBottomY, centerX)
        drawLine(contentStream, startX, consigneeBottomY, endX)
        drawLine(contentStream, centerX, notifyBottomY, centerX, topY, true)

        // Legal Text Splitter (Green Line)
        drawLine(contentStream, centerX, legalSplitY, endX)

        // MTD & Ref Boxes
        val boxW = 140f; val boxH = 22f
        val boxX = endX - boxW - 5f
        val mtdY = topY - 25f; val refY = mtdY - boxH
        contentStream.addRect(boxX, mtdY, boxW, boxH); contentStream.stroke()
        contentStream.addRect(boxX, refY, boxW, boxH); contentStream.stroke()

        // Routing Grid
        drawLine(contentStream, startX, notifyBottomY, endX)
        drawLine(contentStream, startX, routingRow1Bottom, centerX)
        drawLine(contentStream, startX, routingRow2Bottom, endX)
        drawLine(contentStream, startX, routingRow3Bottom, endX)

        drawLine(contentStream, q1, notifyBottomY, q1, routingRow1Bottom, true)
        drawLine(contentStream, centerX, notifyBottomY, centerX, routingRow1Bottom, true)

        drawLine(contentStream, q1, routingRow1Bottom, q1, routingRow2Bottom, true)
        drawLine(contentStream, centerX, routingRow1Bottom, centerX, routingRow2Bottom, true)

        drawLine(contentStream, q1, routingRow2Bottom, q1, routingRow3Bottom, true)
        drawLine(contentStream, centerX, routingRow2Bottom, centerX, routingRow3Bottom, true)
        drawLine(contentStream, q3, routingRow2Bottom, q3, routingRow3Bottom, true)

        // Footer
        drawLine(contentStream, startX, footerTopY, endX)
        drawLine(contentStream, startX, footerMidY, endX)
        drawLine(contentStream, f_Payable_Start, footerTopY, f_Payable_Start, footerMidY, true)
        drawLine(contentStream, f_Originals_Start, footerTopY, f_Originals_Start, footerMidY, true)
        drawLine(contentStream, f_Date_Start, footerTopY, f_Date_Start, footerMidY, true)
        drawLine(contentStream, f_Legal_Split, footerMidY, f_Legal_Split, bottomY, true)

        // --- DRAW STATIC CONTENT ---

        // 1. Logo & Address
        val logoY = 695f
        drawLogo(document, contentStream, centerX, logoY)
        drawCompanyInfo(contentStream, centerX, endX, logoY - 15f)

        // 2. Legal Text
        val legalText1 = "Taken in charge in apparently good condition herein at the place of reciept for transport and delivery as mentioned above unless otherwise stated. the MTO in accordance with the provisions contained in the MTD undertakes to perform or to procure the performance of the multimodal transport from the place of which the good are taken in charge, to the place designated for delivery and assumes responsiblity for such transport."
        val legalText2 = "One of the MTD(s) must be surrendered, duly endorsed in exchange for the goods in witness where of the original MTD all of this tenor and date have been signed in the number indicated below one of which being accomplished the other(s) to be void."

        drawParagraphJustified(contentStream, legalText1, centerX + 4f, 620f, (width / 2) - 8f, 5f)
        drawParagraphJustified(contentStream, legalText2, centerX + 4f, legalSplitY - 10f, (width / 2) - 8f, 5f)

        // 3. Labels (Restored to 7f)
        fun lbl(t: String, x: Float, y: Float) = drawText(contentStream, t, x, y - 10f, true, 7f)
        val pad = 4f

        lbl("Consignor", startX + pad, topY)
        lbl("Consignee (or Order)", startX + pad, consignorBottomY)
        lbl("Notify Address", startX + pad, consigneeBottomY)

        // MTD & Ref Labels (Restored to 7f)
        drawTextRightAligned(contentStream, "MTD Number", boxX - 5f, mtdY + 8f)
        drawTextRightAligned(contentStream, "Shipment Reference No.", boxX - 5f, refY + 8f)

        lbl("Pre-Carriage by", startX + pad, notifyBottomY)
        lbl("Place of Receipt", q1 + pad, notifyBottomY)
        lbl("Delivery Agent", centerX + pad, notifyBottomY)

        lbl("Ocean Vessel / Voy No", startX + pad, routingRow1Bottom)
        lbl("Port of Loading", q1 + pad, routingRow1Bottom)

        lbl("Port of Discharge", startX + pad, routingRow2Bottom)
        lbl("Place of Delivery", q1 + pad, routingRow2Bottom)
        lbl("Mode/Means", centerX + pad, routingRow2Bottom)
        lbl("Route/Transhipment", q3 + pad, routingRow2Bottom)

        val cL = routingRow3Bottom
        lbl("Container No.", startX + pad, cL)
        lbl("Marks & Nos", col_Cont_End + pad, cL)

        // REMOVED "Pkgs" Label

        // Description starts where Marks end
        lbl("Description of Goods", col_Marks_End + pad, cL)
        lbl("Gross Weight", col_Desc_End + pad, cL)
        lbl("Measurement", col_Gross_End + pad, cL)

        lbl("Freight & Charges Amount", startX + pad, footerTopY)
        lbl("Freight Payable at", f_Payable_Start + pad, footerTopY)
        lbl("No. of Originals", f_Originals_Start + pad, footerTopY)
        lbl("Place and Date of Issue", f_Date_Start + pad, footerTopY)
        lbl("Other Particulars", startX + pad, footerMidY)

        // Footer Signatory Label (Restored to 8f)
        drawText(contentStream, "Authorised Signatory", f_Legal_Split + 20f, 40f, true, 8f)

        // --- MAP USER DATA (DATA = 8f) ---
        val dataY = 22f

        // Header Boxes
        drawSmartWrappedText(contentStream, data.consignor, startX + pad, topY - dataY, 260f, maxLines = 7)
        drawSmartWrappedText(contentStream, data.consignee, startX + pad, consignorBottomY - dataY, 260f, maxLines = 7)
        drawSmartWrappedText(contentStream, data.notifyAddress, startX + pad, consigneeBottomY - dataY, 260f, maxLines = 7)

        // Box Data
        drawSmartWrappedText(contentStream, data.mtdNumber, boxX + pad, mtdY + 6f, 130f, maxLines = 1)
        drawSmartWrappedText(contentStream, data.refNumber, boxX + pad, refY + 6f, 130f, maxLines = 1)

        // Routing Row 1
        drawSmartWrappedText(contentStream, data.preCarriage, startX + pad, notifyBottomY - dataY, (q1 - startX - pad), maxLines = 3)
        drawSmartWrappedText(contentStream, data.placeReceipt, q1 + pad, notifyBottomY - dataY, (centerX - q1 - pad), maxLines = 3)

        // Delivery Agent (Exception: 7f)
        drawSmartWrappedText(contentStream, data.deliveryAgent, centerX + pad, notifyBottomY - dataY, (endX - centerX - pad), fontSize = 7f, maxLines = 10)

        // Routing Row 2 (FIXED: Constrained to fit within Q1 boundary)
        val vesselTextWidth = 90f
        val voyTextWidth = 35f

        drawSmartWrappedText(contentStream, data.vessel, startX + pad, routingRow1Bottom - dataY, vesselTextWidth, maxLines = 2)
        drawSmartWrappedText(contentStream, data.voyNumber, startX + pad + vesselTextWidth + 5f, routingRow1Bottom - dataY, voyTextWidth, maxLines = 2)
        drawSmartWrappedText(contentStream, data.portLoading, q1 + pad, routingRow1Bottom - dataY, (centerX - q1 - pad), maxLines = 3)

        // Routing Row 3
        drawSmartWrappedText(contentStream, data.portDischarge, startX + pad, routingRow2Bottom - dataY, (q1 - startX - pad), maxLines = 3)
        drawSmartWrappedText(contentStream, data.placeDelivery, q1 + pad, routingRow2Bottom - dataY, (centerX - q1 - pad), maxLines = 3)
        drawSmartWrappedText(contentStream, data.mode, centerX + pad, routingRow2Bottom - dataY, (q3 - centerX - pad), maxLines = 2)
        drawSmartWrappedText(contentStream, data.route, q3 + pad, routingRow2Bottom - dataY, (endX - q3 - pad), maxLines = 2)

        val cargoTextY = routingRow3Bottom - 25f

        // Calculated Max Lines for Digital PDF:
        // Start 395f (cargoTextY) - End 140f (footerTopY) = 255f.
        // 255 / 11 (leading) = ~23 lines.
        val safeMaxLines = 23

        // Goods Description
        // WIDTH UPDATED: Now occupies from col_Marks_End(220f) to col_Desc_End(440f)
        val descWidth = col_Desc_End - col_Marks_End - pad // 440 - 220 - 4 = 216f width
        drawSmartWrappedText(contentStream, data.goodsDescription, col_Marks_End + pad, cargoTextY, descWidth, maxLines = safeMaxLines)

        // NEW: Marks & Numbers (Global)
        // Starts at 112f, Ends at 218f (Width ~106f).
        drawSmartWrappedText(contentStream, data.marksNumbers, 112f, cargoTextY, 106f, maxLines = safeMaxLines)

        if (data.cargoItems.isNotEmpty()) {
            val item = data.cargoItems[0]
            drawSmartWrappedText(contentStream, item.containerNo, startX + pad, cargoTextY, (col_Cont_End - startX - pad), isBold = true, maxLines = safeMaxLines)

            // REMOVED: Pkgs from Main Page
            // drawSmartWrappedText(contentStream, item.pkgCount, ...)

            drawSmartWrappedText(contentStream, item.grossWeight, col_Desc_End + pad, cargoTextY, (col_Gross_End - col_Desc_End - pad), maxLines = safeMaxLines)
            drawSmartWrappedText(contentStream, item.measurement, col_Gross_End + pad, cargoTextY, (endX - col_Gross_End - pad), maxLines = safeMaxLines)

            // Seal Labels
            var sealY = cargoTextY - 35f
            drawText(contentStream, "Custom Seal:", startX + pad, sealY, true, 7f)
            sealY -= 10f
            drawSmartWrappedText(contentStream, data.mainCustomsSeal, startX + pad, sealY, (col_Cont_End - startX - pad), fontSize = 7f, maxLines = 1)

            sealY -= 20f
            drawText(contentStream, "Agent Seal:", startX + pad, sealY, true, 7f)
            sealY -= 10f
            drawSmartWrappedText(contentStream, data.mainAgentSeal, startX + pad, sealY, (col_Cont_End - startX - pad), fontSize = 7f, maxLines = 1)

            if (data.cargoItems.size > 1) {
                sealY -= 20f
                drawText(contentStream, "AND ${data.cargoItems.size - 1} OTHERS", startX + pad, sealY, true, 7f)
                sealY -= 10f
                drawText(contentStream, "(SEE ANNEXURE)", startX + pad, sealY, true, 7f)
            }
        }

        // Footer Data
        drawSmartWrappedText(contentStream, data.freightAmount, startX + pad, footerTopY - dataY, (f_Payable_Start - startX - pad), maxLines = 3)
        drawSmartWrappedText(contentStream, data.freightPayableAt, f_Payable_Start + pad, footerTopY - dataY, (f_Originals_Start - f_Payable_Start - pad), maxLines = 3)
        drawSmartWrappedText(contentStream, data.originalMtds, f_Originals_Start + pad, footerTopY - dataY, (f_Date_Start - f_Originals_Start - pad), maxLines = 3)
        drawSmartWrappedText(contentStream, data.placeDateIssue, f_Date_Start + pad, footerTopY - dataY, (endX - f_Date_Start - pad), maxLines = 3)

        drawSmartWrappedText(contentStream, data.otherParticulars, startX + pad, footerMidY - dataY, (f_Legal_Split - startX - pad), maxLines = 4)

        drawText(contentStream, "For SAN SHIPPING & LOGISTICS (INDIA) PVT. LTD.", f_Legal_Split + 10f, footerMidY - 15f, true, 8f)

        contentStream.close()
    }

    private fun drawAnnexurePage(document: PDDocument, data: MblData) {
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        val contentStream = PDPageContentStream(document, page)
        val startX = 30f; val startY = 800f
        val headerHeight = 30f; val rowHeight = 20f; val tableWidth = 535f

        drawTextCentered(contentStream, "ANNEXURE FOR BL NO - ${data.mtdNumber}", startX, startX + tableWidth, startY, fontSize = 11f, isBold = true)

        var y = startY - 30f
        val cols = floatArrayOf(startX, startX + 25, startX + 115, startX + 155, startX + 205, startX + 255, startX + 340, startX + 425, startX + 485, startX + tableWidth)
        val headers = listOf("Sr.no", "Container No", "No of Packages", "Net Weight", "Gross Weight", "A-Seal No", "cx-Seal no", "Shipping Bill no", "Date")

        contentStream.setStrokingColor(Color.BLACK); contentStream.setLineWidth(1f)
        contentStream.addRect(startX, y - headerHeight, tableWidth, headerHeight); contentStream.stroke()

        for (i in 0 until headers.size) {
            drawSmartWrappedText(contentStream, headers[i], cols[i] + 2, y - 10, (cols[i+1] - cols[i] - 4), fontSize = 7f, isBold = true)
            if (i > 0) drawLine(contentStream, cols[i], y, cols[i], y - headerHeight, true)
        }
        y -= headerHeight

        data.cargoItems.forEachIndexed { index, item ->
            contentStream.addRect(startX, y - rowHeight, tableWidth, rowHeight); contentStream.stroke()
            val rowData = listOf((index + 1).toString(), item.containerNo, item.pkgCount, item.netWeight, item.grossWeight, item.customsSeal, item.agentSeal, item.sbNumber, item.sbDate)
            for (i in rowData.indices) {
                drawSmartWrappedText(contentStream, rowData[i], cols[i] + 2, y - 13, (cols[i+1] - cols[i] - 4), fontSize = 7f, maxLines = 1)
                if (i > 0) drawLine(contentStream, cols[i], y, cols[i], y - rowHeight, true)
            }
            y -= rowHeight
        }
        contentStream.addRect(startX, y - rowHeight, tableWidth, rowHeight); contentStream.stroke()
        drawText(contentStream, "TOTAL", cols[1] + 5, y - 13, true)
        val totalNet = data.cargoItems.sumOf { it.netWeight.toDoubleOrNull() ?: 0.0 }
        val totalGross = data.cargoItems.sumOf { it.grossWeight.toDoubleOrNull() ?: 0.0 }
        val totalPkgs = data.cargoItems.sumOf { it.pkgCount.toIntOrNull() ?: 0 }
        drawText(contentStream, totalPkgs.toString(), cols[2] + 2, y - 13, true)
        drawText(contentStream, totalNet.toString(), cols[3] + 2, y - 13, true)
        drawText(contentStream, totalGross.toString(), cols[4] + 2, y - 13, true)
        for (i in 1 until cols.size) drawLine(contentStream, cols[i], y, cols[i], y - rowHeight, true)
        contentStream.close()
    }

    private fun drawLine(s: PDPageContentStream, sx: Float, sy: Float, ex: Float, ey: Float = sy, isVertical: Boolean = false) {
        s.moveTo(sx, sy); s.lineTo(if (isVertical) sx else ex, if (isVertical) ey else sy); s.stroke()
    }

    private fun drawText(s: PDPageContentStream, t: String?, x: Float, y: Float, isLabel: Boolean, fontSize: Float = 0f) {
        if (t.isNullOrEmpty()) return
        s.beginText(); s.newLineAtOffset(x, y)
        s.setFont(if(isLabel) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA, if(fontSize>0) fontSize else (if(isLabel) 7f else 8f))
        s.setNonStrokingColor(if(isLabel) Color.DARK_GRAY else Color.BLACK)
        s.showText(t); s.endText()
    }

    private fun drawTextCentered(s: PDPageContentStream, t: String, sx: Float, ex: Float, y: Float, fontSize: Float = 9f, isBold: Boolean = false) {
        val font = if (isBold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
        val w = font.getStringWidth(t)/1000*fontSize
        s.beginText(); s.setFont(font, fontSize); s.newLineAtOffset(sx + (ex-sx-w)/2, y); s.showText(t); s.endText()
    }
    private fun drawTextRightAligned(s: PDPageContentStream, t: String, alignX: Float, y: Float) {
        val font = PDType1Font.HELVETICA_BOLD
        val fontSize = 7f // Fixed at 7f
        val w = font.getStringWidth(t) / 1000 * fontSize
        s.beginText()
        s.setFont(font, fontSize)
        s.setNonStrokingColor(Color.DARK_GRAY)
        s.newLineAtOffset(alignX - w, y)
        s.showText(t)
        s.endText()
    }
    private fun drawCompanyInfo(stream: PDPageContentStream, centerX: Float, endX: Float, startY: Float) {
        stream.setNonStrokingColor(Color.RED)
        drawTextCentered(stream, "SAN SHIPPING & LOGISTICS INDIA PVT. LTD.", centerX, endX, startY, fontSize = 10f, isBold = true)
        stream.setNonStrokingColor(Color.BLACK)
        drawTextCentered(stream, "Reg. Add.: 302, A-wing, Kukreja Centre, Sector-11,", centerX, endX, startY - 10f, fontSize = 6f)
        drawTextCentered(stream, "CBD Belapur, Navi Mumbai - 400 614 - India", centerX, endX, startY - 20f, fontSize = 6f)
        drawTextCentered(stream, "MTO Reg. No.: MTO/DGS/3425/FEB/2027", centerX, endX, startY - 30f, fontSize = 7f, isBold = true)
    }
    private fun drawLogo(doc: PDDocument, stream: PDPageContentStream, centerX: Float, y: Float) {
        try {
            val logoStream = this::class.java.getResourceAsStream("/Logo.png") ?: return
            val bufferedImage = ImageIO.read(logoStream)
            if (bufferedImage != null) {
                val pdImage = LosslessFactory.createFromImage(doc, bufferedImage)
                val w = 120f; val h = pdImage.height * (w / pdImage.width)
                val areaWidth = 545f / 2
                val xPos = centerX + (areaWidth - w) / 2
                stream.drawImage(pdImage, xPos, y, w, h)
            }
        } catch (e: Exception) {}
    }
    private fun drawWatermark(doc: PDDocument, stream: PDPageContentStream) {
        try {
            val logoStream = this::class.java.getResourceAsStream("/Logo.png") ?: return
            val bufferedImage = ImageIO.read(logoStream)
            if (bufferedImage != null) {
                val pdImage = LosslessFactory.createFromImage(doc, bufferedImage)
                val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.15f }
                stream.saveGraphicsState(); stream.setGraphicsStateParameters(gs)
                val w = 400f; val h = pdImage.height * (w / pdImage.width)
                stream.drawImage(pdImage, (PDRectangle.A4.width - w)/2, (PDRectangle.A4.height - h)/2, w, h)
                stream.restoreGraphicsState()
            }
        } catch (e: Exception) {}
    }
    private fun drawParagraphJustified(stream: PDPageContentStream, text: String, x: Float, startY: Float, allowedWidth: Float, fontSize: Float) {
        val font = PDType1Font.HELVETICA
        stream.setFont(font, fontSize)
        stream.setNonStrokingColor(Color.BLACK)
        val words = text.split(" ")
        var currentY = startY
        val leading = fontSize * 1.3f
        var line = mutableListOf<String>()
        var lineWidth = 0f
        val spaceW = font.getStringWidth(" ") / 1000 * fontSize
        for (word in words) {
            val w = font.getStringWidth(word) / 1000 * fontSize
            if (lineWidth + spaceW + w < allowedWidth) { line.add(word); lineWidth += (if (line.size > 1) spaceW else 0f) + w }
            else { printLineJustified(stream, line, x, currentY, allowedWidth, font, fontSize); line = mutableListOf(word); lineWidth = w; currentY -= leading }
        }
        if (line.isNotEmpty()) { stream.beginText(); stream.newLineAtOffset(x, currentY); stream.showText(line.joinToString(" ")); stream.endText() }
    }

    private fun printLineJustified(stream: PDPageContentStream, words: List<String>, x: Float, y: Float, allowedWidth: Float, font: PDType1Font, fontSize: Float) {
        if (words.isEmpty()) return
        if (words.size == 1) { stream.beginText(); stream.newLineAtOffset(x, y); stream.showText(words[0]); stream.endText(); return }
        val totalTextW = words.sumOf { (font.getStringWidth(it) / 1000 * fontSize).toDouble() }.toFloat()
        val gap = (allowedWidth - totalTextW) / (words.size - 1)
        var cx = x
        for (w in words) { stream.beginText(); stream.newLineAtOffset(cx, y); stream.showText(w); stream.endText(); cx += (font.getStringWidth(w) / 1000 * fontSize) + gap }
    }

    private fun drawSmartWrappedText(
        stream: PDPageContentStream,
        text: String?,
        x: Float,
        startY: Float,
        allowedWidth: Float,
        fontSize: Float = 8f,
        leading: Float = 11f,
        isBold: Boolean = false,
        maxLines: Int = 100
    ) {
        if (text.isNullOrEmpty()) return
        val font = if (isBold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
        stream.setFont(font, fontSize); stream.setNonStrokingColor(Color.BLACK)
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
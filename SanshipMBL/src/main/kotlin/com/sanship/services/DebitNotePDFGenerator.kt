package com.sanship.services

import com.sanship.data.InvoiceModels.InvoiceHeader
import com.sanship.data.InvoiceModels.InvoiceItem
import com.sanship.utils.PDFUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.text.DecimalFormat
import java.awt.Color

object DebitNotePDFGenerator {
    
    private val df = DecimalFormat("#,##0.00")
    
    // Layout Config
    private const val MARGIN = 20f
    private const val HEADER_HEIGHT = 80f
    private const val IRN_HEIGHT = 20f
    private const val DETAILS_HEIGHT = 210f
    private const val FOOTER_HEIGHT = 160f 
    private const val ROW_HEIGHT = 20f
    private const val TABLE_HEADER_HEIGHT = 20f
    private const val TABLE_TOTAL_HEIGHT = 20f
    
    fun generatePDF(header: InvoiceHeader, items: List<InvoiceItem>, outputPath: String) {
        val document = PDDocument()
        try {
            val pageWidth = PDRectangle.A4.width
            val pageHeight = PDRectangle.A4.height
            val contentWidth = pageWidth - 2 * MARGIN
            
            val yTableStart = pageHeight - MARGIN - HEADER_HEIGHT - IRN_HEIGHT - DETAILS_HEIGHT - TABLE_HEADER_HEIGHT
            val yTableEnd = MARGIN + FOOTER_HEIGHT + TABLE_TOTAL_HEIGHT
            val availableTableHeight = yTableStart - yTableEnd
            
            val maxRowsPerPage = (availableTableHeight / ROW_HEIGHT).toInt()
            
            val itemChunks = if (items.isNotEmpty()) items.chunked(maxRowsPerPage) else listOf(emptyList())
            
            itemChunks.forEachIndexed { pageIndex, pageItems ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                val contentStream = PDPageContentStream(document, page)
                
                var topY = pageHeight - MARGIN
                
                // 1. Header (DEBIT NOTE SPECIFIC)
                drawHeader(document, contentStream, MARGIN, topY, contentWidth, HEADER_HEIGHT, header)
                topY -= HEADER_HEIGHT
                
                // 2. IRN
                drawIRNRow(contentStream, MARGIN, topY, contentWidth, IRN_HEIGHT)
                topY -= IRN_HEIGHT
                
                // 3. Details
                drawDetailsSection(contentStream, header, MARGIN, topY, contentWidth, DETAILS_HEIGHT)
                topY -= DETAILS_HEIGHT
                
                // 4. Table Header
                val colWidths = floatArrayOf(25f, 130f, 40f, 25f, 35f, 25f, 45f, 50f, 45f, 45f, 45f, 45f)
                val scale = contentWidth / colWidths.sum()
                val scaledWidths = colWidths.map { it * scale }.toFloatArray()
                
                drawTableHeader(contentStream, MARGIN, topY, contentWidth, TABLE_HEADER_HEIGHT, scaledWidths)
                topY -= TABLE_HEADER_HEIGHT
                
                // 5. Table Rows 
                var currentY = topY
                pageItems.forEachIndexed { i, item ->
                    val sn = ((pageIndex * maxRowsPerPage) + i + 1).toString()
                    drawItemRow(contentStream, item, MARGIN, currentY, scaledWidths, sn)
                    currentY -= ROW_HEIGHT
                }
                
                val remainingRows = maxRowsPerPage - pageItems.size
                repeat(remainingRows) {
                    drawItemRow(contentStream, null, MARGIN, currentY, scaledWidths, "")
                    currentY -= ROW_HEIGHT
                }
                
                // 6. Total Row
                val isLastPage = pageIndex == itemChunks.lastIndex
                drawTotals(contentStream, if(isLastPage) items else emptyList(), MARGIN, currentY, scaledWidths, TABLE_TOTAL_HEIGHT)
                currentY -= TABLE_TOTAL_HEIGHT
                
                // 7. Footer
                drawFooter(contentStream, header, if(isLastPage) items.sumOf{it.totalAmt} else 0.0, MARGIN, currentY, contentWidth, FOOTER_HEIGHT)
                
                contentStream.close()
            }
            
            document.save(outputPath)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            document.close()
        }
    }
    
    // --- DRAW FUNCTIONS ---
    
    private fun drawHeader(document: PDDocument, stream: PDPageContentStream, x: Float, topY: Float, width: Float, height: Float, header: InvoiceHeader) {
        val bottomY = topY - height
        PDFUtils.drawRect(stream, x, bottomY, width, height)
        
        val col1 = width * 0.25f
        val col2 = width * 0.5f
        
        PDFUtils.drawLine(stream, x + col1, bottomY, x + col1, topY)
        PDFUtils.drawLine(stream, x + col1 + col2, bottomY, x + col1 + col2, topY)
        
        // Logo (Left Box)
        val logoPath = "logo.png" 
        val logoW = 100f
        val logoH = 50f
        val logoX = x + (col1 - logoW)/2
        val logoY = bottomY + (height - logoH)/2
        PDFUtils.drawImage(document, stream, logoPath, logoX, logoY, logoW, logoH)
        
        // Company Info (Middle Box)
        val midX = x + col1 + (col2/2)
        var textY = topY - 15f
        val boldFont = PDType1Font.HELVETICA_BOLD
        val regFont = PDType1Font.HELVETICA
        
        PDFUtils.drawCenteredText(stream, "San Shipping & Logistics Pvt. Ltd.", midX, textY, boldFont, 14f)
        textY -= 15f
        PDFUtils.drawCenteredText(stream, "Reg. Add.: 302, A-wing, Kukreja Centre, Sector-11,", midX, textY, regFont, 9f)
        textY -= 12f
        PDFUtils.drawCenteredText(stream, "CBD Belapur, Navi Mumbai - 400 614 - India", midX, textY, regFont, 9f)
        textY -= 12f
        PDFUtils.drawCenteredText(stream, "MTO Reg. No.: MTO/DGS/3425/FEB/2027", midX, textY, boldFont, 9f)
        
        // DEBIT NOTE Titles
        val rightX = x + col1 + col2 + ((width*0.25f)/2)
        val rightBoxCenterY = bottomY + (height / 2)
        PDFUtils.drawCenteredText(stream, "DEBIT NOTE", rightX, rightBoxCenterY + 10f, PDType1Font.HELVETICA_BOLD, 14f)
        PDFUtils.drawCenteredText(stream, "DN No: ${header.invoiceNo}", rightX, rightBoxCenterY - 5f, PDType1Font.HELVETICA_BOLD, 9f) 
        PDFUtils.drawCenteredText(stream, "Date: ${header.invoiceDate}", rightX, rightBoxCenterY - 20f, PDType1Font.HELVETICA, 8f)
    }
    
    private fun drawIRNRow(stream: PDPageContentStream, x: Float, topY: Float, width: Float, height: Float) {
        val bottomY = topY - height
        PDFUtils.drawRect(stream, x, bottomY, width, height)
        val centerY = bottomY + 6f
        PDFUtils.drawText(stream, "IRN:", x + 5f, centerY, PDType1Font.HELVETICA_BOLD, 8f)
        PDFUtils.drawText(stream, "Ack no:", x + (width * 0.45f), centerY, PDType1Font.HELVETICA_BOLD, 8f)
        PDFUtils.drawText(stream, "Ack Date:", x + (width * 0.75f), centerY, PDType1Font.HELVETICA_BOLD, 8f)
    }
    
    private fun drawDetailsSection(stream: PDPageContentStream, header: InvoiceHeader, x: Float, topY: Float, width: Float, height: Float) {
        val bottomY = topY - height
        PDFUtils.drawRect(stream, x, bottomY, width, height)
        val midX = x + (width / 2)
        PDFUtils.drawLine(stream, midX, bottomY, midX, topY)
        
        // --- Left Column ---
        var curY = topY - 12f
        val leftX = x + 5f
        val leftTextWidth = (width / 2) - 10f
        
        PDFUtils.drawText(stream, "BILL TO:", leftX, curY, PDType1Font.HELVETICA_BOLD, 8f); curY -= 12f
        PDFUtils.drawText(stream, "RECEIVING COMPANIES ADDRESS", leftX, curY, PDType1Font.HELVETICA_BOLD, 8f); curY -= 12f
        
        // Wrap Billing Address
        if (header.billingAddress.isNotBlank()) {
            curY = PDFUtils.drawWrappedText(stream, header.billingAddress, leftX, curY, leftTextWidth, PDType1Font.HELVETICA, 8f, 10f, 4)
        } else {
            curY -= 10f
        }
        
        // Fixed position for PAN/State/GSTIN
        var lowerY = bottomY + 85f
        PDFUtils.drawText(stream, "PAN/IT No: -", leftX, lowerY, PDType1Font.HELVETICA, 8f); lowerY -= 10f
        PDFUtils.drawText(stream, "State Name: ${extractState(header.placeOfSupply)}", leftX, lowerY, PDType1Font.HELVETICA, 8f); lowerY -= 10f
        PDFUtils.drawText(stream, "GSTIN/UIN: ${header.gstin}", leftX, lowerY, PDType1Font.HELVETICA, 8f); lowerY -= 15f
        
        PDFUtils.drawText(stream, "Details like", leftX, lowerY, PDType1Font.HELVETICA_BOLD, 8f); lowerY -= 12f
        
        // Wrap Shipper/Consignee/POL/POD
        val details = listOf("1.Shipper" to header.shipper, "2.consignee" to header.consignee, "3.POL" to header.pol, "4.POD" to header.pod)
        for ((label, value) in details) {
            val text = "$label : $value"
            lowerY = PDFUtils.drawWrappedText(stream, text, leftX, lowerY, leftTextWidth, PDType1Font.HELVETICA, 7f, 9f, 2)
            lowerY -= 1f
        }
        
        // --- Right Column ---
        curY = topY - 12f
        val rightX = midX + 5f
        PDFUtils.drawText(stream, "DETAILS ABOUT CONSIGNMENT", rightX, curY, PDType1Font.HELVETICA_BOLD, 8f); curY -= 20f
        
        listOf(
            "1.Date" to header.invoiceDate, "2.Invoice number" to header.invoiceNo, "3.MBL no" to header.mblNo,
            "4.Job no" to header.jobNo, "5.Gross weight" to header.grossWeight, "6.Net weight" to header.netWeight,
            "7.Volume" to header.volumeCbm, "8.Packages" to header.packages, "9.BE no" to header.beNo,
            "10.IGM no" to header.igmNo, "11.Item no" to header.itemNo, "12.Ex. Rate" to header.exchangeRate, "13.Ref no" to header.refNo
        ).forEach {
            PDFUtils.drawText(stream, it.first, rightX, curY, PDType1Font.HELVETICA, 8f)
            PDFUtils.drawText(stream, ": ${it.second}", rightX + 80f, curY, PDType1Font.HELVETICA_BOLD, 8f)
            curY -= 12f
        }
    }
    
    private fun drawTableHeader(stream: PDPageContentStream, x: Float, topY: Float, width: Float, height: Float, widths: FloatArray) {
        val bottomY = topY - height
        PDFUtils.drawRect(stream, x, bottomY, width, height, fillColor = Color(230, 230, 230))
        val headers = listOf("SN.NO", "CHARGES DETAILS", "HSN/SAC", "CUR", "RATE", "QTY", "AMOUNT", "TAXABLE", "CGST", "SGST", "IGST", "TOTAL")
        var curX = x
        headers.forEachIndexed { i, txt ->
            PDFUtils.drawCenteredText(stream, txt, curX + (widths[i]/2), bottomY + 6f, PDType1Font.HELVETICA_BOLD, 6f)
            // Removed inner vertical lines
            // if (i > 0) PDFUtils.drawLine(stream, curX, bottomY, curX, topY)
            curX += widths[i]
        }
    }
    
    private fun drawItemRow(stream: PDPageContentStream, item: InvoiceItem?, x: Float, topY: Float, widths: FloatArray, sn: String) {
        val bottomY = topY - ROW_HEIGHT
        PDFUtils.drawLine(stream, x, bottomY, x, topY) // Outer Left
        PDFUtils.drawLine(stream, x + widths.sum(), bottomY, x + widths.sum(), topY) // Outer Right
        
        // Removed inner vertical lines loop
        var curX = x
        widths.forEach { w -> curX += w }
        
        if (item != null) {
            val vals = listOf(sn, item.description, item.hsnSac, item.currency, df.format(item.rate), df.format(item.qty), 
                df.format(item.amount), df.format(item.taxableAmount), "${item.cgstRate}", "${item.sgstRate}", "${item.igstRate}", df.format(item.totalAmt))
            curX = x
            vals.forEachIndexed { i, txt ->
                val cx = curX + (widths[i]/2)
                if(i==1) PDFUtils.drawText(stream, txt.take(25), curX+2f, bottomY+6f, PDType1Font.HELVETICA, 7f)
                else PDFUtils.drawCenteredText(stream, txt, cx, bottomY+6f, PDType1Font.HELVETICA, 7f)
                curX += widths[i]
            }
        }
        PDFUtils.drawLine(stream, x, bottomY, x + widths.sum(), bottomY, color = Color.BLACK)
    }
    
    private fun drawTotals(stream: PDPageContentStream, items: List<InvoiceItem>, x: Float, topY: Float, widths: FloatArray, height: Float) {
        val bottomY = topY - height
        val width = widths.sum()
        PDFUtils.drawRect(stream, x, bottomY, width, height)
        
        // Removed inner vertical lines
        var curX = x
        widths.forEach { w -> curX += w }
        
        PDFUtils.drawText(stream, "Total", x + 5f, bottomY + 6f, PDType1Font.HELVETICA_BOLD, 8f)
        
        if (items.isNotEmpty()) {
            val totals = listOf(
                7 to items.sumOf { it.taxableAmount }, 8 to items.sumOf { it.cgstAmt }, 9 to items.sumOf { it.sgstAmt },
                10 to items.sumOf { it.igstAmt }, 11 to items.sumOf { it.totalAmt }
            )
            curX = x
            widths.forEachIndexed { i, w ->
                val match = totals.find { it.first == i }
                if (match != null) PDFUtils.drawCenteredText(stream, df.format(match.second), curX + (w/2), bottomY+6f, PDType1Font.HELVETICA_BOLD, 7f)
                curX += w
            }
        }
    }
    
    // UPDATED GRID FOOTER
    private fun drawFooter(stream: PDPageContentStream, header: InvoiceHeader, amount: Double, x: Float, topY: Float, width: Float, height: Float) {
        val bottomY = topY - height
        var curY = topY
        
        // 1. PAN & GSTIN Row
        val row1Height = 25f
        PDFUtils.drawRect(stream, x, curY - row1Height, width, row1Height, strokeColor = Color.BLACK)
        PDFUtils.drawText(stream, "PAN NO: ________________", x + 5f, curY - 10f, PDType1Font.HELVETICA, 8f)
        PDFUtils.drawText(stream, "GSTIN NO: ${header.gstin}", x + 5f, curY - 20f, PDType1Font.HELVETICA, 8f)
        curY -= row1Height
        
        // 2. Amount in Words Row
        val row2Height = 20f
        PDFUtils.drawRect(stream, x, curY - row2Height, width, row2Height, strokeColor = Color.BLACK)
        PDFUtils.drawText(stream, "Amount in words: ${convertAmountToWords(amount)} Only", x + 5f, curY - 14f, PDType1Font.HELVETICA_BOLD, 9f)
        curY -= row2Height
        
        // 3. Terms Row
        val row3Height = 35f
        PDFUtils.drawRect(stream, x, curY - row3Height, width, row3Height, strokeColor = Color.BLACK)
        PDFUtils.drawText(stream, "TERMS & CONDITIONS :", x + 5f, curY - 10f, PDType1Font.HELVETICA_BOLD, 8f)
        PDFUtils.drawText(stream, "ALREADY DEFAULT", x + 5f, curY - 25f, PDType1Font.HELVETICA, 8f)
        curY -= row3Height
        
        // 4. Bank & Signature Row
        val row4Height = curY - bottomY
        PDFUtils.drawRect(stream, x, bottomY, width, row4Height, strokeColor = Color.BLACK)
        
        val midX = x + (width/2)
        PDFUtils.drawLine(stream, midX, bottomY, midX, curY, color = Color.BLACK)
        
        // Left: Bank
        var bankY = curY - 12f
        val bankX = x + 5f
        PDFUtils.drawText(stream, "Companies Bank Details", bankX, bankY, PDType1Font.HELVETICA_BOLD, 8f); bankY -= 12f
        PDFUtils.drawText(stream, "1.Beneficiary Name: SANSHIP LOGISTICS", bankX, bankY, PDType1Font.HELVETICA, 8f); bankY -= 12f
        PDFUtils.drawText(stream, "2.Bank Name: HDFC BANK", bankX, bankY, PDType1Font.HELVETICA, 8f); bankY -= 12f
        PDFUtils.drawText(stream, "3.A/c no: 502000XXXXXX", bankX, bankY, PDType1Font.HELVETICA, 8f); bankY -= 12f
        PDFUtils.drawText(stream, "Branch & IFS Code: MUMBAI / HDFC0001234", bankX, bankY, PDType1Font.HELVETICA, 8f)
        
        // Right: Sig
        var sigY = curY - 12f
        PDFUtils.drawRightAlignedText(stream, "Company Name", x + width - 5f, sigY, PDType1Font.HELVETICA_BOLD, 9f)
        PDFUtils.drawRightAlignedText(stream, "Signature", x + width - 5f, bottomY + 10f, PDType1Font.HELVETICA, 9f)
    }
    
    private fun convertAmountToWords(amount: Double): String = amount.toInt().toString()
    private fun extractState(pos: String) = if (pos.contains("-")) pos.split("-")[1] else pos
}

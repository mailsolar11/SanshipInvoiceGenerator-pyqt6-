package com.sanship.services

import com.sanship.data.DatabaseManager
import com.sanship.data.InvoiceData
import com.sanship.accounting.Gst
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import java.io.FileOutputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object GstReportService {

    fun generateSalesRegisterExcel(items: List<com.sanship.data.AccountingRepository.SalesRegisterItem>, outputPath: String): String {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sales Register")
        
        val headers = listOf("Date", "Invoice No", "Party Name", "Total Amount")
        createHeader(sheet, headers)
        
        var rowIdx = 1
        for (item in items) {
            val row = sheet.createRow(rowIdx++)
            row.createCell(0).setCellValue(item.date) // raw date string is fine for this report
            row.createCell(1).setCellValue(item.invoiceNo)
            row.createCell(2).setCellValue(item.partyName)
            row.createCell(3).setCellValue(item.totalAmount)
        }
        
        for(i in headers.indices) {
            sheet.autoSizeColumn(i)
        }
        
        try {
            FileOutputStream(File(outputPath)).use { fos ->
                workbook.write(fos)
            }
            workbook.close()
            return "Success: Sales Register exported to $outputPath"
        } catch (e: Exception) {
            e.printStackTrace()
            return "Error: ${e.message}"
        }
    }

    fun generateGstr1(startDate: String, endDate: String, outputPath: String): String {
        val invoices = DatabaseManager.getInvoicesForRange(startDate, endDate)
        println("=== DIAGNOSTIC GSTR1 ===")
        println("StartDate: $startDate, EndDate: $endDate")
        println("Total Invoices Found: ${invoices.size}")
        if (invoices.isNotEmpty()) {
            println("First Invoice: ${invoices[0].invoiceNo}, Items: ${invoices[0].items.size}")
        }
        println("========================")
        
        val workbook = XSSFWorkbook()

        // 1. Create Sheets
        createB2BSheet(workbook, invoices)
        createB2CLSheet(workbook, invoices)
        createB2CSSheet(workbook, invoices)
        createHsnSheet(workbook, invoices)

        // 2. Save File
        try {
            FileOutputStream(File(outputPath)).use { fos ->
                workbook.write(fos)
            }
            workbook.close()
            return "Success: Report saved to $outputPath"
        } catch (e: Exception) {
            e.printStackTrace()
            return "Error: ${e.message}"
        }
    }

    // ==========================================
    // SHEET 1: B2B (Business to Business)
    // Criteria: Customer has GSTIN
    // ==========================================
    private fun createB2BSheet(wb: XSSFWorkbook, invoices: List<InvoiceData>) {
        val sheet = wb.createSheet("b2b")
        val headers = listOf(
            "GSTIN/UIN of Recipient", "Invoice Number", "Invoice date", "Invoice Value",
            "Place Of Supply", "Reverse Charge", "Invoice Type", "E-Commerce GSTIN",
            "Rate", "Taxable Value", "Cess Amount"
        )
        createHeader(sheet, headers)

        val b2bInvoices = invoices.filter { !it.gstin.isNullOrBlank() }

        var rowIdx = 1
        for (inv in b2bInvoices) {
            // Group items by Rate for the same invoice (GSTR-1 format requires line per rate)
            // But verify if GSTR-1 JSON offline tool expects line-item wise or consolidated.
            // Standard practice: Line item wise aggregation by Rate.
            
            val itemsByRate = inv.items.groupBy { it.cgstRate + it.sgstRate + it.igstRate }
            
            for ((rate, items) in itemsByRate) {
                val totalTaxable = items.sumOf { it.taxableAmount }
                // GST Portal expects Rate as integer or decimal?? Usually 5, 12, 18.
                
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(inv.gstin) // GSTIN
                row.createCell(1).setCellValue(inv.invoiceNo) // Inv No
                row.createCell(2).setCellValue(formatDate(inv.date)) // Date dd-MMM-yyyy
                row.createCell(3).setCellValue(inv.grandTotal) // Invoice Value (Total)
                row.createCell(4).setCellValue(getStateCodeFormatted(inv.placeOfSupply)) // POS
                row.createCell(5).setCellValue(if (inv.reverseCharge) "Y" else "N") // Reverse Charge
                row.createCell(6).setCellValue("Regular") // Invoice Type
                row.createCell(7).setCellValue("") // E-Com GSTIN
                row.createCell(8).setCellValue(rate) // Rate
                row.createCell(9).setCellValue(totalTaxable) // Taxable Value
                row.createCell(10).setCellValue(0.0) // Cess
            }
        }
    }

    // ==========================================
    // SHEET 2: B2CL (Business to Consumer Large)
    // Criteria: No GSTIN + Inter-State + Value > 2.5L
    // ==========================================
    private fun createB2CLSheet(wb: XSSFWorkbook, invoices: List<InvoiceData>) {
        val sheet = wb.createSheet("b2cl")
        val headers = listOf(
            "Invoice Number", "Invoice date", "Invoice Value", "Place Of Supply",
            "Rate", "Taxable Value", "Cess Amount", "E-Commerce GSTIN"
        )
        createHeader(sheet, headers)

        val b2clInvoices = invoices.filter { 
            it.gstin.isNullOrBlank() && 
            isInterState(it.placeOfSupply) && 
            it.grandTotal > 250000 
        }

        var rowIdx = 1
        for (inv in b2clInvoices) {
            val itemsByRate = inv.items.groupBy { it.cgstRate + it.sgstRate + it.igstRate }
            
            for ((rate, items) in itemsByRate) {
                val totalTaxable = items.sumOf { it.taxableAmount }
                
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(inv.invoiceNo)
                row.createCell(1).setCellValue(formatDate(inv.date))
                row.createCell(2).setCellValue(inv.grandTotal)
                row.createCell(3).setCellValue(getStateCodeFormatted(inv.placeOfSupply))
                row.createCell(4).setCellValue(rate)
                row.createCell(5).setCellValue(totalTaxable)
                row.createCell(6).setCellValue(0.0)
                row.createCell(7).setCellValue("")
            }
        }
    }

    // ==========================================
    // SHEET 3: B2CS (Business to Consumer Small)
    // Criteria: No GSTIN + (Intra-State OR (Inter-State AND Value <= 2.5L))
    // Format: Consolidated by POS + Rate
    // ==========================================
    private fun createB2CSSheet(wb: XSSFWorkbook, invoices: List<InvoiceData>) {
        val sheet = wb.createSheet("b2cs")
        val headers = listOf(
            "Type", "Place Of Supply", "Rate", "Taxable Value", "Cess Amount", "E-Commerce GSTIN"
        )
        createHeader(sheet, headers)

        // Filter B2CS Invoices
        val b2csInvoices = invoices.filter { 
            it.gstin.isNullOrBlank() && 
            (!isInterState(it.placeOfSupply) || it.grandTotal <= 250000)
        }

        // Flatten all items from all B2CS invoices
        data class B2csKey(val pos: String, val rate: Double)
        val consolidatedMap = mutableMapOf<B2csKey, Double>()

        for (inv in b2csInvoices) {
            val pos = inv.placeOfSupply
            for (item in inv.items) {
                val rate = item.cgstRate + item.sgstRate + item.igstRate
                val key = B2csKey(pos, rate)
                consolidatedMap[key] = consolidatedMap.getOrDefault(key, 0.0) + item.taxableAmount
            }
        }

        var rowIdx = 1
        for ((key, taxableVal) in consolidatedMap) {
            val row = sheet.createRow(rowIdx++)
            row.createCell(0).setCellValue("OE") // Type: OE = Other than E-Commerce
            row.createCell(1).setCellValue(getStateCodeFormatted(key.pos))
            row.createCell(2).setCellValue(key.rate)
            row.createCell(3).setCellValue(taxableVal)
            row.createCell(4).setCellValue(0.0)
            row.createCell(5).setCellValue("")
        }
    }

    // ==========================================
    // SHEET 4: HSN (HSN Summary)
    // Criteria: All items from valid invoices
    // ==========================================
    private fun createHsnSheet(wb: XSSFWorkbook, invoices: List<InvoiceData>) {
        val sheet = wb.createSheet("hsn")
        val headers = listOf(
            "HSN", "Description", "UQC", "Total Quantity", "Total Value", 
            "Taxable Value", "Integrated Tax Amount", "Central Tax Amount", "State/UT Tax Amount", "Cess Amount"
        )
        createHeader(sheet, headers)

        // Flatten all items
        val allItems = invoices.flatMap { it.items }
        
        // Group by HSN
        val hsnMap = allItems.groupBy { it.hsnSac ?: "UNKNOWN" }

        var rowIdx = 1
        for ((hsn, items) in hsnMap) {
            if (hsn == "UNKNOWN" && items.isEmpty()) continue
            
            // Aggregations
            var description = items.firstOrNull()?.description ?: ""
            // Truncate desc if too long or pick most common? taking first for now.
            
            val totalQty = items.sumOf { it.qty }
            val totalValue = items.sumOf { it.totalAmt }
            val taxableValue = items.sumOf { it.taxableAmount }
            val igst = items.sumOf { it.igstAmt }
            val cgst = items.sumOf { it.cgstAmt }
            val sgst = items.sumOf { it.sgstAmt }
            
            val row = sheet.createRow(rowIdx++)
            row.createCell(0).setCellValue(hsn)
            row.createCell(1).setCellValue(description)
            row.createCell(2).setCellValue("OTH") // UQC - Unit Quantity Code (OTH = Others, KGS, NOS) - TODO: Map from Unit
            row.createCell(3).setCellValue(totalQty)
            row.createCell(4).setCellValue(totalValue)
            row.createCell(5).setCellValue(taxableValue)
            row.createCell(6).setCellValue(igst)
            row.createCell(7).setCellValue(cgst)
            row.createCell(8).setCellValue(sgst)
            row.createCell(9).setCellValue(0.0) // Cess
        }
    }

    // ==========================================
    // HELPERS
    // ==========================================
    private fun createHeader(sheet: Sheet, headers: List<String>) {
        val row = sheet.createRow(0)
        val style = sheet.workbook.createCellStyle()
        val font = sheet.workbook.createFont()
        font.bold = true
        style.setFont(font)

        headers.forEachIndexed { index, title ->
            val cell = row.createCell(index)
            cell.setCellValue(title)
            cell.cellStyle = style
            sheet.setColumnWidth(index, 5000) // Approx width
        }
    }

    private fun formatDate(dateStr: String): String {
        // Input: yyyy-MM-dd, Output: dd-MMM-yyyy (e.g. 01-Jan-2024)
        try {
            val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val outputFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy")
            val date = LocalDate.parse(dateStr, inputFormatter)
            return date.format(outputFormatter)
        } catch (e: Exception) {
            return dateStr
        }
    }

    private fun isInterState(placeOfSupply: String?): Boolean {
        // Logic: 27 is Maharashtra. If POS != 27 (or Maharashtra), it's Inter-state.
        // We should rely on State Code logic we just added.
        val targetCode = Gst.getStateCode(placeOfSupply) ?: "27"
        return targetCode != "27"
    }

    private fun getStateCodeFormatted(stateName: String?): String {
        val code = Gst.getStateCode(stateName) ?: "27"
        return "$code-${stateName ?: "Maharashtra"}"
    }

    data class Gstr3bSummary(
        val totalTaxable: Double,
        val igst: Double,
        val cgst: Double,
        val sgst: Double
    )

    fun getGstr3bSummary(startDate: String, endDate: String): Gstr3bSummary {
        val invoices = DatabaseManager.getInvoicesForRange(startDate, endDate)
        var taxable = 0.0
        var igst = 0.0
        var cgst = 0.0
        var sgst = 0.0

        for (inv in invoices) {
            val multiplier = if (inv.documentType == "CREDIT_NOTE") -1.0 else 1.0
            taxable += inv.taxableAmount * multiplier
            igst += inv.igstAmount * multiplier
            cgst += inv.cgstAmount * multiplier
            sgst += inv.sgstAmount * multiplier
        }

        return Gstr3bSummary(taxable, igst, cgst, sgst)
    }
}

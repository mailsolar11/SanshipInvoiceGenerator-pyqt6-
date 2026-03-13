package com.sanship.services

import com.sanship.data.InvoiceModels.InvoiceHeader
import com.sanship.data.InvoiceModels.InvoiceItem
import org.jsoup.nodes.Document
import java.io.FileOutputStream

/**
 * HTML -> PDF Service
 * Generates invoice/debit note PDFs using OpenHtmlToPdf
 */
object HtmlPdfService {

    fun generateInvoicePdf(header: InvoiceHeader, items: List<InvoiceItem>, outputPath: String) {
        val templateName = if (header.documentType == "DEBIT_NOTE") "debit_note.html" else "invoice.html"
        
        // 1. Load Template
        val templateStream = javaClass.getResourceAsStream("/templates/$templateName") 
            ?: throw RuntimeException("Template not found: /templates/$templateName")
            
        var rawTemplate = templateStream.bufferedReader().use { it.readText() }
        
        // FIX: Remove XML-incompatible entities for OpenHtmlToPdf
        rawTemplate = rawTemplate.replace("&nbsp;", "&#160;")
        
        // 2. Determine if e-Invoice (has IRN)
        val isEInvoice = header.irn.isNotBlank()
        
        // 3. Prepare text placeholders (will be HTML-escaped) and HTML placeholders (raw HTML, NOT escaped)
        val textPlaceholders = mutableMapOf<String, String>()
        val htmlPlaceholders = mutableMapOf<String, String>()
        
        // Header / Company Data
        textPlaceholders["company_name"] = "SAN SHIPPING & LOGISTICS INDIA PVT LTD"
        htmlPlaceholders["company_address"] = "302,A-WING KUKREIA CENTRE SECTOR-11<br/>CBD BELAPUR NAVI MUMBAI-400614<br/>CIN No : U74999MH2016PTC285881<br/>Tel : +91 22 49706655 E-mail : info@sanship.com<br/>Website : www.sanship.in"
        textPlaceholders["logo_path"] = javaClass.getResource("/Logo.png")?.toString() ?: ""
        
        // Document title
        textPlaceholders["document_title"] = if (isEInvoice) "TAX INVOICE" else "Invoice"
        
        // e-Invoice header section (QR + label) — contains HTML, use htmlPlaceholders
        if (isEInvoice) {
            val qrImg = generateQrCodeBase64(header.signedQr)
            htmlPlaceholders["einvoice_header"] = """
                <div class="einvoice-label">e-Invoice</div>
                <div class="qr-code-container">$qrImg</div>
            """.trimIndent()
        } else {
            htmlPlaceholders["einvoice_header"] = ""
        }
        
        // IRN section — contains HTML
        if (isEInvoice) {
            htmlPlaceholders["irn_section"] = """
                <div class="irn-section">
                    <strong>IRN</strong> &#160; : &#160; ${escapeHtml(header.irn)}<br/>
                    <strong>Ack No.</strong> : ${escapeHtml(header.ackNo)} &#160;&#160;&#160;&#160;
                    <strong>Ack Date :</strong> ${escapeHtml(header.ackDate)}
                </div>
            """.trimIndent()
        } else {
            htmlPlaceholders["irn_section"] = ""
        }
        
        // Bill To PAN/GSTIN section — contains HTML
        if (isEInvoice) {
            htmlPlaceholders["billto_pan_gstin"] = """
                <strong>PAN/IT No :</strong> ${escapeHtml(header.pan)}<br/>
                <strong>State Name :</strong> ${escapeHtml(header.placeOfSupply)}, <strong>Code :</strong> ${escapeHtml(header.stateCode)}<br/>
                <strong>GSTIN/UIN :</strong> ${escapeHtml(header.gstin)}
            """.trimIndent()
        } else {
            htmlPlaceholders["billto_pan_gstin"] = """
                <strong>State Name :</strong> ${escapeHtml(header.placeOfSupply)}, <strong>Code :</strong> ${escapeHtml(header.stateCode)}
            """.trimIndent()
        }
        
        // Invoice Header fields
        textPlaceholders["invoice_no"] = header.invoiceNo
        textPlaceholders["invoice_date"] = header.invoiceDate
        
        // Customer fields
        textPlaceholders["billed_to_name"] = header.customerName
        htmlPlaceholders["billed_to_address"] = header.billingAddress.replace("\n", "<br/>")
        
        // Consignment / Job
        textPlaceholders["category"] = header.category
        textPlaceholders["mbl_no"] = header.mblNo
        textPlaceholders["hbl_no"] = header.hblNo
        textPlaceholders["job_no"] = header.jobNo
        textPlaceholders["gross_weight"] = header.grossWeight
        textPlaceholders["net_weight"] = header.netWeight
        textPlaceholders["net_weight_unit"] = header.netWeightUnit
        textPlaceholders["volume"] = header.volumeCbm
        textPlaceholders["packages"] = header.packages
        textPlaceholders["container_nos"] = header.containerNos
        
        textPlaceholders["be_no"] = header.beNo
        textPlaceholders["be_date"] = header.beDate
        textPlaceholders["igm_no"] = header.igmNo
        textPlaceholders["igm_date"] = header.igmDate
        textPlaceholders["item_no"] = header.itemNo
        textPlaceholders["exchange_rate"] = header.exchangeRate
        textPlaceholders["ref_no"] = header.refNo
        textPlaceholders["other_ref_no"] = header.otherRefNo
        
        // Shipment
        textPlaceholders["shipper_name"] = header.shipper
        textPlaceholders["consignee_name"] = header.consignee
        textPlaceholders["shipper_invoice_no"] = header.shipperInvoiceNo
        textPlaceholders["shipper_invoice_date"] = header.shipperInvoiceDate
        textPlaceholders["pol"] = header.pol
        textPlaceholders["pod"] = header.pod
        textPlaceholders["vessel_flight"] = header.vesselFlight
        textPlaceholders["etd"] = header.etd
        textPlaceholders["eta"] = header.eta
        
        // Totals
        textPlaceholders["total_taxable"] = "%.2f".format(header.taxableAmount)
        textPlaceholders["total_cgst"] = "%.2f".format(header.cgstAmount)
        textPlaceholders["total_sgst"] = "%.2f".format(header.sgstAmount)
        textPlaceholders["total_igst"] = "%.2f".format(header.igstAmount)
        textPlaceholders["grand_total"] = "%.2f".format(header.grandTotal)
        textPlaceholders["amount_in_words"] = convertAmountToWords(header.grandTotal, items)
        
        // Company PAN/GSTIN (company's own)
        textPlaceholders["company_pan"] = "AAXCS6011R"
        textPlaceholders["company_gstin"] = "27AAXCS6011R1ZF"
        
        // Bank
        textPlaceholders["bank_beneficiary"] = "SAN SHIPPING & LOGISTICS INDIA PVT LTD"
        textPlaceholders["bank_name"] = "HDFC BANK LTD"
        textPlaceholders["bank_account_no"] = "50200022090431"
        textPlaceholders["bank_ifsc"] = "C.B.D. Belapur & HDFC0000B30"
        textPlaceholders["bank_swift"] = "HDFCINBB"
        
        // Stamp image — contains HTML
        val stampUrl = javaClass.getResource("/stamp.png")?.toString() ?: ""
        if (stampUrl.isNotBlank()) {
            htmlPlaceholders["stamp_image"] = "<img src='$stampUrl' style='max-height: 50px;'/>"
        } else {
            htmlPlaceholders["stamp_image"] = ""
        }
        
        // 4. Build item table headers (different for regular vs e-Invoice)
        if (isEInvoice) {
            htmlPlaceholders["items_thead"] = """
                <tr>
                    <th width="3%">SR. NO.</th>
                    <th width="20%">CHARGES DETAILS</th>
                    <th width="7%">HSN/SAC</th>
                    <th width="4%">CUR</th>
                    <th width="7%">RATE</th>
                    <th width="4%">QTY</th>
                    <th width="9%">AMOUNT in(CUR)</th>
                    <th width="10%">TAXABLE AMOUNT</th>
                    <th width="5%">IGST Rate</th>
                    <th width="8%">IGST Amt</th>
                    <th width="9%">TOTAL AMT(INR)</th>
                </tr>
            """.trimIndent()
        } else {
            htmlPlaceholders["items_thead"] = """
                <tr>
                    <th width="4%">SI No.</th>
                    <th width="30%">Particulars</th>
                    <th width="10%">HSN/SAC</th>
                    <th width="8%">GST Rate</th>
                    <th width="6%">Qty</th>
                    <th width="10%">Rate</th>
                    <th width="10%">CURRENCY</th>
                    <th width="12%">Amount</th>
                </tr>
            """.trimIndent()
        }
        
        // 5. Pagination Logic (Chunk by 5)
        val chunks = items.chunked(5)
        val totalPages = chunks.size.coerceAtLeast(1)
        val combinedHtml = StringBuilder()
        
        combinedHtml.append("<html><head><link rel='stylesheet' href='style.css'/><style>.page { page-break-after: always; } .page:last-child { page-break-after: auto; }</style></head><body>")
        
        // Extract body content from template
        val bodyContentMatch = Regex("<body>(.*?)</body>", RegexOption.DOT_MATCHES_ALL).find(rawTemplate)
        val templateBody = bodyContentMatch?.groupValues?.get(1) ?: rawTemplate
        
        chunks.forEachIndexed { pageIndex, chunkItems ->
            val isLastPage = (pageIndex == chunks.size - 1)
            var pageHtml = templateBody
            
            // Generate item rows for this page
            val rowsHtml = StringBuilder()
            val startSn = pageIndex * 5 + 1
            
            chunkItems.forEachIndexed { i, item ->
                val sn = startSn + i
                if (isEInvoice) {
                    // e-Invoice columns: SR NO | CHARGES DETAILS | HSN/SAC | CUR | RATE | QTY | AMOUNT in(CUR) | TAXABLE AMOUNT | IGST Rate | IGST Amt | TOTAL AMT(INR)
                    rowsHtml.append("<tr>")
                    rowsHtml.append("<td class='text-center'>$sn</td>")
                    rowsHtml.append("<td>${escapeHtml(item.description)}</td>")
                    rowsHtml.append("<td class='text-center'>${escapeHtml(item.hsnSac)}</td>")
                    rowsHtml.append("<td class='text-center'>${item.currency}</td>")
                    rowsHtml.append("<td class='text-right'>${formatNumber(item.rate)}</td>")
                    rowsHtml.append("<td class='text-center'>${item.qty.toInt()}</td>")
                    rowsHtml.append("<td class='text-right'>${formatNumber(item.amount)}</td>")
                    rowsHtml.append("<td class='text-right'>${formatNumber(item.taxableAmount)}</td>")
                    // IGST
                    val igstRate = if (item.igstRate > 0) "${item.igstRate.toInt()} %" else ""
                    rowsHtml.append("<td class='text-center'>$igstRate</td>")
                    rowsHtml.append("<td class='text-right'>${formatNumber(item.igstAmt)}</td>")
                    rowsHtml.append("<td class='text-right'>${formatNumber(item.totalAmt)}</td>")
                    rowsHtml.append("</tr>")
                } else {
                    // Regular Invoice columns: SI No | Particulars | HSN/SAC | GST Rate | Qty | Rate | CURRENCY | Amount
                    rowsHtml.append("<tr>")
                    rowsHtml.append("<td class='text-center'>$sn</td>")
                    rowsHtml.append("<td>${escapeHtml(item.description)}</td>")
                    rowsHtml.append("<td class='text-center'>${escapeHtml(item.hsnSac)}</td>")
                    // GST Rate - show whichever is non-zero
                    val gstRate = when {
                        item.igstRate > 0 -> "${item.igstRate.toInt()}%"
                        item.cgstRate > 0 -> "${(item.cgstRate + item.sgstRate).toInt()}%"
                        else -> ""
                    }
                    rowsHtml.append("<td class='text-center'>$gstRate</td>")
                    rowsHtml.append("<td class='text-center'>${item.qty.toInt()}</td>")
                    rowsHtml.append("<td class='text-right'>${formatNumber(item.rate)}</td>")
                    rowsHtml.append("<td class='text-center'>${item.currency}</td>")
                    rowsHtml.append("<td class='text-right'>${formatCurrency(item.amount, item.currency)}</td>")
                    rowsHtml.append("</tr>")
                }
            }
            
            // PAD empty rows to always have 5 rows per page
            val emptyRowsNeeded = 5 - chunkItems.size
            for (e in 0 until emptyRowsNeeded) {
                if (isEInvoice) {
                    rowsHtml.append("<tr>")
                    for (c in 0 until 11) rowsHtml.append("<td>&#160;</td>")
                    rowsHtml.append("</tr>")
                } else {
                    rowsHtml.append("<tr>")
                    for (c in 0 until 8) rowsHtml.append("<td>&#160;</td>")
                    rowsHtml.append("</tr>")
                }
            }
            
            pageHtml = pageHtml.replace("{{item_rows}}", rowsHtml.toString())
            
            // Generate tfoot (totals) - only on last page
            if (isLastPage) {
                if (isEInvoice) {
                    val tfootHtml = """
                        <tr class="total-row">
                            <td colspan="7">&#160;</td>
                            <td class="text-right text-bold">Total</td>
                            <td class="text-right text-bold">&#160;</td>
                            <td class="text-right text-bold">${"%.2f".format(header.igstAmount)}</td>
                            <td class="text-right text-bold">${"%.2f".format(header.grandTotal)}</td>
                        </tr>
                    """.trimIndent()
                    pageHtml = pageHtml.replace("{{items_tfoot}}", tfootHtml)
                } else {
                    val currency = determinePrimaryCurrency(items)
                    val totalFormatted = formatCurrency(header.grandTotal, currency)
                    val tfootHtml = """
                        <tr class="total-row">
                            <td colspan="7" class="text-right text-bold">Total</td>
                            <td class="text-right text-bold">$totalFormatted</td>
                        </tr>
                    """.trimIndent()
                    pageHtml = pageHtml.replace("{{items_tfoot}}", tfootHtml)
                }
            } else {
                pageHtml = pageHtml.replace("{{items_tfoot}}", "")
            }
            
            // Replace HTML placeholders FIRST (these contain raw HTML, NOT escaped)
            htmlPlaceholders.forEach { (key, value) ->
                pageHtml = pageHtml.replace("{{$key}}", value)
            }
            
            // Replace text placeholders (escaped to prevent injection)
            textPlaceholders.forEach { (key, value) ->
                pageHtml = pageHtml.replace("{{$key}}", escapeHtml(value))
            }
            
            combinedHtml.append("<div class='page'>")
            combinedHtml.append(pageHtml)
            combinedHtml.append("</div>")
        }
        
        // Handle empty items case - still need one page with 5 empty rows
        if (chunks.isEmpty()) {
            var pageHtml = templateBody
            val emptyRows = StringBuilder()
            val colCount = if (isEInvoice) 11 else 8
            for (e in 0 until 5) {
                emptyRows.append("<tr>")
                for (c in 0 until colCount) emptyRows.append("<td>&#160;</td>")
                emptyRows.append("</tr>")
            }
            pageHtml = pageHtml.replace("{{item_rows}}", emptyRows.toString())
            
            val currency = determinePrimaryCurrency(items)
            val totalFormatted = if (isEInvoice) "%.2f".format(header.grandTotal) else formatCurrency(header.grandTotal, currency)
            if (isEInvoice) {
                pageHtml = pageHtml.replace("{{items_tfoot}}", """<tr class="total-row"><td colspan="7">&#160;</td><td class="text-right text-bold">Total</td><td class="text-right text-bold">&#160;</td><td class="text-right text-bold">${"%.2f".format(header.igstAmount)}</td><td class="text-right text-bold">$totalFormatted</td></tr>""")
            } else {
                pageHtml = pageHtml.replace("{{items_tfoot}}", """<tr class="total-row"><td colspan="7" class="text-right text-bold">Total</td><td class="text-right text-bold">$totalFormatted</td></tr>""")
            }
            
            htmlPlaceholders.forEach { (key, value) ->
                pageHtml = pageHtml.replace("{{$key}}", value)
            }
            textPlaceholders.forEach { (key, value) ->
                pageHtml = pageHtml.replace("{{$key}}", escapeHtml(value))
            }
            combinedHtml.append("<div class='page'>")
            combinedHtml.append(pageHtml)
            combinedHtml.append("</div>")
        }
        
        combinedHtml.append("</body></html>")
        
        // 6. Render PDF
        val document = org.jsoup.Jsoup.parse(combinedHtml.toString())
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml)
        
        FileOutputStream(outputPath).use { os ->
            val builder = com.openhtmltopdf.pdfboxout.PdfRendererBuilder()
            builder.useFastMode()
            
            val resourceUrl = javaClass.getResource("/templates/")
            builder.withHtmlContent(document.html(), resourceUrl?.toExternalForm() ?: "")
            builder.toStream(os)
            builder.run()
        }
    }
    
    private fun formatNumber(value: Double): String {
        return if (value == 0.0) "" else "%.2f".format(value)
    }
    
    private fun formatCurrency(amount: Double, currency: String): String {
        if (amount == 0.0) return ""
        val prefix = if (currency == "USD") "$" else ""
        return "$prefix${"%.2f".format(amount)}"
    }
    
    private fun determinePrimaryCurrency(items: List<InvoiceItem>): String {
        return items.firstOrNull()?.currency ?: "INR"
    }
    
    private fun convertAmountToWords(amount: Double, items: List<InvoiceItem>): String {
        val currency = determinePrimaryCurrency(items)
        val currencyName = when (currency) {
            "USD" -> "US Dollar"
            "EUR" -> "Euro"
            "GBP" -> "British Pound"
            else -> "INR"
        }
        val wholePart = amount.toLong()
        val words = numberToWords(wholePart)
        return if (currency == "INR") {
            "$currencyName $words only"
        } else {
            "$currencyName $words only"
        }
    }
    
    private fun numberToWords(number: Long): String {
        if (number == 0L) return "Zero"
        
        val ones = arrayOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
        val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")
        
        var num = number
        val parts = mutableListOf<String>()
        
        if (num >= 10000000) {
            parts.add("${numberToWords(num / 10000000)} Crore")
            num %= 10000000
        }
        if (num >= 100000) {
            parts.add("${numberToWords(num / 100000)} Lakh")
            num %= 100000
        }
        if (num >= 1000) {
            parts.add("${numberToWords(num / 1000)} Thousand")
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
    
    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
    
    private fun generateQrCodeBase64(text: String): String {
        if (text.isBlank()) return ""
        try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 200, 200)
            val config = com.google.zxing.client.j2se.MatrixToImageConfig(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
            val byteArrayOutputStream = java.io.ByteArrayOutputStream()
            com.google.zxing.client.j2se.MatrixToImageWriter.writeToStream(bitMatrix, "PNG", byteArrayOutputStream, config)
            val bytes = byteArrayOutputStream.toByteArray()
            val base64 = java.util.Base64.getEncoder().encodeToString(bytes)
            return "<img src='data:image/png;base64,$base64' style='height:100px; width:100px;'/>"
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }
}

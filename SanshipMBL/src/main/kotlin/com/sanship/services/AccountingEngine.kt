package com.sanship.services

import com.sanship.accounting.Gst
import com.sanship.accounting.Vouchers
import com.sanship.data.InvoiceModels
import com.sanship.data.PurchaseHeader
import com.sanship.data.PurchaseItem
import java.sql.Connection
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * ACCOUNTING ENGINE
 * =================
 *
 * Authoritative bridge between business documents
 * (INVOICE / DEBIT NOTE) and accounting vouchers.
 *
 * Responsibilities:
 * - Decide voucher intent (SALES vs DEBIT_NOTE)
 * - Enforce GST correctness
 * - Aggregate values deterministically
 * - Build auditable voucher payloads
 * - Delegate persistence to voucher layer ONLY
 *
 * ⚠ This layer NEVER writes ledger entries directly.
 *
 * EXACT REPLICA of Python src/services/accounting_engine.py
 */
object AccountingEngine {
    
    // ==========================================================
    // INTERNAL UTILS
    // ==========================================================
    private fun toBigDecimal(v: Any?): BigDecimal {
        return BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP)
    }
    
    // ==========================================================
    // CORE BUILDER (PURE – NO DB)
    // ==========================================================
    data class AccountingPayload(
        val voucherType: String,
        val postingData: PostingData,
        val summary: Summary
    )
    
    data class PostingData(
        val voucherNo: String,
        val voucherDate: String,
        val partyName: String,
        val partyGstin: String?,
        val narration: String,
        val taxableAmount: Double,
        val cgstAmount: Double,
        val sgstAmount: Double,
        val igstAmount: Double
    )
    
    data class Summary(
        val taxable: Double,
        val cgst: Double,
        val sgst: Double,
        val igst: Double,
        val grandTotal: Double
    )

    data class PostResult(
        val voucherId: Int,
        val documentType: String
    )
    
    fun buildAccountingPayload(
        documentType: String,
        documentNumber: String,
        documentDate: String,
        partyName: String,
        partyGstin: String?,
        narration: String?,
        items: List<Map<String, Any?>>,
        supplierStateCode: String? = null,
        customerStateCode: String? = null
    ): AccountingPayload {
        /**
         * Builds and validates accounting payload WITHOUT persistence.
         *
         * This function is PURE, DETERMINISTIC and AUDITABLE.
         */
        
        // ------------------------------
        // Document intent
        // ------------------------------
        if (documentType !in listOf("INVOICE", "DEBIT_NOTE", "CREDIT_NOTE", "PURCHASE")) {
            throw IllegalArgumentException("Unsupported document type: $documentType")
        }
        
        val voucherType = when (documentType) {
            "INVOICE" -> "SALES"
            "CREDIT_NOTE" -> "CREDIT_NOTE"
            "PURCHASE" -> "PURCHASE"
            else -> "DEBIT_NOTE"
        }
        
        if (items.isEmpty()) {
            throw RuntimeException("Accounting aborted: no invoice items")
        }
        
        // ------------------------------
        // GST computation (authoritative)
        // ------------------------------
        val gstResult = Gst.computeGst(
            items = items,
            supplierStateCode = supplierStateCode,
            customerStateCode = customerStateCode
        )
        
        val taxableTotal = toBigDecimal(gstResult.taxableTotal)
        val cgstTotal = toBigDecimal(gstResult.cgst?.amount ?: 0)
        val sgstTotal = toBigDecimal(gstResult.sgst?.amount ?: 0)
        val igstTotal = toBigDecimal(gstResult.igst?.amount ?: 0)
        
        if (taxableTotal <= BigDecimal.ZERO) {
            throw RuntimeException("Accounting aborted: taxable amount is zero")
        }
        
        // ------------------------------
        // Auditable narration
        // ------------------------------
        val fullNarration = if (!narration.isNullOrBlank()) {
            "$documentType $documentNumber | $narration"
        } else {
            "$documentType $documentNumber"
        }
        
        // ------------------------------
        // Payload (NO LEDGER LOGIC HERE)
        // ------------------------------
        return AccountingPayload(
            voucherType = voucherType,
            postingData = PostingData(
                voucherNo = documentNumber,
                voucherDate = documentDate,
                partyName = partyName.trim(),
                partyGstin = partyGstin,
                narration = fullNarration,
                taxableAmount = taxableTotal.toDouble(),
                cgstAmount = cgstTotal.toDouble(),
                sgstAmount = sgstTotal.toDouble(),
                igstAmount = igstTotal.toDouble()
            ),
            summary = Summary(
                taxable = taxableTotal.toDouble(),
                cgst = cgstTotal.toDouble(),
                sgst = sgstTotal.toDouble(),
                igst = igstTotal.toDouble(),
                grandTotal = (taxableTotal + cgstTotal + sgstTotal + igstTotal).toDouble()
            )
        )
    }
    
    // ==========================================================
    // POSTING ENTRY POINT (WITH DB)
    // ==========================================================
    fun postDocumentToAccounting(
        documentType: String,
        documentNumber: String,
        documentDate: String,
        partyName: String,
        partyGstin: String?,
        narration: String?,
        items: List<Map<String, Any?>>,
        supplierStateCode: String? = null,
        customerStateCode: String? = null,
        externalConn: Connection? = null
    ): Int {
        /**
         * Validates and POSTS accounting voucher.
         *
         * This is the ONLY DB-touching entry point
         * from services → accounting.
         */
        
        val payload = buildAccountingPayload(
            documentType = documentType,
            documentNumber = documentNumber,
            documentDate = documentDate,
            partyName = partyName,
            partyGstin = partyGstin,
            narration = narration,
            items = items,
            supplierStateCode = supplierStateCode,
            customerStateCode = customerStateCode
        )
        val voucherId = when (payload.voucherType) {
            "PURCHASE" -> Vouchers.postPurchaseVoucher(
                voucherNo = payload.postingData.voucherNo,
                voucherDate = payload.postingData.voucherDate,
                partyName = payload.postingData.partyName,
                partyGstin = payload.postingData.partyGstin,
                narration = payload.postingData.narration,
                taxableAmount = payload.postingData.taxableAmount,
                cgstAmount = payload.postingData.cgstAmount,
                sgstAmount = payload.postingData.sgstAmount,
                igstAmount = payload.postingData.igstAmount,
                externalConn = externalConn
            )
            else -> Vouchers.postSalesVoucher(
                voucherType = payload.voucherType,
                voucherNo = payload.postingData.voucherNo,
                voucherDate = payload.postingData.voucherDate,
                partyName = payload.postingData.partyName,
                partyGstin = payload.postingData.partyGstin,
                narration = payload.postingData.narration,
                taxableAmount = payload.postingData.taxableAmount,
                cgstAmount = payload.postingData.cgstAmount,
                sgstAmount = payload.postingData.sgstAmount,
                igstAmount = payload.postingData.igstAmount,
                externalConn = externalConn
            )
        }
        
        return voucherId
    }
    
    // ==========================================================
    
    // ==========================================================
    // TRANSACTION MANAGER SUPPORT (Internal)
    // ==========================================================
    fun postInvoiceInternal(
        conn: Connection,
        header: InvoiceModels.InvoiceHeader,
        items: List<InvoiceModels.InvoiceItem>
    ) {
        // Convert Models to Map
        val headerMap = mapOf(
             "invoice_number" to header.invoiceNo,
             "date" to header.invoiceDate,
             "customerName" to header.customerName,
             "gstin" to header.gstin,
             "place_of_supply" to header.placeOfSupply,
             "jobNo" to header.jobNo,
             "narration" to header.narration
        )
        
        val itemsList = items.map { item ->
            mapOf(
                "description" to item.description,
                "hsn_sac" to item.hsnSac,
                "qty" to item.qty,
                "rate" to item.rate,
                "amount" to item.amount,
                "taxable_amount" to item.taxableAmount,
                "cgst_rate" to item.cgstRate,
                "sgst_rate" to item.sgstRate,
                "igst_rate" to item.igstRate,
                "container_number" to item.containerNumber
            )
        }
        
        // Reuse logic but pass connection
        postInvoice(headerMap, itemsList, conn)
    }

    fun postDebitNoteInternal(
        conn: Connection,
        header: InvoiceModels.InvoiceHeader,
        items: List<InvoiceModels.InvoiceItem>
    ) {
        // Convert Models to Map
        val headerMap = mapOf(
             "invoice_number" to header.invoiceNo,
             "date" to header.invoiceDate,
             "customerName" to header.customerName,
             "gstin" to header.gstin,
             "place_of_supply" to header.placeOfSupply,
             "narration" to header.narration
        )
        
        val itemsList = items.map { item ->
            mapOf(
                "description" to item.description,
                "hsn_sac" to item.hsnSac,
                "qty" to item.qty,
                "rate" to item.rate,
                "amount" to item.amount,
                "taxable_amount" to item.taxableAmount,
                "cgst_rate" to item.cgstRate,
                "sgst_rate" to item.sgstRate,
                "igst_rate" to item.igstRate,
                "container_number" to item.containerNumber
            )
        }
        
        postDebitNote(headerMap, itemsList, conn) // Pass connection!
    }
    
    fun postInvoice(
        header: Map<String, Any?>, 
        items: List<Map<String, Any?>>, 
        externalConn: Connection? = null
    ): PostResult {
        // 1. Resolve State Codes
        val supplierStateCode = "27" // TODO: Fetch from Settings (San Shipping = MH)
        val customerStateName = header["place_of_supply"] as? String
        val customerStateCode = Gst.getStateCode(customerStateName) ?: "27" // Default to intra-state if unknown? Or Error? Tally defaults to local.
        
        // 2. Build narration with job number for profitability tracking
        val jobNo = header["jobNo"] as? String ?: ""
        val originalNarration = header["narration"] as? String ?: ""
        val narrationWithJob = if (jobNo.isNotBlank()) {
            if (originalNarration.isNotBlank()) "$originalNarration [Job: $jobNo]"
            else "Job: $jobNo"
        } else {
            originalNarration
        }
        
        val voucherId = postDocumentToAccounting(
            documentType = "INVOICE",
            documentNumber = header["invoice_number"] as? String ?: header["invoiceNo"] as? String ?: "",
            documentDate = header["date"] as? String ?: "",
            partyName = header["customerName"] as? String ?: (header["bill_to"] as? String ?: "").split("\n").firstOrNull() ?: "",
            partyGstin = header["gstin"] as? String,
            narration = narrationWithJob,
            items = items,
            supplierStateCode = supplierStateCode,
            customerStateCode = customerStateCode,
            externalConn = externalConn
        )
        
        return PostResult(
            voucherId = voucherId,
            documentType = "INVOICE"
        )
    }
    
    fun postDebitNote(
        header: Map<String, Any?>, 
        items: List<Map<String, Any?>>,
        externalConn: Connection? = null
    ): PostResult {
        // 1. Resolve State Codes
        val supplierStateCode = "27" 
        val customerStateName = header["place_of_supply"] as? String
        val customerStateCode = Gst.getStateCode(customerStateName) ?: "27"
        
        val voucherId = postDocumentToAccounting(
            documentType = "DEBIT_NOTE",
            documentNumber = header["invoice_number"] as? String ?: header["invoiceNo"] as? String ?: "",
            documentDate = header["date"] as? String ?: "",
            partyName = header["customerName"] as? String ?: (header["bill_to"] as? String ?: "").split("\n").firstOrNull() ?: "",
            partyGstin = header["gstin"] as? String,
            narration = header["narration"] as? String,
            items = items,
            supplierStateCode = supplierStateCode,
            customerStateCode = customerStateCode,
            externalConn = externalConn
        )
        
        return PostResult(
            voucherId = voucherId,
            documentType = "DEBIT_NOTE"
        )
    }
    
    fun postCreditNote(
        header: Map<String, Any?>, 
        items: List<Map<String, Any?>>,
        externalConn: Connection? = null
    ): PostResult {
        val supplierStateCode = "27"
        val customerStateName = header["place_of_supply"] as? String
        val customerStateCode = Gst.getStateCode(customerStateName) ?: "27"
        
        // Build payload to compute GST
        val payload = buildAccountingPayload(
            documentType = "CREDIT_NOTE",
            documentNumber = header["invoice_number"] as? String ?: header["invoiceNo"] as? String ?: "",
            documentDate = header["date"] as? String ?: "",
            partyName = header["customerName"] as? String ?: "",
            partyGstin = header["gstin"] as? String,
            narration = header["narration"] as? String,
            items = items,
            supplierStateCode = supplierStateCode,
            customerStateCode = customerStateCode
        )
        
        // Use Credit Note voucher poster (reversed entries)
        val voucherId = Vouchers.postCreditNoteVoucher(
            voucherNo = payload.postingData.voucherNo,
            voucherDate = payload.postingData.voucherDate,
            partyName = payload.postingData.partyName,
            partyGstin = payload.postingData.partyGstin,
            narration = payload.postingData.narration,
            taxableAmount = payload.postingData.taxableAmount,
            cgstAmount = payload.postingData.cgstAmount,
            sgstAmount = payload.postingData.sgstAmount,
            igstAmount = payload.postingData.igstAmount,
            externalConn = externalConn
        )
        
        return PostResult(voucherId = voucherId, documentType = "CREDIT_NOTE")
    }
    
    fun postCreditNoteInternal(
        conn: Connection,
        header: InvoiceModels.InvoiceHeader,
        items: List<InvoiceModels.InvoiceItem>
    ) {
        val headerMap = mapOf(
             "invoice_number" to header.invoiceNo,
             "date" to header.invoiceDate,
             "customerName" to header.customerName,
             "gstin" to header.gstin,
             "place_of_supply" to header.placeOfSupply,
             "narration" to header.narration
        )
        
        val itemsList = items.map { item ->
            mapOf(
                "description" to item.description,
                "hsn_sac" to item.hsnSac,
                "qty" to item.qty,
                "rate" to item.rate,
                "amount" to item.amount,
                "taxable_amount" to item.taxableAmount,
                "cgst_rate" to item.cgstRate,
                "sgst_rate" to item.sgstRate,
                "igst_rate" to item.igstRate,
                "container_number" to item.containerNumber
            )
        }
        
        postCreditNote(headerMap, itemsList, conn)
    }
    fun postPurchase(
        header: Map<String, Any?>,
        items: List<Map<String, Any?>>,
        externalConn: Connection? = null
    ): PostResult {
        // Resolve State Codes
        val supplierStateName = header["place_of_supply"] as? String
        val supplierStateCode = Gst.getStateCode(supplierStateName) ?: "27"
        val customerStateCode = "27" // San Shipping (Receiver MH)
        
        val voucherId = postDocumentToAccounting(
            documentType = "PURCHASE",
            documentNumber = header["purchaseNo"] as? String ?: "",
            documentDate = header["date"] as? String ?: "",
            partyName = header["vendorName"] as? String ?: "",
            partyGstin = header["vendorGstin"] as? String,
            narration = header["narration"] as? String,
            items = items,
            supplierStateCode = supplierStateCode,
            customerStateCode = customerStateCode,
            externalConn = externalConn
        )
        
        return PostResult(voucherId = voucherId, documentType = "PURCHASE")
    }

    fun postPurchaseInternal(
        conn: Connection,
        header: PurchaseHeader,
        items: List<PurchaseItem>
    ) {
        val headerMap = mapOf<String, Any?>(
            "purchaseNo" to header.purchaseNo,
            "date" to header.date,
            "vendorName" to header.vendorName,
            "vendorGstin" to header.vendorGstin,
            "place_of_supply" to header.placeOfSupply,
            "narration" to header.narration
        )
        
        val itemsList = items.map { item ->
            mapOf<String, Any?>(
                "description" to item.description,
                "hsn_sac" to item.hsnSac,
                "qty" to item.qty,
                "rate" to item.rate,
                "amount" to item.amount,
                "taxable_amount" to item.taxableAmount,
                "cgst_rate" to item.cgstRate,
                "sgst_rate" to item.sgstRate,
                "igst_rate" to item.igstRate
            )
        }
        
        postPurchase(headerMap, itemsList, conn)
    }
}


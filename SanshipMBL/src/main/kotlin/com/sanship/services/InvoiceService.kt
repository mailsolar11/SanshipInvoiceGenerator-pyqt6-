package com.sanship.services

import com.sanship.data.DatabaseManager
import com.sanship.services.AccountingEngine.postInvoice
import com.sanship.services.AccountingEngine.postDebitNote
import com.sanship.data.InvoiceModels.InvoiceHeader
import com.sanship.data.InvoiceModels.InvoiceItem
import com.sanship.services.TransactionManager

/**
 * INVOICE SERVICE (APPLICATION LAYER)
 * -----------------------------------
 *
 * This file wires together:
 *
 * UI → Business DB → Accounting Engine
 *
 * Responsibilities:
 * - Save invoice + items
 * - Post accounting voucher
 * - Ensure atomic consistency (business + accounting)
 *
 * EXACT REPLICA of Python src/services/invoice_service.py
 */
object InvoiceService {
    
    // =====================================================
    // INTERNAL HELPERS FOR TRANSACTION MANAGER
    // =====================================================
    fun saveInvoiceInternal(
        conn: java.sql.Connection,
        header: InvoiceHeader,
        items: List<InvoiceItem>
    ): Int {
        // Convert models to maps for existing insertInvoice
        // OR better: overload insertInvoice to take models
        
        // For now, let's map models to map to reuse existing private fun
        val headerMap = mapOf<String, Any?>(
            "id" to header.id,
            "invoiceNo" to header.invoiceNo,
            "date" to header.invoiceDate,
            "type" to header.documentType,
            "customerId" to header.customerId,
            "customerName" to header.customerName,
            "billingAddress" to header.billingAddress,
            "gstin" to header.gstin,
            "reverseCharge" to if (header.reverseCharge) 1 else 0,
            "jobId" to header.jobId,
            "jobNo" to header.jobNo,
            "placeOfSupply" to header.placeOfSupply,
            "pan" to header.pan,
            "stateCode" to header.stateCode,
            "shipper" to header.shipper,
            "consignee" to header.consignee,
            "pol" to header.pol,
            "pod" to header.pod,
            "vessel" to header.vesselFlight,
            "etd" to header.etd,
            "eta" to header.eta,
            "mblNo" to header.mblNo,
            "hblNo" to header.hblNo,
            "containerNos" to header.containerNos,
            "shipperInvoiceNo" to header.shipperInvoiceNo,
            "shipperInvoiceDate" to header.shipperInvoiceDate,
            "category" to header.category,
            "grossWeight" to header.grossWeight,
            "netWeight" to header.netWeight,
            "netWeightUnit" to header.netWeightUnit,
            "packages" to header.packages,
            "volumeCbm" to header.volumeCbm,
            "beNo" to header.beNo,
            "beDate" to header.beDate,
            "igmNo" to header.igmNo,
            "igmDate" to header.igmDate,
            "itemNo" to header.itemNo,
            "exchangeRate" to header.exchangeRate,
            "refNo" to header.refNo,
            "otherRefNo" to header.otherRefNo,
            "irn" to header.irn,
            "ackNo" to header.ackNo,
            "ackDate" to header.ackDate,
            "signedQr" to header.signedQr,
            "signedInvoice" to header.signedInvoice,
            "taxableAmount" to header.taxableAmount,
            "cgstAmount" to header.cgstAmount,
            "sgstAmount" to header.sgstAmount,
            "igstAmount" to header.igstAmount,
            "grandTotal" to header.grandTotal,
            "narration" to header.narration
        )
        
        val itemsMap = items.map { item ->
            mapOf<String, Any?>(
                "description" to item.description,
                "hsnCode" to item.hsnSac,
                "qty" to item.qty,
                "rate" to item.rate,
                "amount" to item.amount,
                "taxableAmount" to item.taxableAmount,
                "cgstRate" to item.cgstRate,
                "cgstAmount" to item.cgstAmt,
                "sgstRate" to item.sgstRate,
                "sgstAmount" to item.sgstAmt,
                "igstRate" to item.igstRate,
                "igstAmount" to item.igstAmt,
                "total" to item.totalAmt
            )
        }
        
        return insertInvoice(conn, headerMap, itemsMap)
    }

    // =====================================================
    // INVOICE SAVE + POST
    // =====================================================
    fun saveAndPostInvoice(
        header: Map<String, Any?>,
        items: List<Map<String, Any?>>
    ): Int {
        /**
         * Saves invoice to business DB and posts accounting voucher.
         *
         * If accounting fails → invoice is NOT saved.
         */
        
        DatabaseManager.connect()?.use { conn ->
            conn.autoCommit = false
            
            try {
                // ---------------------------------------------
                // 1. Save Invoice (Business DB)
                // ---------------------------------------------
                val invoiceId = insertInvoice(conn, header, items)
                
                // ---------------------------------------------
                // 2. Post to Accounting (authoritative)
                // ---------------------------------------------
                postInvoice(
                    header = header,
                    items = items
                )
                
                // ---------------------------------------------
                // 3. Save Voucher Summary to Business DB for Job Profitability
                // ---------------------------------------------
                val jobId = header["jobId"] as? Int ?: 0
                val invoiceNo = header["invoiceNo"] as? String ?: ""
                val date = header["date"] as? String ?: ""
                val taxableAmount = header["taxableAmount"] as? Double ?: 0.0
                val cgstAmount = header["cgstAmount"] as? Double ?: 0.0
                val sgstAmount = header["sgstAmount"] as? Double ?: 0.0
                val igstAmount = header["igstAmount"] as? Double ?: 0.0
                val grandTotal = header["grandTotal"] as? Double ?: 0.0
                
                // Insert voucher to business DB
                val voucherSql = """
                    INSERT INTO vouchers (voucher_no, voucher_type, voucher_date, narration, job_id)
                    VALUES (?, 'SALES', ?, ?, ?)
                """
                var voucherId = 0
                conn.prepareStatement(voucherSql, java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setString(1, invoiceNo)
                    ps.setString(2, date)
                    ps.setString(3, "Invoice: $invoiceNo")
                    ps.setInt(4, jobId)
                    ps.executeUpdate()
                    val rs = ps.generatedKeys
                    if (rs.next()) voucherId = rs.getInt(1)
                }
                
                // Insert ledger entries for profitability tracking
                // We need to get/create ledger IDs in business DB
                if (voucherId > 0) {
                    // Get or create SALES ledger in business DB
                    val salesLedgerId = getOrCreateLedger(conn, "SALES", "INCOME")
                    
                    // Create entry: CREDIT SALES (income)
                    val entrySql = """
                        INSERT INTO ledger_entries (voucher_id, ledger_id, dr_amount, cr_amount)
                        VALUES (?, ?, ?, ?)
                    """
                    conn.prepareStatement(entrySql).use { ps ->
                        ps.setInt(1, voucherId)
                        ps.setInt(2, salesLedgerId)
                        ps.setDouble(3, 0.0)
                        ps.setDouble(4, taxableAmount)
                        ps.executeUpdate()
                    }
                }
                
                conn.commit()
                return invoiceId
                
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        } ?: throw RuntimeException("Database connection failed")
    }
    
    private fun getOrCreateLedger(conn: java.sql.Connection, name: String, groupNature: String): Int {
        // Check if ledger exists
        val checkSql = "SELECT id FROM ledgers WHERE name = ?"
        conn.prepareStatement(checkSql).use { ps ->
            ps.setString(1, name)
            val rs = ps.executeQuery()
            if (rs.next()) return rs.getInt("id")
        }
        
        // Get or create group
        var groupId = 0
        val groupCheckSql = "SELECT id FROM ledger_groups WHERE nature = ?"
        conn.prepareStatement(groupCheckSql).use { ps ->
            ps.setString(1, groupNature)
            val rs = ps.executeQuery()
            if (rs.next()) {
                groupId = rs.getInt("id")
            } else {
                // Create group
                val groupInsertSql = "INSERT INTO ledger_groups (name, nature) VALUES (?, ?)"
                conn.prepareStatement(groupInsertSql, java.sql.Statement.RETURN_GENERATED_KEYS).use { insertPs ->
                    insertPs.setString(1, groupNature)
                    insertPs.setString(2, groupNature)
                    insertPs.executeUpdate()
                    val grs = insertPs.generatedKeys
                    if (grs.next()) groupId = grs.getInt(1)
                }
            }
        }
        
        // Create ledger
        val ledgerInsertSql = "INSERT INTO ledgers (name, group_id, is_system, opening_balance) VALUES (?, ?, 1, 0)"
        conn.prepareStatement(ledgerInsertSql, java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, name)
            ps.setInt(2, groupId)
            ps.executeUpdate()
            val rs = ps.generatedKeys
            if (rs.next()) return rs.getInt(1)
        }
        
        return 0
    }
    
    // =====================================================
    // DEBIT NOTE SAVE + POST
    // =====================================================
    fun saveAndPostDebitNote(
        header: Map<String, Any?>,
        items: List<Map<String, Any?>>
    ): Int {
        /**
         * Saves debit note and posts accounting voucher.
         */
        
        DatabaseManager.connect()?.use { conn ->
            conn.autoCommit = false
            
            try {
                val invoiceId = insertInvoice(conn, header, items)
                
                postDebitNote(
                    header = header,
                    items = items
                )
                
                conn.commit()
                return invoiceId
                
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        } ?: throw RuntimeException("Database connection failed")
    }
    
    // =====================================================
    // PRIVATE HELPER (matches database.py insert_invoice)
    // =====================================================
    // =====================================================
    // PRIVATE HELPER (matches database.py insert_invoice)
    // =====================================================
    private fun insertInvoice(
        conn: java.sql.Connection,
        header: Map<String, Any?>,
        items: List<Map<String, Any?>>
    ): Int {
        // Build column list and values from header
        // FIX: Remove 'id' if present to let autoincrement work, or handle update
        val cleanHeader = header.filterKeys { it != "id" }
        
        val cols = cleanHeader.keys.joinToString(", ")
        val placeholders = cleanHeader.keys.joinToString(", ") { "?" }
        
        val stmt = conn.prepareStatement(
            "INSERT OR REPLACE INTO invoices ($cols) VALUES ($placeholders)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        )
        
        cleanHeader.values.forEachIndexed { index, value ->
            stmt.setObject(index + 1, value)
        }
        
        stmt.executeUpdate()
        val rs = stmt.generatedKeys
        val invoiceId = if (rs.next()) rs.getInt(1) else 0
        
        // Insert items
        val itemStmt = conn.prepareStatement("""
            INSERT INTO invoice_items
            (invoice_id, sr_no, description, hsn_sac, currency, rate, qty, amount,
             taxable_amount, cgst_rate, cgst_amt, sgst_rate, sgst_amt, igst_rate, igst_amt, total_amt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent())
        
        for (item in items) {
            itemStmt.setInt(1, invoiceId) // Use integer ID as FK
            itemStmt.setInt(2, item["sr_no"] as? Int ?: 0)
            itemStmt.setString(3, item["description"] as? String)
            itemStmt.setString(4, item["hsn_sac"] as? String)
            itemStmt.setString(5, item["currency"] as? String)
            itemStmt.setDouble(6, item["rate"] as? Double ?: 0.0)
            itemStmt.setDouble(7, item["qty"] as? Double ?: 0.0)
            itemStmt.setDouble(8, item["amount"] as? Double ?: 0.0)
            itemStmt.setDouble(9, item["taxable_amount"] as? Double ?: 0.0)
            itemStmt.setDouble(10, item["cgst_rate"] as? Double ?: 0.0)
            itemStmt.setDouble(11, item["cgst_amt"] as? Double ?: 0.0)
            itemStmt.setDouble(12, item["sgst_rate"] as? Double ?: 0.0)
            itemStmt.setDouble(13, item["sgst_amt"] as? Double ?: 0.0)
            itemStmt.setDouble(14, item["igst_rate"] as? Double ?: 0.0)
            itemStmt.setDouble(15, item["igst_amt"] as? Double ?: 0.0)
            itemStmt.setDouble(16, item["total_amt"] as? Double ?: 0.0)
            itemStmt.addBatch()
        }
        
        itemStmt.executeBatch()
        
        return invoiceId
    }
    
    // =====================================================
    // TYPED API (for ViewModel)
    // =====================================================
    fun saveInvoiceWithAccounting(
        header: com.sanship.data.InvoiceModels.InvoiceHeader,
        items: List<com.sanship.data.InvoiceModels.InvoiceItem>,
        voucherType: String // "SALES" or "DEBIT_NOTE"
    ): Int {
        /**
         * Typed version for use with ViewModels.
         * Delegates to TransactionManager for 2PC.
         */
        
        return when (voucherType) {
            "SALES" -> TransactionManager.saveInvoice(header, items)
            "DEBIT_NOTE" -> TransactionManager.saveDebitNote(header, items)
            else -> throw IllegalArgumentException("Invalid voucher type: $voucherType")
        }
    }
}

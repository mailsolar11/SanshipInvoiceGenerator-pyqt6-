package com.sanship.accounting

import com.sanship.accounting.AccountingDb.getConnection
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * VOUCHER POSTING ENGINE
 * =====================
 *
 * Single authority for:
 * - Voucher creation
 * - Ledger entry posting
 * - DR / CR enforcement
 * - Atomic persistence
 *
 * NO OTHER MODULE writes to accounting DB.
 *
 * EXACT REPLICA of Python src/accounting/vouchers.py
 */
object Vouchers {
    
    // ==========================================================
    // UTILS
    // ==========================================================
    private fun toBigDecimal(v: Any?): BigDecimal {
        return BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP)
    }
    
    // ==========================================================
    // CORE POSTING FUNCTION
    // ==========================================================
    fun postSalesVoucher(
        voucherType: String,
        voucherNo: String,
        voucherDate: String,
        partyName: String,
        partyGstin: String?,
        narration: String,
        taxableAmount: Double,
        cgstAmount: Double = 0.0,
        sgstAmount: Double = 0.0,
        igstAmount: Double = 0.0,
        externalConn: java.sql.Connection? = null
    ): Int {
        /**
         * Posts SALES / DEBIT NOTE voucher.
         *
         * Accounting rules:
         * - Party ledger → DR
         * - Income & tax ledgers → CR
         * - DR must equal CR (strict)
         */
        
        if (voucherType !in listOf("SALES", "DEBIT_NOTE")) {
            throw IllegalArgumentException("Unsupported voucher type: $voucherType")
        }
        
        // Ensure base ledgers exist (safe, idempotent)
        Ledgers.ensureSystemLedgers()
        
        // --------------------------------------------------
        // Resolve ledgers (CANONICAL NAMES)
        // --------------------------------------------------
        val partyLedger = Ledgers.getOrCreatePartyLedger(partyName, partyGstin)
        val salesLedger = Ledgers.getLedgerId("SALES")
        val cgstLedger = Ledgers.getLedgerId("CGST OUTPUT")
        val sgstLedger = Ledgers.getLedgerId("SGST OUTPUT")
        val igstLedger = Ledgers.getLedgerId("IGST OUTPUT")
        
        val total = toBigDecimal(taxableAmount) +
                    toBigDecimal(cgstAmount) +
                    toBigDecimal(sgstAmount) +
                    toBigDecimal(igstAmount)
        
        if (total <= BigDecimal.ZERO) {
            throw RuntimeException("Voucher total cannot be zero")
        }
        
        // --------------------------------------------------
        // Build ledger entries
        // --------------------------------------------------
        val entries = mutableListOf<Map<String, Any?>>()
        
        // Party DR
        entries.add(mapOf(
            "ledger_id" to partyLedger,
            "dr" to total,
            "cr" to BigDecimal.ZERO
        ))
        
        // Sales CR
        if (toBigDecimal(taxableAmount) > BigDecimal.ZERO) {
            entries.add(mapOf(
                "ledger_id" to salesLedger,
                "dr" to BigDecimal.ZERO,
                "cr" to toBigDecimal(taxableAmount)
            ))
        }
        
        // GST CRs
        if (toBigDecimal(cgstAmount) > BigDecimal.ZERO) {
            entries.add(mapOf(
                "ledger_id" to cgstLedger,
                "dr" to BigDecimal.ZERO,
                "cr" to toBigDecimal(cgstAmount)
            ))
        }
        
        if (toBigDecimal(sgstAmount) > BigDecimal.ZERO) {
            entries.add(mapOf(
                "ledger_id" to sgstLedger,
                "dr" to BigDecimal.ZERO,
                "cr" to toBigDecimal(sgstAmount)
            ))
        }
        
        if (toBigDecimal(igstAmount) > BigDecimal.ZERO) {
            entries.add(mapOf(
                "ledger_id" to igstLedger,
                "dr" to BigDecimal.ZERO,
                "cr" to toBigDecimal(igstAmount)
            ))
        }
        
        // --------------------------------------------------
        // Validate accounting (NON-NEGOTIABLE)
        // --------------------------------------------------
        Validation.validateEntries(entries)
        
        // --------------------------------------------------
        // Persist ATOMICALLY (UPSERT LOGIC)
        // --------------------------------------------------
        
        // Helper block to execute logic with a given connection
        fun executeWithConnection(conn: java.sql.Connection): Int {
             try {
                // 1. Check if Voucher Exists
                var voucherId = -1
                conn.prepareStatement("SELECT id FROM vouchers WHERE voucher_no = ?").use { ps ->
                    ps.setString(1, voucherNo)
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        voucherId = rs.getInt("id")
                    }
                }
                
                if (voucherId != -1) {
                    // --- UPDATE EXISTING ---
                    
                    // Update Header
                    conn.prepareStatement(
                        "UPDATE vouchers SET voucher_date = ?, narration = ?, voucher_type = ? WHERE id = ?"
                    ).use { ps ->
                        ps.setString(1, voucherDate)
                        ps.setString(2, narration)
                        ps.setString(3, voucherType)
                        ps.setInt(4, voucherId)
                        ps.executeUpdate()
                    }
                    
                    // Delete Old Entries (to be replaced)
                    conn.prepareStatement("DELETE FROM ledger_entries WHERE voucher_id = ?").use { ps ->
                        ps.setInt(1, voucherId)
                        ps.executeUpdate()
                    }
                    
                } else {
                    // --- INSERT NEW ---
                    conn.prepareStatement(
                        """
                        INSERT INTO vouchers
                        (voucher_no, voucher_type, voucher_type_id, voucher_date, narration)
                        VALUES (?, ?, ?, ?, ?)
                        """.trimIndent(),
                        java.sql.Statement.RETURN_GENERATED_KEYS
                    ).use { ps ->
                        ps.setString(1, voucherNo)
                        ps.setString(2, voucherType)
                        ps.setInt(3, 1) // Dummy value to satisfy old NOT NULL constraint
                        ps.setString(4, voucherDate)
                        ps.setString(5, narration)
                        ps.executeUpdate()
                        
                        val rs = ps.generatedKeys
                        if (rs.next()) voucherId = rs.getInt(1) else 0
                    }
                }
                
                // 2. Insert Ledger Entries (New or Replacement)
                if (voucherId != 0) {
                    val insertEntry = """
                        INSERT INTO ledger_entries (voucher_id, ledger_id, dr_amount, cr_amount)
                        VALUES (?, ?, ?, ?)
                    """
                    conn.prepareStatement(insertEntry).use { ps ->
                        for (e in entries) {
                            ps.setInt(1, voucherId)
                            ps.setInt(2, e["ledger_id"] as Int)
                            ps.setDouble(3, (e["dr"] as BigDecimal).toDouble())
                            ps.setDouble(4, (e["cr"] as BigDecimal).toDouble())
                            ps.addBatch()
                        }
                        ps.executeBatch()
                    }
                }
                
                return voucherId
                
            } catch (e: Exception) {
                // Should only throw, rollback is handled by caller if externalConn is present
                throw e
            }
        }

        return if (externalConn != null) {
            // Use external connection (TransactionManager handles commit/rollback)
            executeWithConnection(externalConn)
        } else {
            // Use internal connection management (Default)
            getConnection().use { conn ->
                conn.autoCommit = false
                try {
                    val result = executeWithConnection(conn)
                    conn.commit()
                    return result
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                }
            }
        }
    }
    
    // ==========================================================
    // CREDIT NOTE POSTING (REVERSAL OF SALES)
    // ==========================================================
    fun postCreditNoteVoucher(
        voucherNo: String,
        voucherDate: String,
        partyName: String,
        partyGstin: String?,
        narration: String,
        taxableAmount: Double,
        cgstAmount: Double = 0.0,
        sgstAmount: Double = 0.0,
        igstAmount: Double = 0.0,
        externalConn: java.sql.Connection? = null
    ): Int {
        /**
         * Posts CREDIT NOTE voucher.
         *
         * Accounting rules (REVERSE of Sales):
         * - Sales & tax ledgers → DR (reduce income/liability)
         * - Party ledger → CR (reduce receivable)
         * - DR must equal CR (strict)
         */
        
        Ledgers.ensureSystemLedgers()
        
        val partyLedger = Ledgers.getOrCreatePartyLedger(partyName, partyGstin)
        val salesLedger = Ledgers.getLedgerId("SALES")
        val cgstLedger = Ledgers.getLedgerId("CGST OUTPUT")
        val sgstLedger = Ledgers.getLedgerId("SGST OUTPUT")
        val igstLedger = Ledgers.getLedgerId("IGST OUTPUT")
        
        val total = toBigDecimal(taxableAmount) +
                    toBigDecimal(cgstAmount) +
                    toBigDecimal(sgstAmount) +
                    toBigDecimal(igstAmount)
        
        if (total <= BigDecimal.ZERO) {
            throw RuntimeException("Credit Note total cannot be zero")
        }
        
        val entries = mutableListOf<Map<String, Any?>>()
        
        // Sales DR (reverse income)
        if (toBigDecimal(taxableAmount) > BigDecimal.ZERO) {
            entries.add(mapOf(
                "ledger_id" to salesLedger,
                "dr" to toBigDecimal(taxableAmount),
                "cr" to BigDecimal.ZERO
            ))
        }
        
        // GST DRs (reverse tax liability)
        if (toBigDecimal(cgstAmount) > BigDecimal.ZERO) {
            entries.add(mapOf(
                "ledger_id" to cgstLedger,
                "dr" to toBigDecimal(cgstAmount),
                "cr" to BigDecimal.ZERO
            ))
        }
        if (toBigDecimal(sgstAmount) > BigDecimal.ZERO) {
            entries.add(mapOf(
                "ledger_id" to sgstLedger,
                "dr" to toBigDecimal(sgstAmount),
                "cr" to BigDecimal.ZERO
            ))
        }
        if (toBigDecimal(igstAmount) > BigDecimal.ZERO) {
            entries.add(mapOf(
                "ledger_id" to igstLedger,
                "dr" to toBigDecimal(igstAmount),
                "cr" to BigDecimal.ZERO
            ))
        }
        
        // Party CR (reduce receivable)
        entries.add(mapOf(
            "ledger_id" to partyLedger,
            "dr" to BigDecimal.ZERO,
            "cr" to total
        ))
        
        Validation.validateEntries(entries)
        
        // Persist using same upsert logic as postSalesVoucher
        fun executeWithConnection(conn: java.sql.Connection): Int {
            try {
                var voucherId = -1
                conn.prepareStatement("SELECT id FROM vouchers WHERE voucher_no = ?").use { ps ->
                    ps.setString(1, voucherNo)
                    val rs = ps.executeQuery()
                    if (rs.next()) voucherId = rs.getInt("id")
                }
                
                if (voucherId != -1) {
                    conn.prepareStatement(
                        "UPDATE vouchers SET voucher_date = ?, narration = ?, voucher_type = ? WHERE id = ?"
                    ).use { ps ->
                        ps.setString(1, voucherDate)
                        ps.setString(2, narration)
                        ps.setString(3, "CREDIT_NOTE")
                        ps.setInt(4, voucherId)
                        ps.executeUpdate()
                    }
                    conn.prepareStatement("DELETE FROM ledger_entries WHERE voucher_id = ?").use { ps ->
                        ps.setInt(1, voucherId)
                        ps.executeUpdate()
                    }
                } else {
                    conn.prepareStatement(
                        """
                        INSERT INTO vouchers
                        (voucher_no, voucher_type, voucher_type_id, voucher_date, narration)
                        VALUES (?, ?, ?, ?, ?)
                        """.trimIndent(),
                        java.sql.Statement.RETURN_GENERATED_KEYS
                    ).use { ps ->
                        ps.setString(1, voucherNo)
                        ps.setString(2, "CREDIT_NOTE")
                        ps.setInt(3, 1)
                        ps.setString(4, voucherDate)
                        ps.setString(5, narration)
                        ps.executeUpdate()
                        val rs = ps.generatedKeys
                        if (rs.next()) voucherId = rs.getInt(1) else 0
                    }
                }
                
                if (voucherId != 0) {
                    val insertEntry = """
                        INSERT INTO ledger_entries (voucher_id, ledger_id, dr_amount, cr_amount)
                        VALUES (?, ?, ?, ?)
                    """
                    conn.prepareStatement(insertEntry).use { ps ->
                        for (e in entries) {
                            ps.setInt(1, voucherId)
                            ps.setInt(2, e["ledger_id"] as Int)
                            ps.setDouble(3, (e["dr"] as BigDecimal).toDouble())
                            ps.setDouble(4, (e["cr"] as BigDecimal).toDouble())
                            ps.addBatch()
                        }
                        ps.executeBatch()
                    }
                }
                return voucherId
            } catch (e: Exception) { throw e }
        }
        
        return if (externalConn != null) {
            executeWithConnection(externalConn)
        } else {
            getConnection().use { conn ->
                conn.autoCommit = false
                try {
                    val result = executeWithConnection(conn)
                    conn.commit()
                    return result
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                }
            }
        }
    }
    fun postPurchaseVoucher(
        voucherNo: String,
        voucherDate: String,
        partyName: String,
        partyGstin: String?,
        narration: String,
        taxableAmount: Double,
        cgstAmount: Double = 0.0,
        sgstAmount: Double = 0.0,
        igstAmount: Double = 0.0,
        externalConn: java.sql.Connection? = null
    ): Int {
        /**
         * Posts PURCHASE voucher.
         *
         * Accounting rules:
         * - Purchase & tax ledgers → DR
         * - Party ledger → CR
         * - DR must equal CR
         */
        
        Ledgers.ensureSystemLedgers()
        
        val partyLedger = Ledgers.getOrCreatePartyLedger(partyName, partyGstin, groupName = "Liabilities")
        val purchaseLedger = Ledgers.getLedgerId("PURCHASES")
        val cgstLedger = Ledgers.getLedgerId("CGST INPUT")
        val sgstLedger = Ledgers.getLedgerId("SGST INPUT")
        val igstLedger = Ledgers.getLedgerId("IGST INPUT")
        
        val total = toBigDecimal(taxableAmount) +
                    toBigDecimal(cgstAmount) +
                    toBigDecimal(sgstAmount) +
                    toBigDecimal(igstAmount)
        
        if (total <= BigDecimal.ZERO) {
            throw RuntimeException("Purchase total cannot be zero")
        }
        
        val entries = mutableListOf<Map<String, Any?>>()
        
        // Purchase DR
        if (toBigDecimal(taxableAmount) > BigDecimal.ZERO) {
            entries.add(mapOf(
                "ledger_id" to purchaseLedger,
                "dr" to toBigDecimal(taxableAmount),
                "cr" to BigDecimal.ZERO
            ))
        }
        
        // GST DRs
        if (toBigDecimal(cgstAmount) > BigDecimal.ZERO) {
            entries.add(mapOf("ledger_id" to cgstLedger, "dr" to toBigDecimal(cgstAmount), "cr" to BigDecimal.ZERO))
        }
        if (toBigDecimal(sgstAmount) > BigDecimal.ZERO) {
            entries.add(mapOf("ledger_id" to sgstLedger, "dr" to toBigDecimal(sgstAmount), "cr" to BigDecimal.ZERO))
        }
        if (toBigDecimal(igstAmount) > BigDecimal.ZERO) {
            entries.add(mapOf("ledger_id" to igstLedger, "dr" to toBigDecimal(igstAmount), "cr" to BigDecimal.ZERO))
        }
        
        // Party CR
        entries.add(mapOf(
            "ledger_id" to partyLedger,
            "dr" to BigDecimal.ZERO,
            "cr" to total
        ))
        
        Validation.validateEntries(entries)
        
        fun executeWithConnection(conn: java.sql.Connection): Int {
            try {
                var voucherId = -1
                conn.prepareStatement("SELECT id FROM vouchers WHERE voucher_no = ?").use { ps ->
                    ps.setString(1, voucherNo)
                    val rs = ps.executeQuery()
                    if (rs.next()) voucherId = rs.getInt("id")
                }
                
                if (voucherId != -1) {
                    conn.prepareStatement(
                        "UPDATE vouchers SET voucher_date = ?, narration = ?, voucher_type = ? WHERE id = ?"
                    ).use { ps ->
                        ps.setString(1, voucherDate)
                        ps.setString(2, narration)
                        ps.setString(3, "PURCHASE")
                        ps.setInt(4, voucherId)
                        ps.executeUpdate()
                    }
                    conn.prepareStatement("DELETE FROM ledger_entries WHERE voucher_id = ?").use { ps ->
                        ps.setInt(1, voucherId)
                        ps.executeUpdate()
                    }
                } else {
                    conn.prepareStatement(
                        """
                        INSERT INTO vouchers
                        (voucher_no, voucher_type, voucher_type_id, voucher_date, narration)
                        VALUES (?, ?, ?, ?, ?)
                        """.trimIndent(),
                        java.sql.Statement.RETURN_GENERATED_KEYS
                    ).use { ps ->
                        ps.setString(1, voucherNo)
                        ps.setString(2, "PURCHASE")
                        ps.setInt(3, 1)
                        ps.setString(4, voucherDate)
                        ps.setString(5, narration)
                        ps.executeUpdate()
                        val rs = ps.generatedKeys
                        if (rs.next()) voucherId = rs.getInt(1) else 0
                    }
                }
                
                if (voucherId != 0) {
                    val insertEntry = """
                        INSERT INTO ledger_entries (voucher_id, ledger_id, dr_amount, cr_amount)
                        VALUES (?, ?, ?, ?)
                    """
                    conn.prepareStatement(insertEntry).use { ps ->
                        for (e in entries) {
                            ps.setInt(1, voucherId)
                            ps.setInt(2, e["ledger_id"] as Int)
                            ps.setDouble(3, (e["dr"] as BigDecimal).toDouble())
                            ps.setDouble(4, (e["cr"] as BigDecimal).toDouble())
                            ps.addBatch()
                        }
                        ps.executeBatch()
                    }
                }
                return voucherId
            } catch (e: Exception) { throw e }
        }
        
        return if (externalConn != null) {
            executeWithConnection(externalConn)
        } else {
            getConnection().use { conn ->
                conn.autoCommit = false
                try {
                    val result = executeWithConnection(conn)
                    conn.commit()
                    return result
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                }
            }
        }
    }
}

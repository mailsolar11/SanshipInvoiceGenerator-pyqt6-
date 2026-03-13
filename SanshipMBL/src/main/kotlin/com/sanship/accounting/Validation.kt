package com.sanship.accounting

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * ACCOUNTING VALIDATION ENGINE
 * ============================
 *
 * This module ENFORCES accounting law.
 *
 * If anything violates:
 * - DR ≠ CR
 * - Invalid amounts
 * - Empty entries
 * - Wrong voucher shape
 *
 * → it MUST FAIL.
 *
 * No UI, no DB writes, no side-effects.
 * Pure validation only.
 *
 * EXACT REPLICA of Python src/accounting/validation.py
 */
object Validation {
    
    // ======================================================
    // INTERNAL HELPERS
    // ======================================================
    private fun toBigDecimal(v: Any?): BigDecimal {
        return try {
            BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP)
        } catch (e: Exception) {
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        }
    }
    
    // ======================================================
    // CORE VALIDATION
    // ======================================================
    fun validateVoucher(voucher: Map<String, Any?>) {
        /**
         * Validates a voucher BEFORE it touches the database.
         *
         * Required structure:
         * {
         *     voucher_type: String,
         *     voucher_date: String,
         *     narration: String,
         *     entries: [
         *         {
         *             ledger_id: Int,
         *             dr: BigDecimal,
         *             cr: BigDecimal
         *         }
         *     ]
         * }
         */
        
        // --------------------------------------------------
        // Basic shape validation
        // --------------------------------------------------
        if (voucher.isEmpty()) {
            throw IllegalArgumentException("Voucher object missing")
        }
        
        for (key in listOf("voucher_type", "voucher_date", "entries")) {
            if (!voucher.containsKey(key)) {
                throw IllegalArgumentException("Voucher missing field: $key")
            }
        }
        
        @Suppress("UNCHECKED_CAST")
        val entries = voucher["entries"] as? List<Map<String, Any?>>
            ?: throw IllegalArgumentException("Entries must be a list")
        
        if (entries.size < 2) {
            throw IllegalArgumentException("Voucher must have at least 2 ledger entries")
        }
        
        // --------------------------------------------------
        // Entry-level validation
        // --------------------------------------------------
        var totalDr = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        var totalCr = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        
        entries.forEachIndexed { index, e ->
            val idx = index + 1
            
            if (!e.containsKey("ledger_id")) {
                throw IllegalArgumentException("Entry $idx: ledger_id missing")
            }
            
            val ledgerId = e["ledger_id"]
            if (ledgerId !is Int && ledgerId !is Long) {
                throw IllegalArgumentException("Entry $idx: invalid ledger_id")
            }
            
            val dr = toBigDecimal(e["dr"] ?: 0)
            val cr = toBigDecimal(e["cr"] ?: 0)
            
            if (dr < BigDecimal.ZERO || cr < BigDecimal.ZERO) {
                throw IllegalArgumentException("Entry $idx: negative amounts not allowed")
            }
            
            if (dr == BigDecimal.ZERO && cr == BigDecimal.ZERO) {
                throw IllegalArgumentException("Entry $idx: both DR and CR are zero")
            }
            
            if (dr > BigDecimal.ZERO && cr > BigDecimal.ZERO) {
                throw IllegalArgumentException("Entry $idx: both DR and CR present")
            }
            
            totalDr += dr
            totalCr += cr
        }
        
        // --------------------------------------------------
        // Fundamental accounting law
        // --------------------------------------------------
        if (totalDr != totalCr) {
            throw IllegalArgumentException(
                "DR/CR mismatch — DR=$totalDr CR=$totalCr"
            )
        }
        
        // --------------------------------------------------
        // Voucher-type specific rules (future-safe)
        // --------------------------------------------------
        val vt = voucher["voucher_type"] as? String ?: ""
        
        if (vt in listOf("SALES", "DEBIT_NOTE")) {
            // Party must be DR (at least one DR entry)
            val hasDr = entries.any { toBigDecimal(it["dr"] ?: 0) > BigDecimal.ZERO }
            if (!hasDr) {
                throw IllegalArgumentException("Sales voucher must have DR entry")
            }
        }
        
        // Additional voucher types can be enforced here:
        // PURCHASE, RECEIPT, PAYMENT, JOURNAL, CREDIT_NOTE
    }
    
    // ======================================================
    // SIMPLIFIED VALIDATION (for ledger entries only)
    // ======================================================
    fun validateEntries(entries: List<Map<String, Any?>>) {
        /**
         * Hard rules:
         * - Total DR must equal total CR
         * - No zero-value vouchers
         */
        
        val totalDr = entries.sumOf { toBigDecimal(it["dr"] ?: 0) }
        val totalCr = entries.sumOf { toBigDecimal(it["cr"] ?: 0) }
        
        if (totalDr.setScale(2, RoundingMode.HALF_UP) != totalCr.setScale(2, RoundingMode.HALF_UP)) {
            throw RuntimeException(
                "Accounting mismatch: DR=${totalDr.setScale(2, RoundingMode.HALF_UP)} CR=${totalCr.setScale(2, RoundingMode.HALF_UP)}"
            )
        }
        
        if (totalDr <= BigDecimal.ZERO) {
            throw RuntimeException("Voucher has zero value")
        }
    }
}

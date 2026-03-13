package com.sanship.data

import com.sanship.accounting.AccountingDb
import com.sanship.accounting.Ledgers

/**
 * Accounting Database Initialization
 * Exact replica of Python src/init_db.py
 */
object AccountingDatabaseManager {
    
    fun initAccountingDb() {
        val conn = AccountingDb.getConnection()
        conn.autoCommit = true // Enable auto-commit for DDL and seed data
        val stmt = conn.createStatement()
        
        // ---------------- Ledger Groups ----------------
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS ledger_groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                nature TEXT CHECK (
                    nature IN ('ASSET', 'LIABILITY', 'INCOME', 'EXPENSE')
                ) NOT NULL,
                parent_id INTEGER,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent())
        
        // ---------------- Ledgers ----------------
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS ledgers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                group_id INTEGER NOT NULL,
                opening_balance REAL DEFAULT 0,
                opening_type TEXT CHECK (opening_type IN ('DR','CR')),
                gstin TEXT,
                is_system INTEGER DEFAULT 0,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (group_id) REFERENCES ledger_groups(id)
            )
        """.trimIndent())
        
        // ---------------- Voucher Types ----------------
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS voucher_types (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL,
                affects_inventory INTEGER DEFAULT 0,
                is_system INTEGER DEFAULT 1
            )
        """.trimIndent())
        
        // ---------------- Vouchers ----------------
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS vouchers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                voucher_no TEXT UNIQUE NOT NULL,
                voucher_type_id INTEGER NOT NULL,
                voucher_date TEXT NOT NULL,
                narration TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (voucher_type_id) REFERENCES voucher_types(id)
            )
        """.trimIndent())
        
        // ---------------- Ledger Entries ----------------
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS ledger_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                voucher_id INTEGER NOT NULL,
                ledger_id INTEGER NOT NULL,
                dr_amount REAL DEFAULT 0,
                cr_amount REAL DEFAULT 0,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (voucher_id) REFERENCES vouchers(id),
                FOREIGN KEY (ledger_id) REFERENCES ledgers(id)
            )
        """.trimIndent())
        
        // ---------------- Seed Ledger Groups ----------------
        val ledgerGroups = listOf(
            "Assets" to "ASSET",
            "Liabilities" to "LIABILITY",
            "Income" to "INCOME",
            "Expenses" to "EXPENSE",
            "Duties & Taxes" to "LIABILITY"
        )
        
        val insertGroupStmt = conn.prepareStatement(
            "INSERT OR IGNORE INTO ledger_groups (name, nature) VALUES (?, ?)"
        )
        
        for ((name, nature) in ledgerGroups) {
            insertGroupStmt.setString(1, name)
            insertGroupStmt.setString(2, nature)
            insertGroupStmt.addBatch()
        }
        insertGroupStmt.executeBatch()
        
        // ---------------- Seed Voucher Types ----------------
        val voucherTypes = listOf(
            "SALES",
            "DEBIT_NOTE",
            "RECEIPT",
            "PAYMENT",
            "JOURNAL"
        )
        
        val insertVoucherTypeStmt = conn.prepareStatement(
            "INSERT OR IGNORE INTO voucher_types (name, affects_inventory, is_system) VALUES (?, 0, 1)"
        )
        
        for (vt in voucherTypes) {
            insertVoucherTypeStmt.setString(1, vt)
            insertVoucherTypeStmt.addBatch()
        }
        insertVoucherTypeStmt.executeBatch()

        // --- PHASE 20 MIGRATIONS (Fix Schema Mismatch for Vouchers) ---
        // The code now uses 'voucher_type' (String) directly instead of 'voucher_type_id' (FK)
        // because the 'voucher_types' table link was causing issues.
        try {
            stmt.execute("ALTER TABLE vouchers ADD COLUMN voucher_type TEXT")
            // Backfill attempts (if voucher_type_id exists) could go here, but for now just ensuring column exists
            // is enough to stop the crash.
        } catch (e: Exception) { 
             // Column likely exists
        }

        // --- PHASE 4 MIGRATIONS (Bank Reconciliation) ---
        try {
            stmt.execute("ALTER TABLE ledger_entries ADD COLUMN bank_date TEXT")
        } catch (e: Exception) {
            // Column likely exists
        }
        
        // Close connection
        conn.close()
        
        // ---------------- System Ledgers ----------------
        // Must be called AFTER connection is closed
        Ledgers.ensureSystemLedgers()
        
        println("✅ Accounting database initialized successfully")
    }
}

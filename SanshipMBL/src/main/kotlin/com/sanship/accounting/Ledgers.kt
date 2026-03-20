package com.sanship.accounting

import com.sanship.accounting.AccountingDb.getConnection

/**
 * LEDGER ENGINE
 * =============
 * Single source of truth for:
 * - Ledger creation
 * - Ledger lookup
 * - System ledger protection
 *
 * This module is SELF-HEALING:
 * It ensures required tables exist before use.
 *
 * EXACT REPLICA of Python src/accounting/ledgers.py
 */
object Ledgers {
    
    // ======================================================
    // INTERNAL: ENSURE TABLES EXIST
    // ======================================================
    private fun ensureTables() {
        getConnection().use { conn ->
            val stmt = conn.createStatement()
            
            // Ledger Groups
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
            
            // Ledgers
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
        }
    }
    
    // ======================================================
    // INTERNAL HELPERS
    // ======================================================
    private fun fetchOne(query: String, vararg params: Any?): Map<String, Any?>? {
        ensureTables()
        getConnection().use { conn ->
            conn.prepareStatement(query).use { ps ->
                params.forEachIndexed { index, param ->
                    ps.setObject(index + 1, param)
                }
                val rs = ps.executeQuery()
                if (rs.next()) {
                    val map = mutableMapOf<String, Any?>()
                    val metaData = rs.metaData
                    for (i in 1..metaData.columnCount) {
                        map[metaData.getColumnName(i)] = rs.getObject(i)
                    }
                    return map
                }
            }
        }
        return null
    }
    
    private fun execute(query: String, vararg params: Any?): Int {
        ensureTables()
        getConnection().use { conn ->
            conn.prepareStatement(query).use { ps ->
                params.forEachIndexed { index, param ->
                    ps.setObject(index + 1, param)
                }
                ps.executeUpdate()
                val rs = ps.generatedKeys
                if (rs.next()) {
                    return rs.getInt(1)
                }
            }
        }
        return 0
    }
    
    // ======================================================
    // LEDGER GROUP
    // ======================================================
    fun getLedgerGroupId(name: String): Int {
        ensureTables()
        val row = fetchOne("SELECT id FROM ledger_groups WHERE name=?", name)
        if (row == null) {
            throw RuntimeException("Ledger group missing: $name")
        }
        val id = row["id"]
        return when (id) {
            is Int -> id
            is Long -> id.toInt()
            else -> throw RuntimeException("Invalid ledger group ID type: ${id?.javaClass?.name}")
        }
    }
    
    // ======================================================
    // LEDGER LOOKUP
    // ======================================================
    fun getLedgerByName(name: String): Map<String, Any?>? {
        ensureTables()
        return fetchOne("SELECT * FROM ledgers WHERE name=?", name)
    }
    
    fun getLedgerId(name: String): Int {
        val ledger = getLedgerByName(name)
            ?: throw RuntimeException("Ledger not found: $name")
        val id = ledger["id"]
        return when (id) {
            is Int -> id
            is Long -> id.toInt()
            else -> throw RuntimeException("Invalid ledger ID type: ${id?.javaClass?.name}")
        }
    }
    
    // ======================================================
    // LEDGER CREATION
    // ======================================================
    fun createLedger(
        name: String,
        groupName: String,
        openingBalance: Double = 0.0,
        openingType: String? = null,
        gstin: String? = null,
        isSystem: Int = 0
    ): Int {
        if (name.isBlank()) {
            throw IllegalArgumentException("Ledger name required")
        }
        
        // Return existing if found
        val existing = getLedgerByName(name)
        if (existing != null) {
            val id = existing["id"]
            return when (id) {
                is Int -> id
                is Long -> id.toInt()
                else -> 0
            }
        }
        
        if (openingBalance != 0.0 && openingType !in listOf("DR", "CR")) {
            throw IllegalArgumentException("Opening type must be DR or CR")
        }
        
        val gid = getLedgerGroupId(groupName)
        
        return execute(
            """
            INSERT INTO ledgers
            (name, group_id, opening_balance, opening_type, gstin, is_system)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            name, gid, openingBalance, openingType, gstin, isSystem
        )
    }
    
    // ======================================================
    // SYSTEM LEDGERS
    // ======================================================
    fun ensureSystemLedgers() {
        ensureTables()
        
        // Core accounts
        createLedger(name = "SALES", groupName = "Income", isSystem = 1)
        createLedger(name = "PURCHASES", groupName = "Expenses", isSystem = 1)
        
        // GST Output
        createLedger(name = "CGST OUTPUT", groupName = "Duties & Taxes", isSystem = 1)
        createLedger(name = "SGST OUTPUT", groupName = "Duties & Taxes", isSystem = 1)
        createLedger(name = "IGST OUTPUT", groupName = "Duties & Taxes", isSystem = 1)
        
        // GST Input
        createLedger(name = "CGST INPUT", groupName = "Duties & Taxes", isSystem = 1)
        createLedger(name = "SGST INPUT", groupName = "Duties & Taxes", isSystem = 1)
        createLedger(name = "IGST INPUT", groupName = "Duties & Taxes", isSystem = 1)
        
        // Rounding
        createLedger(name = "ROUND OFF", groupName = "Income", isSystem = 1)
        
        // Common Expense Ledgers (for shipping/logistics business)
        createLedger(name = "Freight Charges", groupName = "Expenses", isSystem = 0)
        createLedger(name = "Liner Agency", groupName = "Expenses", isSystem = 0)
        createLedger(name = "Customs Duty", groupName = "Expenses", isSystem = 0)
        createLedger(name = "Documentation Charges", groupName = "Expenses", isSystem = 0)
        createLedger(name = "Port Charges", groupName = "Expenses", isSystem = 0)
        createLedger(name = "Handling Charges", groupName = "Expenses", isSystem = 0)
        createLedger(name = "Transportation", groupName = "Expenses", isSystem = 0)
        createLedger(name = "Miscellaneous Expenses", groupName = "Expenses", isSystem = 0)
        
        // Custom accounts for testing
        createLedger(name = "Depreciation Account", groupName = "Expenses", isSystem = 0)
        createLedger(name = "Computer Equipment", groupName = "Assets", isSystem = 0)
    }

    
    // ======================================================
    // PARTY LEDGER
    // ======================================================
    fun getOrCreatePartyLedger(partyName: String, gstin: String? = null, groupName: String = "Assets"): Int {
        if (partyName.isBlank()) {
            throw IllegalArgumentException("Party name required")
        }
        
        return createLedger(
            name = partyName.trim(),
            groupName = groupName,
            gstin = gstin,
            isSystem = 0
        )
    }
    
    // ======================================================
    // LEDGER LISTING
    // ======================================================
    fun listLedgers(text: String? = null): List<Map<String, Any?>> {
        ensureTables()
        getConnection().use { conn ->
            val query = if (text != null) {
                "SELECT * FROM ledgers WHERE name LIKE ? ORDER BY name ASC"
            } else {
                "SELECT * FROM ledgers ORDER BY name ASC"
            }
            
            conn.prepareStatement(query).use { ps ->
                if (text != null) {
                    ps.setString(1, "%$text%")
                }
                val rs = ps.executeQuery()
                val list = mutableListOf<Map<String, Any?>>()
                while (rs.next()) {
                    val map = mutableMapOf<String, Any?>()
                    val metaData = rs.metaData
                    for (i in 1..metaData.columnCount) {
                        map[metaData.getColumnName(i)] = rs.getObject(i)
                    }
                    list.add(map)
                }
                return list
            }
        }
    }
}

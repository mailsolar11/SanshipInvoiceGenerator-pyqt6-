package com.sanship.data

import java.sql.Statement

object AccountingRepository {

    // --- INIT ---
    fun ensureSystemLedgers() {
        com.sanship.accounting.AccountingDb.getConnection().use { updateConn ->
            // 1. Groups
            val groups = listOf(
                "ASSET", "LIABILITY", "INCOME", "EXPENSE", "DUTIES & TAXES"
            )
            groups.forEach { name ->
                val nature = when(name) {
                    "ASSET" -> "ASSET"
                    "LIABILITY", "DUTIES & TAXES" -> "LIABILITY"
                    "INCOME" -> "INCOME"
                    "EXPENSE" -> "EXPENSE"
                    else -> "ASSET"
                }
                val check = "SELECT id FROM ledger_groups WHERE name = ?"
                updateConn.prepareStatement(check).use { ps ->
                    ps.setString(1, name)
                    val rs = ps.executeQuery()
                    if (!rs.next()) {
                        val insert = "INSERT INTO ledger_groups (name, nature) VALUES (?, ?)"
                        updateConn.prepareStatement(insert).use { insertPs ->
                            insertPs.setString(1, name)
                            insertPs.setString(2, nature)
                            insertPs.executeUpdate()
                        }
                    }
                }
            }

            // 2. System Ledgers
            createSystemLedger(updateConn, "SALES", "INCOME")
            createSystemLedger(updateConn, "CGST OUTPUT", "DUTIES & TAXES")
            createSystemLedger(updateConn, "SGST OUTPUT", "DUTIES & TAXES")
            createSystemLedger(updateConn, "IGST OUTPUT", "DUTIES & TAXES")
            createSystemLedger(updateConn, "ROUND OFF", "INCOME")
        }
    }

    private fun createSystemLedger(conn: java.sql.Connection, name: String, groupName: String) {
        // Get Group ID
        val gid = getGroupId(conn, groupName)
        
        // Check Exists
        val check = "SELECT id FROM ledgers WHERE name = ?"
        conn.prepareStatement(check).use { ps ->
            ps.setString(1, name)
            val rs = ps.executeQuery()
            
            if (!rs.next()) {
                val insert = """
                    INSERT INTO ledgers (name, group_id, is_system, opening_balance, opening_type)
                    VALUES (?, ?, 1, 0, 'CR')
                """
                conn.prepareStatement(insert).use { insertPs ->
                    insertPs.setString(1, name)
                    insertPs.setInt(2, gid)
                    insertPs.executeUpdate()
                }
            }
        }
    }

    private fun getGroupId(conn: java.sql.Connection, name: String): Int {
        val q = "SELECT id FROM ledger_groups WHERE name = ?"
        conn.prepareStatement(q).use { ps ->
            ps.setString(1, name)
            val rs = ps.executeQuery()
            if (rs.next()) return rs.getInt("id")
        }
        throw RuntimeException("Group not found: $name")
    }

    private fun getLedgerId(conn: java.sql.Connection, name: String): Int {
        val q = "SELECT id FROM ledgers WHERE name = ?"
        conn.prepareStatement(q).use { ps ->
            ps.setString(1, name)
            val rs = ps.executeQuery()
            if (rs.next()) return rs.getInt("id")
        }
        return -1
    }

    // --- PARTY LEDGER ---
    // Helper that opens its own connection if needed, but here we expect calls from safe places usually.
    // However, for ViewModel calls, we can open a new connection.
    fun getOrCreatePartyLedger(partyName: String, gstin: String? = ""): Int {
        if (partyName.isBlank()) return -1
        
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            val existingId = getLedgerId(conn, partyName)
            if (existingId != -1) return existingId

            val assetGroupId = getGroupId(conn, "Assets") // Sundry Debtors
            val insert = """
                INSERT INTO ledgers (name, group_id, is_system, gstin, opening_balance)
                VALUES (?, ?, 0, ?, 0)
            """
            conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS).use { ps ->
                ps.setString(1, partyName)
                ps.setInt(2, assetGroupId)
                ps.setString(3, gstin ?: "")
                ps.executeUpdate()
                val rs = ps.generatedKeys
                if (rs.next()) return rs.getInt(1)
            }
            return getLedgerId(conn, partyName)
        }
        return -1
    }

    // --- POSTING EXPENSE VOUCHER ---
fun saveExpenseVoucher(
    voucherNo: String,
    date: String,
    expenseLedgerId: Int,
    partyName: String, // Can be Cash or Bank or Vendor
    amount: Double,
    narration: String,
    jobId: Int
) {
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
        conn.autoCommit = false
        try {
            // 1. Get the expense ledger name from accounting database
            var expenseLedgerName = ""
            com.sanship.accounting.AccountingDb.getConnection().use { accConn ->
                val sql = "SELECT name FROM ledgers WHERE id = ?"
                accConn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, expenseLedgerId)
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        expenseLedgerName = rs.getString("name")
                    }
                }
            }
            
            if (expenseLedgerName.isBlank()) {
                throw IllegalArgumentException("Expense ledger not found")
            }
            
            // 2. Get or create expense ledger in business database with EXPENSE group
            var businessExpenseLedgerId = getLedgerId(conn, expenseLedgerName)
            if (businessExpenseLedgerId == -1) {
                // Create EXPENSE group if it doesn't exist
                var expenseGroupId = -1
                val groupCheckSql = "SELECT id FROM ledger_groups WHERE nature = 'EXPENSE'"
                conn.prepareStatement(groupCheckSql).use { ps ->
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        expenseGroupId = rs.getInt("id")
                    } else {
                        // Create EXPENSE group
                        val groupInsertSql = "INSERT INTO ledger_groups (name, nature) VALUES ('Expenses', 'EXPENSE')"
                        conn.prepareStatement(groupInsertSql, Statement.RETURN_GENERATED_KEYS).use { insertPs ->
                            insertPs.executeUpdate()
                            val grs = insertPs.generatedKeys
                            if (grs.next()) expenseGroupId = grs.getInt(1)
                        }
                    }
                }
                
                // Create expense ledger
                val ledgerInsertSql = "INSERT INTO ledgers (name, group_id, is_system, opening_balance) VALUES (?, ?, 0, 0)"
                conn.prepareStatement(ledgerInsertSql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setString(1, expenseLedgerName)
                    ps.setInt(2, expenseGroupId)
                    ps.executeUpdate()
                    val rs = ps.generatedKeys
                    if (rs.next()) businessExpenseLedgerId = rs.getInt(1)
                }
            }
            
            // 3. Resolve Party (Credit Ledger)
            var creditLedgerId = getLedgerId(conn, partyName)
            if (creditLedgerId == -1) {
                 // Create as LIABILITY
                 var liabilityGroupId = -1
                 val groupCheckSql = "SELECT id FROM ledger_groups WHERE nature = 'LIABILITY'"
                 conn.prepareStatement(groupCheckSql).use { ps ->
                     val rs = ps.executeQuery()
                     if (rs.next()) {
                         liabilityGroupId = rs.getInt("id")
                     } else {
                         // Create LIABILITY group
                         val groupInsertSql = "INSERT INTO ledger_groups (name, nature) VALUES ('Liabilities', 'LIABILITY')"
                         conn.prepareStatement(groupInsertSql, Statement.RETURN_GENERATED_KEYS).use { insertPs ->
                             insertPs.executeUpdate()
                             val grs = insertPs.generatedKeys
                             if (grs.next()) liabilityGroupId = grs.getInt(1)
                         }
                     }
                 }
                 
                 val insert = "INSERT INTO ledgers (name, group_id, is_system, opening_balance) VALUES (?, ?, 0, 0)"
                 conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS).use { p ->
                    p.setString(1, partyName)
                    p.setInt(2, liabilityGroupId)
                    p.executeUpdate()
                    val r = p.generatedKeys
                    if (r.next()) creditLedgerId = r.getInt(1)
                 }
            }

            // 4. Create Voucher
            val insertVoucher = """
                INSERT INTO vouchers (voucher_no, voucher_type_id, voucher_type, voucher_date, narration, job_id)
                VALUES (?, COALESCE((SELECT id FROM voucher_types WHERE name = 'EXPENSE'), 1), 'EXPENSE', ?, ?, ?)
            """
            var voucherId = -1
            conn.prepareStatement(insertVoucher, Statement.RETURN_GENERATED_KEYS).use { ps ->
                ps.setString(1, voucherNo)
                ps.setString(2, date)
                ps.setString(3, narration)
                ps.setInt(4, jobId)
                ps.executeUpdate()
                val rs = ps.generatedKeys
                if (rs.next()) voucherId = rs.getInt(1)
            }

            if (voucherId != -1) {
                // 5. Post Entries
                // ENTRY 1: EXPENSE (DEBIT)
                insertEntry(conn, voucherId, businessExpenseLedgerId, dr = amount, cr = 0.0)

                // ENTRY 2: PARTY/CASH (CREDIT)
                insertEntry(conn, voucherId, creditLedgerId, dr = 0.0, cr = amount)
            }

            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            e.printStackTrace()
            throw e
        }
    }
}

    // --- RECEIPT ENTRY ---
    fun saveReceiptVoucher(
        voucherNo: String,
        date: String,
        mode: String, // Cash or Bank
        receivedFromLedgerId: Int, // Customer (Credit)
        amount: Double,
        narration: String,
        jobId: Int // Optional Link
    ) {
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            conn.autoCommit = false
            try {
                // 1. Resolve Deposit Ledger (Debit)
                // "Cash" or "Bank" -> Asset
                // Simple logic: check if exists, else create under CASH-IN-HAND or BANK ACCOUNTS
                // For now, simpler: Use getOrCreate and assume ASSET default if not exists
                var debitLedgerId = getLedgerId(conn, mode)
                if (debitLedgerId == -1) {
                    val gid = getGroupId(conn, "Assets") 
                    val insert = "INSERT INTO ledgers (name, group_id, is_system, opening_balance) VALUES (?, ?, 0, 0)"
                    conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS).use { p ->
                        p.setString(1, mode)
                        p.setInt(2, gid)
                        p.executeUpdate()
                        val r = p.generatedKeys
                        if (r.next()) debitLedgerId = r.getInt(1)
                    }
                }

                // 2. Create Voucher
                val insertVoucher = """
                    INSERT INTO vouchers (voucher_no, voucher_type_id, voucher_type, voucher_date, narration, job_id)
                    VALUES (?, COALESCE((SELECT id FROM voucher_types WHERE name = 'RECEIPT'), 1), 'RECEIPT', ?, ?, ?)
                """
                var voucherId = -1
                conn.prepareStatement(insertVoucher, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setString(1, voucherNo)
                    ps.setString(2, date)
                    ps.setString(3, narration)
                    if (jobId > 0) ps.setInt(4, jobId) else ps.setNull(4, java.sql.Types.INTEGER)
                    ps.executeUpdate()
                    val rs = ps.generatedKeys
                    if (rs.next()) voucherId = rs.getInt(1)
                }

                if (voucherId != -1) {
                    // 3. Post Entries
                    // ENTRY 1: BANK/CASH (DEBIT) - Asset Increases
                    insertEntry(conn, voucherId, debitLedgerId, dr = amount, cr = 0.0)

                    // ENTRY 2: CUSTOMER (CREDIT) - Asset (Receivable) Decreases
                    insertEntry(conn, voucherId, receivedFromLedgerId, dr = 0.0, cr = amount)
                }

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                e.printStackTrace()
            }
        }
    }

    // --- REPORTING ---
    // Job Profitability
    data class JobProfitabilityItem(
        val jobNo: String,
        val shipper: String,
        val income: Double,
        val expense: Double,
        val profit: Double
    )

    fun getJobProfitability(): List<JobProfitabilityItem> {
        val list = mutableListOf<JobProfitabilityItem>()
        
        DatabaseManager.connect()?.use { conn ->
            // Query business database vouchers which have job_id
            // Join with ledgers to get ledger group nature (INCOME vs EXPENSE)
            val sql = """
                SELECT 
                    j.job_no, 
                    j.shipper,
                    SUM(CASE WHEN lg.nature = 'INCOME' THEN le.cr_amount - le.dr_amount ELSE 0 END) as income,
                    SUM(CASE WHEN lg.nature = 'EXPENSE' THEN le.dr_amount - le.cr_amount ELSE 0 END) as expense
                FROM jobs j
                LEFT JOIN vouchers v ON v.job_id = j.id
                LEFT JOIN ledger_entries le ON le.voucher_id = v.id
                LEFT JOIN ledgers l ON le.ledger_id = l.id
                LEFT JOIN ledger_groups lg ON l.group_id = lg.id
                GROUP BY j.id
                ORDER BY j.created_at DESC
            """
            
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                while (rs.next()) {
                    val inc = rs.getDouble("income")
                    val exp = rs.getDouble("expense")
                    list.add(JobProfitabilityItem(
                        jobNo = rs.getString("job_no") ?: "Unknown",
                        shipper = rs.getString("shipper") ?: "",
                        income = inc,
                        expense = exp,
                        profit = inc - exp
                    ))
                }
            }
        }
        
        return list
    }


    // --- POSTING ENGINE ---
    fun postSalesVoucher(
        voucherNo: String,
        date: String,
        partyName: String,
        gstin: String?,
        taxable: Double,
        cgst: Double,
        sgst: Double,
        igst: Double,
        narration: String,
        voucherType: String = "SALES"
    ) {
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            conn.autoCommit = false
            try {
                // 1. Resolve IDs
                var partyId = getLedgerId(conn, partyName)
                if (partyId == -1) {
                    val gid = getGroupId(conn, "Assets")
                    val insert = "INSERT INTO ledgers (name, group_id, is_system, gstin) VALUES (?, ?, 0, ?)"
                    conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS).use { p ->
                        p.setString(1, partyName)
                        p.setInt(2, gid)
                        p.setString(3, gstin ?: "")
                        p.executeUpdate()
                        val r = p.generatedKeys
                        if (r.next()) partyId = r.getInt(1)
                    }
                }

                val salesId = getLedgerId(conn, "SALES")
                val cgstId = getLedgerId(conn, "CGST OUTPUT")
                val sgstId = getLedgerId(conn, "SGST OUTPUT")
                val igstId = getLedgerId(conn, "IGST OUTPUT")
                
                val total = taxable + cgst + sgst + igst

                // 2. Create Voucher
                val insertVoucher = """
                    INSERT INTO vouchers (voucher_no, voucher_type_id, voucher_type, voucher_date, narration)
                    VALUES (?, COALESCE((SELECT id FROM voucher_types WHERE name = ?), 1), ?, ?, ?)
                """
                var voucherId = -1
                conn.prepareStatement(insertVoucher, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setString(1, voucherNo)
                    ps.setString(2, voucherType)
                    ps.setString(3, voucherType)
                    ps.setString(4, date)
                    ps.setString(5, narration)
                    ps.executeUpdate()
                    val rs = ps.generatedKeys
                    if (rs.next()) voucherId = rs.getInt(1)
                }

                if (voucherId != -1) {
                    // 3. Post Entries
                    // ENTRY 1: PARTY (DEBIT)
                    insertEntry(conn, voucherId, partyId, dr = total, cr = 0.0)

                    // ENTRY 2: SALES (CREDIT)
                    if (taxable > 0) insertEntry(conn, voucherId, salesId, dr = 0.0, cr = taxable)

                    // ENTRY 3: DUTIES (CREDIT)
                    if (cgst > 0) insertEntry(conn, voucherId, cgstId, dr = 0.0, cr = cgst)
                    if (sgst > 0) insertEntry(conn, voucherId, sgstId, dr = 0.0, cr = sgst)
                    if (igst > 0) insertEntry(conn, voucherId, igstId, dr = 0.0, cr = igst)
                }

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                e.printStackTrace()
            }
        }
    }

    private fun insertEntry(conn: java.sql.Connection, voucherId: Int, ledgerId: Int, dr: Double, cr: Double) {
        val q = "INSERT INTO ledger_entries (voucher_id, ledger_id, dr_amount, cr_amount) VALUES (?, ?, ?, ?)"
        conn.prepareStatement(q).use { ps ->
            ps.setInt(1, voucherId)
            ps.setInt(2, ledgerId)
            ps.setDouble(3, dr)
            ps.setDouble(4, cr)
            ps.executeUpdate()
        }
    }

    // --- REPORTING ---
    data class LedgerReportItem(
        val date: String,
        val voucherNo: String,
        val narration: String,
        val dr: Double,
        val cr: Double
    )

    fun getLedgerEntries(ledgerId: Int): List<LedgerReportItem> {
        val list = mutableListOf<LedgerReportItem>()
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            val q = """
                SELECT v.voucher_date, v.voucher_no, v.narration, le.dr_amount, le.cr_amount
                FROM ledger_entries le
                JOIN vouchers v ON le.voucher_id = v.id
                WHERE le.ledger_id = ?
                ORDER BY v.voucher_date ASC, v.id ASC
            """
            conn.prepareStatement(q).use { ps ->
                ps.setInt(1, ledgerId)
                val rs = ps.executeQuery()
                while(rs.next()) {
                    list.add(LedgerReportItem(
                        date = rs.getString("voucher_date"),
                        voucherNo = rs.getString("voucher_no"),
                        narration = rs.getString("narration"),
                        dr = rs.getDouble("dr_amount"),
                        cr = rs.getDouble("cr_amount")
                    ))
                }
            }
        }
        return list
    }

    fun getAllLedgers(): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            val q = "SELECT id, name FROM ledgers ORDER BY name"
            val rs = conn.prepareStatement(q).executeQuery()
            while(rs.next()) {
                map[rs.getInt("id")] = rs.getString("name")
            }
        }
        return map
    }

    fun getCashAndBankLedgers(): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            // Return all Asset ledgers from accounting DB
            val q = """
                SELECT l.id, l.name 
                FROM ledgers l
                JOIN ledger_groups lg ON l.group_id = lg.id
                WHERE lg.nature = 'ASSET' 
                  AND (l.name LIKE '%Cash%' OR l.name LIKE '%Bank%' OR l.name LIKE '%HDFC%' OR l.name LIKE '%ICICI%')
                ORDER BY l.name
            """.trimIndent()
            val rs = conn.prepareStatement(q).executeQuery()
            while(rs.next()) {
                map[rs.getInt("id")] = rs.getString("name")
            }
        }
        return map
    }

    data class ChartOfAccountItem(
        val groupName: String,
        val ledgerName: String,
        val balance: Double
    )

    fun getChartOfAccounts(): List<ChartOfAccountItem> {
        val list = mutableListOf<ChartOfAccountItem>()
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            val q = """
                SELECT 
                    lg.name as group_name, 
                    l.name as ledger_name, 
                    COALESCE(SUM(le.dr_amount) - SUM(le.cr_amount), 0) as balance 
                FROM ledgers l 
                JOIN ledger_groups lg ON l.group_id = lg.id 
                LEFT JOIN ledger_entries le ON l.id = le.ledger_id 
                GROUP BY l.id 
                ORDER BY lg.name, l.name
            """.trimIndent()
            val rs = conn.createStatement().executeQuery(q)
            while(rs.next()) {
                list.add(ChartOfAccountItem(
                    groupName = rs.getString("group_name"),
                    ledgerName = rs.getString("ledger_name"),
                    balance = rs.getDouble("balance")
                ))
            }
        }
        return list
    }

    // --- SALES REGISTER ---
    data class SalesRegisterItem(
        val date: String,
        val invoiceNo: String,
        val partyName: String,
        val totalAmount: Double
    )

    fun getSalesRegister(): List<SalesRegisterItem> {
        val list = mutableListOf<SalesRegisterItem>()
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            val q = """
                SELECT v.voucher_date, v.voucher_no, v.narration, SUM(le.cr_amount) as total
                FROM vouchers v
                JOIN ledger_entries le ON v.id = le.voucher_id
                WHERE v.voucher_type = 'SALES'
                GROUP BY v.id
                ORDER BY v.voucher_date DESC
            """
            val rs = conn.prepareStatement(q).executeQuery()
            while(rs.next()) {
                list.add(SalesRegisterItem(
                    date = rs.getString("voucher_date"),
                    invoiceNo = rs.getString("voucher_no"),
                    partyName = rs.getString("narration"), 
                    totalAmount = rs.getDouble("total")
                ))
            }
        }
        return list
    }
    // --- FINANCIAL REPORTS ---
    
    // 1. Trial Balance Item
    data class TrialBalanceItem(
        val ledgerId: Int,
        val ledgerName: String,
        val groupName: String,
        val nature: String, // ASSET, LIABILITY, INCOME, EXPENSE
        val debitTotal: Double,
        val creditTotal: Double,
        val netBalance: Double // +ve = Debit Bal, -ve = Credit Bal
    )

    fun getTrialBalanceReport(): List<TrialBalanceItem> {
        val list = mutableListOf<TrialBalanceItem>()
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            // Query to fetch all ledgers and their balances
            // We left join entries to get sums
            val sql = """
                SELECT 
                    l.id, l.name, lg.name as group_name, lg.nature,
                    IFNULL(SUM(le.dr_amount), 0) as dr_total,
                    IFNULL(SUM(le.cr_amount), 0) as cr_total
                FROM ledgers l
                JOIN ledger_groups lg ON l.group_id = lg.id
                LEFT JOIN ledger_entries le ON l.id = le.ledger_id
                GROUP BY l.id
                ORDER BY lg.nature, lg.name, l.name
            """
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                while(rs.next()) {
                    val dr = rs.getDouble("dr_total")
                    val cr = rs.getDouble("cr_total")
                    // If Dr and Cr are 0, we might skip, but let's include for completeness if needed?
                    // Usually TB only shows active ledgers.
                    if (dr != 0.0 || cr != 0.0) {
                        list.add(TrialBalanceItem(
                            ledgerId = rs.getInt("id"),
                            ledgerName = rs.getString("name"),
                            groupName = rs.getString("group_name"),
                            nature = rs.getString("nature"),
                            debitTotal = dr,
                            creditTotal = cr,
                            netBalance = dr - cr
                        ))
                    }
                }
            }
        }
        return list
    }

    // --- PAYMENT VOUCHER ---
    fun savePaymentVoucher(
        voucherNo: String,
        date: String,
        mode: String,
        payToLedgerId: Int,
        amount: Double,
        narration: String,
        jobId: Int
    ) {
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            conn.autoCommit = false
            try {
                // 1. Resolve Credit Ledger (Cash/Bank - Asset decreases)
                var creditLedgerId = getLedgerId(conn, mode)
                if (creditLedgerId == -1) {
                    val gid = getGroupId(conn, "Assets")
                    val insert = "INSERT INTO ledgers (name, group_id, is_system, opening_balance) VALUES (?, ?, 0, 0)"
                    conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS).use { p ->
                        p.setString(1, mode)
                        p.setInt(2, gid)
                        p.executeUpdate()
                        val r = p.generatedKeys
                        if (r.next()) creditLedgerId = r.getInt(1)
                    }
                }

                // 2. Create Voucher
                val insertVoucher = """
                    INSERT INTO vouchers (voucher_no, voucher_type_id, voucher_type, voucher_date, narration, job_id)
                    VALUES (?, COALESCE((SELECT id FROM voucher_types WHERE name = 'PAYMENT'), 1), 'PAYMENT', ?, ?, ?)
                """
                var voucherId = -1
                conn.prepareStatement(insertVoucher, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setString(1, voucherNo)
                    ps.setString(2, date)
                    ps.setString(3, narration)
                    if (jobId > 0) ps.setInt(4, jobId) else ps.setNull(4, java.sql.Types.INTEGER)
                    ps.executeUpdate()
                    val rs = ps.generatedKeys
                    if (rs.next()) voucherId = rs.getInt(1)
                }

                if (voucherId != -1) {
                    // ENTRY 1: PARTY/VENDOR (DEBIT) - Liability Decreases
                    insertEntry(conn, voucherId, payToLedgerId, dr = amount, cr = 0.0)
                    // ENTRY 2: CASH/BANK (CREDIT) - Asset Decreases
                    insertEntry(conn, voucherId, creditLedgerId, dr = 0.0, cr = amount)
                }

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    // --- JOURNAL VOUCHER ---
    fun saveJournalVoucher(
        voucherNo: String,
        date: String,
        narration: String,
        entries: List<Triple<Int, Double, Double>> // ledgerId, dr, cr
    ) {
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            conn.autoCommit = false
            try {
                // Validate DR = CR
                val totalDr = entries.sumOf { it.second }
                val totalCr = entries.sumOf { it.third }
                if (kotlin.math.abs(totalDr - totalCr) > 0.01) {
                    throw RuntimeException("Journal Entry DR ($totalDr) ≠ CR ($totalCr)")
                }

                val insertVoucher = """
                    INSERT INTO vouchers (voucher_no, voucher_type_id, voucher_type, voucher_date, narration)
                    VALUES (?, COALESCE((SELECT id FROM voucher_types WHERE name = 'JOURNAL'), 1), 'JOURNAL', ?, ?)
                """
                var voucherId = -1
                conn.prepareStatement(insertVoucher, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setString(1, voucherNo)
                    ps.setString(2, date)
                    ps.setString(3, narration)
                    ps.executeUpdate()
                    val rs = ps.generatedKeys
                    if (rs.next()) voucherId = rs.getInt(1)
                }

                if (voucherId != -1) {
                    for ((ledgerId, dr, cr) in entries) {
                        insertEntry(conn, voucherId, ledgerId, dr = dr, cr = cr)
                    }
                }

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    // --- OUTSTANDING REPORTS ---
    fun getOutstandingReceivables(): List<com.sanship.ui.reports.OutstandingEntry> {
        val result = mutableListOf<com.sanship.ui.reports.OutstandingEntry>()
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            // Sundry Debtors group - parties with net DR balance
            val sql = """
                SELECT l.name, 
                       SUM(le.dr_amount) - SUM(le.cr_amount) as balance,
                       v.voucher_date
                FROM ledger_entries le
                JOIN ledgers l ON le.ledger_id = l.id
                JOIN ledger_groups lg ON l.group_id = lg.id
                JOIN vouchers v ON le.voucher_id = v.id
                WHERE (lg.nature = 'ASSET' OR lg.name = 'Assets')
                  AND l.name NOT LIKE '%Cash%' 
                  AND l.name NOT LIKE '%Bank%'
                  AND l.name NOT LIKE '%HDFC%'
                  AND l.name NOT LIKE '%ICICI%'
                  AND l.is_system = 0
                GROUP BY l.name
                HAVING balance > 0.01
                ORDER BY balance DESC
            """
            conn.prepareStatement(sql).use { ps ->
                val rs = ps.executeQuery()
                while (rs.next()) {
                    val name = rs.getString("name")
                    val balance = rs.getDouble("balance")
                    // Simplified aging - total in 0-30 bucket for now
                    result.add(com.sanship.ui.reports.OutstandingEntry(
                        partyName = name,
                        totalAmount = balance,
                        bucket0to30 = balance
                    ))
                }
            }
        }
        return result
    }

    fun getOutstandingPayables(): List<com.sanship.ui.reports.OutstandingEntry> {
        val result = mutableListOf<com.sanship.ui.reports.OutstandingEntry>()
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            // Sundry Creditors group - parties with net CR balance
            val sql = """
                SELECT l.name, 
                       SUM(le.cr_amount) - SUM(le.dr_amount) as balance
                FROM ledger_entries le
                JOIN ledgers l ON le.ledger_id = l.id
                JOIN ledger_groups lg ON l.group_id = lg.id
                JOIN vouchers v ON le.voucher_id = v.id
                WHERE (lg.nature = 'LIABILITY' OR lg.name = 'Liabilities')
                  AND l.name NOT IN ('CGST OUTPUT', 'SGST OUTPUT', 'IGST OUTPUT')
                  AND l.is_system = 0
                GROUP BY l.name
                HAVING balance > 0.01
                ORDER BY balance DESC
            """
            conn.prepareStatement(sql).use { ps ->
                val rs = ps.executeQuery()
                while (rs.next()) {
                    val name = rs.getString("name")
                    val balance = rs.getDouble("balance")
                    result.add(com.sanship.ui.reports.OutstandingEntry(
                        partyName = name,
                        totalAmount = balance,
                        bucket0to30 = balance
                    ))
                }
            }
        }
        return result
    }

    // --- BANK RECONCILIATION ---
    data class BankReconItem(
        val entryId: Int,
        val date: String,
        val voucherNo: String,
        val narration: String,
        val dr: Double,
        val cr: Double,
        val bankDate: String?
    )

    fun getBankReconEntries(ledgerId: Int): List<BankReconItem> {
        val list = mutableListOf<BankReconItem>()
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            val q = """
                SELECT 
                    le.id as entry_id,
                    v.voucher_date as date,
                    v.voucher_no as voucher_no,
                    v.narration as narration,
                    le.dr_amount as dr,
                    le.cr_amount as cr,
                    le.bank_date as bank_date
                FROM ledger_entries le
                JOIN vouchers v ON le.voucher_id = v.id
                WHERE le.ledger_id = ?
                ORDER BY v.voucher_date ASC, le.id ASC
            """.trimIndent()
            conn.prepareStatement(q).use { ps ->
                ps.setInt(1, ledgerId)
                val rs = ps.executeQuery()
                while (rs.next()) {
                    list.add(BankReconItem(
                        entryId = rs.getInt("entry_id"),
                        date = rs.getString("date"),
                        voucherNo = rs.getString("voucher_no"),
                        narration = rs.getString("narration") ?: "",
                        dr = rs.getDouble("dr"),
                        cr = rs.getDouble("cr"),
                        bankDate = rs.getString("bank_date")
                    ))
                }
            }
        }
        return list
    }

    fun updateBankDate(entryId: Int, bankDate: String?) {
        com.sanship.accounting.AccountingDb.getConnection().use { conn ->
            val q = "UPDATE ledger_entries SET bank_date = ? WHERE id = ?"
            conn.prepareStatement(q).use { ps ->
                if (bankDate.isNullOrBlank()) {
                    ps.setNull(1, java.sql.Types.VARCHAR)
                } else {
                    ps.setString(1, bankDate)
                }
                ps.setInt(2, entryId)
                ps.executeUpdate()
            }
        }
    }
}

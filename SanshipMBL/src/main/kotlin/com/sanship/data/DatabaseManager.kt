package com.sanship.data

import java.sql.DriverManager
import java.sql.Connection
import java.sql.SQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseManager {
    private val DB_URL: String
        get() = "jdbc:sqlite:${com.sanship.utils.DocumentPaths.getAppDatabasePath()}"

    fun initDatabase() {
        try {
            Database.connect(DB_URL, driver = "org.sqlite.JDBC")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        createTables()
        
        // Run migrations for existing databases
        try {
            DatabaseMigration.migrateToV2()
            DatabaseMigration.migrateDatesToIso() // Ensures all old mixed dates are standard yyyy-MM-dd
        } catch (e: Exception) {
            println("Migration warning: ${e.message}")
        }
    }

    internal fun connect(): Connection? {
        return try {
            DriverManager.getConnection(DB_URL)
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        }
    }

    private fun createTables() {
        // Table definition logic
        val sqlHeader = """
            CREATE TABLE IF NOT EXISTS mbl_headers (
                mtdNumber TEXT PRIMARY KEY,
                refNumber TEXT,
                consignor TEXT,
                consignee TEXT,
                notifyAddress TEXT,
                preCarriage TEXT,
                placeReceipt TEXT,
                deliveryAgent TEXT,
                vessel TEXT,
                voyNumber TEXT,
                portLoading TEXT,
                portDischarge TEXT,
                placeDelivery TEXT,
                mode TEXT,
                route TEXT,
                mainCustomsSeal TEXT,
                mainAgentSeal TEXT,
                goodsDescription TEXT,
                marksNumbers TEXT, 
                freightAmount TEXT,
                freightPayableAt TEXT,
                originalMtds TEXT,
                placeDateIssue TEXT,
                otherParticulars TEXT
            );
        """

        val sqlCargo = """
            CREATE TABLE IF NOT EXISTS cargo_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                mtdNumber TEXT,
                containerNo TEXT,
                grossWeight TEXT,
                measurement TEXT,
                pkgCount TEXT,
                netWeight TEXT,
                agentSeal TEXT,
                customsSeal TEXT,
                sbNumber TEXT,
                sbDate TEXT,
                FOREIGN KEY(mtdNumber) REFERENCES mbl_headers(mtdNumber)
            );
        """

        transaction {
            SchemaUtils.create(ClientTable, VendorTable, UsersTable)
            
            // Seed default admin if missing
            val adminExists = UsersTable.select { UsersTable.username eq "admin" }.count() > 0
            if (!adminExists) {
                UsersTable.insert {
                    it[username] = "admin"
                    it[password] = "admin123" // In production, hash this
                    it[role] = "ADMIN"
                }
            }
            // Seed default user if missing
            val userExists = UsersTable.select { UsersTable.username eq "user" }.count() > 0
            if (!userExists) {
                UsersTable.insert {
                    it[username] = "user"
                    it[password] = "user123"
                    it[role] = "USER"
                }
            }
        }

        connect()?.use { conn ->
            val stmt = conn.createStatement()
            stmt.execute(sqlHeader)
            stmt.execute(sqlCargo)

            // Migration: Add marksNumbers if it doesn't exist
            try {
                stmt.execute("ALTER TABLE mbl_headers ADD COLUMN marksNumbers TEXT")
                println("Migration: Added marksNumbers column to mbl_headers")
            } catch (e: Exception) {
                // Ignore if exists
            }
            
            // Migration: Add new columns to client_master if they don't exist
            try {
                stmt.execute("ALTER TABLE client_master ADD COLUMN gstin TEXT")
            } catch (e: Exception) {
                // Column already exists
            }
            try {
                stmt.execute("ALTER TABLE client_master ADD COLUMN stateCode TEXT")
            } catch (e: Exception) {
                // Column already exists
            }
            try {
                stmt.execute("ALTER TABLE client_master ADD COLUMN email TEXT")
            } catch (e: Exception) {
                // Column already exists
            }
            // --- JOB MANAGEMENT ---
            val createJobsTable = """
                CREATE TABLE IF NOT EXISTS jobs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_no TEXT NOT NULL UNIQUE,
                    customer_id INTEGER,
                    shipper TEXT,
                    consignee TEXT,
                    pol TEXT,
                    pod TEXT,
                    vessel_flight TEXT,
                    etd TEXT,
                    eta TEXT,
                    mbl_no TEXT,
                    gross_weight TEXT,
                    net_weight TEXT,
                    volume_cbm TEXT,
                    packages TEXT,
                    exchange_rate REAL DEFAULT 1.0,
                    ref_no TEXT,
                    status TEXT DEFAULT 'OPEN',
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                );
            """.trimIndent()
            stmt.execute(createJobsTable)
            
            // Migration: Add new columns to jobs table if they don't exist
            val jobMigrations = listOf(
                "vessel_flight", "net_weight", "volume_cbm", "packages", "exchange_rate", "ref_no"
            )
            jobMigrations.forEach { col ->
                try {
                    stmt.execute("ALTER TABLE jobs ADD COLUMN $col TEXT")
                } catch (e: Exception) {
                    // Column already exists
                }
            }

            // --- ACCOUNTING ENGINE ---
            // 1. Ledger Groups
            val createGroups = """
                CREATE TABLE IF NOT EXISTS ledger_groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    nature TEXT CHECK ( nature IN ('ASSET', 'LIABILITY', 'INCOME', 'EXPENSE') ) NOT NULL,
                    parent_id INTEGER,
                    created_at TEXT
                );
            """.trimIndent()
            stmt.execute(createGroups)
            
            // Re-seed exact accounting legacy groups needed by Ledgers.kt
            try {
                stmt.execute("""
                    INSERT OR IGNORE INTO ledger_groups (id, name, nature) VALUES 
                    (1, 'ASSET', 'ASSET'),
                    (2, 'LIABILITY', 'LIABILITY'),
                    (3, 'INCOME', 'INCOME'),
                    (4, 'EXPENSE', 'EXPENSE'),
                    (5, 'Assets', 'ASSET'),
                    (6, 'Liabilities', 'LIABILITY'),
                    (7, 'Income', 'INCOME'),
                    (8, 'Expenses', 'EXPENSE'),
                    (9, 'Duties & Taxes', 'LIABILITY')
                """)
            } catch(e: Exception) {}

            // 2. Ledgers
            val createLedgers = """
                CREATE TABLE IF NOT EXISTS ledgers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    group_id INTEGER NOT NULL,
                    opening_balance REAL DEFAULT 0,
                    opening_type TEXT CHECK (opening_type IN ('DR','CR')),
                    gstin TEXT,
                    party_id INTEGER,
                    is_system INTEGER DEFAULT 0,
                    created_at TEXT,
                    FOREIGN KEY (group_id) REFERENCES ledger_groups(id)
                );
            """.trimIndent()
            stmt.execute(createLedgers)

            // 3. Vouchers
            val createVouchers = """
                CREATE TABLE IF NOT EXISTS vouchers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    voucher_no TEXT NOT NULL UNIQUE,
                    voucher_type_id INTEGER,
                    voucher_type TEXT NOT NULL,
                    voucher_date TEXT NOT NULL,
                    narration TEXT,
                    job_id INTEGER,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                );
            """.trimIndent()
            stmt.execute(createVouchers)

            // Migration: add voucher_type_id if missing (existing DBs)
            try { stmt.execute("ALTER TABLE vouchers ADD COLUMN voucher_type_id INTEGER") } catch (e: Exception) {}

            // 4. Ledger Entries
            val createEntries = """
                CREATE TABLE IF NOT EXISTS ledger_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    voucher_id INTEGER NOT NULL,
                    ledger_id INTEGER NOT NULL,
                    dr_amount REAL DEFAULT 0,
                    cr_amount REAL DEFAULT 0,
                    container_number TEXT,
                    bank_date TEXT,
                    created_at TEXT,
                    FOREIGN KEY (voucher_id) REFERENCES vouchers(id),
                    FOREIGN KEY (ledger_id) REFERENCES ledgers(id)
                );
            """.trimIndent()
            stmt.execute(createEntries)

            // Migrations: add columns for existing databases
            val accountingMigrations = listOf(
                "ledger_groups" to "created_at" to "TEXT",
                "ledgers" to "created_at" to "TEXT",
                "ledger_entries" to "created_at" to "TEXT",
                "ledger_entries" to "bank_date" to "TEXT"
            )
            accountingMigrations.forEach { (tableCol, type) ->
                try { stmt.execute("ALTER TABLE ${tableCol.first} ADD COLUMN ${tableCol.second} $type") } catch (e: Exception) {}
            }
            
            // --- CHARGE MASTER ---
            val createCharges = """
                CREATE TABLE IF NOT EXISTS charges_master (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    charge_name TEXT NOT NULL,
                    hsn_sac TEXT,
                    currency TEXT DEFAULT 'INR',
                    cgst_rate REAL DEFAULT 0,
                    sgst_rate REAL DEFAULT 0,
                    igst_rate REAL DEFAULT 0,
                    default_rate REAL DEFAULT 0,
                    description TEXT
                );
            """.trimIndent()
            stmt.execute(createCharges)
            
            // --- ENHANCED INVOICE TABLES (Updated to match legacy camelCase schema + new fields) ---
            val createInvoices = """
                CREATE TABLE IF NOT EXISTS invoices (
                     id INTEGER PRIMARY KEY AUTOINCREMENT,
                     invoiceNo TEXT NOT NULL UNIQUE,
                     date TEXT NOT NULL,
                     type TEXT DEFAULT 'INVOICE',
                     customerId INTEGER,
                     customerName TEXT, -- Legacy
                     billingAddress TEXT, -- Legacy
                     gstin TEXT,
                     placeOfSupply TEXT,
                     reverseCharge INTEGER,
                     jobId TEXT,
                     jobNo TEXT,
                     shipper TEXT,
                     consignee TEXT,
                     vessel TEXT,
                     pol TEXT,
                     pod TEXT,
                     etd TEXT,
                     eta TEXT,
                     mblNo TEXT,
                     hblNo TEXT,
                     containerNos TEXT,
                     shipperInvoiceNo TEXT,
                     shipperInvoiceDate TEXT,
                     category TEXT,
                     grossWeight TEXT,
                     netWeight TEXT,
                     netWeightUnit TEXT,
                     volumeCbm TEXT,
                     packages TEXT,
                     beNo TEXT,
                     beDate TEXT,
                     igmNo TEXT,
                     igmDate TEXT,
                     itemNo TEXT,
                     currency TEXT DEFAULT 'INR',
                     exchangeRate REAL DEFAULT 1.0,
                     refNo TEXT,
                     otherRefNo TEXT,
                     pan TEXT,
                     stateCode TEXT,
                     irn TEXT,
                     ackNo TEXT,
                     ackDate TEXT,
                     signedQr TEXT,
                     signedInvoice TEXT,
                     taxableAmount REAL DEFAULT 0,
                     cgstAmount REAL DEFAULT 0,
                     sgstAmount REAL DEFAULT 0,
                     igstAmount REAL DEFAULT 0,
                     grandTotal REAL DEFAULT 0,
                     narration TEXT,
                     createdAt TEXT DEFAULT CURRENT_TIMESTAMP
                 );
            """.trimIndent()
            stmt.execute(createInvoices)

            // --- PURCHASE INVOICE TABLES ---
            val createPurchases = """
                CREATE TABLE IF NOT EXISTS purchase_invoices (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    purchase_no TEXT NOT NULL UNIQUE,
                    date TEXT NOT NULL,
                    vendor_id INTEGER,
                    vendor_name TEXT,
                    vendor_gstin TEXT,
                    vendor_address TEXT,
                    place_of_supply TEXT,
                    reverse_charge INTEGER DEFAULT 0,
                    job_id INTEGER,
                    job_no TEXT,
                    taxable_amount REAL DEFAULT 0,
                    cgst_amount REAL DEFAULT 0,
                    sgst_amount REAL DEFAULT 0,
                    igst_amount REAL DEFAULT 0,
                    grand_total REAL DEFAULT 0,
                    currency TEXT DEFAULT 'INR',
                    exchange_rate REAL DEFAULT 1.0,
                    narration TEXT,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                );
            """.trimIndent()
            stmt.execute(createPurchases)

            val createPurchaseItems = """
                CREATE TABLE IF NOT EXISTS purchase_invoice_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    purchase_id INTEGER NOT NULL,
                    sr_no INTEGER,
                    description TEXT,
                    hsn_sac TEXT,
                    qty REAL DEFAULT 0,
                    rate REAL DEFAULT 0,
                    amount REAL DEFAULT 0,
                    taxable_amount REAL DEFAULT 0,
                    cgst_rate REAL DEFAULT 0,
                    cgst_amount REAL DEFAULT 0,
                    sgst_rate REAL DEFAULT 0,
                    sgst_amount REAL DEFAULT 0,
                    igst_rate REAL DEFAULT 0,
                    igst_amount REAL DEFAULT 0,
                    currency TEXT DEFAULT 'INR',
                    exchange_rate REAL DEFAULT 1.0,
                    total_amount REAL DEFAULT 0,
                    FOREIGN KEY (purchase_id) REFERENCES purchase_invoices(id)
                );
            """.trimIndent()
            stmt.execute(createPurchaseItems)

            // --- AUDIT LOG ---
            val createAudit = """
                CREATE TABLE IF NOT EXISTS transaction_audit_log (
                    id TEXT PRIMARY KEY,
                    entity_type TEXT NOT NULL,
                    status TEXT NOT NULL,
                    payload TEXT,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                );
            """.trimIndent()
            stmt.execute(createAudit)

            val createInvoiceItems = """
                CREATE TABLE IF NOT EXISTS invoice_items (
                     id INTEGER PRIMARY KEY AUTOINCREMENT,
                     invoice_id INTEGER NOT NULL,
                     sr_no INTEGER,
                     description TEXT NOT NULL,
                     hsn_sac TEXT,
                     currency TEXT DEFAULT 'INR',
                     rate REAL DEFAULT 0,
                     qty REAL DEFAULT 0,
                     amount REAL DEFAULT 0,
                     taxable_amount REAL DEFAULT 0,
                     cgst_rate REAL DEFAULT 0,
                     cgst_amt REAL DEFAULT 0,
                     sgst_rate REAL DEFAULT 0,
                     sgst_amt REAL DEFAULT 0,
                     igst_rate REAL DEFAULT 0,
                     igst_amt REAL DEFAULT 0,
                     total_amt REAL DEFAULT 0,
                     FOREIGN KEY(invoice_id) REFERENCES invoices(id) ON DELETE CASCADE
                );
            """.trimIndent()
            stmt.execute(createInvoiceItems)
            
            // --- MIGRATIONS FOR INVOICES (Phase 19 Fix) ---
            val invoiceCols = listOf(
                "type" to "TEXT DEFAULT 'INVOICE'",
                "jobNo" to "TEXT",
                "etd" to "TEXT",
                "eta" to "TEXT",
                "mblNo" to "TEXT",
                "grossWeight" to "TEXT",
                "netWeight" to "TEXT",
                "volumeCbm" to "TEXT",
                "packages" to "TEXT",
                "beNo" to "TEXT",
                "beDate" to "TEXT", // Added
                "igmNo" to "TEXT",
                "igmDate" to "TEXT", // Added
                "itemNo" to "TEXT",
                "exchangeRate" to "TEXT",
                "refNo" to "TEXT",
                "narration" to "TEXT",
                "place_of_supply" to "TEXT",
                "invoice_number" to "TEXT",
                "irn" to "TEXT",
                "ackNo" to "TEXT",
                "ackDate" to "TEXT",
                "signedQr" to "TEXT",
                "signedInvoice" to "TEXT",
                "netWeightUnit" to "TEXT",
                "otherRefNo" to "TEXT",
                "pan" to "TEXT",
                "stateCode" to "TEXT"
            )
            invoiceCols.forEach { (col, type) ->
                try {
                    stmt.execute("ALTER TABLE invoices ADD COLUMN $col $type")
                } catch (e: Exception) { /* Column exists */ }
            }

            // ADD MISSING COLUMNS TO AVOID CRASHES ON SAVE
            try { stmt.execute("ALTER TABLE vouchers ADD COLUMN job_id INTEGER") } catch(e: Exception) {}
            
            // CLEANUP & SYNC: Ensure all Vendors and Clients are physically present in the Ledgers table now that we unified to sanship.db
            try {
                // Sync Vendors to Liabilities
                stmt.execute("""
                    INSERT OR IGNORE INTO ledgers (name, group_id, opening_balance, is_system)
                    SELECT short_name, (SELECT id FROM ledger_groups WHERE name = 'Liabilities' LIMIT 1), 0, 0
                    FROM vendors WHERE short_name NOT IN (SELECT name FROM ledgers) AND short_name != ''
                """)
                stmt.execute("""
                    INSERT OR IGNORE INTO ledgers (name, group_id, opening_balance, is_system)
                    SELECT full_name, (SELECT id FROM ledger_groups WHERE name = 'Liabilities' LIMIT 1), 0, 0
                    FROM vendors WHERE full_name NOT IN (SELECT name FROM ledgers) AND full_name != ''
                """)
                
                // Sync Clients to Assets
                stmt.execute("""
                    INSERT OR IGNORE INTO ledgers (name, group_id, opening_balance, is_system)
                    SELECT short_name, (SELECT id FROM ledger_groups WHERE name = 'Assets' LIMIT 1), 0, 0
                    FROM clients WHERE short_name NOT IN (SELECT name FROM ledgers) AND short_name != ''
                """)
                stmt.execute("""
                    INSERT OR IGNORE INTO ledgers (name, group_id, opening_balance, is_system)
                    SELECT full_name, (SELECT id FROM ledger_groups WHERE name = 'Assets' LIMIT 1), 0, 0
                    FROM clients WHERE full_name NOT IN (SELECT name FROM ledgers) AND full_name != ''
                """)
                
                // Fix incorrectly categorized Cash/Bank ledgers that were assigned Liability nature via Expense vouchers
                stmt.execute("UPDATE ledgers SET group_id = COALESCE((SELECT id FROM ledger_groups WHERE nature = 'ASSET' AND name = 'ASSET' LIMIT 1), (SELECT id FROM ledger_groups WHERE nature = 'ASSET' LIMIT 1)) WHERE (name LIKE '%Cash%' OR name LIKE '%Bank%' OR name LIKE '%HDFC%' OR name LIKE '%ICICI%') AND group_id IN (SELECT id FROM ledger_groups WHERE nature = 'LIABILITY')") 
            } catch(e: Exception) {}

            // CLEANUP: Ensure exchangeRate and exchange_rate are NEVER empty string or NULL
            try {
                stmt.execute("UPDATE jobs SET exchange_rate = 1.0 WHERE exchange_rate IS NULL OR exchange_rate = ''")
                stmt.execute("UPDATE invoices SET exchangeRate = 1.0 WHERE exchangeRate IS NULL OR exchangeRate = ''")
                // Add currency column to invoices if missing
                try { stmt.execute("ALTER TABLE invoices ADD COLUMN currency TEXT DEFAULT 'INR'") } catch(e: Exception) {}
                stmt.execute("UPDATE invoices SET currency = 'INR' WHERE currency IS NULL OR currency = ''")
                
                try { stmt.execute("ALTER TABLE purchase_invoices ADD COLUMN currency TEXT DEFAULT 'INR'") } catch(e: Exception) {}
                try { stmt.execute("ALTER TABLE purchase_invoices ADD COLUMN exchange_rate REAL DEFAULT 1.0") } catch(e: Exception) {}
                try { stmt.execute("ALTER TABLE purchase_invoice_items ADD COLUMN currency TEXT DEFAULT 'INR'") } catch(e: Exception) {}
                try { stmt.execute("ALTER TABLE purchase_invoice_items ADD COLUMN exchange_rate REAL DEFAULT 1.0") } catch(e: Exception) {}
                
                // Migrations to fix cgst_amt -> cgst_amount
                try { stmt.execute("ALTER TABLE purchase_invoice_items ADD COLUMN cgst_amount REAL DEFAULT 0") } catch(e: Exception) {}
                try { stmt.execute("ALTER TABLE purchase_invoice_items ADD COLUMN sgst_amount REAL DEFAULT 0") } catch(e: Exception) {}
                try { stmt.execute("ALTER TABLE purchase_invoice_items ADD COLUMN igst_amount REAL DEFAULT 0") } catch(e: Exception) {}
                try { stmt.execute("ALTER TABLE purchase_invoice_items ADD COLUMN total_amount REAL DEFAULT 0") } catch(e: Exception) {}
                
                stmt.execute("UPDATE purchase_invoices SET exchange_rate = 1.0 WHERE exchange_rate IS NULL OR exchange_rate = ''")
                stmt.execute("UPDATE purchase_invoices SET currency = 'INR' WHERE currency IS NULL OR currency = ''")
                stmt.execute("UPDATE purchase_invoice_items SET exchange_rate = 1.0 WHERE exchange_rate IS NULL OR exchange_rate = ''")
                stmt.execute("UPDATE purchase_invoice_items SET currency = 'INR' WHERE currency IS NULL OR currency = ''")
                
                println("Migration: Harmonized currency and exchange_rate columns across all tables.")
            } catch (e: Exception) {
                println("Migration Error during currency cleanup: ${e.message}")
            }

            // --- PHASE 20 MIGRATIONS (Fix Schema Mismatch for Invoice Items) ---
            // Ensure snake_case columns exist in invoice_items (legacy/hybrid support)
            val itemSnakeCols = listOf(
                "invoice_id" to "INTEGER",
                "sr_no" to "INTEGER",
                "description" to "TEXT",
                "hsn_sac" to "TEXT",
                "currency" to "TEXT DEFAULT 'INR'",
                "rate" to "REAL DEFAULT 0",
                "qty" to "REAL DEFAULT 0",
                "amount" to "REAL DEFAULT 0",
                "taxable_amount" to "REAL DEFAULT 0",
                "cgst_amt" to "REAL DEFAULT 0",
                "sgst_amt" to "REAL DEFAULT 0",
                "igst_amt" to "REAL DEFAULT 0",
                "total_amt" to "REAL DEFAULT 0",
                "cgst_rate" to "REAL DEFAULT 0",
                "sgst_rate" to "REAL DEFAULT 0",
                "igst_rate" to "REAL DEFAULT 0"
            )
            itemSnakeCols.forEach { (col, type) ->
                try {
                    stmt.execute("ALTER TABLE invoice_items ADD COLUMN $col $type")
                } catch (e: Exception) { /* Column exists */ }
            }

            // Backfill invoice_id from invoiceNo if invoice_id is NULL or 0
            // This links items to invoices properly using the legacy invoiceNo field
            try {
                // Check if invoiceNo column exists before running update
                 stmt.execute("""
                    UPDATE invoice_items 
                    SET invoice_id = (SELECT id FROM invoices WHERE invoices.invoiceNo = invoice_items.invoiceNo)
                    WHERE (invoice_id IS NULL OR invoice_id = 0) AND invoiceNo IS NOT NULL
                """)
            } catch (e: Exception) {
                // invoiceNo might not exist in invoice_items if it was created purely with snake_case.
                // Or invoices table might not match. Safe to ignore if fails.
            }
            
            // --- BL MODULE (Phase 16) ---
            val createHBLInstructions = """
                CREATE TABLE IF NOT EXISTS hbl_instructions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_id INTEGER NOT NULL,
                    hbl_no TEXT NOT NULL UNIQUE,
                    mbl_no TEXT,
                    shipper_text TEXT,
                    consignee_text TEXT,
                    notify_party_text TEXT,
                    delivery_agent_text TEXT,
                    marks_and_numbers TEXT,
                    description_of_goods TEXT,
                    bl_type TEXT DEFAULT 'ORIGINAL', -- ORIGINAL, TELEX, SEAWAY
                    freight_terms TEXT DEFAULT 'PREPAID', -- PREPAID, COLLECT
                    place_of_receipt TEXT,
                    port_of_loading TEXT,
                    port_of_discharge TEXT,
                    place_of_delivery TEXT,
                    shipped_on_board_date TEXT,
                    no_of_originals INTEGER DEFAULT 3,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY(job_id) REFERENCES jobs(id)
                );
            """.trimIndent()
            stmt.execute(createHBLInstructions)
            
            val createContainers = """
                CREATE TABLE IF NOT EXISTS containers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_id INTEGER NOT NULL,
                    container_no TEXT NOT NULL,
                    seal_no TEXT,
                    container_type TEXT,
                    packages INTEGER DEFAULT 0,
                    package_type TEXT,
                    gross_weight REAL DEFAULT 0,
                    net_weight REAL DEFAULT 0,
                    volume_cbm REAL DEFAULT 0,
                    vgm_weight REAL DEFAULT 0,
                    description TEXT, -- Specific cargo description per container if needed
                    FOREIGN KEY(job_id) REFERENCES jobs(id)
                );
            """.trimIndent()
            stmt.execute(createContainers)

            // --- ADDRESS MANAGEMENT (Phase 19 Fix) ---
            val createAddresses = """
                CREATE TABLE IF NOT EXISTS consignee_addresses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    consignee_id INTEGER NOT NULL,
                    label TEXT,
                    address TEXT,
                    state TEXT,
                    pincode TEXT,
                    country TEXT DEFAULT 'India',
                    is_default INTEGER DEFAULT 0,
                    FOREIGN KEY(consignee_id) REFERENCES client_master(id)
                );
            """.trimIndent()
            stmt.execute(createAddresses)

            // --- PROFITABILITY MIGRATION ---
            try {
                stmt.execute("ALTER TABLE ledger_entries ADD COLUMN container_number TEXT")
            } catch (e: Exception) {
                // Column likely already exists
            }

            // --- INVOICE COLUMN MIGRATIONS ---
            val invoiceMigrations = listOf(
                "ALTER TABLE invoices ADD COLUMN category TEXT",
                "ALTER TABLE invoices ADD COLUMN hblNo TEXT",
                "ALTER TABLE invoices ADD COLUMN containerNos TEXT",
                "ALTER TABLE invoices ADD COLUMN shipperInvoiceNo TEXT",
                "ALTER TABLE invoices ADD COLUMN shipperInvoiceDate TEXT",
                "ALTER TABLE invoices ADD COLUMN customerId INTEGER"
            )
            for (migration in invoiceMigrations) {
                try { stmt.execute(migration) } catch (_: Exception) { }
            }

            // ─── QUOTATIONS (dedicated table, no tax) ───────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS quotations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    quotation_no TEXT NOT NULL UNIQUE,
                    date         TEXT NOT NULL,
                    valid_until  TEXT,
                    customer_id  INTEGER,
                    customer_name TEXT,
                    billing_address TEXT,
                    job_id       INTEGER,
                    job_no       TEXT,
                    shipper      TEXT,
                    consignee    TEXT,
                    pol          TEXT,
                    pod          TEXT,
                    mode         TEXT,
                    container_type TEXT,
                    vessel_flight TEXT,
                    etd          TEXT,
                    eta          TEXT,
                    terms        TEXT,
                    notes        TEXT,
                    total_amount REAL DEFAULT 0,
                    status       TEXT DEFAULT 'DRAFT',
                    created_at   TEXT DEFAULT CURRENT_TIMESTAMP
                );
            """.trimIndent())

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS quotation_items (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    quotation_id   INTEGER NOT NULL,
                    sr_no          INTEGER,
                    description    TEXT NOT NULL,
                    currency       TEXT DEFAULT 'INR',
                    qty            REAL DEFAULT 1,
                    unit           TEXT DEFAULT 'Lumpsum',
                    rate           REAL DEFAULT 0,
                    amount         REAL DEFAULT 0,
                    remarks        TEXT,
                    FOREIGN KEY(quotation_id) REFERENCES quotations(id) ON DELETE CASCADE
                );
            """.trimIndent())

            // ─── CREDIT NOTES (dedicated table, full GST) ───────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS credit_notes (
                    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
                    credit_note_no       TEXT NOT NULL UNIQUE,
                    date                 TEXT NOT NULL,
                    original_invoice_no  TEXT NOT NULL,
                    original_invoice_date TEXT,
                    reason               TEXT,
                    customer_id          INTEGER,
                    customer_name        TEXT,
                    billing_address      TEXT,
                    gstin                TEXT,
                    place_of_supply      TEXT,
                    state_code           TEXT,
                    job_id               INTEGER,
                    job_no               TEXT,
                    shipper              TEXT,
                    consignee            TEXT,
                    pol                  TEXT,
                    pod                  TEXT,
                    vessel_flight        TEXT,
                    mbl_no               TEXT,
                    hbl_no               TEXT,
                    container_nos        TEXT,
                    taxable_amount       REAL DEFAULT 0,
                    cgst_amount          REAL DEFAULT 0,
                    sgst_amount          REAL DEFAULT 0,
                    igst_amount          REAL DEFAULT 0,
                    grand_total          REAL DEFAULT 0,
                    created_at           TEXT DEFAULT CURRENT_TIMESTAMP
                );
            """.trimIndent())

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS credit_note_items (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    credit_note_id  INTEGER NOT NULL,
                    sr_no           INTEGER,
                    description     TEXT NOT NULL,
                    hsn_sac         TEXT,
                    currency        TEXT DEFAULT 'INR',
                    qty             REAL DEFAULT 1,
                    rate            REAL DEFAULT 0,
                    amount          REAL DEFAULT 0,
                    taxable_amount  REAL DEFAULT 0,
                    cgst_rate       REAL DEFAULT 0,
                    cgst_amt        REAL DEFAULT 0,
                    sgst_rate       REAL DEFAULT 0,
                    sgst_amt        REAL DEFAULT 0,
                    igst_rate       REAL DEFAULT 0,
                    igst_amt        REAL DEFAULT 0,
                    total_amt       REAL DEFAULT 0,
                    FOREIGN KEY(credit_note_id) REFERENCES credit_notes(id) ON DELETE CASCADE
                );
            """.trimIndent())
        }
    }

    // --- e-Invoice IRN UPDATE ---
    
    fun updateIrnFields(invoiceNo: String, irn: String, ackNo: String, ackDate: String, signedQr: String, signedInvoice: String) {
        connect()?.use { conn ->
            val sql = """
                UPDATE invoices 
                SET irn = ?, ackNo = ?, ackDate = ?, signedQr = ?, signedInvoice = ?
                WHERE invoiceNo = ?
            """.trimIndent()
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, irn)
                ps.setString(2, ackNo)
                ps.setString(3, ackDate)
                ps.setString(4, signedQr)
                ps.setString(5, signedInvoice)
                ps.setString(6, invoiceNo)
                ps.executeUpdate()
            }
        }
    }

    // --- INVOICE CRUD ---

    fun saveInvoice(data: InvoiceData) {
        if (data.invoiceNo.isBlank()) return

        val deleteItems = "DELETE FROM invoice_items WHERE invoiceNo = ?"
        
        val upsertInv = """
            INSERT OR REPLACE INTO invoices (
                invoiceNo, date, customerName, billingAddress, gstin, placeOfSupply, reverseCharge,
                jobId, shipper, consignee, vessel, pol, pod,
                taxableAmount, cgstAmount, sgstAmount, igstAmount, grandTotal
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        val insertItem = """
            INSERT INTO invoice_items (
                invoiceNo, description, hsnSac, qty, rate, per, amount,
                gstRate, cgstAmount, sgstAmount, igstAmount, total
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        connect()?.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(upsertInv).use { ps ->
                    ps.setString(1, data.invoiceNo)
                    ps.setString(2, data.date)
                    ps.setString(3, data.customerName)
                    ps.setString(4, data.billingAddress)
                    ps.setString(5, data.gstin)
                    ps.setString(6, data.placeOfSupply)
                    ps.setInt(7, if (data.reverseCharge) 1 else 0)
                    ps.setString(8, data.jobId)
                    ps.setString(9, data.shipper)
                    ps.setString(10, data.consignee)
                    ps.setString(11, data.vessel)
                    ps.setString(12, data.pol)
                    ps.setString(13, data.pod)
                    ps.setDouble(14, data.taxableAmount)
                    ps.setDouble(15, data.cgstAmount)
                    ps.setDouble(16, data.sgstAmount)
                    ps.setDouble(17, data.igstAmount)
                    ps.setDouble(18, data.grandTotal)
                    ps.executeUpdate()
                }

                conn.prepareStatement(deleteItems).use { ps ->
                    ps.setString(1, data.invoiceNo)
                    ps.executeUpdate()
                }

                conn.prepareStatement(insertItem).use { ps ->
                    for (item in data.items) {
                        ps.setString(1, data.invoiceNo)
                        ps.setString(2, item.description)
                        ps.setString(3, item.hsnSac)
                        ps.setDouble(4, item.qty)
                        ps.setDouble(5, item.rate)
                        ps.setString(6, item.currency) // Using currency instead of per
                        ps.setDouble(7, item.amount)
                        ps.setDouble(8, item.cgstRate + item.sgstRate) // Combined GST rate
                        ps.setDouble(9, item.cgstAmt)
                        ps.setDouble(10, item.sgstAmt)
                        ps.setDouble(11, item.igstAmt)
                        ps.setDouble(12, item.totalAmt)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                e.printStackTrace()
            }
        }
    }

    fun getInvoice(invoiceNo: String): InvoiceData? {
        // Use LIKE search so partial or slightly different format matches are found
        val qInv = "SELECT * FROM invoices WHERE invoiceNo LIKE ? OR invoiceNo = ? ORDER BY rowid DESC LIMIT 1"
        val qItems = "SELECT * FROM invoice_items WHERE invoiceNo = ?"
        var inv: InvoiceData? = null

        connect()?.use { conn ->
            conn.prepareStatement(qInv).use { ps ->
                ps.setString(1, "%${invoiceNo}%")
                ps.setString(2, invoiceNo)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    inv = InvoiceData(
                        invoiceNo = rs.getString("invoiceNo"),
                        date = rs.getString("date"),
                        documentType = rs.getString("type") ?: "INVOICE",
                        customerName = rs.getString("customerName"),
                        billingAddress = rs.getString("billingAddress"),
                        gstin = rs.getString("gstin"),
                        placeOfSupply = rs.getString("placeOfSupply"),
                        reverseCharge = rs.getInt("reverseCharge") == 1,
                        jobId = rs.getString("jobId"),
                        shipper = rs.getString("shipper"),
                        consignee = rs.getString("consignee"),
                        vessel = rs.getString("vessel"),
                        pol = rs.getString("pol"),
                        pod = rs.getString("pod"),
                        taxableAmount = rs.getDouble("taxableAmount"),
                        cgstAmount = rs.getDouble("cgstAmount"),
                        sgstAmount = rs.getDouble("sgstAmount"),
                        igstAmount = rs.getDouble("igstAmount"),
                        grandTotal = rs.getDouble("grandTotal"),
                        items = emptyList()
                    )
                }
            }
            
            if (inv != null) {
                val items = mutableListOf<InvoiceModels.InvoiceItem>()
                conn.prepareStatement(qItems).use { ps ->
                    ps.setString(1, invoiceNo)
                    val rs = ps.executeQuery()
                    while (rs.next()) {
                        items.add(InvoiceModels.InvoiceItem(
                            id = rs.getInt("id"),
                            invoiceId = 0, // Not stored in old schema
                            srNo = items.size + 1,
                            description = rs.getString("description"),
                            hsnSac = rs.getString("hsnSac"),
                            currency = "INR", // Default
                            rate = rs.getDouble("rate"),
                            qty = rs.getDouble("qty"),
                            amount = rs.getDouble("amount"),
                            taxableAmount = rs.getDouble("amount"),
                            cgstRate = rs.getDouble("gstRate") / 2, // Assuming equal split
                            cgstAmt = rs.getDouble("cgstAmount"),
                            sgstRate = rs.getDouble("gstRate") / 2,
                            sgstAmt = rs.getDouble("sgstAmount"),
                            igstRate = 0.0,
                            igstAmt = rs.getDouble("igstAmount"),
                            totalAmt = rs.getDouble("total")
                        ))
                    }
                }
                inv = inv!!.copy(items = items)
            }
        }
        return inv
    }
    fun getInvoicesForRange(startDate: String, endDate: String): List<InvoiceData> {
        val qInv = "SELECT rowid as db_id, * FROM invoices WHERE date BETWEEN ? AND ? ORDER BY date ASC"
        val qItems = "SELECT * FROM invoice_items WHERE invoice_id = ? OR invoiceNo = ?"
        val list = mutableListOf<InvoiceData>()

        connect()?.use { conn ->
            conn.prepareStatement(qInv).use { ps ->
                ps.setString(1, startDate)
                ps.setString(2, endDate)
                val rs = ps.executeQuery()
                
                // Helper functions to safely read columns that might not exist in old schemas
                fun java.sql.ResultSet.safeGetDouble(colName: String): Double {
                    return try { this.getDouble(colName) } catch (e: Exception) { 0.0 }
                }
                fun java.sql.ResultSet.safeGetString(colName: String): String? {
                    return try { this.getString(colName) } catch (e: Exception) { null }
                }

                while (rs.next()) {
                    val dbId = rs.getInt("db_id")
                    var inv = InvoiceData(
                        invoiceNo = rs.getString("invoiceNo"),
                        date = rs.getString("date"),
                        documentType = rs.safeGetString("type") ?: "INVOICE",
                        customerName = rs.getString("customerName"),
                        billingAddress = rs.getString("billingAddress"),
                        gstin = rs.getString("gstin"),
                        placeOfSupply = rs.getString("placeOfSupply"),
                        reverseCharge = rs.getInt("reverseCharge") == 1,
                        jobId = rs.getString("jobId"),
                        shipper = rs.getString("shipper"),
                        consignee = rs.getString("consignee"),
                        vessel = rs.getString("vessel"),
                        pol = rs.getString("pol"),
                        pod = rs.getString("pod"),
                        taxableAmount = rs.getDouble("taxableAmount"),
                        cgstAmount = rs.getDouble("cgstAmount"),
                        sgstAmount = rs.getDouble("sgstAmount"),
                        igstAmount = rs.getDouble("igstAmount"),
                        grandTotal = rs.getDouble("grandTotal"),
                        items = emptyList()
                    )
                    
                    // Fetch items for this invoice
                    val items = mutableListOf<InvoiceModels.InvoiceItem>()
                    conn.prepareStatement(qItems).use { psItems ->
                        psItems.setInt(1, dbId)
                        psItems.setString(2, inv.invoiceNo)
                        val rsItems = psItems.executeQuery()
                        while (rsItems.next()) {
                            // Map columns dynamically checking if new columns exist/populated, fallback to old
                            val taxableAmountNew = rsItems.safeGetDouble("taxable_amount")
                            val amountOld = rsItems.safeGetDouble("amount")
                            val taxable = if (taxableAmountNew > 0.0) taxableAmountNew else amountOld

                            val cgstRateNew = rsItems.safeGetDouble("cgst_rate")
                            val gstRateOld = rsItems.safeGetDouble("gstRate")
                            val cgstR = if (cgstRateNew > 0.0) cgstRateNew else gstRateOld / 2

                            val cgstAmtNew = rsItems.safeGetDouble("cgst_amt")
                            val cgstAmountOld = rsItems.safeGetDouble("cgstAmount")
                            val cgstA = if (cgstAmtNew > 0.0) cgstAmtNew else cgstAmountOld

                            val sgstRateNew = rsItems.safeGetDouble("sgst_rate")
                            val sgstR = if (sgstRateNew > 0.0) sgstRateNew else gstRateOld / 2

                            val sgstAmtNew = rsItems.safeGetDouble("sgst_amt")
                            val sgstAmountOld = rsItems.safeGetDouble("sgstAmount")
                            val sgstA = if (sgstAmtNew > 0.0) sgstAmtNew else sgstAmountOld

                            val igstRateNew = rsItems.safeGetDouble("igst_rate")
                            val igstR = if (igstRateNew > 0.0) igstRateNew else 0.0 // igst rate missing in old schema

                            val igstAmtNew = rsItems.safeGetDouble("igst_amt")
                            val igstAmountOld = rsItems.safeGetDouble("igstAmount")
                            val igstA = if (igstAmtNew > 0.0) igstAmtNew else igstAmountOld

                            val totalAmtNew = rsItems.safeGetDouble("total_amt")
                            val totalOld = rsItems.safeGetDouble("total")
                            val totalA = if (totalAmtNew > 0.0) totalAmtNew else totalOld
                            
                            val itemInvoiceId = try { rsItems.getInt("invoice_id") } catch(e: Exception) { 0 }

                            items.add(InvoiceModels.InvoiceItem(
                                id = rsItems.getInt("id"),
                                invoiceId = if (itemInvoiceId > 0) itemInvoiceId else 0,
                                srNo = items.size + 1,
                                description = rsItems.getString("description"),
                                hsnSac = rsItems.safeGetString("hsnSac") ?: rsItems.safeGetString("hsn_sac") ?: "",
                                currency = rsItems.safeGetString("currency") ?: "INR",
                                rate = rsItems.getDouble("rate"),
                                qty = rsItems.getDouble("qty"),
                                amount = if (amountOld > 0.0) amountOld else taxable,
                                taxableAmount = taxable,
                                cgstRate = cgstR,
                                cgstAmt = cgstA,
                                sgstRate = sgstR,
                                sgstAmt = sgstA,
                                igstRate = igstR,
                                igstAmt = igstA,
                                totalAmt = totalA
                            ))
                        }
                    }
                    inv = inv.copy(items = items)
                    list.add(inv)
                }
            }
        }
        return list
    }

    // --- SAVE / UPDATE (FIXED: Uses Explicit Column Names) ---
    fun saveBill(data: MblData) {
        if (data.mtdNumber.isBlank()) return

        val deleteCargo = "DELETE FROM cargo_items WHERE mtdNumber = ?"

        // FIX: Explicitly naming columns prevents data shifting
        val upsertHeader = """
            INSERT OR REPLACE INTO mbl_headers (
                mtdNumber, refNumber, consignor, consignee, notifyAddress,
                preCarriage, placeReceipt, deliveryAgent, vessel, voyNumber,
                portLoading, portDischarge, placeDelivery, mode, route,
                mainCustomsSeal, mainAgentSeal, goodsDescription, marksNumbers,
                freightAmount, freightPayableAt, originalMtds, placeDateIssue, otherParticulars
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 
                ?, ?, ?, ?
            )
        """

        val insertCargo = """
            INSERT INTO cargo_items(
                mtdNumber, containerNo, grossWeight, measurement, 
                pkgCount, netWeight, agentSeal, customsSeal, sbNumber, sbDate
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        connect()?.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(upsertHeader).use { pstmt ->
                    pstmt.setString(1, data.mtdNumber)
                    pstmt.setString(2, data.refNumber)
                    pstmt.setString(3, data.consignor)
                    pstmt.setString(4, data.consignee)
                    pstmt.setString(5, data.notifyAddress)
                    pstmt.setString(6, data.preCarriage)
                    pstmt.setString(7, data.placeReceipt)
                    pstmt.setString(8, data.deliveryAgent)
                    pstmt.setString(9, data.vessel)
                    pstmt.setString(10, data.voyNumber)
                    pstmt.setString(11, data.portLoading)
                    pstmt.setString(12, data.portDischarge)
                    pstmt.setString(13, data.placeDelivery)
                    pstmt.setString(14, data.mode)
                    pstmt.setString(15, data.route)
                    pstmt.setString(16, data.mainCustomsSeal)
                    pstmt.setString(17, data.mainAgentSeal)
                    pstmt.setString(18, data.goodsDescription)
                    pstmt.setString(19, data.marksNumbers) // Correctly mapped now
                    pstmt.setString(20, data.freightAmount)
                    pstmt.setString(21, data.freightPayableAt)
                    pstmt.setString(22, data.originalMtds)
                    pstmt.setString(23, data.placeDateIssue)
                    pstmt.setString(24, data.otherParticulars)
                    pstmt.executeUpdate()
                }

                conn.prepareStatement(deleteCargo).use { pstmt ->
                    pstmt.setString(1, data.mtdNumber)
                    pstmt.executeUpdate()
                }

                conn.prepareStatement(insertCargo).use { pstmt ->
                    for (item in data.cargoItems) {
                        pstmt.setString(1, data.mtdNumber)
                        pstmt.setString(2, item.containerNo)
                        // Removed item.marks
                        pstmt.setString(3, item.grossWeight)
                        pstmt.setString(4, item.measurement)
                        pstmt.setString(5, item.pkgCount)
                        pstmt.setString(6, item.netWeight)
                        pstmt.setString(7, item.agentSeal)
                        pstmt.setString(8, item.customsSeal)
                        pstmt.setString(9, item.sbNumber)
                        pstmt.setString(10, item.sbDate)
                        pstmt.addBatch()
                    }
                    pstmt.executeBatch()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                e.printStackTrace()
            }
        }
    }

    // --- LOAD ---
    fun getBill(mtdNumber: String): MblData? {
        val queryHeader = "SELECT * FROM mbl_headers WHERE mtdNumber = ?"
        val queryCargo = "SELECT * FROM cargo_items WHERE mtdNumber = ?"
        var data: MblData? = null

        connect()?.use { conn ->
            conn.prepareStatement(queryHeader).use { pstmt ->
                pstmt.setString(1, mtdNumber)
                val rs = pstmt.executeQuery()
                if (rs.next()) {
                    // Safe handling for potentially null columns
                    val marksVal = try { rs.getString("marksNumbers") ?: "" } catch(e: Exception) { "" }

                    data = MblData(
                        mtdNumber = rs.getString("mtdNumber") ?: "",
                        refNumber = rs.getString("refNumber") ?: "",
                        consignor = rs.getString("consignor") ?: "",
                        consignee = rs.getString("consignee") ?: "",
                        notifyAddress = rs.getString("notifyAddress") ?: "",
                        preCarriage = rs.getString("preCarriage") ?: "",
                        placeReceipt = rs.getString("placeReceipt") ?: "",
                        deliveryAgent = rs.getString("deliveryAgent") ?: "",
                        vessel = rs.getString("vessel") ?: "",
                        voyNumber = rs.getString("voyNumber") ?: "",
                        portLoading = rs.getString("portLoading") ?: "",
                        portDischarge = rs.getString("portDischarge") ?: "",
                        placeDelivery = rs.getString("placeDelivery") ?: "",
                        mode = rs.getString("mode") ?: "",
                        route = rs.getString("route") ?: "",
                        mainCustomsSeal = rs.getString("mainCustomsSeal") ?: "",
                        mainAgentSeal = rs.getString("mainAgentSeal") ?: "",
                        goodsDescription = rs.getString("goodsDescription") ?: "",
                        marksNumbers = marksVal,
                        freightAmount = rs.getString("freightAmount") ?: "",
                        freightPayableAt = rs.getString("freightPayableAt") ?: "",
                        originalMtds = rs.getString("originalMtds") ?: "",
                        placeDateIssue = rs.getString("placeDateIssue") ?: "",
                        otherParticulars = rs.getString("otherParticulars") ?: "",
                        cargoItems = mutableListOf()
                    )
                }
            }
            if (data != null) {
                conn.prepareStatement(queryCargo).use { pstmt ->
                    pstmt.setString(1, mtdNumber)
                    val rs = pstmt.executeQuery()
                    val items = mutableListOf<CargoItem>()
                    while (rs.next()) {
                        items.add(CargoItem(
                            containerNo = rs.getString("containerNo") ?: "",
                            grossWeight = rs.getString("grossWeight") ?: "",
                            measurement = rs.getString("measurement") ?: "",
                            pkgCount = rs.getString("pkgCount") ?: "",
                            netWeight = rs.getString("netWeight") ?: "",
                            agentSeal = rs.getString("agentSeal") ?: "",
                            customsSeal = rs.getString("customsSeal") ?: "",
                            sbNumber = rs.getString("sbNumber") ?: "",
                            sbDate = rs.getString("sbDate") ?: ""
                        ))
                    }
                    if (items.isNotEmpty()) {
                        data = data!!.copy(cargoItems = items)
                    }
                }
            }
        }
        return data
    }

    fun getAllMtdNumbers(): List<String> {
        val list = mutableListOf<String>()
        val sql = "SELECT mtdNumber FROM mbl_headers ORDER BY mtdNumber DESC"
        connect()?.use { conn ->
            val rs = conn.createStatement().executeQuery(sql)
            while (rs.next()) {
                list.add(rs.getString("mtdNumber"))
            }
        }
        return list
    }
}
package com.sanship.data

/**
 * Database Migration Script
 * Adds new columns to existing invoices table
 */
object DatabaseMigration {
    
    fun migrateToV2() {
        DatabaseManager.connect()?.use { conn ->
            conn.createStatement().use { stmt ->
                try {
                    // Add new columns if they don't exist
                    val migrations = listOf(
                        "ALTER TABLE invoices ADD COLUMN be_no TEXT",
                        "ALTER TABLE invoices ADD COLUMN be_date TEXT",
                        "ALTER TABLE invoices ADD COLUMN igm_no TEXT",
                        "ALTER TABLE invoices ADD COLUMN igm_date TEXT",
                        "ALTER TABLE invoices ADD COLUMN item_no TEXT"
                    )
                    
                    migrations.forEach { sql ->
                        try {
                            stmt.execute(sql)
                            println("✓ Migration executed: $sql")
                        } catch (e: Exception) {
                            // Column might already exist, ignore
                            if (e.message?.contains("duplicate column") != true) {
                                println("⚠ Migration skipped (column exists): $sql")
                            }
                        }
                    }
                    
                    println("✓ Database migration completed successfully")
                } catch (e: Exception) {
                    println("✗ Migration error: ${e.message}")
                    throw e
                }
            }
        }
    }

    fun migrateDatesToIso() {
        val inFormatter = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy")
        val outFormatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
        DatabaseManager.connect()?.use { conn ->
            try {
                // 1. Invoices
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT rowid, date FROM invoices")
                    val updates = mutableListOf<Pair<Int, String>>()
                    while (rs.next()) {
                        val rowid = rs.getInt(1)
                        val d = rs.getString(2)
                        if (d != null && d.matches(Regex("\\d{2}-[a-zA-Z]{3}-\\d{4}"))) {
                            try {
                                val newDate = java.time.LocalDate.parse(d, inFormatter).format(outFormatter)
                                updates.add(Pair(rowid, newDate))
                            } catch (e: Exception) {}
                        }
                    }
                    if (updates.isNotEmpty()) {
                        conn.prepareStatement("UPDATE invoices SET date = ? WHERE rowid = ?").use { psUpdate ->
                            for (u in updates) {
                                psUpdate.setString(1, u.second)
                                psUpdate.setInt(2, u.first)
                                psUpdate.executeUpdate()
                            }
                        }
                        println("✓ Migrated ${updates.size} invoice dates to ISO format.")
                    }
                }
            } catch (e: Exception) {
                println("✗ Date Migration error: ${e.message}")
            }
        }
    }
}

package com.sanship.accounting

import java.sql.Connection
import java.sql.DriverManager

/**
 * Accounting Database Connection Manager
 * Single source of truth for accounting database path and connections
 */
object AccountingDb {
    
    // Use legacy data.db for accounting as requested by user
    private val DB_PATH = "data.db"
    
    fun getConnection(): Connection {
        val conn = DriverManager.getConnection("jdbc:sqlite:$DB_PATH")
        // Enable foreign keys
        conn.createStatement().execute("PRAGMA foreign_keys = ON")
        
        // Patch migration: ensure vouchers table has job_id without recreating DB
        try {
            conn.createStatement().execute("ALTER TABLE vouchers ADD COLUMN job_id INTEGER")
        } catch (e: Exception) {
            // Column exists, safe to ignore
        }
        
        return conn
    }
}

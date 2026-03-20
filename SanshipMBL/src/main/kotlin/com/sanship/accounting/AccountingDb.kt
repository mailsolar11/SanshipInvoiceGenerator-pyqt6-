package com.sanship.accounting

import java.sql.Connection
import java.sql.DriverManager

/**
 * Accounting Database Connection Manager
 * Single source of truth for accounting database path and connections
 */
object AccountingDb {
    
    // UNIFIED: All data now lives in sanship.db (migrated from data.db)
    private val DB_PATH = "sanship.db"
    
    fun getConnection(): Connection {
        val conn = DriverManager.getConnection("jdbc:sqlite:$DB_PATH")
        // Enable foreign keys
        conn.createStatement().execute("PRAGMA foreign_keys = ON")
        return conn
    }
}

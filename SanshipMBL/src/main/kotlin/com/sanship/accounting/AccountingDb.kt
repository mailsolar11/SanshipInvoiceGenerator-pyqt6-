package com.sanship.accounting

import java.sql.Connection
import java.sql.DriverManager

/**
 * Accounting Database Connection Manager
 * Single source of truth for accounting database path and connections
 */
object AccountingDb {
    
    // Use same database as business data (Python uses data.db for both)
    private val DB_PATH = "data.db"
    
    fun getConnection(): Connection {
        val conn = DriverManager.getConnection("jdbc:sqlite:$DB_PATH")
        // Enable foreign keys
        conn.createStatement().execute("PRAGMA foreign_keys = ON")
        return conn
    }
}

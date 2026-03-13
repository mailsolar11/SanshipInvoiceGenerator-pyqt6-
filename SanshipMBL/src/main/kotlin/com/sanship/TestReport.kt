package com.sanship

import com.sanship.data.DatabaseManager
import com.sanship.services.GstReportService

fun main() {
    DatabaseManager.initDatabase()
    println("--- STARTING REPORT TEST ---")
    GstReportService.generateGstr1("2026-02-01", "2026-03-31", "test_export.xlsx")
    println("--- END REPORT TEST ---")
}

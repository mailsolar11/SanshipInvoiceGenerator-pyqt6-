package com.sanship.services

import com.sanship.data.DatabaseManager
import com.sanship.accounting.AccountingDb
import com.sanship.data.InvoiceModels.InvoiceHeader
import com.sanship.data.InvoiceModels.InvoiceItem
import com.google.gson.Gson
import java.sql.Connection
import java.sql.Statement
import java.util.UUID

object TransactionManager {
    
    private val gson = Gson()
    
    enum class TransactionStatus {
        PENDING, COMMITTED_BUSINESS, COMMITTED_ACCOUNTING, COMPLETED, FAILED
    }

    enum class EntityType {
        INVOICE, EXPENSE, RECEIPT
    }

    /**
     * Coordinate Save Invoice — UNIFIED single database
     */
    fun saveInvoice(header: InvoiceHeader, items: List<InvoiceItem>): Int {
        val transactionId = UUID.randomUUID().toString()
        val payload = gson.toJson(mapOf("header" to header, "items" to items))
        
        logTransaction(transactionId, EntityType.INVOICE, TransactionStatus.PENDING, payload)
        
        val conn = DatabaseManager.connect()
            ?: throw RuntimeException("Failed to connect to database")
        
        try {
            conn.autoCommit = false
            
            // Business DB write
            val businessInvoiceId = InvoiceService.saveInvoiceInternal(conn, header, items)
            updateTransactionStatus(transactionId, TransactionStatus.COMMITTED_BUSINESS, conn)
            
            // Accounting write (same DB now)
            val currentHeader = header.copy(id = businessInvoiceId)
            AccountingEngine.postInvoiceInternal(conn, currentHeader, items)
            updateTransactionStatus(transactionId, TransactionStatus.COMMITTED_ACCOUNTING, conn)
            
            // Single atomic commit
            conn.commit()
            updateTransactionStatus(transactionId, TransactionStatus.COMPLETED, conn)
            return businessInvoiceId
            
        } catch (e: Exception) {
            e.printStackTrace()
            try { conn.rollback() } catch (e2: Exception) {}
            updateTransactionStatus(transactionId, TransactionStatus.FAILED)
            throw e
        } finally {
            try { conn.close() } catch (e: Exception) {}
        }
    }

    /**
     * Coordinate Save Debit Note — UNIFIED single database
     */
    fun saveDebitNote(header: InvoiceHeader, items: List<InvoiceItem>): Int {
        val transactionId = UUID.randomUUID().toString()
        val payload = gson.toJson(mapOf("header" to header, "items" to items, "type" to "DEBIT_NOTE"))
        
        logTransaction(transactionId, EntityType.INVOICE, TransactionStatus.PENDING, payload)
        
        val conn = DatabaseManager.connect()
            ?: throw RuntimeException("Failed to connect to database")
        
        try {
            conn.autoCommit = false
            
            val businessInvoiceId = InvoiceService.saveInvoiceInternal(conn, header, items)
            updateTransactionStatus(transactionId, TransactionStatus.COMMITTED_BUSINESS, conn)
            
            val currentHeader = header.copy(id = businessInvoiceId) 
            AccountingEngine.postDebitNoteInternal(conn, currentHeader, items)
            updateTransactionStatus(transactionId, TransactionStatus.COMMITTED_ACCOUNTING, conn)
            
            conn.commit()
            updateTransactionStatus(transactionId, TransactionStatus.COMPLETED, conn)
            return businessInvoiceId
            
        } catch (e: Exception) {
            e.printStackTrace()
            try { conn.rollback() } catch (e2: Exception) {}
            updateTransactionStatus(transactionId, TransactionStatus.FAILED)
            throw e
        } finally {
            try { conn.close() } catch (e: Exception) {}
        }
    }

    /**
     * Coordinate Save Credit Note — UNIFIED single database
     */
    fun saveCreditNote(header: InvoiceHeader, items: List<InvoiceItem>): Int {
        val transactionId = UUID.randomUUID().toString()
        val payload = gson.toJson(mapOf("header" to header, "items" to items, "type" to "CREDIT_NOTE"))
        
        logTransaction(transactionId, EntityType.INVOICE, TransactionStatus.PENDING, payload)
        
        val conn = DatabaseManager.connect()
            ?: throw RuntimeException("Failed to connect to database")
        
        try {
            conn.autoCommit = false
            
            val businessInvoiceId = InvoiceService.saveInvoiceInternal(conn, header, items)
            updateTransactionStatus(transactionId, TransactionStatus.COMMITTED_BUSINESS, conn)
            
            val currentHeader = header.copy(id = businessInvoiceId) 
            AccountingEngine.postCreditNoteInternal(conn, currentHeader, items)
            updateTransactionStatus(transactionId, TransactionStatus.COMMITTED_ACCOUNTING, conn)
            
            conn.commit()
            updateTransactionStatus(transactionId, TransactionStatus.COMPLETED, conn)
            return businessInvoiceId
            
        } catch (e: Exception) {
            e.printStackTrace()
            try { conn.rollback() } catch (e2: Exception) {}
            updateTransactionStatus(transactionId, TransactionStatus.FAILED)
            throw e
        } finally {
            try { conn.close() } catch (e: Exception) {}
        }
    }

    // Coordinate Save Quotation (Business DB only, no accounting)
    fun saveQuotation(header: InvoiceHeader, items: List<InvoiceItem>): Int {
        val businessConn = DatabaseManager.connect() ?: return -1

        val currentHeader = header.copy(
            documentType = "QUOTATION"
        )
        
        try {
            businessConn.autoCommit = false
            val businessInvoiceId = com.sanship.services.InvoiceService.saveInvoiceInternal(businessConn, currentHeader, items)
            businessConn.commit()
            return businessInvoiceId
        } catch (e: Exception) {
            try { businessConn.rollback() } catch (re: Exception) { }
            e.printStackTrace()
            return -1
        } finally {
            try { businessConn.close() } catch (e: Exception) {}
        }
    }
    
    private fun logTransaction(id: String, type: EntityType, status: TransactionStatus, payload: String, conn: Connection? = null) {
        val useConn = conn ?: DatabaseManager.connect()
        val ownConnection = conn == null
        try {
            useConn?.let { c ->
                val sql = "INSERT INTO transaction_audit_log (id, entity_type, status, payload) VALUES (?, ?, ?, ?)"
                c.prepareStatement(sql).use { ps ->
                    ps.setString(1, id)
                    ps.setString(2, type.name)
                    ps.setString(3, status.name)
                    ps.setString(4, payload)
                    ps.executeUpdate()
                }
            }
        } finally {
            if (ownConnection) {
                try { useConn?.close() } catch (_: Exception) {}
            }
        }
    }
    
    private fun updateTransactionStatus(id: String, status: TransactionStatus, conn: Connection? = null) {
        val useConn = conn ?: DatabaseManager.connect()
        val ownConnection = conn == null
        try {
            useConn?.let { c ->
                val sql = "UPDATE transaction_audit_log SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
                c.prepareStatement(sql).use { ps ->
                    ps.setString(1, status.name)
                    ps.setString(2, id)
                    ps.executeUpdate()
                }
            }
        } finally {
            if (ownConnection) {
                try { useConn?.close() } catch (_: Exception) {}
            }
        }
    }
}

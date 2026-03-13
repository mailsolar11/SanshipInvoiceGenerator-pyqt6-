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
     * Coordinate Save Invoice to both databases
     */
    fun saveInvoice(header: InvoiceHeader, items: List<InvoiceItem>): Int {
        val transactionId = UUID.randomUUID().toString()
        val payload = gson.toJson(mapOf("header" to header, "items" to items))
        
        // 1. Log Pending Transaction
        logTransaction(transactionId, EntityType.INVOICE, TransactionStatus.PENDING, payload)
        
        var businessConn: Connection? = null
        var accountingConn: Connection? = null
        
        try {
            businessConn = DatabaseManager.connect()
            accountingConn = AccountingDb.getConnection()
            
            if (businessConn == null || accountingConn == null) {
                throw RuntimeException("Failed to connect to databases")
            }
            
            businessConn.autoCommit = false
            accountingConn.autoCommit = false
            
            // 2. Write to Business DB
            // --- BUSINESS DB WRITE START ---
            val businessInvoiceId = InvoiceService.saveInvoiceInternal(businessConn, header, items)
            // --- BUSINESS DB WRITE END ---
            
            updateTransactionStatus(transactionId, TransactionStatus.COMMITTED_BUSINESS, businessConn)
            
            // 3. Write to Accounting DB
            // --- ACCOUNTING DB WRITE START ---
            // Ensure ID sync: The business ID is the source of truth for linking? 
            // Actually, we usually link via Invoice No.
            val currentHeader = header.copy(id = businessInvoiceId) 
            AccountingEngine.postInvoiceInternal(accountingConn, currentHeader, items)
            // --- ACCOUNTING DB WRITE END ---
            
            updateTransactionStatus(transactionId, TransactionStatus.COMMITTED_ACCOUNTING, businessConn)
            
            // 4. Commit Both
            businessConn.commit()
            accountingConn.commit()
            
            updateTransactionStatus(transactionId, TransactionStatus.COMPLETED, businessConn)
            return businessInvoiceId
            
        } catch (e: Exception) {
            e.printStackTrace()
            try { businessConn?.rollback() } catch (e2: Exception) {}
            try { accountingConn?.rollback() } catch (e2: Exception) {}
            updateTransactionStatus(transactionId, TransactionStatus.FAILED)
            throw e
        } finally {
            try { businessConn?.close() } catch (e: Exception) {}
            try { accountingConn?.close() } catch (e: Exception) {}
        }
    }

    /**
     * Coordinate Save Debit Note to both databases
     */
    fun saveDebitNote(header: InvoiceHeader, items: List<InvoiceItem>): Int {
        val transactionId = UUID.randomUUID().toString()
        val payload = gson.toJson(mapOf("header" to header, "items" to items, "type" to "DEBIT_NOTE"))
        
        // 1. Log Pending Transaction
        logTransaction(transactionId, EntityType.INVOICE, TransactionStatus.PENDING, payload)
        
        var businessConn: Connection? = null
        var accountingConn: Connection? = null
        
        try {
            businessConn = DatabaseManager.connect()
            accountingConn = AccountingDb.getConnection()
            
            if (businessConn == null || accountingConn == null) {
                throw RuntimeException("Failed to connect to databases")
            }
            
            businessConn.autoCommit = false
            accountingConn.autoCommit = false
            
            // 2. Write to Business DB
            // reuse saveInvoiceInternal as it saves to invoices table regardless of type
            val businessInvoiceId = InvoiceService.saveInvoiceInternal(businessConn, header, items)
            
            updateTransactionStatus(transactionId, TransactionStatus.COMMITTED_BUSINESS, businessConn)
            
            // 3. Write to Accounting DB
            val currentHeader = header.copy(id = businessInvoiceId) 
            AccountingEngine.postDebitNoteInternal(accountingConn, currentHeader, items)
            
            updateTransactionStatus(transactionId, TransactionStatus.COMMITTED_ACCOUNTING, businessConn)
            
            // 4. Commit Both
            businessConn.commit()
            accountingConn.commit()
            
            updateTransactionStatus(transactionId, TransactionStatus.COMPLETED, businessConn)
            return businessInvoiceId
            
        } catch (e: Exception) {
            e.printStackTrace()
            try { businessConn?.rollback() } catch (e2: Exception) {}
            try { accountingConn?.rollback() } catch (e2: Exception) {}
            updateTransactionStatus(transactionId, TransactionStatus.FAILED)
            throw e
        } finally {
            try { businessConn?.close() } catch (e: Exception) {}
            try { accountingConn?.close() } catch (e: Exception) {}
        }
    }

    /**
     * Coordinate Save Credit Note to both databases
     */
    fun saveCreditNote(header: InvoiceHeader, items: List<InvoiceItem>): Int {
        val transactionId = UUID.randomUUID().toString()
        val payload = gson.toJson(mapOf("header" to header, "items" to items, "type" to "CREDIT_NOTE"))
        
        logTransaction(transactionId, EntityType.INVOICE, TransactionStatus.PENDING, payload)
        
        var businessConn: Connection? = null
        var accountingConn: Connection? = null
        
        try {
            businessConn = DatabaseManager.connect()
            accountingConn = AccountingDb.getConnection()
            
            if (businessConn == null || accountingConn == null) {
                throw RuntimeException("Failed to connect to databases")
            }
            
            businessConn.autoCommit = false
            accountingConn.autoCommit = false
            
            // 2. Write to Business DB
            val businessInvoiceId = InvoiceService.saveInvoiceInternal(businessConn, header, items)
            
            updateTransactionStatus(transactionId, TransactionStatus.COMMITTED_BUSINESS, businessConn)
            
            // 3. Write to Accounting DB (reversed entries)
            val currentHeader = header.copy(id = businessInvoiceId) 
            AccountingEngine.postCreditNoteInternal(accountingConn, currentHeader, items)
            
            updateTransactionStatus(transactionId, TransactionStatus.COMMITTED_ACCOUNTING, businessConn)
            
            // 4. Commit Both
            businessConn.commit()
            accountingConn.commit()
            
            updateTransactionStatus(transactionId, TransactionStatus.COMPLETED, businessConn)
            return businessInvoiceId
            
        } catch (e: Exception) {
            e.printStackTrace()
            try { businessConn?.rollback() } catch (e2: Exception) {}
            try { accountingConn?.rollback() } catch (e2: Exception) {}
            updateTransactionStatus(transactionId, TransactionStatus.FAILED)
            throw e
        } finally {
            try { businessConn?.close() } catch (e: Exception) {}
            try { accountingConn?.close() } catch (e: Exception) {}
        }
    }

    // Coordinate Save Quotation (ONLY Business DB, NO Accounting)
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

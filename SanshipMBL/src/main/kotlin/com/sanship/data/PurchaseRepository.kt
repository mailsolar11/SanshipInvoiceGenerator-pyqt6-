package com.sanship.data

import java.sql.Statement

object PurchaseRepository {

    fun savePurchase(header: PurchaseHeader, items: List<PurchaseItem>): Int {
        var purchaseId = 0
        DatabaseManager.connect()?.use { conn ->
            conn.autoCommit = false
            try {
                // 1. Insert Header
                val headerSql = """
                    INSERT INTO purchase_invoices (
                        purchase_no, date, vendor_id, vendor_name, vendor_gstin, vendor_address, 
                        place_of_supply, reverse_charge, job_id, job_no, 
                        currency, exchange_rate,
                        taxable_amount, cgst_amount, sgst_amount, igst_amount, grand_total, narration
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                conn.prepareStatement(headerSql, Statement.RETURN_GENERATED_KEYS).use { pstmt ->
                    pstmt.setString(1, header.purchaseNo)
                    pstmt.setString(2, header.date)
                    pstmt.setInt(3, header.vendorId)
                    pstmt.setString(4, header.vendorName)
                    pstmt.setString(5, header.vendorGstin)
                    pstmt.setString(6, header.vendorAddress)
                    pstmt.setString(7, header.placeOfSupply)
                    pstmt.setBoolean(8, header.reverseCharge)
                    pstmt.setInt(9, header.jobId)
                    pstmt.setString(10, header.jobNo)
                    pstmt.setString(11, header.currency)
                    pstmt.setDouble(12, header.exchangeRate)
                    pstmt.setDouble(13, header.taxableAmount)
                    pstmt.setDouble(14, header.cgstAmount)
                    pstmt.setDouble(15, header.sgstAmount)
                    pstmt.setDouble(16, header.igstAmount)
                    pstmt.setDouble(17, header.grandTotal)
                    pstmt.setString(18, header.narration)
                    pstmt.executeUpdate()
                    
                    val rs = pstmt.generatedKeys
                    if (rs.next()) {
                        purchaseId = rs.getInt(1)
                    }
                }
                
                // 2. Insert Items
                val itemSql = """
                    INSERT INTO purchase_invoice_items (
                        purchase_id, sr_no, description, hsn_sac, qty, rate, amount, 
                        taxable_amount, cgst_rate, cgst_amount, sgst_rate, sgst_amount, 
                        igst_rate, igst_amount, total_amount, currency, exchange_rate
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                conn.prepareStatement(itemSql).use { pstmt ->
                    items.forEach { item ->
                        pstmt.setInt(1, purchaseId)
                        pstmt.setInt(2, item.srNo)
                        pstmt.setString(3, item.description)
                        pstmt.setString(4, item.hsnSac)
                        pstmt.setDouble(5, item.qty)
                        pstmt.setDouble(6, item.rate)
                        pstmt.setDouble(7, item.amount)
                        pstmt.setDouble(8, item.taxableAmount)
                        pstmt.setDouble(9, item.cgstRate)
                        pstmt.setDouble(10, item.cgstAmount)
                        pstmt.setDouble(11, item.sgstRate)
                        pstmt.setDouble(12, item.sgstAmount)
                        pstmt.setDouble(13, item.igstRate)
                        pstmt.setDouble(14, item.igstAmount)
                        pstmt.setDouble(15, item.totalAmount)
                        pstmt.setString(16, item.currency)
                        pstmt.setDouble(17, item.exchangeRate)
                        pstmt.addBatch()
                    }
                    pstmt.executeBatch()
                }
                
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
        return purchaseId
    }

    fun getAllPurchases(): List<PurchaseHeader> {
        val list = mutableListOf<PurchaseHeader>()
        DatabaseManager.connect()?.use { conn ->
            val sql = "SELECT * FROM purchase_invoices ORDER BY date DESC, id DESC"
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                while (rs.next()) {
                    list.add(extractHeader(rs))
                }
            }
        }
        return list
    }

    fun getPurchaseById(id: Int): Pair<PurchaseHeader, List<PurchaseItem>>? {
        DatabaseManager.connect()?.use { conn ->
            val headerSql = "SELECT * FROM purchase_invoices WHERE id = ?"
            val header = conn.prepareStatement(headerSql).use { ps ->
                ps.setInt(1, id)
                val rs = ps.executeQuery()
                if (rs.next()) extractHeader(rs) else null
            } ?: return null

            val itemsSql = "SELECT * FROM purchase_invoice_items WHERE purchase_id = ? ORDER BY sr_no"
            val items = mutableListOf<PurchaseItem>()
            conn.prepareStatement(itemsSql).use { ps ->
                ps.setInt(1, id)
                val rs = ps.executeQuery()
                while (rs.next()) {
                    items.add(
                        PurchaseItem(
                            id = rs.getInt("id"),
                            purchaseId = rs.getInt("purchase_id"),
                            srNo = rs.getInt("sr_no"),
                            description = rs.getString("description") ?: "",
                            hsnSac = rs.getString("hsn_sac") ?: "",
                            qty = rs.getDouble("qty"),
                            rate = rs.getDouble("rate"),
                            amount = rs.getDouble("amount"),
                            taxableAmount = rs.getDouble("taxable_amount"),
                            cgstRate = rs.getDouble("cgst_rate"),
                            cgstAmount = rs.getDouble("cgst_amount"),
                            sgstRate = rs.getDouble("sgst_rate"),
                            sgstAmount = rs.getDouble("sgst_amount"),
                            igstRate = rs.getDouble("igst_rate"),
                            igstAmount = rs.getDouble("igst_amount"),
                            totalAmount = rs.getDouble("total_amount"),
                            currency = rs.getString("currency") ?: "",
                            exchangeRate = rs.getDouble("exchange_rate")
                        )
                    )
                }
            }
            return Pair(header, items)
        }
        return null
    }

    private fun extractHeader(rs: java.sql.ResultSet): PurchaseHeader {
        return PurchaseHeader(
            id = rs.getInt("id"),
            purchaseNo = rs.getString("purchase_no") ?: "",
            date = rs.getString("date") ?: "",
            vendorId = rs.getInt("vendor_id"),
            vendorName = rs.getString("vendor_name") ?: "",
            vendorGstin = rs.getString("vendor_gstin") ?: "",
            vendorAddress = rs.getString("vendor_address") ?: "",
            placeOfSupply = rs.getString("place_of_supply") ?: "",
            reverseCharge = rs.getBoolean("reverse_charge"),
            jobId = rs.getInt("job_id"),
            jobNo = rs.getString("job_no") ?: "",
            currency = rs.getString("currency") ?: "INR",
            exchangeRate = rs.getDouble("exchange_rate"),
            taxableAmount = rs.getDouble("taxable_amount"),
            cgstAmount = rs.getDouble("cgst_amount"),
            sgstAmount = rs.getDouble("sgst_amount"),
            igstAmount = rs.getDouble("igst_amount"),
            grandTotal = rs.getDouble("grand_total"),
            narration = rs.getString("narration") ?: ""
        )
    }
}

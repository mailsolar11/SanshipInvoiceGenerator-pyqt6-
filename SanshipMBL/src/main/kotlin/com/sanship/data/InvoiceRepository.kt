package com.sanship.data

import java.sql.Statement

object InvoiceRepository {

    fun saveInvoice(header: InvoiceModels.InvoiceHeader, items: List<InvoiceModels.InvoiceItem>): Int {
        var invoiceId = 0
        DatabaseManager.connect()?.use { conn ->
            conn.autoCommit = false
            try {
                // 1. Insert Header using camelCase legacy schema
                val headerSql = """
                    INSERT INTO invoices (
                        invoiceNo, date, type, customerId, customerName, billingAddress, consignee,
                        shipper, pol, pod, vessel, etd, eta,
                        mblNo, grossWeight, netWeight, netWeightUnit, volumeCbm, packages,
                        beNo, beDate, igmNo, igmDate, itemNo,
                        currency, exchangeRate, refNo, otherRefNo, pan, stateCode, grandTotal, narration,
                        taxableAmount, cgstAmount, sgstAmount, igstAmount, jobNo, category
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                conn.prepareStatement(headerSql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setString(1, header.invoiceNo)
                    ps.setString(2, header.invoiceDate)
                    ps.setString(3, header.documentType)
                    ps.setInt(4, header.customerId)
                    ps.setString(5, header.customerName)
                    ps.setString(6, header.billingAddress)
                    ps.setString(7, header.consigneeAddress) // Maps consignee address
                    ps.setString(8, header.shipper)
                    ps.setString(9, header.pol)
                    ps.setString(10, header.pod)
                    ps.setString(11, header.vesselFlight)
                    ps.setString(12, header.etd)
                    ps.setString(13, header.eta)
                    ps.setString(14, header.mblNo)
                    ps.setString(15, header.grossWeight)
                    ps.setString(16, header.netWeight)
                    ps.setString(17, header.netWeightUnit)
                    ps.setString(18, header.volumeCbm)
                    ps.setString(19, header.packages)
                    ps.setString(20, header.beNo)
                    ps.setString(21, header.beDate)
                    ps.setString(22, header.igmNo)
                    ps.setString(23, header.igmDate)
                    ps.setString(24, header.itemNo)
                    ps.setString(25, header.currency)
                    ps.setDouble(26, header.exchangeRate)
                    ps.setString(27, header.refNo)
                    ps.setString(28, header.otherRefNo)
                    ps.setString(29, header.pan)
                    ps.setString(30, header.stateCode)
                    ps.setDouble(31, header.grandTotal)
                    ps.setString(32, header.narration)
                    ps.setDouble(33, header.taxableAmount)
                    ps.setDouble(34, header.cgstAmount)
                    ps.setDouble(35, header.sgstAmount)
                    ps.setDouble(36, header.igstAmount)
                    ps.setString(37, header.jobNo)
                    ps.setString(38, header.category)
                    ps.executeUpdate()
                    
                    val rs = ps.generatedKeys
                    if (rs.next()) {
                        invoiceId = rs.getInt(1)
                    } else {
                        // Fallback using last_insert_rowid() if driver doesn't support generated keys on TEXT PK
                        conn.createStatement().use { st ->
                            val rs2 = st.executeQuery("SELECT last_insert_rowid()")
                            if (rs2.next()) invoiceId = rs2.getInt(1)
                        }
                    }
                }
                
                // 2. Insert Items (Handling legacy columns too vs Phase 19 fix)
                // We'll use the snake_case columns as that was added by ALTER TABLE, but also set the legacy `invoiceNo` string!
                val itemSql = """
                    INSERT INTO invoice_items (
                        invoice_id, invoiceNo, sr_no, description, hsn_sac, currency, rate, qty,
                        amount, taxable_amount, cgst_rate, cgst_amt, sgst_rate, sgst_amt, igst_rate, igst_amt, total_amt
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                conn.prepareStatement(itemSql).use { ps ->
                    items.forEach { item ->
                        ps.setInt(1, invoiceId)
                        ps.setString(2, header.invoiceNo) // Legacy foreign key support
                        ps.setInt(3, item.srNo)
                        ps.setString(4, item.description)
                        ps.setString(5, item.hsnSac)
                        ps.setString(6, item.currency)
                        ps.setDouble(7, item.rate)
                        ps.setDouble(8, item.qty)
                        ps.setDouble(9, item.amount)
                        ps.setDouble(10, item.taxableAmount)
                        ps.setDouble(11, item.cgstRate)
                        ps.setDouble(12, item.cgstAmt)
                        ps.setDouble(13, item.sgstRate)
                        ps.setDouble(14, item.sgstAmt)
                        ps.setDouble(15, item.igstRate)
                        ps.setDouble(16, item.igstAmt)
                        ps.setDouble(17, item.totalAmt)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
        return invoiceId
    }

    fun getInvoiceById(id: Int): Pair<InvoiceModels.InvoiceHeader, List<InvoiceModels.InvoiceItem>>? {
        DatabaseManager.connect()?.use { conn ->
            // Get Header using rowid since `id` column doesn't exist for legacy tables
            val headerSql = "SELECT *, rowid as id FROM invoices WHERE rowid = ?"
            val header = conn.prepareStatement(headerSql).use { ps ->
                ps.setInt(1, id)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    extractHeader(rs)
                } else null
            } ?: return null
            
            // Get Items
            val itemsSql = "SELECT * FROM invoice_items WHERE invoice_id = ? OR invoiceNo = ? ORDER BY sr_no"
            val items = conn.prepareStatement(itemsSql).use { ps ->
                ps.setInt(1, id)
                ps.setString(2, header.invoiceNo)
                val rs = ps.executeQuery()
                val list = mutableListOf<InvoiceModels.InvoiceItem>()
                while (rs.next()) {
                    list.add(InvoiceModels.InvoiceItem(
                        id = try { rs.getInt("id") } catch (_: Exception) { 0 },
                        invoiceId = try { rs.getInt("invoice_id") } catch (_: Exception) { id },
                        srNo = try { rs.getInt("sr_no") } catch (_: Exception) { try { rs.getInt("srNo") } catch (_: Exception) { 1 } },
                        description = rs.getString("description") ?: "",
                        hsnSac = try { rs.getString("hsn_sac") } catch (_: Exception) { try { rs.getString("hsnSac") } catch (_: Exception) { "" } } ?: "",
                        currency = rs.getString("currency") ?: "INR",
                        exchangeRate = 1.0,
                        rate = rs.getDouble("rate"),
                        qty = rs.getDouble("qty"),
                        amount = rs.getDouble("amount"),
                        taxableAmount = try { rs.getDouble("taxable_amount") } catch (_: Exception) { try { rs.getDouble("taxableAmount") } catch (_: Exception) { 0.0 } },
                        cgstRate = try { rs.getDouble("cgst_rate") } catch (_: Exception) { 0.0 },
                        cgstAmt = try { rs.getDouble("cgst_amt") } catch (_: Exception) { try { rs.getDouble("cgstAmount") } catch (_: Exception) { 0.0 } },
                        sgstRate = try { rs.getDouble("sgst_rate") } catch (_: Exception) { 0.0 },
                        sgstAmt = try { rs.getDouble("sgst_amt") } catch (_: Exception) { try { rs.getDouble("sgstAmount") } catch (_: Exception) { 0.0 } },
                        igstRate = try { rs.getDouble("igst_rate") } catch (_: Exception) { 0.0 },
                        igstAmt = try { rs.getDouble("igst_amt") } catch (_: Exception) { try { rs.getDouble("igstAmount") } catch (_: Exception) { 0.0 } },
                        totalAmt = try { rs.getDouble("total_amt") } catch (_: Exception) { try { rs.getDouble("total") } catch (_: Exception) { 0.0 } }
                    ))
                }
                list
            }
            
            return Pair(header, items)
        }
        return null
    }

    fun getAllInvoices(): List<InvoiceModels.InvoiceHeader> {
        val list = mutableListOf<InvoiceModels.InvoiceHeader>()
        DatabaseManager.connect()?.use { conn ->
            val sql = "SELECT *, rowid as id FROM invoices ORDER BY date DESC, rowid DESC"
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                while (rs.next()) {
                    list.add(extractHeader(rs))
                }
            }
        }
        return list
    }

    private fun extractHeader(rs: java.sql.ResultSet): InvoiceModels.InvoiceHeader {
        return InvoiceModels.InvoiceHeader(
            id = try { rs.getInt("rowid") } catch (_: Exception) { try { rs.getInt("id") } catch (_: Exception) { 0 } },
            invoiceNo = rs.getString("invoiceNo") ?: "",
            invoiceDate = rs.getString("date") ?: "",
            documentType = rs.getString("type") ?: "INVOICE",
            customerId = rs.getInt("customerId"),
            customerName = rs.getString("customerName") ?: "",
            billingAddress = rs.getString("billingAddress") ?: "",
            consigneeAddress = rs.getString("consignee") ?: "", // Legacy mapping fallback
            placeOfSupply = rs.getString("placeOfSupply") ?: "",
            pan = try { rs.getString("pan") ?: "" } catch (_: Exception) { "" },
            stateCode = try { rs.getString("stateCode") ?: "" } catch (_: Exception) { "" },
            gstin = try { rs.getString("gstin") ?: "" } catch (_: Exception) { "" },
            jobId = try { rs.getString("jobId")?.toIntOrNull() ?: 0 } catch (_: Exception) { 0 },
            jobNo = rs.getString("jobNo") ?: "",
            shipper = rs.getString("shipper") ?: "",
            consignee = rs.getString("consignee") ?: "",
            pol = rs.getString("pol") ?: "",
            pod = rs.getString("pod") ?: "",
            vesselFlight = rs.getString("vessel") ?: "",
            etd = rs.getString("etd") ?: "",
            eta = rs.getString("eta") ?: "",
            mblNo = rs.getString("mblNo") ?: "",
            grossWeight = rs.getString("grossWeight") ?: "",
            netWeight = rs.getString("netWeight") ?: "",
            netWeightUnit = try { rs.getString("netWeightUnit") ?: "" } catch (_: Exception) { "" },
            volumeCbm = rs.getString("volumeCbm") ?: "",
            packages = rs.getString("packages") ?: "",
            beNo = rs.getString("beNo") ?: "",
            beDate = rs.getString("beDate") ?: "",
            igmNo = rs.getString("igmNo") ?: "",
            igmDate = rs.getString("igmDate") ?: "",
            itemNo = rs.getString("itemNo") ?: "",
            currency = rs.getString("currency") ?: "INR",
            exchangeRate = rs.getDouble("exchangeRate"),
            refNo = rs.getString("refNo") ?: "",
            otherRefNo = try { rs.getString("otherRefNo") ?: "" } catch (_: Exception) { "" },
            taxableAmount = try { rs.getDouble("taxableAmount") } catch (_: Exception) { 0.0 },
            cgstAmount = try { rs.getDouble("cgstAmount") } catch (_: Exception) { 0.0 },
            sgstAmount = try { rs.getDouble("sgstAmount") } catch (_: Exception) { 0.0 },
            igstAmount = try { rs.getDouble("igstAmount") } catch (_: Exception) { 0.0 },
            grandTotal = rs.getDouble("grandTotal"),
            narration = rs.getString("narration") ?: ""
        )
    }
}

package com.sanship.data

import java.sql.Statement

object InvoiceRepository {

    fun saveInvoice(header: InvoiceModels.InvoiceHeader, items: List<InvoiceModels.InvoiceItem>): Int {
        var invoiceId = 0
        DatabaseManager.connect()?.use { conn ->
            conn.autoCommit = false
            try {
                // 1. Insert Header
                val headerSql = """
                    INSERT INTO invoices (
                        invoice_no, date, type, customer_id, job_id, job_no, bill_to, consignee_preview,
                        shipper, consignee, pol, pod, vessel_flight, etd, eta,
                        mbl_no, gross_weight, net_weight, net_weight_unit, volume_cbm, packages,
                        be_no, be_date, igm_no, igm_date, item_no,
                        exchange_rate, ref_no, other_ref_no, pan, state_code, total_amount, narration
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                conn.prepareStatement(headerSql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setString(1, header.invoiceNo)
                    ps.setString(2, header.invoiceDate)
                    ps.setString(3, header.documentType)
                    ps.setInt(4, header.customerId)
                    ps.setInt(5, header.jobId)
                    ps.setString(6, header.jobNo)
                    ps.setString(7, header.billingAddress)
                    ps.setString(8, header.consigneeAddress)
                    ps.setString(9, header.shipper)
                    ps.setString(10, header.consignee)
                    ps.setString(11, header.pol)
                    ps.setString(12, header.pod)
                    ps.setString(13, header.vesselFlight)
                    ps.setString(14, header.etd)
                    ps.setString(15, header.eta)
                    ps.setString(16, header.mblNo)
                    ps.setString(17, header.grossWeight)
                    ps.setString(18, header.netWeight)
                    ps.setString(19, header.netWeightUnit)
                    ps.setString(20, header.volumeCbm)
                    ps.setString(21, header.packages)
                    ps.setString(22, header.beNo)
                    ps.setString(23, header.beDate)
                    ps.setString(24, header.igmNo)
                    ps.setString(25, header.igmDate)
                    ps.setString(26, header.itemNo)
                    ps.setString(27, header.exchangeRate)
                    ps.setString(28, header.refNo)
                    ps.setString(29, header.otherRefNo)
                    ps.setString(30, header.pan)
                    ps.setString(31, header.stateCode)
                    ps.setDouble(32, header.grandTotal)
                    ps.setString(33, header.narration)
                    ps.executeUpdate()
                    
                    val rs = ps.generatedKeys
                    if (rs.next()) {
                        invoiceId = rs.getInt(1)
                    }
                }
                
                // 2. Insert Items
                val itemSql = """
                    INSERT INTO invoice_items (
                        invoice_id, sr_no, description, hsn_sac, currency, rate, qty, amount, taxable_amount,
                        cgst_rate, cgst_amt, sgst_rate, sgst_amt, igst_rate, igst_amt, total_amt
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                conn.prepareStatement(itemSql).use { ps ->
                    items.forEach { item ->
                        ps.setInt(1, invoiceId)
                        ps.setInt(2, item.srNo)
                        ps.setString(3, item.description)
                        ps.setString(4, item.hsnSac)
                        ps.setString(5, item.currency)
                        ps.setDouble(6, item.rate)
                        ps.setDouble(7, item.qty)
                        ps.setDouble(8, item.amount)
                        ps.setDouble(9, item.taxableAmount)
                        ps.setDouble(10, item.cgstRate)
                        ps.setDouble(11, item.cgstAmt)
                        ps.setDouble(12, item.sgstRate)
                        ps.setDouble(13, item.sgstAmt)
                        ps.setDouble(14, item.igstRate)
                        ps.setDouble(15, item.igstAmt)
                        ps.setDouble(16, item.totalAmt)
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
            // Get Header
            val headerSql = "SELECT * FROM invoices WHERE id = ?"
            val header = conn.prepareStatement(headerSql).use { ps ->
                ps.setInt(1, id)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    InvoiceModels.InvoiceHeader(
                        id = rs.getInt("id"),
                        invoiceNo = rs.getString("invoice_no") ?: "",
                        invoiceDate = rs.getString("date") ?: "",
                        documentType = rs.getString("type") ?: "INVOICE",
                        customerId = rs.getInt("customer_id"),
                        customerName = "",
                        billingAddress = rs.getString("bill_to") ?: "",
                        consigneeAddress = rs.getString("consignee_preview") ?: "",
                        placeOfSupply = "",
                        pan = try { rs.getString("pan") ?: "" } catch (_: Exception) { "" },
                        stateCode = try { rs.getString("state_code") ?: "" } catch (_: Exception) { "" },
                        gstin = "",
                        jobId = rs.getInt("job_id"),
                        jobNo = rs.getString("job_no") ?: "",
                        shipper = rs.getString("shipper") ?: "",
                        consignee = rs.getString("consignee") ?: "",
                        pol = rs.getString("pol") ?: "",
                        pod = rs.getString("pod") ?: "",
                        vesselFlight = rs.getString("vessel_flight") ?: "",
                        etd = rs.getString("etd") ?: "",
                        eta = rs.getString("eta") ?: "",
                        mblNo = rs.getString("mbl_no") ?: "",
                        grossWeight = rs.getString("gross_weight") ?: "",
                        netWeight = rs.getString("net_weight") ?: "",
                        netWeightUnit = try { rs.getString("net_weight_unit") ?: "" } catch (_: Exception) { "" },
                        volumeCbm = rs.getString("volume_cbm") ?: "",
                        packages = rs.getString("packages") ?: "",
                        beNo = rs.getString("be_no") ?: "",
                        beDate = rs.getString("be_date") ?: "",
                        igmNo = rs.getString("igm_no") ?: "",
                        igmDate = rs.getString("igm_date") ?: "",
                        itemNo = rs.getString("item_no") ?: "",
                        exchangeRate = rs.getString("exchange_rate") ?: "",
                        refNo = rs.getString("ref_no") ?: "",
                        otherRefNo = try { rs.getString("other_ref_no") ?: "" } catch (_: Exception) { "" },
                        taxableAmount = 0.0,
                        cgstAmount = 0.0,
                        sgstAmount = 0.0,
                        igstAmount = 0.0,
                        grandTotal = rs.getDouble("total_amount"),
                        narration = rs.getString("narration") ?: ""
                    )
                } else null
            } ?: return null
            
            // Get Items
            val itemsSql = "SELECT * FROM invoice_items WHERE invoice_id = ? ORDER BY sr_no"
            val items = conn.prepareStatement(itemsSql).use { ps ->
                ps.setInt(1, id)
                val rs = ps.executeQuery()
                val list = mutableListOf<InvoiceModels.InvoiceItem>()
                while (rs.next()) {
                    list.add(InvoiceModels.InvoiceItem(
                        id = rs.getInt("id"),
                        invoiceId = rs.getInt("invoice_id"),
                        srNo = rs.getInt("sr_no"),
                        description = rs.getString("description") ?: "",
                        hsnSac = rs.getString("hsn_sac") ?: "",
                        currency = rs.getString("currency") ?: "INR",
                        rate = rs.getDouble("rate"),
                        qty = rs.getDouble("qty"),
                        amount = rs.getDouble("amount"),
                        taxableAmount = rs.getDouble("taxable_amount"),
                        cgstRate = rs.getDouble("cgst_rate"),
                        cgstAmt = rs.getDouble("cgst_amt"),
                        sgstRate = rs.getDouble("sgst_rate"),
                        sgstAmt = rs.getDouble("sgst_amt"),
                        igstRate = rs.getDouble("igst_rate"),
                        igstAmt = rs.getDouble("igst_amt"),
                        totalAmt = rs.getDouble("total_amt")
                    ))
                }
                list
            }
            
            return Pair(header, items)
        }
        return null
    }
}

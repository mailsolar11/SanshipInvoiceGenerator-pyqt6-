package com.sanship.data

import java.time.LocalDate

// ─────────────────────────── Data Models ────────────────────────────────────

data class CreditNoteItem(
    val id: Int = 0,
    val srNo: Int = 1,
    val description: String = "",
    val hsnSac: String = "",
    val currency: String = "INR",
    val qty: Double = 1.0,
    val rate: Double = 0.0,
    val amount: Double = 0.0,
    val taxableAmount: Double = 0.0,
    val cgstRate: Double = 0.0,
    val cgstAmt: Double = 0.0,
    val sgstRate: Double = 0.0,
    val sgstAmt: Double = 0.0,
    val igstRate: Double = 0.0,
    val igstAmt: Double = 0.0,
    val totalAmt: Double = 0.0
)

data class CreditNoteHeader(
    val id: Int = 0,
    val creditNoteNo: String = "",
    val date: String = LocalDate.now().toString(),
    // Mandatory reference
    val originalInvoiceNo: String = "",
    val originalInvoiceDate: String = "",
    val reason: String = "",
    // Customer
    val customerId: Int? = null,
    val customerName: String = "",
    val billingAddress: String = "",
    val gstin: String = "",
    val placeOfSupply: String = "",
    val stateCode: String = "",
    // Shipment reference
    val jobId: Int? = null,
    val jobNo: String = "",
    val shipper: String = "",
    val consignee: String = "",
    val pol: String = "",
    val pod: String = "",
    val vesselFlight: String = "",
    val mblNo: String = "",
    val hblNo: String = "",
    val containerNos: String = "",
    // Totals
    val taxableAmount: Double = 0.0,
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val igstAmount: Double = 0.0,
    val grandTotal: Double = 0.0
)

// ─────────────────────────── Repository ─────────────────────────────────────

object CreditNoteRepository {

    fun getNextCreditNoteNo(): String {
        val year = LocalDate.now().year
        DatabaseManager.connect()?.use { conn ->
            val rs = conn.prepareStatement(
                "SELECT COUNT(*) as cnt FROM credit_notes WHERE credit_note_no LIKE ?"
            ).apply { setString(1, "CN/$year/%") }.executeQuery()
            if (rs.next()) {
                val count = rs.getInt("cnt") + 1
                return "CN/$year/${count.toString().padStart(3, '0')}"
            }
        }
        return "CN/$year/001"
    }

    fun saveCreditNote(header: CreditNoteHeader, items: List<CreditNoteItem>): Int {
        var generatedId = -1
        DatabaseManager.connect()?.use { conn ->
            conn.autoCommit = false
            try {
                val sql = """
                    INSERT OR REPLACE INTO credit_notes
                    (credit_note_no, date, original_invoice_no, original_invoice_date, reason,
                     customer_id, customer_name, billing_address, gstin, place_of_supply, state_code,
                     job_id, job_no, shipper, consignee, pol, pod, vessel_flight, mbl_no, hbl_no,
                     container_nos, taxable_amount, cgst_amount, sgst_amount, igst_amount, grand_total)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """.trimIndent()
                conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setString(1, header.creditNoteNo)
                    ps.setString(2, header.date)
                    ps.setString(3, header.originalInvoiceNo)
                    ps.setString(4, header.originalInvoiceDate)
                    ps.setString(5, header.reason)
                    if (header.customerId != null) ps.setInt(6, header.customerId) else ps.setNull(6, java.sql.Types.INTEGER)
                    ps.setString(7, header.customerName)
                    ps.setString(8, header.billingAddress)
                    ps.setString(9, header.gstin)
                    ps.setString(10, header.placeOfSupply)
                    ps.setString(11, header.stateCode)
                    if (header.jobId != null) ps.setInt(12, header.jobId) else ps.setNull(12, java.sql.Types.INTEGER)
                    ps.setString(13, header.jobNo)
                    ps.setString(14, header.shipper)
                    ps.setString(15, header.consignee)
                    ps.setString(16, header.pol)
                    ps.setString(17, header.pod)
                    ps.setString(18, header.vesselFlight)
                    ps.setString(19, header.mblNo)
                    ps.setString(20, header.hblNo)
                    ps.setString(21, header.containerNos)
                    ps.setDouble(22, header.taxableAmount)
                    ps.setDouble(23, header.cgstAmount)
                    ps.setDouble(24, header.sgstAmount)
                    ps.setDouble(25, header.igstAmount)
                    ps.setDouble(26, header.grandTotal)
                    ps.executeUpdate()
                    val rs = ps.generatedKeys
                    if (rs.next()) generatedId = rs.getInt(1)
                }

                // Delete old items and insert new ones
                conn.prepareStatement("DELETE FROM credit_note_items WHERE credit_note_id = ?")
                    .apply { setInt(1, generatedId) }.executeUpdate()

                items.forEachIndexed { idx, item ->
                    conn.prepareStatement("""
                        INSERT INTO credit_note_items
                        (credit_note_id, sr_no, description, hsn_sac, currency, qty, rate, amount,
                         taxable_amount, cgst_rate, cgst_amt, sgst_rate, sgst_amt, igst_rate, igst_amt, total_amt)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """.trimIndent()).use { ps ->
                        ps.setInt(1, generatedId)
                        ps.setInt(2, idx + 1)
                        ps.setString(3, item.description)
                        ps.setString(4, item.hsnSac)
                        ps.setString(5, item.currency)
                        ps.setDouble(6, item.qty)
                        ps.setDouble(7, item.rate)
                        ps.setDouble(8, item.amount)
                        ps.setDouble(9, item.taxableAmount)
                        ps.setDouble(10, item.cgstRate)
                        ps.setDouble(11, item.cgstAmt)
                        ps.setDouble(12, item.sgstRate)
                        ps.setDouble(13, item.sgstAmt)
                        ps.setDouble(14, item.igstRate)
                        ps.setDouble(15, item.igstAmt)
                        ps.setDouble(16, item.totalAmt)
                        ps.executeUpdate()
                    }
                }

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
        return generatedId
    }

    fun getCreditNotes(): List<CreditNoteHeader> {
        val list = mutableListOf<CreditNoteHeader>()
        DatabaseManager.connect()?.use { conn ->
            val rs = conn.prepareStatement("SELECT * FROM credit_notes ORDER BY date DESC").executeQuery()
            while (rs.next()) list.add(rsToHeader(rs))
        }
        return list
    }

    fun getCreditNoteWithItems(id: Int): Pair<CreditNoteHeader, List<CreditNoteItem>>? {
        var header: CreditNoteHeader? = null
        val items = mutableListOf<CreditNoteItem>()
        DatabaseManager.connect()?.use { conn ->
            val rsH = conn.prepareStatement("SELECT * FROM credit_notes WHERE id = ?")
                .apply { setInt(1, id) }.executeQuery()
            if (rsH.next()) header = rsToHeader(rsH)

            val rsI = conn.prepareStatement(
                "SELECT * FROM credit_note_items WHERE credit_note_id = ? ORDER BY sr_no"
            ).apply { setInt(1, id) }.executeQuery()
            while (rsI.next()) {
                items.add(CreditNoteItem(
                    id = rsI.getInt("id"),
                    srNo = rsI.getInt("sr_no"),
                    description = rsI.getString("description") ?: "",
                    hsnSac = rsI.getString("hsn_sac") ?: "",
                    currency = rsI.getString("currency") ?: "INR",
                    qty = rsI.getDouble("qty"),
                    rate = rsI.getDouble("rate"),
                    amount = rsI.getDouble("amount"),
                    taxableAmount = rsI.getDouble("taxable_amount"),
                    cgstRate = rsI.getDouble("cgst_rate"),
                    cgstAmt = rsI.getDouble("cgst_amt"),
                    sgstRate = rsI.getDouble("sgst_rate"),
                    sgstAmt = rsI.getDouble("sgst_amt"),
                    igstRate = rsI.getDouble("igst_rate"),
                    igstAmt = rsI.getDouble("igst_amt"),
                    totalAmt = rsI.getDouble("total_amt")
                ))
            }
        }
        return header?.let { Pair(it, items) }
    }

    private fun rsToHeader(rs: java.sql.ResultSet) = CreditNoteHeader(
        id = rs.getInt("id"),
        creditNoteNo = rs.getString("credit_note_no") ?: "",
        date = rs.getString("date") ?: "",
        originalInvoiceNo = rs.getString("original_invoice_no") ?: "",
        originalInvoiceDate = rs.getString("original_invoice_date") ?: "",
        reason = rs.getString("reason") ?: "",
        customerId = rs.getObject("customer_id") as? Int,
        customerName = rs.getString("customer_name") ?: "",
        billingAddress = rs.getString("billing_address") ?: "",
        gstin = rs.getString("gstin") ?: "",
        placeOfSupply = rs.getString("place_of_supply") ?: "",
        stateCode = rs.getString("state_code") ?: "",
        jobId = rs.getObject("job_id") as? Int,
        jobNo = rs.getString("job_no") ?: "",
        shipper = rs.getString("shipper") ?: "",
        consignee = rs.getString("consignee") ?: "",
        pol = rs.getString("pol") ?: "",
        pod = rs.getString("pod") ?: "",
        vesselFlight = rs.getString("vessel_flight") ?: "",
        mblNo = rs.getString("mbl_no") ?: "",
        hblNo = rs.getString("hbl_no") ?: "",
        containerNos = rs.getString("container_nos") ?: "",
        taxableAmount = rs.getDouble("taxable_amount"),
        cgstAmount = rs.getDouble("cgst_amount"),
        sgstAmount = rs.getDouble("sgst_amount"),
        igstAmount = rs.getDouble("igst_amount"),
        grandTotal = rs.getDouble("grand_total")
    )
}

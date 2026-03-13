package com.sanship.data

import java.time.LocalDate

// ─────────────────────────── Data Models ────────────────────────────────────

data class QuotationItem(
    val id: Int = 0,
    val srNo: Int = 1,
    val description: String = "",
    val currency: String = "INR",
    val qty: Double = 1.0,
    val unit: String = "Lumpsum",
    val rate: Double = 0.0,
    val amount: Double = 0.0,
    val remarks: String = ""
)

data class QuotationHeader(
    val id: Int = 0,
    val quotationNo: String = "",
    val date: String = LocalDate.now().toString(),
    val validUntil: String = LocalDate.now().plusDays(15).toString(),
    val customerId: Int? = null,
    val customerName: String = "",
    val billingAddress: String = "",
    val jobId: Int? = null,
    val jobNo: String = "",
    // Shipment / Trade Lane
    val shipper: String = "",
    val consignee: String = "",
    val pol: String = "",
    val pod: String = "",
    val mode: String = "Sea",
    val containerType: String = "FCL",
    val vesselFlight: String = "",
    val etd: String = "",
    val eta: String = "",
    // Footer
    val terms: String = "Subject to space and equipment availability.\nRates are valid till the date mentioned above.",
    val notes: String = "",
    val totalAmount: Double = 0.0,
    val status: String = "DRAFT"
)

// ─────────────────────────── Repository ─────────────────────────────────────

object QuotationRepository {

    fun getNextQuotationNo(): String {
        val year = LocalDate.now().year
        DatabaseManager.connect()?.use { conn ->
            val rs = conn.prepareStatement(
                "SELECT COUNT(*) as cnt FROM quotations WHERE quotation_no LIKE ?"
            ).apply { setString(1, "QT/$year/%") }.executeQuery()
            if (rs.next()) {
                val count = rs.getInt("cnt") + 1
                return "QT/$year/${count.toString().padStart(3, '0')}"
            }
        }
        return "QT/$year/001"
    }

    fun saveQuotation(header: QuotationHeader, items: List<QuotationItem>): Int {
        var generatedId = -1
        DatabaseManager.connect()?.use { conn ->
            conn.autoCommit = false
            try {
                // 1. Insert header
                val sql = """
                    INSERT OR REPLACE INTO quotations
                    (quotation_no, date, valid_until, customer_id, customer_name, billing_address,
                     job_id, job_no, shipper, consignee, pol, pod, mode, container_type, vessel_flight,
                     etd, eta, terms, notes, total_amount, status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """.trimIndent()
                conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setString(1, header.quotationNo)
                    ps.setString(2, header.date)
                    ps.setString(3, header.validUntil)
                    if (header.customerId != null) ps.setInt(4, header.customerId) else ps.setNull(4, java.sql.Types.INTEGER)
                    ps.setString(5, header.customerName)
                    ps.setString(6, header.billingAddress)
                    if (header.jobId != null) ps.setInt(7, header.jobId) else ps.setNull(7, java.sql.Types.INTEGER)
                    ps.setString(8, header.jobNo)
                    ps.setString(9, header.shipper)
                    ps.setString(10, header.consignee)
                    ps.setString(11, header.pol)
                    ps.setString(12, header.pod)
                    ps.setString(13, header.mode)
                    ps.setString(14, header.containerType)
                    ps.setString(15, header.vesselFlight)
                    ps.setString(16, header.etd)
                    ps.setString(17, header.eta)
                    ps.setString(18, header.terms)
                    ps.setString(19, header.notes)
                    ps.setDouble(20, header.totalAmount)
                    ps.setString(21, header.status)
                    ps.executeUpdate()
                    val rs = ps.generatedKeys
                    if (rs.next()) generatedId = rs.getInt(1)
                }

                // 2. Delete old items
                conn.prepareStatement("DELETE FROM quotation_items WHERE quotation_id = ?")
                    .apply { setInt(1, generatedId) }.executeUpdate()

                // 3. Insert items
                items.forEachIndexed { idx, item ->
                    conn.prepareStatement("""
                        INSERT INTO quotation_items
                        (quotation_id, sr_no, description, currency, qty, unit, rate, amount, remarks)
                        VALUES (?,?,?,?,?,?,?,?,?)
                    """.trimIndent()).use { ps ->
                        ps.setInt(1, generatedId)
                        ps.setInt(2, idx + 1)
                        ps.setString(3, item.description)
                        ps.setString(4, item.currency)
                        ps.setDouble(5, item.qty)
                        ps.setString(6, item.unit)
                        ps.setDouble(7, item.rate)
                        ps.setDouble(8, item.amount)
                        ps.setString(9, item.remarks)
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

    fun getQuotations(): List<QuotationHeader> {
        val list = mutableListOf<QuotationHeader>()
        DatabaseManager.connect()?.use { conn ->
            val rs = conn.prepareStatement(
                "SELECT * FROM quotations ORDER BY date DESC"
            ).executeQuery()
            while (rs.next()) {
                list.add(rsToHeader(rs))
            }
        }
        return list
    }

    fun getQuotationWithItems(id: Int): Pair<QuotationHeader, List<QuotationItem>>? {
        var header: QuotationHeader? = null
        val items = mutableListOf<QuotationItem>()
        DatabaseManager.connect()?.use { conn ->
            val rsH = conn.prepareStatement("SELECT * FROM quotations WHERE id = ?")
                .apply { setInt(1, id) }.executeQuery()
            if (rsH.next()) header = rsToHeader(rsH)

            val rsI = conn.prepareStatement(
                "SELECT * FROM quotation_items WHERE quotation_id = ? ORDER BY sr_no"
            ).apply { setInt(1, id) }.executeQuery()
            while (rsI.next()) {
                items.add(QuotationItem(
                    id = rsI.getInt("id"),
                    srNo = rsI.getInt("sr_no"),
                    description = rsI.getString("description") ?: "",
                    currency = rsI.getString("currency") ?: "INR",
                    qty = rsI.getDouble("qty"),
                    unit = rsI.getString("unit") ?: "Lumpsum",
                    rate = rsI.getDouble("rate"),
                    amount = rsI.getDouble("amount"),
                    remarks = rsI.getString("remarks") ?: ""
                ))
            }
        }
        return header?.let { Pair(it, items) }
    }

    private fun rsToHeader(rs: java.sql.ResultSet) = QuotationHeader(
        id = rs.getInt("id"),
        quotationNo = rs.getString("quotation_no") ?: "",
        date = rs.getString("date") ?: "",
        validUntil = rs.getString("valid_until") ?: "",
        customerId = rs.getObject("customer_id") as? Int,
        customerName = rs.getString("customer_name") ?: "",
        billingAddress = rs.getString("billing_address") ?: "",
        jobId = rs.getObject("job_id") as? Int,
        jobNo = rs.getString("job_no") ?: "",
        shipper = rs.getString("shipper") ?: "",
        consignee = rs.getString("consignee") ?: "",
        pol = rs.getString("pol") ?: "",
        pod = rs.getString("pod") ?: "",
        mode = rs.getString("mode") ?: "Sea",
        containerType = rs.getString("container_type") ?: "FCL",
        vesselFlight = rs.getString("vessel_flight") ?: "",
        etd = rs.getString("etd") ?: "",
        eta = rs.getString("eta") ?: "",
        terms = rs.getString("terms") ?: "",
        notes = rs.getString("notes") ?: "",
        totalAmount = rs.getDouble("total_amount"),
        status = rs.getString("status") ?: "DRAFT"
    )
}

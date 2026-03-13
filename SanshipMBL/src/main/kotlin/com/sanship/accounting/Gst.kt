package com.sanship.accounting

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * GST computation engine for SANSHIP ERP
 *
 * Responsibilities:
 * - Determine GST type (CGST/SGST vs IGST)
 * - Aggregate GST amounts from invoice line items
 * - Produce ledger-ready tax breakup
 * - No DB writes
 * - No UI coupling
 *
 * EXACT REPLICA of Python src/accounting/gst.py
 */
object Gst {
    
    // =====================================================
    // HELPERS
    // =====================================================
    // =====================================================
    // HELPERS
    // =====================================================
    
    val stateCodeMap = mapOf(
        "Jammu & Kashmir" to "01",
        "Himachal Pradesh" to "02",
        "Punjab" to "03",
        "Chandigarh" to "04",
        "Uttarakhand" to "05",
        "Haryana" to "06",
        "Delhi" to "07",
        "Rajasthan" to "08",
        "Uttar Pradesh" to "09",
        "Bihar" to "10",
        "Sikkim" to "11",
        "Arunachal Pradesh" to "12",
        "Nagaland" to "13",
        "Manipur" to "14",
        "Mizoram" to "15",
        "Tripura" to "16",
        "Meghalaya" to "17",
        "Assam" to "18",
        "West Bengal" to "19",
        "Jharkhand" to "20",
        "Odisha" to "21",
        "Chhattisgarh" to "22",
        "Madhya Pradesh" to "23",
        "Gujarat" to "24",
        "Dadra & Nagar Haveli and Daman & Diu" to "26",
        "Maharashtra" to "27",
        "Karnataka" to "29",
        "Goa" to "30",
        "Lakshadweep" to "31",
        "Kerala" to "32",
        "Tamil Nadu" to "33",
        "Puducherry" to "34",
        "Andaman & Nicobar Islands" to "35",
        "Telangana" to "36",
        "Andhra Pradesh" to "37",
        "Ladakh" to "38",
        "Other Territory" to "97",
        "Centre Jurisdiction" to "99"
    )
    
    fun getStateCode(stateName: String?): String? {
        if (stateName.isNullOrBlank()) return null
        // direct code check
        if (stateName.length == 2 && stateName.all { it.isDigit() }) return stateName
        // name lookup (case insensitive)
        return stateCodeMap.entries.find { it.key.equals(stateName, ignoreCase = true) }?.value
    }
    
    private fun toBigDecimal(value: Any?): BigDecimal {
        return try {
            BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid numeric value: $value")
        }
    }
    
    // =====================================================
    // GST CORE
    // =====================================================
    data class GstResult(
        val taxableTotal: Double,
        val cgst: GstComponent? = null,
        val sgst: GstComponent? = null,
        val igst: GstComponent? = null
    )
    
    data class GstComponent(
        val rate: Double,
        val amount: Double
    )
    
    fun computeGst(
        items: List<Map<String, Any?>>,
        supplierStateCode: String?,
        customerStateCode: String?
    ): GstResult {
        /**
         * Computes GST breakup for given invoice items.
         *
         * Args:
         *     items: list of invoice item maps
         *     supplierStateCode: e.g. '27'
         *     customerStateCode: e.g. '27'
         *
         * Returns:
         *     GstResult with taxable_total and GST components
         */
        
        if (items.isEmpty()) {
            throw IllegalArgumentException("No items provided for GST computation")
        }
        
        val sameState = supplierStateCode == customerStateCode
        
        var taxableTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        var cgstAmt = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        var sgstAmt = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        var igstAmt = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        
        var cgstRate: BigDecimal? = null
        var sgstRate: BigDecimal? = null
        var igstRate: BigDecimal? = null
        
        for (item in items) {
            val taxable = toBigDecimal(item["taxable_amount"] ?: 0)
            taxableTotal += taxable
            
            if (sameState) {
                cgstAmt += toBigDecimal(item["cgst_amt"] ?: 0)
                sgstAmt += toBigDecimal(item["sgst_amt"] ?: 0)
                
                // capture rate once (audit reference)
                if (cgstRate == null) {
                    cgstRate = toBigDecimal(item["cgst_rate"] ?: 0)
                }
                if (sgstRate == null) {
                    sgstRate = toBigDecimal(item["sgst_rate"] ?: 0)
                }
            } else {
                igstAmt += toBigDecimal(item["igst_amt"] ?: 0)
                if (igstRate == null) {
                    igstRate = toBigDecimal(item["igst_rate"] ?: 0)
                }
            }
        }
        
        return GstResult(
            taxableTotal = taxableTotal.toDouble(),
            cgst = if (sameState && cgstAmt > BigDecimal.ZERO) {
                GstComponent(
                    rate = (cgstRate ?: BigDecimal.ZERO).toDouble(),
                    amount = cgstAmt.toDouble()
                )
            } else null,
            sgst = if (sameState && sgstAmt > BigDecimal.ZERO) {
                GstComponent(
                    rate = (sgstRate ?: BigDecimal.ZERO).toDouble(),
                    amount = sgstAmt.toDouble()
                )
            } else null,
            igst = if (!sameState && igstAmt > BigDecimal.ZERO) {
                GstComponent(
                    rate = (igstRate ?: BigDecimal.ZERO).toDouble(),
                    amount = igstAmt.toDouble()
                )
            } else null
        )
    }
}

package com.sanship.data

data class PurchaseHeader(
    val id: Int = 0,
    val purchaseNo: String,
    val date: String,
    val vendorId: Int,
    val vendorName: String,
    val vendorGstin: String,
    val vendorAddress: String,
    val placeOfSupply: String,
    val reverseCharge: Boolean = false,
    val jobId: Int = 0,
    val jobNo: String = "",
    val currency: String = "INR",
    val exchangeRate: Double = 1.0,
    val taxableAmount: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val igstAmount: Double,
    val grandTotal: Double,
    val narration: String = ""
)

data class PurchaseItem(
    val id: Int = 0,
    val purchaseId: Int = 0,
    val srNo: Int,
    val description: String,
    val hsnSac: String,
    val qty: Double,
    val rate: Double,
    val amount: Double,
    val taxableAmount: Double,
    val cgstRate: Double = 0.0,
    val cgstAmount: Double = 0.0,
    val sgstRate: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val igstRate: Double = 0.0,
    val igstAmount: Double = 0.0,
    val totalAmount: Double,
    val currency: String = "INR",
    val exchangeRate: Double = 1.0
)

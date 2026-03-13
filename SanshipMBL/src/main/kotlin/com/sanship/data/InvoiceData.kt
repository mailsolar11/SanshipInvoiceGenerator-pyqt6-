package com.sanship.data

data class InvoiceData(
    val invoiceNo: String = "",
    val date: String = "",
    val documentType: String = "INVOICE",
    val customerName: String = "",
    val billingAddress: String = "",
    val gstin: String = "",
    val placeOfSupply: String = "",
    val reverseCharge: Boolean = false,
    
    // Shipping / Job Details
    val jobId: String = "",
    val shipper: String = "",
    val consignee: String = "",
    val vessel: String = "",
    val pol: String = "",
    val pod: String = "",
    
    // Totals
    val taxableAmount: Double = 0.0,
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val igstAmount: Double = 0.0,
    val grandTotal: Double = 0.0,
    
    // Items (using InvoiceItem from InvoiceModels.kt)
    val items: List<InvoiceModels.InvoiceItem> = emptyList()
)

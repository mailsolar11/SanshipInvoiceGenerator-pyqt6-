package com.sanship.data

object InvoiceModels {
    data class InvoiceHeader(
        val id: Int = 0,
        val invoiceNo: String = "",
        val invoiceDate: String = "",
        val documentType: String = "INVOICE", // INVOICE or DEBIT_NOTE
        
        // Customer & Billing
        val customerId: Int = 0,
        val customerName: String = "",
        val billingAddress: String = "",
        val consigneeAddress: String = "",
        val placeOfSupply: String = "",
        val stateCode: String = "",       // e.g. "27" for Maharashtra
        val pan: String = "",             // PAN/IT No of customer
        val gstin: String = "",
        val reverseCharge: Boolean = false,
        val category: String = "",        // "Export", "Import", "Local"
        
        // Job Reference
        val jobId: Int = 0,
        val jobNo: String = "",
        
        // Shipment Details
        val shipper: String = "",
        val consignee: String = "",
        val pol: String = "",
        val pod: String = "",
        val vesselFlight: String = "",
        val etd: String = "",
        val eta: String = "",
        
        // Consignment Details
        val mblNo: String = "",
        val hblNo: String = "",
        val containerNos: String = "",     // Container number(s)
        val shipperInvoiceNo: String = "", // Shipper's own invoice no
        val shipperInvoiceDate: String = "", // Shipper's invoice date
        val grossWeight: String = "",
        val netWeight: String = "",
        val netWeightUnit: String = "",   // e.g. "KGS", "MT" - the "Add text" suffix in image
        val volumeCbm: String = "",
        val packages: String = "",
        val beNo: String = "",
        val beDate: String = "",
        val igmNo: String = "",
        val igmDate: String = "",
        val itemNo: String = "",
        val currency: String = "INR",
        val exchangeRate: Double = 1.0,
        val refNo: String = "",
        val otherRefNo: String = "",      // "other Ref no (Optional)" from image
        
        // e-Invoice Info
        val irn: String = "",
        val ackNo: String = "",
        val ackDate: String = "",
        val signedQr: String = "",
        val signedInvoice: String = "",

        // Totals
        val taxableAmount: Double = 0.0,
        val cgstAmount: Double = 0.0,
        val sgstAmount: Double = 0.0,
        val igstAmount: Double = 0.0,
        val grandTotal: Double = 0.0,
        
        val narration: String = "",
        val createdAt: String = ""
    )


    data class InvoiceItem(
        val id: Int = 0,
        val invoiceId: Int = 0,
        val srNo: Int = 1,
        val description: String = "",
        val hsnSac: String = "",
        val currency: String = "INR",
        val exchangeRate: Double = 1.0,
        val rate: Double = 0.0,
        val qty: Double = 0.0,
        val amount: Double = 0.0,
        val taxableAmount: Double = 0.0,
        val cgstRate: Double = 0.0,
        val cgstAmt: Double = 0.0,
        val sgstRate: Double = 0.0,
        val sgstAmt: Double = 0.0,
        val igstRate: Double = 0.0,
        val igstAmt: Double = 0.0,
        val totalAmt: Double = 0.0,
        val containerNumber: String? = null
    )
}

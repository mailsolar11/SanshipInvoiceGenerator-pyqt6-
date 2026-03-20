package com.sanship.models

/**
 * Job/Shipment Model
 * Exact replica of Python jobs table
 */
data class Job(
    val id: Int = 0,
    val jobNo: String = "",
    val customerId: Int = 0,
    val customerName: String = "",
    
    // Shipment Details
    val shipper: String = "",
    val consignee: String = "",
    val pol: String = "",  // Port of Loading
    val pod: String = "",  // Port of Discharge
    val vesselFlight: String = "",
    val etd: String = "",  // Expected Time of Departure
    val eta: String = "",  // Expected Time of Arrival
    
    // Consignment Details
    val mblNo: String = "",
    val grossWeight: String = "",
    val netWeight: String = "",
    val volumeCbm: String = "",
    val packages: String = "",
    val exchangeRate: Double = 1.0,
    val refNo: String = "",
    
    // Status
    val status: String = "OPEN",  // OPEN, CLOSED
    val createdAt: String = ""
)

/**
 * Charge Master Model
 * For charge dropdown with HSN/GST auto-fill
 */
data class ChargeMaster(
    val id: Int = 0,
    val chargeName: String = "",
    val hsnSac: String = "",
    val currency: String = "INR",
    val cgstRate: Double = 0.0,
    val sgstRate: Double = 0.0,
    val igstRate: Double = 0.0
)

/**
 * Customer Address Model
 */
data class CustomerAddress(
    val id: Int = 0,
    val customerId: Int = 0,
    val label: String = "",
    val address: String = "",
    val state: String = "",
    val pincode: String = "",
    val country: String = "India",
    val isDefault: Boolean = false
)

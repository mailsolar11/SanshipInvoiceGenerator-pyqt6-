package com.sanship.data

data class Job(
    val id: Int = 0,
    val jobNo: String = "",
    val customerId: Int? = null,
    
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
    val grossWeight: String = "",
    val netWeight: String = "",
    val volumeCbm: String = "",
    val packages: String = "",
    val exchangeRate: String = "",
    val refNo: String = "",
    
    val status: String = "OPEN",
    val createdAt: String = ""
)

package com.sanship.models

data class HBLInstruction(
    val id: Int = 0,
    val jobId: Int = 0,
    val hblNo: String = "",
    val mblNo: String = "",
    val shipperText: String = "",
    val consigneeText: String = "",
    val notifyPartyText: String = "",
    val deliveryAgentText: String = "",
    val marksAndNumbers: String = "",
    val descriptionOfGoods: String = "",
    val blType: String = "ORIGINAL", // ORIGINAL, TELEX, SEAWAY
    val freightTerms: String = "PREPAID", // PREPAID, COLLECT
    val placeOfReceipt: String = "",
    val portOfLoading: String = "",
    val portOfDischarge: String = "",
    val placeOfDelivery: String = "",
    val shippedOnBoardDate: String = "",
    val noOfOriginals: Int = 3,
    val createdAt: String = ""
)

data class Container(
    val id: Int = 0,
    val jobId: Int = 0,
    val containerNo: String = "",
    val sealNo: String = "",
    val containerType: String = "40HC", // 20GP, 40GP, 40HC, etc.
    val packages: Int = 0,
    val packageType: String = "PACKAGES",
    val grossWeight: Double = 0.0,
    val netWeight: Double = 0.0,
    val volumeCbm: Double = 0.0,
    val vgmWeight: Double = 0.0,
    val description: String = "" // Specific cargo description
)

// Combined model for PDF generation or UI display
data class BLData(
    val instruction: HBLInstruction,
    val containers: List<Container>,
    val totalPackages: Int,
    val totalGrossWeight: Double,
    val totalVolume: Double
)

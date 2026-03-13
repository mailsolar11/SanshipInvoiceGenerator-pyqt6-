package com.sanship.data

data class CargoItem(
    val containerNo: String = "",
    // marks moved to MblData
    val grossWeight: String = "",
    val measurement: String = "",

    // Annexure Specific Fields
    val pkgCount: String = "",
    val netWeight: String = "",
    val agentSeal: String = "",   // Shipping Line Seal (Annexure)
    val customsSeal: String = "", // Self Sealing Seal (Annexure)
    val sbNumber: String = "",
    val sbDate: String = ""
)

data class MblData(
    // Header
    val consignor: String = "",
    val consignee: String = "",
    val notifyAddress: String = "",
    val mtdNumber: String = "",
    val refNumber: String = "",

    // Middle
    val preCarriage: String = "",
    val placeReceipt: String = "",
    val deliveryAgent: String = "",
    val vessel: String = "",
    val voyNumber: String = "",
    val portLoading: String = "",
    val portDischarge: String = "",
    val placeDelivery: String = "",
    val mode: String = "",
    val route: String = "",

    // Page 1 Specific Seals
    val mainCustomsSeal: String = "",
    val mainAgentSeal: String = "",

    // CARGO
    val marksNumbers: String = "", // NEW: Entered once, applies to whole BL
    val goodsDescription: String = "",
    val cargoItems: List<CargoItem> = listOf(CargoItem()),

    // Footer
    val freightAmount: String = "",
    val freightPayableAt: String = "",
    val originalMtds: String = "",
    val placeDateIssue: String = "",
    val otherParticulars: String = ""
) {
    /**
     * Creates a new draft based on this BL.
     */
    fun createCopyForNewEntry(): MblData {
        return this.copy(
            // --- FIELDS TO CLEAR (Transaction Specifics) ---
            mtdNumber = "",         // Clear BL Number
            refNumber = "",         // Clear Ref Number
            placeDateIssue = "",    // Reset Date

            // Clear Vessel & Voyage
            vessel = "",
            voyNumber = "",
            preCarriage = "",

            // Clear Seals
            mainCustomsSeal = "",
            mainAgentSeal = "",

            // Clear Cargo
            cargoItems = listOf(CargoItem()), // Reset to single blank row
            goodsDescription = "",
            marksNumbers = "", // Clear Marks

            // Clear Financials & specific notes
            freightAmount = "",
            originalMtds = "",
            otherParticulars = ""
        )
    }
}
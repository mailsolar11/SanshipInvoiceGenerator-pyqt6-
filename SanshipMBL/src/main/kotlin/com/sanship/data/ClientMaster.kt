package com.sanship.data

data class ClientMaster(
    val id: Int = 0,
    val shortName: String = "",
    val fullName: String = "",
    val fullAddress: String = "",
    val gstin: String = "",
    val stateCode: String = "",
    val email: String = ""
) {
    val searchLabel: String
        get() = "$shortName - $fullName"
}
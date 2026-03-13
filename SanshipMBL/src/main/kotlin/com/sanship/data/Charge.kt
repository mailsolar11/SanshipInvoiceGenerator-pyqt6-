package com.sanship.data

data class Charge(
    val id: Int = 0,
    val chargeName: String = "",
    val hsnSac: String = "",
    val currency: String = "INR",
    val cgstRate: Double = 0.0,
    val sgstRate: Double = 0.0,
    val igstRate: Double = 0.0,
    val defaultRate: Double = 0.0,
    val description: String = ""
)

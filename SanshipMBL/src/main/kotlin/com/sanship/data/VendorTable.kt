package com.sanship.data

import org.jetbrains.exposed.sql.Table

object VendorTable : Table("vendor_master") {
    val id = integer("id").autoIncrement()
    val shortName = varchar("shortName", 100)
    val fullName = varchar("fullName", 200)
    val fullAddress = text("fullAddress")
    val gstin = varchar("gstin", 15).nullable()
    val stateCode = varchar("stateCode", 50).nullable()
    val email = varchar("email", 100).nullable()
    val type = varchar("type", 50).nullable() // e.g. Shipping Line, CHA, Transporter, Agent

    override val primaryKey = PrimaryKey(id)
}

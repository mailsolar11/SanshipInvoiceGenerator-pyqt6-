package com.sanship.data

import org.jetbrains.exposed.sql.Table

object ClientTable : Table("client_master") {
    val id = integer("id").autoIncrement()
    val shortName = varchar("shortName", 100)
    val fullName = varchar("fullName", 200)
    val fullAddress = text("fullAddress")
    val gstin = varchar("gstin", 15).nullable()
    val stateCode = varchar("stateCode", 50).nullable()
    val email = varchar("email", 100).nullable()

    override val primaryKey = PrimaryKey(id)
}
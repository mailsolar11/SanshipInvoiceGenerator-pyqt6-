package com.sanship.data

import org.jetbrains.exposed.sql.Table

object JobsTable : Table("jobs") {
    val id = integer("id").autoIncrement()
    val jobNo = varchar("job_no", 50).uniqueIndex()
    val customerId = integer("customer_id").nullable()
    val shipper = varchar("shipper", 200).nullable()
    val consignee = varchar("consignee", 200).nullable()
    val pol = varchar("pol", 100).nullable()
    val pod = varchar("pod", 100).nullable()
    val vesselFlight = varchar("vessel_flight", 100).nullable()
    val etd = varchar("etd", 50).nullable()
    val eta = varchar("eta", 50).nullable()
    val mblNo = varchar("mbl_no", 50).nullable()
    val grossWeight = varchar("gross_weight", 50).nullable()
    val netWeight = varchar("net_weight", 50).nullable()
    val volumeCbm = varchar("volume_cbm", 50).nullable()
    val packages = varchar("packages", 50).nullable()
    val exchangeRate = varchar("exchange_rate", 20).nullable()
    val refNo = varchar("ref_no", 50).nullable()
    val status = varchar("status", 20).default("OPEN")
    val createdAt = varchar("created_at", 50).default("") // SQLite defaults handle timestamp

    override val primaryKey = PrimaryKey(id)
}

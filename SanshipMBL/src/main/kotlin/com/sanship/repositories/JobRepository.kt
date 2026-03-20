package com.sanship.repositories

import com.sanship.data.DatabaseManager
import com.sanship.data.JobsTable
import com.sanship.models.Job
import com.sanship.models.ChargeMaster
import com.sanship.models.CustomerAddress
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Job Repository
 * Handles CRUD operations for jobs
 */
object JobRepository {
    
    // 1. Get Open Jobs
    fun listOpenJobs(): List<Job> {
        return transaction {
            JobsTable.select { JobsTable.status eq "OPEN" }
                .orderBy(JobsTable.createdAt to SortOrder.DESC)
                .map { toJob(it) }
        }
    }
    
    // Get All Jobs (Open + Closed)
    fun getAllJobs(): List<Job> {
        return transaction {
            JobsTable.selectAll()
                 .orderBy(JobsTable.createdAt to SortOrder.DESC)
                 .map { toJob(it) }
        }
    }
    
    // 2. Get Job by ID
    fun getJobById(id: Int): Job? {
        return transaction {
            JobsTable.select { JobsTable.id eq id }
                .singleOrNull()
                ?.let { toJob(it) }
        }
    }

    // Get Job by Number
    fun getJobByNo(jobNo: String): Job? {
        return transaction {
            JobsTable.select { JobsTable.jobNo eq jobNo }
                .singleOrNull()
                ?.let { toJob(it) }
        }
    }

    // 3. Create Job
    fun createJob(job: Job): Int {
        return transaction {
            JobsTable.insert {
                it[jobNo] = job.jobNo
                it[customerId] = job.customerId
                it[shipper] = job.shipper
                it[consignee] = job.consignee
                it[pol] = job.pol
                it[pod] = job.pod
                it[vesselFlight] = job.vesselFlight
                it[etd] = job.etd
                it[eta] = job.eta
                it[mblNo] = job.mblNo
                it[grossWeight] = job.grossWeight
                it[netWeight] = job.netWeight
                it[volumeCbm] = job.volumeCbm
                it[packages] = job.packages
                it[exchangeRate] = job.exchangeRate
                it[refNo] = job.refNo
                it[status] = "OPEN"
                // created_at handled by DB default (or pass explicit if needed)
            } get JobsTable.id
        }
    }

    // 4. Update Job
    fun updateJob(job: Job) {
        transaction {
            JobsTable.update({ JobsTable.id eq job.id }) {
                it[jobNo] = job.jobNo // Usually job number shouldn't change, but allowing it
                it[customerId] = job.customerId
                it[shipper] = job.shipper
                it[consignee] = job.consignee
                it[pol] = job.pol
                it[pod] = job.pod
                it[vesselFlight] = job.vesselFlight
                it[etd] = job.etd
                it[eta] = job.eta
                it[mblNo] = job.mblNo
                it[grossWeight] = job.grossWeight
                it[netWeight] = job.netWeight
                it[volumeCbm] = job.volumeCbm
                it[packages] = job.packages
                it[exchangeRate] = job.exchangeRate
                it[refNo] = job.refNo
                // Status not updated here
            }
        }
    }

    // 5. Close/Open Job
    fun updateJobStatus(id: Int, status: String) {
        transaction {
            JobsTable.update({ JobsTable.id eq id }) {
                it[JobsTable.status] = status
            }
        }
    }

    // Helper to map ResultRow to Job
    private fun toJob(row: org.jetbrains.exposed.sql.ResultRow): Job {
        return Job(
            id = row[JobsTable.id],
            jobNo = row[JobsTable.jobNo],
            customerId = row[JobsTable.customerId] ?: 0,
            customerName = "", // Join needed or fetch separate? Keeping simple: ID is source of truth.
            // Ideally we should JOIN with ClientTable to get name, but for now we follow old pattern
            // or we can fetch name quickly if needed.
            // Let's leave name empty or fetch it? 
            // In the old code: "customerName = rs.getString("customer_name")" -> This column doesn't exist in `jobs`!
            // Wait, old code select * from jobs used rs.getString("customer_name").
            // Does `jobs` table have `customer_name`? 
            // DatabaseManager schema: NO! `customer_id` exists. `customer_name` does NOT.
            // So the old JDBC code was likely failing or relying on a column that doesn't exist?
            // Ah, looking at DatabaseManager line 121: customer_id INTEGER. No customer_name.
            // So rs.getString("customer_name") would have thrown exception unless SQLite ignores?
            // I will assume we need to join or just return ID.
            shipper = row[JobsTable.shipper] ?: "",
            consignee = row[JobsTable.consignee] ?: "",
            pol = row[JobsTable.pol] ?: "",
            pod = row[JobsTable.pod] ?: "",
            vesselFlight = row[JobsTable.vesselFlight] ?: "",
            etd = row[JobsTable.etd] ?: "",
            eta = row[JobsTable.eta] ?: "",
            mblNo = row[JobsTable.mblNo] ?: "",
            grossWeight = row[JobsTable.grossWeight] ?: "",
            netWeight = row[JobsTable.netWeight] ?: "",
            volumeCbm = row[JobsTable.volumeCbm] ?: "",
            packages = row[JobsTable.packages] ?: "",
            exchangeRate = row[JobsTable.exchangeRate] ?: 1.0,
            refNo = row[JobsTable.refNo] ?: "",
            status = row[JobsTable.status],
            createdAt = row[JobsTable.createdAt]
        )
    }
}

/**
 * Charge Repository
 * Handles charge master operations
 */
object ChargeRepository {
    
    fun listCharges(): List<ChargeMaster> {
        val charges = mutableListOf<ChargeMaster>()
        DatabaseManager.connect()?.use { conn ->
            val query = "SELECT * FROM charges_master ORDER BY charge_name"
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(query)
                while (rs.next()) {
                    charges.add(ChargeMaster(
                        id = rs.getInt("id"),
                        chargeName = rs.getString("charge_name") ?: "",
                        hsnSac = rs.getString("hsn_sac") ?: "",
                        currency = rs.getString("currency") ?: "INR",
                        cgstRate = rs.getDouble("cgst_rate"),
                        sgstRate = rs.getDouble("sgst_rate"),
                        igstRate = rs.getDouble("igst_rate")
                    ))
                }
            }
        }
        return charges
    }
    
    fun getChargeByName(name: String): ChargeMaster? {
        DatabaseManager.connect()?.use { conn ->
            val query = "SELECT * FROM charges_master WHERE charge_name = ?"
            conn.prepareStatement(query).use { ps ->
                ps.setString(1, name)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    return ChargeMaster(
                        id = rs.getInt("id"),
                        chargeName = rs.getString("charge_name") ?: "",
                        hsnSac = rs.getString("hsn_sac") ?: "",
                        currency = rs.getString("currency") ?: "INR",
                        cgstRate = rs.getDouble("cgst_rate"),
                        sgstRate = rs.getDouble("sgst_rate"),
                        igstRate = rs.getDouble("igst_rate")
                    )
                }
            }
        }
        return null
    }
}

/**
 * Address Repository
 */
object AddressRepository {
    
    fun getAddressesForCustomer(customerId: Int): List<CustomerAddress> {
        val addresses = mutableListOf<CustomerAddress>()
        DatabaseManager.connect()?.use { conn ->
            val query = "SELECT * FROM consignee_addresses WHERE consignee_id = ? ORDER BY is_default DESC, label"
            conn.prepareStatement(query).use { ps ->
                ps.setInt(1, customerId)
                val rs = ps.executeQuery()
                while (rs.next()) {
                    addresses.add(CustomerAddress(
                        id = rs.getInt("id"),
                        customerId = rs.getInt("consignee_id"),
                        label = rs.getString("label") ?: "",
                        address = rs.getString("address") ?: "",
                        state = rs.getString("state") ?: "",
                        pincode = rs.getString("pincode") ?: "",
                        country = rs.getString("country") ?: "India",
                        isDefault = rs.getInt("is_default") == 1
                    ))
                }
            }
        }
        return addresses
    }
}

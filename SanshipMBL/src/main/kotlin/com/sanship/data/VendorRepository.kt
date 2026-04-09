package com.sanship.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class VendorRepository {

    // 1. Get ALL vendors
    fun getAllVendors(): Flow<List<VendorMaster>> = flow {
        val results = transaction {
            VendorTable.selectAll().map {
                VendorMaster(
                    id = it[VendorTable.id],
                    shortName = it[VendorTable.shortName],
                    fullName = it[VendorTable.fullName],
                    fullAddress = it[VendorTable.fullAddress],
                    gstin = it[VendorTable.gstin] ?: "",
                    stateCode = it[VendorTable.stateCode] ?: "",
                    email = it[VendorTable.email] ?: "",
                    type = it[VendorTable.type] ?: "Shipping Line"
                )
            }
        }
        emit(results)
    }.flowOn(Dispatchers.IO)

    // 2. Add or Update Vendor
    fun addVendor(vendor: VendorMaster) {
        transaction {
            if (vendor.id != 0) {
                // Update existing by ID
                VendorTable.update({ VendorTable.id eq vendor.id }) {
                    it[VendorTable.shortName] = vendor.shortName
                    it[VendorTable.fullName] = vendor.fullName
                    it[VendorTable.fullAddress] = vendor.fullAddress
                    it[VendorTable.gstin] = vendor.gstin
                    it[VendorTable.stateCode] = vendor.stateCode
                    it[VendorTable.email] = vendor.email
                    it[VendorTable.type] = vendor.type
                }
            } else {
                // Check if name exists
                val existingVendor = VendorTable.select {
                    (VendorTable.shortName eq vendor.shortName) or (VendorTable.fullName eq vendor.fullName)
                }.firstOrNull()

                if (existingVendor != null) {
                    // Update
                    VendorTable.update({ VendorTable.id eq existingVendor[VendorTable.id] }) {
                        it[VendorTable.shortName] = vendor.shortName
                        it[VendorTable.fullName] = vendor.fullName
                        it[VendorTable.fullAddress] = vendor.fullAddress
                        it[VendorTable.gstin] = vendor.gstin
                        it[VendorTable.stateCode] = vendor.stateCode
                        it[VendorTable.email] = vendor.email
                        it[VendorTable.type] = vendor.type
                    }
                } else {
                    VendorTable.insert {
                        it[VendorTable.shortName] = vendor.shortName
                        it[VendorTable.fullName] = vendor.fullName
                        it[VendorTable.fullAddress] = vendor.fullAddress
                        it[VendorTable.gstin] = vendor.gstin
                        it[VendorTable.stateCode] = vendor.stateCode
                        it[VendorTable.email] = vendor.email
                        it[VendorTable.type] = vendor.type
                    }
                }
            }
        }
        // Add to or update in Ledger Engine as a Creditor (Liability) OUTSIDE the transaction lock
        com.sanship.accounting.Ledgers.getOrCreatePartyLedger(vendor.fullName, vendor.gstin, "Liabilities")
    }

    // 3. Delete Vendor
    fun deleteVendor(id: Int) {
        transaction {
            VendorTable.deleteWhere {
                with(SqlExpressionBuilder) {
                    VendorTable.id eq id
                }
            }
        }
    }
}

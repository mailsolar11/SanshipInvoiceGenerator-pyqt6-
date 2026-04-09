package com.sanship.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ClientRepository {

    // 1. Get ALL clients
    fun getAllClients(): Flow<List<ClientMaster>> = flow {
        val results = transaction {
            ClientTable.selectAll().map {
                ClientMaster(
                    id = it[ClientTable.id],
                    shortName = it[ClientTable.shortName],
                    fullName = it[ClientTable.fullName],
                    fullAddress = it[ClientTable.fullAddress],
                    gstin = it[ClientTable.gstin] ?: "",
                    stateCode = it[ClientTable.stateCode] ?: "",
                    email = it[ClientTable.email] ?: ""
                )
            }
        }
        emit(results)
    }.flowOn(Dispatchers.IO)

    // 2. Add or Update Client
    fun addClient(client: ClientMaster) {
        transaction {
            if (client.id != 0) {
                // Update existing by ID
                ClientTable.update({ ClientTable.id eq client.id }) {
                    it[ClientTable.shortName] = client.shortName
                    it[ClientTable.fullName] = client.fullName
                    it[ClientTable.fullAddress] = client.fullAddress
                    it[ClientTable.gstin] = client.gstin
                    it[ClientTable.stateCode] = client.stateCode
                    it[ClientTable.email] = client.email
                }
            } else {
                // Check if name exists (only for new entries to avoid duplicates)
                val existingClient = ClientTable.select {
                    (ClientTable.shortName eq client.shortName) or (ClientTable.fullName eq client.fullName)
                }.firstOrNull()

                if (existingClient != null) {
                    // Update the existing match (Upsert logic)
                    ClientTable.update({ ClientTable.id eq existingClient[ClientTable.id] }) {
                        it[ClientTable.shortName] = client.shortName
                        it[ClientTable.fullName] = client.fullName
                        it[ClientTable.fullAddress] = client.fullAddress
                        it[ClientTable.gstin] = client.gstin
                        it[ClientTable.stateCode] = client.stateCode
                        it[ClientTable.email] = client.email
                    }
                } else {
                    ClientTable.insert {
                        it[ClientTable.shortName] = client.shortName
                        it[ClientTable.fullName] = client.fullName
                        it[ClientTable.fullAddress] = client.fullAddress
                        it[ClientTable.gstin] = client.gstin
                        it[ClientTable.stateCode] = client.stateCode
                        it[ClientTable.email] = client.email
                    }
                }
            }
        }
        // Add to or update in Ledger Engine as a Debtor (Asset) OUTSIDE the transaction lock
        com.sanship.accounting.Ledgers.getOrCreatePartyLedger(client.fullName, client.gstin, "Assets")
    }

    // 3. Delete Client
    fun deleteClient(id: Int) {
        transaction {
            ClientTable.deleteWhere {
                with(SqlExpressionBuilder) {
                    ClientTable.id eq id
                }
            }
        }
    }
}
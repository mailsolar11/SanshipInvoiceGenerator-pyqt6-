package com.sanship.repositories

import com.sanship.data.User
import com.sanship.data.UsersTable
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

object UserRepository {
    fun authenticate(username: String, pass: String): Boolean {
        return transaction {
            UsersTable.select { 
                (UsersTable.username eq username) and (UsersTable.password eq pass) 
            }.count() > 0
        }
    }

    fun getRole(username: String): String {
        return transaction {
            UsersTable.select { UsersTable.username eq username }
                .singleOrNull()
                ?.let { it[UsersTable.role] } ?: "USER"
        }
    }
}

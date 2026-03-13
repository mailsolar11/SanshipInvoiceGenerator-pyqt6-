package com.sanship.data

import org.jetbrains.exposed.sql.Table
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object UsersTable : Table("users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 255) // In production, this must be hashed!
    val role = varchar("role", 20) // ADMIN, USER
    val createdAt = varchar("created_at", 50).default(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    override val primaryKey = PrimaryKey(id)
}

data class User(
    val id: Int = 0,
    val username: String,
    val role: String
)

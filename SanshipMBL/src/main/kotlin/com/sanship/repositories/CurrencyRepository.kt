package com.sanship.repositories

import com.sanship.data.DatabaseManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.sql.Connection

data class Currency(
    val id: Int,
    val code: String,
    val name: String,
    val exchangeRate: Double,
    val lastUpdated: String? = null
)

object CurrencyRepository {

    fun getAllCurrencies(): Flow<List<Currency>> = flow {
        val list = mutableListOf<Currency>()
        DatabaseManager.connect()?.use { conn ->
            val sql = "SELECT * FROM currency_master ORDER BY code ASC"
            conn.prepareStatement(sql).use { pstmt ->
                val rs = pstmt.executeQuery()
                while (rs.next()) {
                    list.add(
                        Currency(
                            id = rs.getInt("id"),
                            code = rs.getString("code"),
                            name = rs.getString("name"),
                            exchangeRate = rs.getDouble("exchange_rate"),
                            lastUpdated = rs.getString("last_updated")
                        )
                    )
                }
            }
        }
        emit(list)
    }

    fun updateExchangeRate(code: String, rate: Double) {
        DatabaseManager.connect()?.use { conn ->
            val sql = "UPDATE currency_master SET exchange_rate = ?, last_updated = CURRENT_TIMESTAMP WHERE code = ?"
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setDouble(1, rate)
                pstmt.setString(2, code)
                pstmt.executeUpdate()
            }
        }
    }
    
    fun getRateForCurrency(code: String): Double {
        var rate = 1.0
        DatabaseManager.connect()?.use { conn ->
            val sql = "SELECT exchange_rate FROM currency_master WHERE code = ?"
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, code)
                val rs = pstmt.executeQuery()
                if (rs.next()) {
                    rate = rs.getDouble("exchange_rate")
                }
            }
        }
        return rate
    }
}

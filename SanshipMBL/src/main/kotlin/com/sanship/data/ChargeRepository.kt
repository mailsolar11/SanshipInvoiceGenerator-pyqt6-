package com.sanship.data

import java.sql.Statement

object ChargeRepository {

    fun getAllCharges(): List<Charge> {
        val list = mutableListOf<Charge>()
        DatabaseManager.connect()?.use { conn ->
            val q = "SELECT * FROM charges ORDER BY charge_name"
            val rs = conn.prepareStatement(q).executeQuery()
            while (rs.next()) {
                list.add(Charge(
                    id = rs.getInt("id"),
                    chargeName = rs.getString("charge_name") ?: "",
                    hsnSac = rs.getString("hsn_sac") ?: "",
                    currency = rs.getString("currency") ?: "INR",
                    cgstRate = rs.getDouble("cgst_rate"),
                    sgstRate = rs.getDouble("sgst_rate"),
                    igstRate = rs.getDouble("igst_rate"),
                    defaultRate = rs.getDouble("default_rate"),
                    description = rs.getString("description") ?: ""
                ))
            }
        }
        return list
    }

    fun saveCharge(charge: Charge) {
        DatabaseManager.connect()?.use { conn ->
            val sql = if (charge.id == 0) {
                """
                INSERT INTO charges (charge_name, hsn_sac, currency, cgst_rate, sgst_rate, igst_rate, default_rate, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """
            } else {
                """
                UPDATE charges SET charge_name=?, hsn_sac=?, currency=?, cgst_rate=?, sgst_rate=?, igst_rate=?, default_rate=?, description=?
                WHERE id=?
                """
            }
            
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, charge.chargeName)
                stmt.setString(2, charge.hsnSac)
                stmt.setString(3, charge.currency)
                stmt.setDouble(4, charge.cgstRate)
                stmt.setDouble(5, charge.sgstRate)
                stmt.setDouble(6, charge.igstRate)
                stmt.setDouble(7, charge.defaultRate)
                stmt.setString(8, charge.description)
                if (charge.id != 0) {
                    stmt.setInt(9, charge.id)
                }
                stmt.executeUpdate()
            }
        }
    }
}

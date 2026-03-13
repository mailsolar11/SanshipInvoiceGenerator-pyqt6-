package com.sanship.repositories

import com.sanship.data.DatabaseManager
import com.sanship.models.HBLInstruction
import com.sanship.models.Container
import java.sql.ResultSet

object BLRepository {

    // --- HBL INSTRUCTIONS ---

    fun saveHBLInstruction(hbl: HBLInstruction): Int {
        var id = 0
        val upsert = """
            INSERT OR REPLACE INTO hbl_instructions (
                id, job_id, hbl_no, mbl_no, shipper_text, consignee_text, 
                notify_party_text, delivery_agent_text, marks_and_numbers, 
                description_of_goods, bl_type, freight_terms, place_of_receipt, 
                port_of_loading, port_of_discharge, place_of_delivery, 
                shipped_on_board_date, no_of_originals
            ) VALUES (
                (SELECT id FROM hbl_instructions WHERE hbl_no = ?), 
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
        """
        // Note: SQLite REPLACE works by checking unique constraints. 
        // If hbl_no is unique, we need to handle the ID logic carefully or rely on hbl_no conflict.
        // A better approach for SQLite "Upsert" with auto-inc ID:
        // INSERT INTO ... ON CONFLICT(hbl_no) DO UPDATE SET ...
        // or separate Check/Update/Insert logic.
        
        // Let's use Check + Insert/Update for safety
        
        DatabaseManager.connect()?.use { conn ->
            val checkSql = "SELECT id FROM hbl_instructions WHERE hbl_no = ?"
            var existingId = 0
            conn.prepareStatement(checkSql).use { ps ->
                ps.setString(1, hbl.hblNo)
                val rs = ps.executeQuery()
                if (rs.next()) existingId = rs.getInt("id")
            }
            
            if (existingId > 0) {
                val updateSql = """
                    UPDATE hbl_instructions SET 
                        mbl_no=?, shipper_text=?, consignee_text=?, notify_party_text=?, 
                        delivery_agent_text=?, marks_and_numbers=?, description_of_goods=?, 
                        bl_type=?, freight_terms=?, place_of_receipt=?, port_of_loading=?, 
                        port_of_discharge=?, place_of_delivery=?, shipped_on_board_date=?, 
                        no_of_originals=? 
                    WHERE id = ?
                """
                conn.prepareStatement(updateSql).use { ps ->
                    ps.setString(1, hbl.mblNo)
                    ps.setString(2, hbl.shipperText)
                    ps.setString(3, hbl.consigneeText)
                    ps.setString(4, hbl.notifyPartyText)
                    ps.setString(5, hbl.deliveryAgentText)
                    ps.setString(6, hbl.marksAndNumbers)
                    ps.setString(7, hbl.descriptionOfGoods)
                    ps.setString(8, hbl.blType)
                    ps.setString(9, hbl.freightTerms)
                    ps.setString(10, hbl.placeOfReceipt)
                    ps.setString(11, hbl.portOfLoading)
                    ps.setString(12, hbl.portOfDischarge)
                    ps.setString(13, hbl.placeOfDelivery)
                    ps.setString(14, hbl.shippedOnBoardDate)
                    ps.setInt(15, hbl.noOfOriginals)
                    ps.setInt(16, existingId)
                    ps.executeUpdate()
                }
                id = existingId
            } else {
                val insertSql = """
                    INSERT INTO hbl_instructions (
                        job_id, hbl_no, mbl_no, shipper_text, consignee_text, 
                        notify_party_text, delivery_agent_text, marks_and_numbers, 
                        description_of_goods, bl_type, freight_terms, place_of_receipt, 
                        port_of_loading, port_of_discharge, place_of_delivery, 
                        shipped_on_board_date, no_of_originals
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                conn.prepareStatement(insertSql).use { ps ->
                    ps.setInt(1, hbl.jobId)
                    ps.setString(2, hbl.hblNo)
                    ps.setString(3, hbl.mblNo)
                    ps.setString(4, hbl.shipperText)
                    ps.setString(5, hbl.consigneeText)
                    ps.setString(6, hbl.notifyPartyText)
                    ps.setString(7, hbl.deliveryAgentText)
                    ps.setString(8, hbl.marksAndNumbers)
                    ps.setString(9, hbl.descriptionOfGoods)
                    ps.setString(10, hbl.blType)
                    ps.setString(11, hbl.freightTerms)
                    ps.setString(12, hbl.placeOfReceipt)
                    ps.setString(13, hbl.portOfLoading)
                    ps.setString(14, hbl.portOfDischarge)
                    ps.setString(15, hbl.placeOfDelivery)
                    ps.setString(16, hbl.shippedOnBoardDate)
                    ps.setInt(17, hbl.noOfOriginals)
                    ps.executeUpdate()
                    id = ps.generatedKeys.getInt(1)
                }
            }
        }
        return id
    }

    fun getHBLByJobId(jobId: Int): HBLInstruction? {
        var hbl: HBLInstruction? = null
        val sql = "SELECT * FROM hbl_instructions WHERE job_id = ?"
        
        DatabaseManager.connect()?.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, jobId)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    hbl = mapResultSetToHBL(rs)
                }
            }
        }
        return hbl
    }

    private fun mapResultSetToHBL(rs: ResultSet): HBLInstruction {
        return HBLInstruction(
            id = rs.getInt("id"),
            jobId = rs.getInt("job_id"),
            hblNo = rs.getString("hbl_no"),
            mblNo = rs.getString("mbl_no") ?: "",
            shipperText = rs.getString("shipper_text") ?: "",
            consigneeText = rs.getString("consignee_text") ?: "",
            notifyPartyText = rs.getString("notify_party_text") ?: "",
            deliveryAgentText = rs.getString("delivery_agent_text") ?: "",
            marksAndNumbers = rs.getString("marks_and_numbers") ?: "",
            descriptionOfGoods = rs.getString("description_of_goods") ?: "",
            blType = rs.getString("bl_type") ?: "ORIGINAL",
            freightTerms = rs.getString("freight_terms") ?: "PREPAID",
            placeOfReceipt = rs.getString("place_of_receipt") ?: "",
            portOfLoading = rs.getString("port_of_loading") ?: "",
            portOfDischarge = rs.getString("port_of_discharge") ?: "",
            placeOfDelivery = rs.getString("place_of_delivery") ?: "",
            shippedOnBoardDate = rs.getString("shipped_on_board_date") ?: "",
            noOfOriginals = rs.getInt("no_of_originals")
        )
    }

    // --- CONTAINERS ---

    fun saveContainer(c: Container) {
        val sql = """
            INSERT INTO containers (
                job_id, container_no, seal_no, container_type, packages, 
                package_type, gross_weight, net_weight, volume_cbm, vgm_weight, description
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        // For editing, we might need Update. Assume Add only for now or handle ID?
        // Let's handle Edit if ID > 0
        if (c.id > 0) {
            updateContainer(c)
            return
        }

        DatabaseManager.connect()?.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, c.jobId)
                ps.setString(2, c.containerNo)
                ps.setString(3, c.sealNo)
                ps.setString(4, c.containerType)
                ps.setInt(5, c.packages)
                ps.setString(6, c.packageType)
                ps.setDouble(7, c.grossWeight)
                ps.setDouble(8, c.netWeight)
                ps.setDouble(9, c.volumeCbm)
                ps.setDouble(10, c.vgmWeight)
                ps.setString(11, c.description)
                ps.executeUpdate()
            }
        }
    }

    fun updateContainer(c: Container) {
        val sql = """
            UPDATE containers SET 
                container_no=?, seal_no=?, container_type=?, packages=?, 
                package_type=?, gross_weight=?, net_weight=?, volume_cbm=?, 
                vgm_weight=?, description=?
            WHERE id = ?
        """
        DatabaseManager.connect()?.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, c.containerNo)
                ps.setString(2, c.sealNo)
                ps.setString(3, c.containerType)
                ps.setInt(4, c.packages)
                ps.setString(5, c.packageType)
                ps.setDouble(6, c.grossWeight)
                ps.setDouble(7, c.netWeight)
                ps.setDouble(8, c.volumeCbm)
                ps.setDouble(9, c.vgmWeight)
                ps.setString(10, c.description)
                ps.setInt(11, c.id)
                ps.executeUpdate()
            }
        }
    }

    fun deleteContainer(id: Int) {
        val sql = "DELETE FROM containers WHERE id = ?"
        DatabaseManager.connect()?.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, id)
                ps.executeUpdate()
            }
        }
    }

    fun getContainersByJobId(jobId: Int): List<Container> {
        val list = mutableListOf<Container>()
        val sql = "SELECT * FROM containers WHERE job_id = ?"
        
        DatabaseManager.connect()?.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, jobId)
                val rs = ps.executeQuery()
                while (rs.next()) {
                    list.add(Container(
                        id = rs.getInt("id"),
                        jobId = rs.getInt("job_id"),
                        containerNo = rs.getString("container_no"),
                        sealNo = rs.getString("seal_no") ?: "",
                        containerType = rs.getString("container_type") ?: "",
                        packages = rs.getInt("packages"),
                        packageType = rs.getString("package_type") ?: "",
                        grossWeight = rs.getDouble("gross_weight"),
                        netWeight = rs.getDouble("net_weight"),
                        volumeCbm = rs.getDouble("volume_cbm"),
                        vgmWeight = rs.getDouble("vgm_weight"),
                        description = rs.getString("description") ?: ""
                    ))
                }
            }
        }
        return list
    }
}

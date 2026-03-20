package com.sanship.ui.creditnote

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanship.data.*
import kotlinx.coroutines.launch

@Composable
fun CreditNoteScreen() {
    val scope = rememberCoroutineScope()

    // ── State ─────────────────────────────────────────
    var header by remember { mutableStateOf(CreditNoteHeader()) }
    var items by remember { mutableStateOf(listOf(CreditNoteItem())) }
    var customers by remember { mutableStateOf(emptyList<Pair<Int, String>>()) }
    var jobs by remember { mutableStateOf(emptyList<Pair<Int, String>>()) }
    var successMsg by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    // Calculated totals
    val totalTaxable = items.sumOf { it.taxableAmount }
    val totalCgst = items.sumOf { it.cgstAmt }
    val totalSgst = items.sumOf { it.sgstAmt }
    val totalIgst = items.sumOf { it.igstAmt }
    val grandTotal = totalTaxable + totalCgst + totalSgst + totalIgst

    LaunchedEffect(Unit) {
        header = header.copy(creditNoteNo = CreditNoteRepository.getNextCreditNoteNo())
        DatabaseManager.connect()?.use { conn ->
            val rs = conn.prepareStatement("SELECT id, fullName, gstin, stateCode FROM client_master ORDER BY fullName").executeQuery()
            val tmp = mutableListOf<Pair<Int, String>>()
            while (rs.next()) tmp.add(Pair(rs.getInt("id"), rs.getString("fullName") ?: ""))
            customers = tmp
        }
        DatabaseManager.connect()?.use { conn ->
            val rs = conn.prepareStatement("SELECT id, job_no FROM jobs WHERE status='OPEN' ORDER BY job_no DESC").executeQuery()
            val tmp = mutableListOf<Pair<Int, String>>()
            while (rs.next()) tmp.add(Pair(rs.getInt("id"), rs.getString("job_no") ?: ""))
            jobs = tmp
        }
    }

    fun onJobSelected(jobId: Int?) {
        if (jobId == null) return
        DatabaseManager.connect()?.use { conn ->
            val rs = conn.prepareStatement("SELECT * FROM jobs WHERE id = ?").apply { setInt(1, jobId) }.executeQuery()
            if (rs.next()) {
                val custId = rs.getObject("customer_id") as? Int
                val custName = customers.firstOrNull { it.first == custId }?.second ?: ""
                header = header.copy(
                    jobId = jobId, jobNo = rs.getString("job_no") ?: "",
                    customerId = custId, customerName = custName,
                    shipper = rs.getString("shipper") ?: "",
                    consignee = rs.getString("consignee") ?: "",
                    pol = rs.getString("pol") ?: "", pod = rs.getString("pod") ?: "",
                    vesselFlight = rs.getString("vessel_flight") ?: "",
                    mblNo = rs.getString("mbl_no") ?: ""
                )
            }
        }
    }

    fun recalcItem(idx: Int, item: CreditNoteItem): CreditNoteItem {
        val amt = item.qty * item.rate
        val cgstAmt = amt * item.cgstRate / 100
        val sgstAmt = amt * item.sgstRate / 100
        val igstAmt = amt * item.igstRate / 100
        val total = amt + cgstAmt + sgstAmt + igstAmt
        return item.copy(amount = amt, taxableAmount = amt, cgstAmt = cgstAmt, sgstAmt = sgstAmt, igstAmt = igstAmt, totalAmt = total)
    }

    fun save() {
        scope.launch {
            try {
                if (header.originalInvoiceNo.isBlank()) throw RuntimeException("Original Invoice No is required")
                val h = header.copy(taxableAmount = totalTaxable, cgstAmount = totalCgst, sgstAmount = totalSgst, igstAmount = totalIgst, grandTotal = grandTotal)
                val id = CreditNoteRepository.saveCreditNote(h, items)
                successMsg = "Credit Note ${header.creditNoteNo} saved! (id=$id)"
                errorMsg = ""
                header = header.copy(creditNoteNo = CreditNoteRepository.getNextCreditNoteNo())
            } catch (e: Exception) {
                errorMsg = "Save failed: ${e.message}"
                successMsg = ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credit Note", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                backgroundColor = Color(0xFF5C00D9),
                contentColor = Color.White,
                actions = {
                    Button(
                        onClick = { save() },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2E7D32)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("Save", color = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (successMsg.isNotBlank()) Card(backgroundColor = Color(0xFFE8F5E9), modifier = Modifier.fillMaxWidth()) {
                Text(successMsg, modifier = Modifier.padding(12.dp), color = Color(0xFF2E7D32))
            }
            if (errorMsg.isNotBlank()) Card(backgroundColor = Color(0xFFFFEBEE), modifier = Modifier.fillMaxWidth()) {
                Text(errorMsg, modifier = Modifier.padding(12.dp), color = Color(0xFFD32F2F))
            }

            // ── Section 1: Credit Note Header ─────────────────
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Credit Note Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = header.creditNoteNo, onValueChange = { header = header.copy(creditNoteNo = it) }, label = { Text("Credit Note No") }, modifier = Modifier.weight(1f))
                        com.sanship.ui.components.DatePickerField(label = "Date", value = header.date, onValueChange = { header = header.copy(date = it) }, modifier = Modifier.weight(1f))
                    }
                    // ★ Key Credit Note fields
                    OutlinedTextField(
                        value = header.originalInvoiceNo,
                        onValueChange = { header = header.copy(originalInvoiceNo = it) },
                        label = { Text("Original Invoice No *") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFE64A19))
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        com.sanship.ui.components.DatePickerField(label = "Original Invoice Date", value = header.originalInvoiceDate, onValueChange = { header = header.copy(originalInvoiceDate = it) }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = header.placeOfSupply, onValueChange = { header = header.copy(placeOfSupply = it) }, label = { Text("Place of Supply") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(
                        value = header.reason,
                        onValueChange = { header = header.copy(reason = it) },
                        label = { Text("Reason for Credit Note *") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }

            // ── Section 2: Customer ────────────────────────────
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Customer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    com.sanship.ui.components.SearchableDropdown(
                        label = "Customer",
                        items = customers,
                        selectedItem = customers.find { it.first == header.customerId },
                        itemToString = { it.second },
                        onItemSelected = { selected ->
                            if (selected != null) {
                                val (id, name) = selected
                                var gstin = ""; var sc = ""
                                DatabaseManager.connect()?.use { conn ->
                                    val rs = conn.prepareStatement("SELECT gstin, stateCode FROM client_master WHERE id = ?").apply { setInt(1, id) }.executeQuery()
                                    if (rs.next()) { gstin = rs.getString("gstin") ?: ""; sc = rs.getString("stateCode") ?: "" }
                                }
                                header = header.copy(customerId = id, customerName = name, gstin = gstin, stateCode = sc)
                            } else {
                                header = header.copy(customerId = null, customerName = "", gstin = "", stateCode = "")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = header.billingAddress, onValueChange = { header = header.copy(billingAddress = it) }, label = { Text("Billing Address") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = header.gstin, onValueChange = { header = header.copy(gstin = it) }, label = { Text("GSTIN") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = header.stateCode, onValueChange = { header = header.copy(stateCode = it) }, label = { Text("State Code") }, modifier = Modifier.weight(0.4f))
                    }
                }
            }

            // ── Section 3: Shipment Reference ─────────────────
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Shipment Reference", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    com.sanship.ui.components.SearchableDropdown(
                        label = "Job No",
                        items = jobs,
                        selectedItem = jobs.find { it.first == header.jobId },
                        itemToString = { it.second },
                        onItemSelected = { selected ->
                            if (selected != null) {
                                onJobSelected(selected.first)
                            } else {
                                header = header.copy(jobId = null, jobNo = "")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = header.mblNo, onValueChange = { header = header.copy(mblNo = it) }, label = { Text("MBL No") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = header.hblNo, onValueChange = { header = header.copy(hblNo = it) }, label = { Text("HBL No") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = header.containerNos, onValueChange = { header = header.copy(containerNos = it) }, label = { Text("Container No(s)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = header.pol, onValueChange = { header = header.copy(pol = it) }, label = { Text("POL") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = header.pod, onValueChange = { header = header.copy(pod = it) }, label = { Text("POD") }, modifier = Modifier.weight(1f))
                    }
                }
            }

            // ── Section 4: Line Items (WITH GST columns) ───────
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Credit Items", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Button(onClick = { items = items + CreditNoteItem(srNo = items.size + 1) }) {
                            Icon(Icons.Default.Add, null); Text("Add Row")
                        }
                    }
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Column {
                            Row(modifier = Modifier.background(Color(0xFFE0E0E0)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("SR", Modifier.width(30.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("DESCRIPTION", Modifier.width(180.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("HSN", Modifier.width(70.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("CUR", Modifier.width(45.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("QTY", Modifier.width(55.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("RATE", Modifier.width(80.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("TAXABLE", Modifier.width(90.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("CGST%", Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("CGST", Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("SGST%", Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("SGST", Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("TOTAL", Modifier.width(90.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("DEL", Modifier.width(36.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Divider()
                            items.forEachIndexed { idx, item ->
                                Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${idx + 1}", Modifier.width(30.dp), fontSize = 11.sp)
                                    OutlinedTextField(value = item.description, onValueChange = { v -> items = items.toMutableList().also { it[idx] = recalcItem(idx, item.copy(description = v)) } }, modifier = Modifier.width(180.dp).height(52.dp), singleLine = true)
                                    OutlinedTextField(value = item.hsnSac, onValueChange = { v -> items = items.toMutableList().also { it[idx] = item.copy(hsnSac = v) } }, modifier = Modifier.width(70.dp).height(52.dp), singleLine = true)
                                    OutlinedTextField(value = item.currency, onValueChange = { v -> items = items.toMutableList().also { it[idx] = item.copy(currency = v) } }, modifier = Modifier.width(45.dp).height(52.dp), singleLine = true)
                                    OutlinedTextField(value = if (item.qty == 0.0) "" else item.qty.toString(), onValueChange = { v -> items = items.toMutableList().also { it[idx] = recalcItem(idx, item.copy(qty = v.toDoubleOrNull() ?: 0.0)) } }, modifier = Modifier.width(55.dp).height(52.dp), singleLine = true)
                                    OutlinedTextField(value = if (item.rate == 0.0) "" else item.rate.toString(), onValueChange = { v -> items = items.toMutableList().also { it[idx] = recalcItem(idx, item.copy(rate = v.toDoubleOrNull() ?: 0.0)) } }, modifier = Modifier.width(80.dp).height(52.dp), singleLine = true)
                                    Text("%.2f".format(item.taxableAmount), Modifier.width(90.dp), fontSize = 11.sp)
                                    OutlinedTextField(value = if (item.cgstRate == 0.0) "" else item.cgstRate.toString(), onValueChange = { v -> items = items.toMutableList().also { it[idx] = recalcItem(idx, item.copy(cgstRate = v.toDoubleOrNull() ?: 0.0)) } }, modifier = Modifier.width(60.dp).height(52.dp), singleLine = true)
                                    Text("%.2f".format(item.cgstAmt), Modifier.width(75.dp), fontSize = 11.sp)
                                    OutlinedTextField(value = if (item.sgstRate == 0.0) "" else item.sgstRate.toString(), onValueChange = { v -> items = items.toMutableList().also { it[idx] = recalcItem(idx, item.copy(sgstRate = v.toDoubleOrNull() ?: 0.0)) } }, modifier = Modifier.width(60.dp).height(52.dp), singleLine = true)
                                    Text("%.2f".format(item.sgstAmt), Modifier.width(75.dp), fontSize = 11.sp)
                                    Text("%.2f".format(item.totalAmt), Modifier.width(90.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    IconButton(onClick = { if (items.size > 1) items = items.toMutableList().also { it.removeAt(idx) } }, modifier = Modifier.width(36.dp)) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red)
                                    }
                                }
                                Divider()
                            }
                            // Summary row
                            Column(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Taxable: ₹ %.2f".format(totalTaxable))
                                Text("CGST: ₹ %.2f".format(totalCgst))
                                Text("SGST: ₹ %.2f".format(totalSgst))
                                if (totalIgst > 0) Text("IGST: ₹ %.2f".format(totalIgst))
                                Divider(modifier = Modifier.width(200.dp))
                                Text("Grand Total: ₹ %.2f".format(grandTotal), fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
            }
        }
    }
}

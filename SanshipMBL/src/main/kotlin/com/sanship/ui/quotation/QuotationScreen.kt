package com.sanship.ui.quotation

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
import java.time.LocalDate

@Composable
fun QuotationScreen() {
    val scope = rememberCoroutineScope()

    // ── State ─────────────────────────────────────────
    var header by remember { mutableStateOf(QuotationHeader()) }
    var items by remember { mutableStateOf(listOf(QuotationItem())) }
    var customers by remember { mutableStateOf(emptyList<Pair<Int, String>>()) }
    var jobs by remember { mutableStateOf(emptyList<Pair<Int, String>>()) }
    var savedList by remember { mutableStateOf(emptyList<QuotationHeader>()) }
    var successMsg by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    val modes = listOf("Sea", "Air", "Road", "Multimodal")
    val containerTypes = listOf("FCL", "LCL", "BULK", "OFT/OOG")

    fun totalAmount() = items.sumOf { it.qty * it.rate }

    LaunchedEffect(Unit) {
        header = header.copy(quotationNo = QuotationRepository.getNextQuotationNo())
        // Load customers
        DatabaseManager.connect()?.use { conn ->
            val rs = conn.prepareStatement(
                "SELECT id, fullName FROM client_master ORDER BY fullName"
            ).executeQuery()
            val tmp = mutableListOf<Pair<Int, String>>()
            while (rs.next()) tmp.add(Pair(rs.getInt("id"), rs.getString("fullName") ?: ""))
            customers = tmp
        }
        // Load open jobs
        DatabaseManager.connect()?.use { conn ->
            val rs = conn.prepareStatement(
                "SELECT id, job_no FROM jobs WHERE status='OPEN' ORDER BY job_no DESC"
            ).executeQuery()
            val tmp = mutableListOf<Pair<Int, String>>()
            while (rs.next()) tmp.add(Pair(rs.getInt("id"), rs.getString("job_no") ?: ""))
            jobs = tmp
        }
        savedList = QuotationRepository.getQuotations()
    }

    fun onJobSelected(jobId: Int?) {
        if (jobId == null) return
        DatabaseManager.connect()?.use { conn ->
            val rs = conn.prepareStatement("SELECT * FROM jobs WHERE id = ?")
                .apply { setInt(1, jobId) }.executeQuery()
            if (rs.next()) {
                val custId = rs.getObject("customer_id") as? Int
                val custName = customers.firstOrNull { it.first == custId }?.second ?: ""
                header = header.copy(
                    jobId = jobId,
                    jobNo = rs.getString("job_no") ?: "",
                    customerId = custId,
                    customerName = custName,
                    shipper = rs.getString("shipper") ?: "",
                    consignee = rs.getString("consignee") ?: "",
                    pol = rs.getString("pol") ?: "",
                    pod = rs.getString("pod") ?: "",
                    vesselFlight = rs.getString("vessel_flight") ?: "",
                    etd = rs.getString("etd") ?: "",
                    eta = rs.getString("eta") ?: ""
                )
            }
        }
    }

    fun save() {
        scope.launch {
            try {
                val total = totalAmount()
                val saved = QuotationRepository.saveQuotation(
                    header.copy(totalAmount = total),
                    items.map { it.copy(amount = it.qty * it.rate) }
                )
                successMsg = "Quotation ${header.quotationNo} saved! (id=$saved)"
                errorMsg = ""
                savedList = QuotationRepository.getQuotations()
            } catch (e: Exception) {
                errorMsg = "Save failed: ${e.message}"
                successMsg = ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quotation", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
            // Messages
            if (successMsg.isNotBlank()) Card(backgroundColor = Color(0xFFE8F5E9), modifier = Modifier.fillMaxWidth()) {
                Text(successMsg, modifier = Modifier.padding(12.dp), color = Color(0xFF2E7D32))
            }
            if (errorMsg.isNotBlank()) Card(backgroundColor = Color(0xFFFFEBEE), modifier = Modifier.fillMaxWidth()) {
                Text(errorMsg, modifier = Modifier.padding(12.dp), color = Color(0xFFD32F2F))
            }

            // ── Section 1: Header ──────────────────────────────
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Quotation Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = header.quotationNo,
                            onValueChange = { header = header.copy(quotationNo = it) },
                            label = { Text("Quotation No") },
                            modifier = Modifier.weight(1f)
                        )
                        com.sanship.ui.components.DatePickerField(
                            label = "Date", value = header.date,
                            onValueChange = { header = header.copy(date = it) },
                            modifier = Modifier.weight(1f)
                        )
                        com.sanship.ui.components.DatePickerField(
                            label = "Valid Until", value = header.validUntil,
                            onValueChange = { header = header.copy(validUntil = it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Section 2: Customer ────────────────────────────
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Customer", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    // Job dropdown (auto-fills customer + shipment)
                    com.sanship.ui.components.SearchableDropdown(
                        label = "Select Job (optional)",
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

                    // Customer dropdown
                    com.sanship.ui.components.SearchableDropdown(
                        label = "Customer",
                        items = customers,
                        selectedItem = customers.find { it.first == header.customerId },
                        itemToString = { it.second },
                        onItemSelected = { selected ->
                            if (selected != null) {
                                header = header.copy(customerId = selected.first, customerName = selected.second)
                            } else {
                                header = header.copy(customerId = null, customerName = "")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = header.billingAddress,
                        onValueChange = { header = header.copy(billingAddress = it) },
                        label = { Text("Billing Address") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }

            // ── Section 3: Trade Lane ──────────────────────────
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Trade Lane", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = header.pol, onValueChange = { header = header.copy(pol = it) }, label = { Text("POL (Origin Port)") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = header.pod, onValueChange = { header = header.copy(pod = it) }, label = { Text("POD (Destination Port)") }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Mode dropdown
                        com.sanship.ui.components.SearchableDropdown(
                            label = "Mode",
                            items = modes,
                            selectedItem = header.mode.takeIf { it.isNotBlank() },
                            itemToString = { it },
                            onItemSelected = { if (it != null) header = header.copy(mode = it) },
                            modifier = Modifier.weight(1f)
                        )
                        // Container Type dropdown
                        com.sanship.ui.components.SearchableDropdown(
                            label = "Container Type",
                            items = containerTypes,
                            selectedItem = header.containerType.takeIf { it.isNotBlank() },
                            itemToString = { it },
                            onItemSelected = { if (it != null) header = header.copy(containerType = it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = header.shipper, onValueChange = { header = header.copy(shipper = it) }, label = { Text("Shipper") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = header.consignee, onValueChange = { header = header.copy(consignee = it) }, label = { Text("Consignee") }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        com.sanship.ui.components.DatePickerField(label = "ETD", value = header.etd, onValueChange = { header = header.copy(etd = it) }, modifier = Modifier.weight(1f))
                        com.sanship.ui.components.DatePickerField(label = "ETA", value = header.eta, onValueChange = { header = header.copy(eta = it) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            // ── Section 4: Line Items (NO TAX columns) ─────────
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Rate Schedule", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Button(onClick = { items = items + QuotationItem(srNo = items.size + 1) }) {
                            Icon(Icons.Default.Add, null)
                            Text("Add Row")
                        }
                    }

                    // Table header
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Column {
                            Row(modifier = Modifier.background(Color(0xFFE0E0E0)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("SR", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("DESCRIPTION", modifier = Modifier.width(220.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("CUR", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("QTY", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("UNIT", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("RATE", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("AMOUNT", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("REMARKS", modifier = Modifier.width(120.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("DEL", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Divider()
                            items.forEachIndexed { idx, item ->
                                Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${idx + 1}", modifier = Modifier.width(30.dp), fontSize = 12.sp)
                                    OutlinedTextField(value = item.description, onValueChange = { v -> items = items.toMutableList().also { it[idx] = item.copy(description = v) } }, modifier = Modifier.width(220.dp).height(52.dp), singleLine = true)
                                    OutlinedTextField(value = item.currency, onValueChange = { v -> items = items.toMutableList().also { it[idx] = item.copy(currency = v) } }, modifier = Modifier.width(50.dp).height(52.dp), singleLine = true)
                                    OutlinedTextField(value = if (item.qty == 0.0) "" else item.qty.toString(), onValueChange = { v -> items = items.toMutableList().also { it[idx] = item.copy(qty = v.toDoubleOrNull() ?: 0.0) } }, modifier = Modifier.width(60.dp).height(52.dp), singleLine = true)
                                    OutlinedTextField(value = item.unit, onValueChange = { v -> items = items.toMutableList().also { it[idx] = item.copy(unit = v) } }, modifier = Modifier.width(80.dp).height(52.dp), singleLine = true)
                                    OutlinedTextField(value = if (item.rate == 0.0) "" else item.rate.toString(), onValueChange = { v -> items = items.toMutableList().also { it[idx] = item.copy(rate = v.toDoubleOrNull() ?: 0.0) } }, modifier = Modifier.width(90.dp).height(52.dp), singleLine = true)
                                    Text("%.2f".format(item.qty * item.rate), modifier = Modifier.width(90.dp), fontSize = 12.sp)
                                    OutlinedTextField(value = item.remarks, onValueChange = { v -> items = items.toMutableList().also { it[idx] = item.copy(remarks = v) } }, modifier = Modifier.width(120.dp).height(52.dp), singleLine = true)
                                    IconButton(onClick = { if (items.size > 1) items = items.toMutableList().also { it.removeAt(idx) } }, modifier = Modifier.width(40.dp)) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red)
                                    }
                                }
                                Divider()
                            }
                            // Total row
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                                Text("Total: ", fontWeight = FontWeight.Bold)
                                Text("₹ %.2f".format(totalAmount()), fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                            }
                        }
                    }
                }
            }

            // ── Section 5: Terms ───────────────────────────────
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Terms & Notes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(value = header.terms, onValueChange = { header = header.copy(terms = it) }, label = { Text("Terms & Conditions") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    OutlinedTextField(value = header.notes, onValueChange = { header = header.copy(notes = it) }, label = { Text("Additional Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
            }
        }
    }
}

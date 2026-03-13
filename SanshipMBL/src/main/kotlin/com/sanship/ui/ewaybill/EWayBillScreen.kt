package com.sanship.ui.ewaybill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanship.data.DatabaseManager
import kotlinx.coroutines.launch

@Composable
fun EWayBillScreen() {
    val scope = rememberCoroutineScope()

    // All invoice numbers loaded once at startup for autocomplete
    var allInvoiceNos by remember { mutableStateOf(emptyList<String>()) }

    // Search state
    var invoiceNo by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var linkedInvoice by remember { mutableStateOf<com.sanship.data.InvoiceData?>(null) }

    // Filtered suggestions — start showing from 1 character
    val suggestions = remember(invoiceNo, allInvoiceNos) {
        if (invoiceNo.isBlank()) emptyList()
        else allInvoiceNos.filter { it.contains(invoiceNo.trim(), ignoreCase = true) }
    }

    // Transport form fields
    var transporterName by remember { mutableStateOf("") }
    var transporterId by remember { mutableStateOf("") }
    var vehicleNo by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }

    var errorMsg by remember { mutableStateOf("") }
    var successMsg by remember { mutableStateOf("") }

    // Load all invoice numbers on first composition
    LaunchedEffect(Unit) {
        val nos = mutableListOf<String>()
        DatabaseManager.connect()?.use { conn ->
            val rs = conn.prepareStatement(
                "SELECT invoiceNo FROM invoices WHERE type = 'INVOICE' OR type IS NULL ORDER BY rowid DESC"
            ).executeQuery()
            while (rs.next()) {
                val no = rs.getString("invoiceNo")
                if (!no.isNullOrBlank()) nos.add(no)
            }
        }
        allInvoiceNos = nos
    }

    fun searchInvoice(no: String = invoiceNo) {
        scope.launch {
            errorMsg = ""; successMsg = ""
            if (no.isBlank()) { errorMsg = "Please enter an invoice number."; return@launch }
            try {
                val inv = DatabaseManager.getInvoice(no.trim())
                if (inv != null) {
                    linkedInvoice = inv
                    successMsg = "✅ Invoice Found: ${inv.customerName}  |  Grand Total: ₹${inv.grandTotal}"
                } else {
                    linkedInvoice = null
                    errorMsg = "Invoice not found. Available: ${allInvoiceNos.take(3).joinToString(", ")}"
                }
            } catch (e: Exception) {
                errorMsg = "Search failed: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)
    ) {
        Text("E-Way Bill Generation Form", style = MaterialTheme.typography.h4)
        Spacer(Modifier.height(20.dp))

        // ── Section 1: Link Invoice ──────────────────────────
        Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Text("1. Link Invoice", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.DarkGray)

                // Invoice search with autocomplete
                Box {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = invoiceNo,
                            onValueChange = {
                                invoiceNo = it
                                showSuggestions = it.isNotBlank()
                                if (linkedInvoice != null) { linkedInvoice = null; successMsg = ""; errorMsg = "" }
                            },
                            label = { Text("Enter Invoice No") },
                            placeholder = { Text("Type to search…", color = Color.LightGray) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF5C00D9)) },
                            trailingIcon = {
                                if (invoiceNo.isNotBlank()) {
                                    IconButton(onClick = {
                                        invoiceNo = ""; showSuggestions = false
                                        linkedInvoice = null; successMsg = ""; errorMsg = ""
                                    }) { Icon(Icons.Default.Clear, null) }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = { showSuggestions = false; searchInvoice() },
                            modifier = Modifier.padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF5C00D9))
                        ) {
                            Icon(Icons.Default.Search, null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Search", color = Color.White)
                        }
                    }

                    // ── True popup autocomplete — overlays everything ──
                    DropdownMenu(
                        expanded = showSuggestions && suggestions.isNotEmpty(),
                        onDismissRequest = { showSuggestions = false },
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .heightIn(max = 240.dp)
                            .background(Color.White)
                    ) {
                        suggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                onClick = {
                                    invoiceNo = suggestion
                                    showSuggestions = false
                                    searchInvoice(suggestion)
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.List,
                                        contentDescription = null,
                                        tint = Color(0xFF5C00D9),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(suggestion, fontSize = 14.sp)
                                }
                            }
                            Divider()
                        }
                    }
                }

                // Status messages
                if (successMsg.isNotBlank()) {
                    Card(backgroundColor = Color(0xFFE8F5E9), modifier = Modifier.fillMaxWidth()) {
                        Text(successMsg, modifier = Modifier.padding(10.dp), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                }
                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = MaterialTheme.colors.error, fontSize = 13.sp)
                }

                // Linked invoice preview
                linkedInvoice?.let { inv ->
                    Divider()
                    Text("Linked Invoice Details", fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column {
                            Text("Invoice No", fontSize = 11.sp, color = Color.Gray)
                            Text(inv.invoiceNo, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Customer", fontSize = 11.sp, color = Color.Gray)
                            Text(inv.customerName ?: "-")
                        }
                        Column {
                            Text("Grand Total", fontSize = 11.sp, color = Color.Gray)
                            Text("₹ ${inv.grandTotal}", fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Section 2: Transport Details ─────────────────────
        Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("2. Transport Details", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.DarkGray)

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(value = transporterName, onValueChange = { transporterName = it }, label = { Text("Transporter Name") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = transporterId, onValueChange = { transporterId = it }, label = { Text("Transporter ID / GSTIN") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(value = vehicleNo, onValueChange = { vehicleNo = it }, label = { Text("Vehicle Number") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = distance, onValueChange = { distance = it }, label = { Text("Approx Distance (KM)") }, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (linkedInvoice == null) { errorMsg = "Please link an invoice first."; return@Button }
                            if (vehicleNo.isBlank() && transporterId.isBlank()) { errorMsg = "Provide either Vehicle No or Transporter ID."; return@Button }
                            successMsg = "✅ E-Way Bill Generated Successfully! (Demo Mode)"
                            errorMsg = ""
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF5C00D9))
                    ) {
                        Icon(Icons.Default.List, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate E-Way Bill", color = Color.White)
                    }
                }
            }
        }
    }
}

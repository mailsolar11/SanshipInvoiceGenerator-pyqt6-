package com.sanship.ui.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sanship.data.AccountingRepository
import com.sanship.models.Job
import com.sanship.repositories.JobRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PaymentVoucherScreen() {
    val scope = rememberCoroutineScope()
    
    // Form State
    var voucherNo by remember { mutableStateOf("") }
    var voucherDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var narration by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    
    // Pay To — all ledgers from accounting DB
    var allLedgers by remember { mutableStateOf(emptyList<Pair<Int, String>>()) }
    var selectedPayToLedgerId by remember { mutableStateOf<Int?>(null) }
    var payToExpanded by remember { mutableStateOf(false) }
    
    // Payment Mode (Cash/Bank)
    val modes = listOf("Cash", "Bank HDFC", "Bank SBI")
    var selectedMode by remember { mutableStateOf("Cash") }
    var modeExpanded by remember { mutableStateOf(false) }
    
    // Job Link
    var jobs by remember { mutableStateOf(emptyList<Job>()) }
    var selectedJobId by remember { mutableStateOf<Int?>(null) }
    var jobExpanded by remember { mutableStateOf(false) }
    
    // UI State
    var successMsg by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    fun loadData() {
        scope.launch {
            jobs = JobRepository.listOpenJobs()
            val ledgers = com.sanship.accounting.Ledgers.listLedgers()
            allLedgers = ledgers.map { ledger ->
                val id = when (val idVal = ledger["id"]) {
                    is Int -> idVal
                    is Long -> idVal.toInt()
                    else -> 0
                }
                val name = ledger["name"] as? String ?: ""
                id to name
            }.filter { it.first != 0 }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
        voucherNo = "PMT/${LocalDate.now().year}/${System.currentTimeMillis() % 1000}"
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Payment Voucher", style = MaterialTheme.typography.h4)
            IconButton(onClick = { loadData() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        Spacer(Modifier.height(20.dp))
        
        Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                // Row 1: Voucher Info
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = voucherNo,
                        onValueChange = { voucherNo = it },
                        label = { Text("Voucher No") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = voucherDate,
                        onValueChange = { voucherDate = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f),
                        trailingIcon = { Icon(Icons.Default.DateRange, null) }
                    )
                }
                
                // Row 2: Pay To (Vendor/Agent Ledger)
                Box {
                    OutlinedButton(
                        onClick = { payToExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val ledger = allLedgers.find { it.first == selectedPayToLedgerId }
                        Text(if (ledger != null) "Pay To: ${ledger.second}" else "Select Payee (Vendor/Agent)")
                    }
                    DropdownMenu(expanded = payToExpanded, onDismissRequest = { payToExpanded = false }) {
                        allLedgers.forEach { (id, name) ->
                            DropdownMenuItem(onClick = {
                                selectedPayToLedgerId = id
                                payToExpanded = false
                            }) {
                                Text(name)
                            }
                        }
                    }
                }
                
                // Row 3: Job Link & Payment Mode
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { jobExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            val job = jobs.find { it.id == selectedJobId }
                            Text(if (job != null) "Link Job: ${job.jobNo}" else "Link Job (Optional)")
                        }
                        DropdownMenu(expanded = jobExpanded, onDismissRequest = { jobExpanded = false }) {
                            DropdownMenuItem(onClick = { selectedJobId = null; jobExpanded = false }) { Text("None") }
                            jobs.forEach { job ->
                                DropdownMenuItem(onClick = { selectedJobId = job.id; jobExpanded = false }) {
                                    Text("${job.jobNo} - ${job.shipper}")
                                }
                            }
                        }
                    }
                    
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { modeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Pay From: $selectedMode")
                        }
                        DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                            modes.forEach { mode ->
                                DropdownMenuItem(onClick = { selectedMode = mode; modeExpanded = false }) { Text(mode) }
                            }
                        }
                    }
                }
                
                // Row 4: Amount
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (INR)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Row 5: Narration
                OutlinedTextField(
                    value = narration,
                    onValueChange = { narration = it },
                    label = { Text("Narration/Reference") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Buttons
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    if (selectedPayToLedgerId == null || amount.toDoubleOrNull() == null) {
                                        errorMsg = "Please select a payee and enter a valid amount."
                                        return@launch
                                    }
                                    
                                    AccountingRepository.savePaymentVoucher(
                                        voucherNo = voucherNo,
                                        date = voucherDate,
                                        mode = selectedMode,
                                        payToLedgerId = selectedPayToLedgerId!!,
                                        amount = amount.toDouble(),
                                        narration = narration,
                                        jobId = selectedJobId ?: 0
                                    )
                                    
                                    successMsg = "Payment Voucher Saved: $voucherNo"
                                    errorMsg = ""
                                    
                                    // Reset
                                    amount = ""
                                    narration = ""
                                    voucherNo = "PMT/${LocalDate.now().year}/${System.currentTimeMillis() % 1000}"
                                    
                                } catch (e: Exception) {
                                    errorMsg = "Error: ${e.message}"
                                }
                            }
                        }
                    ) {
                        Text("Save Payment")
                    }
                }
            }
        }
        
        if (successMsg.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(successMsg, color = Color(0xFF2E7D32))
        }
        if (errorMsg.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(errorMsg, color = MaterialTheme.colors.error)
        }
    }
}

package com.sanship.ui.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.unit.dp
import com.sanship.data.AccountingRepository
import com.sanship.models.Job
import com.sanship.repositories.JobRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ExpenseVoucherScreen() {
    val scope = rememberCoroutineScope()
    
    // Form State
    var voucherNo by remember { mutableStateOf("") }
    var voucherDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var narration by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var partyName by remember { mutableStateOf("Cash") } // Or Vendor Name
    
    // Dropdown State
    var jobs by remember { mutableStateOf(emptyList<Job>()) }
    var selectedJobId by remember { mutableStateOf<Int?>(null) }
    var jobExpanded by remember { mutableStateOf(false) }

    var expenseLedgers by remember { mutableStateOf(emptyList<Pair<Int, String>>()) }
    var selectedLedgerId by remember { mutableStateOf<Int?>(null) }
    var ledgerExpanded by remember { mutableStateOf(false) }
    
    // UI State
    var successMsg by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }



    // Load Data Helper
    fun loadData() {
        scope.launch {
            // Load Jobs
            jobs = JobRepository.listOpenJobs()
            
            // Load Expense Ledgers from Accounting Database
            val allLedgers = com.sanship.accounting.Ledgers.listLedgers()
            expenseLedgers = allLedgers.map { ledger ->
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
        voucherNo = "EXP/${LocalDate.now().year}/${System.currentTimeMillis() % 1000}"
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Expense Voucher Entry", style = MaterialTheme.typography.h4)
            IconButton(onClick = { loadData() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Data")
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
                
                // Row 2: Job Selection
                com.sanship.ui.components.SearchableDropdown(
                    label = "Select Job (Optional)",
                    items = jobs,
                    selectedItem = jobs.find { it.id == selectedJobId },
                    itemToString = { "${it.jobNo} - ${it.shipper}" },
                    onItemSelected = { selectedJobId = it?.id },
                    modifier = Modifier.fillMaxWidth()
                )

                

                // Row 3: Expense Ledger
                com.sanship.ui.components.SearchableDropdown(
                    label = "Select Expense Account",
                    items = expenseLedgers,
                    selectedItem = expenseLedgers.find { it.first == selectedLedgerId },
                    itemToString = { it.second },
                    onItemSelected = { selectedLedgerId = it?.first },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Row 4: Party & Amount
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = partyName,
                        onValueChange = { partyName = it },
                        label = { Text("Paid To / Payment Mode (Cash/Bank)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount") },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Row 5: Narration
                OutlinedTextField(
                    value = narration,
                    onValueChange = { narration = it },
                    label = { Text("Narration") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Buttons
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    if (selectedLedgerId == null || amount.toDoubleOrNull() == null) {
                                        errorMsg = "Please select an account and enter a valid amount."
                                        return@launch
                                    }
                                    
                                    AccountingRepository.saveExpenseVoucher(
                                        voucherNo = voucherNo,
                                        date = voucherDate,
                                        expenseLedgerId = selectedLedgerId!!,
                                        partyName = partyName,
                                        amount = amount.toDouble(),
                                        narration = narration,
                                        jobId = selectedJobId ?: 0
                                    )
                                    
                                    // Auto-Generate PDF
                                    val pdfPath = com.sanship.utils.DocumentPaths.getExpenseVoucherPath("${voucherNo.replace("/","_")}.pdf")
                                    try {
                                        val data = com.sanship.services.AccountingVoucherPdfService.VoucherData(
                                            title = "EXPENSE VOUCHER",
                                            voucherNo = voucherNo,
                                            date = voucherDate,
                                            mainLabel = "Paid To (Party Name):",
                                            mainValue = partyName,
                                            amount = amount.toDouble(),
                                            mode = "N/A",
                                            narration = narration,
                                            jobNo = if (selectedJobId != null) jobs.find { it.id == selectedJobId }?.jobNo else null
                                        )
                                        com.sanship.services.AccountingVoucherPdfService.generateVoucherPDF(data, pdfPath)
                                        successMsg = "Voucher Saved & PDF Generated: $pdfPath"
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        successMsg = "Voucher Saved but PDF Failed: ${e.message}"
                                    }
                                    
                                    errorMsg = ""
                                    
                                    // Reset
                                    amount = ""
                                    narration = ""
                                    voucherNo = "EXP/${LocalDate.now().year}/${System.currentTimeMillis() % 1000}"
                                    
                                } catch (e: Exception) {
                                    errorMsg = "Error: ${e.message}"
                                }
                            }
                        }
                    ) {
                        Text("Save Expense")
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

package com.sanship.ui.receipt

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
import com.sanship.data.ClientMaster
import com.sanship.data.ClientRepository
import com.sanship.models.Job
import com.sanship.repositories.JobRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ReceiptVoucherScreen() {
    val scope = rememberCoroutineScope()
    
    // Form State
    var voucherNo by remember { mutableStateOf("") }
    var voucherDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var narration by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    
    // Dropdown State: Received From (Customer)
    var clients by remember { mutableStateOf(emptyList<ClientMaster>()) }
    var selectedClientId by remember { mutableStateOf<Int?>(null) }
    var clientExpanded by remember { mutableStateOf(false) }

    // Dropdown State: Deposit To (Bank/Cash)
    val modes = listOf("Cash", "Bank HDFC", "Bank SBI")
    var selectedMode by remember { mutableStateOf("Cash") }
    var modeExpanded by remember { mutableStateOf(false) }
    
    // Dropdown State: Job Link (Optional)
    var jobs by remember { mutableStateOf(emptyList<Job>()) }
    var selectedJobId by remember { mutableStateOf<Int?>(null) }
    var jobExpanded by remember { mutableStateOf(false) }

    // UI State
    var successMsg by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    val clientRepo = remember { ClientRepository() }

    // Load Jobs Helper
    fun loadJobs() {
        scope.launch {
            jobs = JobRepository.listOpenJobs()
        }
    }

    // Load Data
    LaunchedEffect(Unit) {
        loadJobs()
        clientRepo.getAllClients().collectLatest { clients = it }
    }
    
    // Voucher No Init
    LaunchedEffect(Unit) {
         voucherNo = "RCPT/${LocalDate.now().year}/${System.currentTimeMillis() % 1000}"
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Receipt Entry", style = MaterialTheme.typography.h4)
            IconButton(onClick = { loadJobs() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Jobs")
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
                
                // Row 2: Received From (Customer)
                com.sanship.ui.components.SearchableDropdown(
                    label = "Received From (Customer)",
                    items = clients,
                    selectedItem = clients.find { it.id == selectedClientId },
                    itemToString = { it.fullName },
                    onItemSelected = { selectedClientId = it?.id },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Row 3: Job Link & Payment Mode
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Job Link
                    com.sanship.ui.components.SearchableDropdown(
                        label = "Link Job (Optional)",
                        items = jobs,
                        selectedItem = jobs.find { it.id == selectedJobId },
                        itemToString = { "${it.jobNo} - ${it.shipper}" },
                        onItemSelected = { selectedJobId = it?.id },
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Deposit To (Mode)
                    com.sanship.ui.components.SearchableDropdown(
                        label = "Deposit To",
                        items = modes,
                        selectedItem = selectedMode,
                        itemToString = { it },
                        onItemSelected = { if (it != null) selectedMode = it },
                        modifier = Modifier.weight(1f)
                    )
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
                                    if (selectedClientId == null || amount.toDoubleOrNull() == null) {
                                        errorMsg = "Please select a customer and enter a valid amount."
                                        return@launch
                                    }
                                    
                                    // Resolve Ledger Name for Customer (Parties are created with Name)
                                    val clientName = clients.find { it.id == selectedClientId }!!.fullName
                                    // Use accounting module's Ledgers to query accounting database
                                    var partyLedgerId = com.sanship.accounting.Ledgers.getOrCreatePartyLedger(clientName)
                                    
                                    AccountingRepository.saveReceiptVoucher(
                                        voucherNo = voucherNo,
                                        date = voucherDate,
                                        mode = selectedMode,
                                        receivedFromLedgerId = partyLedgerId,
                                        amount = amount.toDouble(),
                                        narration = narration,
                                        jobId = selectedJobId ?: 0
                                    )
                                    
                                    // Auto-Generate PDF
                                    val pdfPath = "receipts/${voucherNo.replace("/","_")}.pdf"
                                    try {
                                        java.io.File("receipts").mkdirs()
                                        com.sanship.services.ReceiptPDFGenerator.generateReceiptPDF(
                                            com.sanship.services.ReceiptPDFGenerator.ReceiptData(
                                                receiptNo = voucherNo,
                                                date = voucherDate,
                                                receivedFrom = clientName,
                                                amount = amount.toDouble(),
                                                mode = selectedMode,
                                                narration = narration,
                                                jobNo = if(selectedJobId != null) jobs.find { it.id == selectedJobId }?.jobNo else null
                                            ),
                                            pdfPath
                                        )
                                        successMsg = "Receipt Saved & PDF Generated: $pdfPath"
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        successMsg = "Receipt Saved but PDF Failed: ${e.message}"
                                    }
                                    
                                    errorMsg = ""
                                    
                                    // Reset
                                    amount = ""
                                    narration = ""
                                    voucherNo = "RCPT/${LocalDate.now().year}/${System.currentTimeMillis() % 1000}"
                                    
                                } catch (e: Exception) {
                                    errorMsg = "Error: ${e.message}"
                                }
                            }
                        }
                    ) {
                        Text("Save Receipt")
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

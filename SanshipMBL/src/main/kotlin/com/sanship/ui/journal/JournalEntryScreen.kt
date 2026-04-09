package com.sanship.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanship.data.AccountingRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

data class JournalLine(
    val ledgerId: Int? = null,
    val ledgerName: String = "",
    val drAmount: String = "",
    val crAmount: String = ""
)

@Composable
fun JournalEntryScreen() {
    val scope = rememberCoroutineScope()
    
    // Form State
    var voucherNo by remember { mutableStateOf("") }
    var voucherDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var narration by remember { mutableStateOf("") }
    
    // Multi-line entries
    var lines by remember { mutableStateOf(listOf(JournalLine(), JournalLine())) }
    
    // Ledger dropdown
    var allLedgers by remember { mutableStateOf(emptyList<Pair<Int, String>>()) }
    
    // UI State
    var successMsg by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    fun loadData() {
        scope.launch {
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
        voucherNo = "JV/${LocalDate.now().year}/${System.currentTimeMillis() % 1000}"
    }

    // Running totals
    val totalDr = lines.sumOf { it.drAmount.toDoubleOrNull() ?: 0.0 }
    val totalCr = lines.sumOf { it.crAmount.toDoubleOrNull() ?: 0.0 }
    val isBalanced = kotlin.math.abs(totalDr - totalCr) < 0.01 && totalDr > 0

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Journal Entry", style = MaterialTheme.typography.h4)
            IconButton(onClick = { loadData() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        Spacer(Modifier.height(20.dp))
        
        Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                
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
                
                // Narration
                OutlinedTextField(
                    value = narration,
                    onValueChange = { narration = it },
                    label = { Text("Narration") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Divider()
                
                // Column Headers
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ledger", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
                    Text("DR Amount", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("CR Amount", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(40.dp))
                }
                
                // Journal Lines
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    itemsIndexed(lines) { index, line ->
                        var ledgerExpanded by remember { mutableStateOf(false) }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Ledger dropdown
                            com.sanship.ui.components.SearchableDropdown(
                                label = "Select Ledger",
                                items = allLedgers,
                                selectedItem = allLedgers.find { it.first == line.ledgerId },
                                itemToString = { it.second },
                                onItemSelected = { selected ->
                                    lines = lines.toMutableList().also {
                                        it[index] = it[index].copy(
                                            ledgerId = selected?.first,
                                            ledgerName = selected?.second ?: ""
                                        )
                                    }
                                },
                                modifier = Modifier.weight(2f)
                            )                            
                            // DR Amount
                            OutlinedTextField(
                                value = line.drAmount,
                                onValueChange = { newVal ->
                                    lines = lines.toMutableList().also {
                                        it[index] = it[index].copy(drAmount = newVal)
                                    }
                                },
                                label = { Text("DR") },
                                modifier = Modifier.weight(1f)
                            )
                            
                            // CR Amount
                            OutlinedTextField(
                                value = line.crAmount,
                                onValueChange = { newVal ->
                                    lines = lines.toMutableList().also {
                                        it[index] = it[index].copy(crAmount = newVal)
                                    }
                                },
                                label = { Text("CR") },
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Delete Line
                            IconButton(
                                onClick = {
                                    if (lines.size > 2) {
                                        lines = lines.toMutableList().also { it.removeAt(index) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, "Remove", tint = Color.Gray)
                            }
                        }
                    }
                }
                
                // Add Line Button
                TextButton(onClick = { lines = lines + JournalLine() }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Line")
                }
                
                Divider()
                
                // Totals Row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TOTALS", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
                    Text(
                        "%.2f".format(totalDr),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        color = if (isBalanced) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                    )
                    Text(
                        "%.2f".format(totalCr),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        color = if (isBalanced) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                    )
                    Spacer(Modifier.width(40.dp))
                }
                
                if (!isBalanced && totalDr > 0) {
                    Text(
                        "⚠ DR and CR totals must be equal to save",
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.caption
                    )
                }
                
                // Save Button
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    if (!isBalanced) {
                                        errorMsg = "DR and CR totals must be equal."
                                        return@launch
                                    }
                                    
                                    val entries = lines.filter { 
                                        it.ledgerId != null && ((it.drAmount.toDoubleOrNull() ?: 0.0) > 0 || (it.crAmount.toDoubleOrNull() ?: 0.0) > 0) 
                                    }.map { line ->
                                        Triple(line.ledgerId!!, line.drAmount.toDoubleOrNull() ?: 0.0, line.crAmount.toDoubleOrNull() ?: 0.0)
                                    }
                                    
                                    if (entries.size < 2) {
                                        errorMsg = "At least 2 entries required."
                                        return@launch
                                    }
                                    
                                    AccountingRepository.saveJournalVoucher(
                                        voucherNo = voucherNo,
                                        date = voucherDate,
                                        narration = narration,
                                        entries = entries
                                    )
                                    
                                    // Auto-Generate PDF
                                    val pdfPath = com.sanship.utils.DocumentPaths.getJournalVoucherPath("${voucherNo.replace("/","_")}.pdf")
                                    try {
                                        val debitsList = entries.filter { it.second > 0 }.map { entry ->
                                            Pair(allLedgers.find { l -> l.first == entry.first }?.second ?: "Unknown", entry.second)
                                        }
                                        val creditsList = entries.filter { it.third > 0 }.map { entry ->
                                            Pair(allLedgers.find { l -> l.first == entry.first }?.second ?: "Unknown", entry.third)
                                        }
                                        val totalAmt = entries.sumOf { it.second }
                                        
                                        val data = com.sanship.services.AccountingVoucherPdfService.VoucherData(
                                            title = "JOURNAL VOUCHER",
                                            voucherNo = voucherNo,
                                            date = voucherDate,
                                            mainLabel = "",
                                            mainValue = "",
                                            amount = totalAmt,
                                            mode = "Journal",
                                            narration = narration,
                                            isJournal = true,
                                            debits = debitsList,
                                            credits = creditsList
                                        )
                                        com.sanship.services.AccountingVoucherPdfService.generateVoucherPDF(data, pdfPath)
                                        successMsg = "Journal Saved & PDF Generated: $pdfPath"
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        successMsg = "Journal Saved but PDF Failed: ${e.message}"
                                    }
                                    
                                    errorMsg = ""
                                    
                                    // Reset
                                    lines = listOf(JournalLine(), JournalLine())
                                    narration = ""
                                    voucherNo = "JV/${LocalDate.now().year}/${System.currentTimeMillis() % 1000}"
                                    
                                } catch (e: Exception) {
                                    errorMsg = "Error: ${e.message}"
                                }
                            }
                        },
                        enabled = isBalanced
                    ) {
                        Text("Save Journal Entry")
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

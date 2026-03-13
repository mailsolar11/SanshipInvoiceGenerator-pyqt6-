package com.sanship.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanship.data.AccountingRepository
import com.sanship.data.AccountingRepository.LedgerReportItem

@Composable
fun CashBankBookScreen() {
    var ledgers by remember { mutableStateOf(emptyMap<Int, String>()) }
    var selectedLedgerId by remember { mutableStateOf<Int?>(null) }
    var reportItems by remember { mutableStateOf(emptyList<LedgerReportItem>()) }
    var expanded by remember { mutableStateOf(false) }

    // Init Logic
    LaunchedEffect(Unit) {
        ledgers = AccountingRepository.getCashAndBankLedgers()
    }

    LaunchedEffect(selectedLedgerId) {
        if (selectedLedgerId != null) {
            reportItems = AccountingRepository.getLedgerEntries(selectedLedgerId!!)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)) {
        Column {
            Text("Cash & Bank Book", style = MaterialTheme.typography.h4)
            Text("Running balance for cash and bank accounts.", style = MaterialTheme.typography.subtitle1, color = Color.Gray)
            Spacer(Modifier.height(16.dp))

            // Selector
            Box {
                OutlinedTextField(
                    value = ledgers[selectedLedgerId] ?: "Select Cash / Bank Ledger",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { IconButton(onClick = { expanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ledgers.forEach { (id, name) ->
                        DropdownMenuItem(onClick = { selectedLedgerId = id; expanded = false }) {
                            Text(name)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Table Header
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFE0E0E0)).padding(12.dp)) {
                Text("Date", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                Text("Voucher No", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                Text("Narration", modifier = Modifier.weight(3f), fontWeight = FontWeight.Bold)
                Text("Debit", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                Text("Credit", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                Text("Running Balance", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
            }

            // Table Content
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn {
                    var runningBalance = 0.0
                    
                    itemsIndexed(reportItems) { index, item ->
                        // Calculate running balance based on Dr and Cr
                        runningBalance += (item.dr - item.cr)
                        
                        val bkgColor = if (index % 2 == 0) Color.White else Color(0xFFFAFAFA)
                        Column(modifier = Modifier.background(bkgColor)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(item.date, modifier = Modifier.weight(1.5f))
                                Text(item.voucherNo, modifier = Modifier.weight(1.5f))
                                Text(item.narration ?: "", modifier = Modifier.weight(3f))
                                Text(if (item.dr > 0) "%.2f".format(item.dr) else "", modifier = Modifier.weight(1.5f))
                                Text(if (item.cr > 0) "%.2f".format(item.cr) else "", modifier = Modifier.weight(1.5f))
                                
                                val balText = if (runningBalance >= 0) "%.2f Dr".format(runningBalance) else "%.2f Cr".format(-runningBalance)
                                val balColor = if (runningBalance >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                
                                Text(balText, modifier = Modifier.weight(1.5f), color = balColor, fontWeight = FontWeight.SemiBold)
                            }
                            Divider()
                        }
                    }
                    
                    if (reportItems.isEmpty() && selectedLedgerId != null) {
                        item {
                            Text("No transactions found for this ledger.", modifier = Modifier.padding(16.dp), color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

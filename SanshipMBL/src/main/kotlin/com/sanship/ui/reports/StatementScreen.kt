package com.sanship.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun StatementScreen() {
    var ledgers by remember { mutableStateOf(emptyMap<Int, String>()) }
    var selectedLedgerId by remember { mutableStateOf<Int?>(null) }
    var reportItems by remember { mutableStateOf(emptyList<LedgerReportItem>()) }
    var expanded by remember { mutableStateOf(false) }

    // Init Logic
    LaunchedEffect(Unit) {
        ledgers = AccountingRepository.getAllLedgers()
    }

    LaunchedEffect(selectedLedgerId) {
        if (selectedLedgerId != null) {
            reportItems = AccountingRepository.getLedgerEntries(selectedLedgerId!!)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)) {
        Column {
            Text("Account Statement", style = MaterialTheme.typography.h4)
            Spacer(Modifier.height(16.dp))

            // Selector
            com.sanship.ui.components.SearchableDropdown(
                label = "Select Ledger",
                items = ledgers.toList(),
                selectedItem = ledgers.toList().find { it.first == selectedLedgerId },
                itemToString = { it.second },
                onItemSelected = { selectedLedgerId = it?.first },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            // Table Header
            Row(modifier = Modifier.fillMaxWidth().background(Color.LightGray).padding(8.dp)) {
                Text("Date", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Voucher No", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Narration", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
                Text("Debit", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Credit", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }

            // Table Content
            LazyColumn {
                items(reportItems) { item ->
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Text(item.date, modifier = Modifier.weight(1f))
                            Text(item.voucherNo, modifier = Modifier.weight(1f))
                            Text(item.narration, modifier = Modifier.weight(2f))
                            Text(if (item.dr > 0) "%.2f".format(item.dr) else "", modifier = Modifier.weight(1f))
                            Text(if (item.cr > 0) "%.2f".format(item.cr) else "", modifier = Modifier.weight(1f))
                        }
                        Divider()
                    }
                }
                
                // Summary Footer
                item {
                    val totalDr = reportItems.sumOf { it.dr }
                    val totalCr = reportItems.sumOf { it.cr }
                    val balance = totalDr - totalCr
                    
                    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFE0E0E0)).padding(16.dp)) {
                        Text("TOTAL", modifier = Modifier.weight(4f), fontWeight = FontWeight.Bold)
                        Text("%.2f".format(totalDr), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("%.2f".format(totalCr), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                        Text(
                            "Closing Balance: ${if (balance >= 0) "%.2f Dr".format(balance) else "%.2f Cr".format(-balance)}",
                            style = MaterialTheme.typography.h6,
                            color = if (balance >= 0) Color.Red else Color.Green
                        )
                    }
                }
            }
        }
    }
}

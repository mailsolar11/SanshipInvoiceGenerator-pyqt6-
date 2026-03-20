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
import com.sanship.data.AccountingRepository.BankReconItem
import kotlinx.coroutines.launch

@Composable
fun BankReconScreen() {
    var ledgers by remember { mutableStateOf(emptyMap<Int, String>()) }
    var selectedLedgerId by remember { mutableStateOf<Int?>(null) }
    var reportItems by remember { mutableStateOf(emptyList<BankReconItem>()) }
    var expanded by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()

    // Init Logic
    LaunchedEffect(Unit) {
        // Technically Bank Reconciliation is only for Bank Accounts, but we can reuse the cash/bank query
        // and let users filter manually.
        ledgers = AccountingRepository.getCashAndBankLedgers()
    }

    fun loadEntries() {
        if (selectedLedgerId != null) {
            reportItems = AccountingRepository.getBankReconEntries(selectedLedgerId!!)
        }
    }

    LaunchedEffect(selectedLedgerId) {
        loadEntries()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)) {
        Column {
            Text("Bank Reconciliation", style = MaterialTheme.typography.h4)
            Text("Clear transactions by entering the statement date", style = MaterialTheme.typography.subtitle1, color = Color.Gray)
            Spacer(Modifier.height(16.dp))

            // Selector
            com.sanship.ui.components.SearchableDropdown(
                label = "Select Bank Account",
                items = ledgers.toList(),
                selectedItem = ledgers.toList().find { it.first == selectedLedgerId },
                itemToString = { it.second },
                onItemSelected = { selectedLedgerId = it?.first },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            // Table Header
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFE0E0E0)).padding(10.dp)) {
                Text("Date", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Voucher No", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Narration", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
                Text("Withdrawal (Cr)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Deposit (Dr)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Bank Date", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
            }

            // Table Content
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(reportItems) { item ->
                        var bankDateText by remember(item.bankDate) { mutableStateOf(item.bankDate ?: "") }
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(item.date, modifier = Modifier.weight(1f))
                            Text(item.voucherNo, modifier = Modifier.weight(1f))
                            Text(item.narration, modifier = Modifier.weight(2f))
                            Text(if (item.cr > 0) "%.2f".format(item.cr) else "", modifier = Modifier.weight(1f), color = Color(0xFFD32F2F))
                            Text(if (item.dr > 0) "%.2f".format(item.dr) else "", modifier = Modifier.weight(1f), color = Color(0xFF2E7D32))
                            
                            OutlinedTextField(
                                value = bankDateText,
                                onValueChange = { newVal ->
                                    bankDateText = newVal
                                },
                                placeholder = { Text("YYYY-MM-DD") },
                                singleLine = true,
                                modifier = Modifier.weight(1.5f).height(50.dp)
                            )
                            
                            Spacer(Modifier.width(8.dp))
                            
                            Button(
                                onClick = {
                                    scope.launch {
                                        val dateToSave = if (bankDateText.isBlank()) null else bankDateText.trim()
                                        AccountingRepository.updateBankDate(item.entryId, dateToSave)
                                        // We don't necessarily need to reload whole list, UI is updated optimistically
                                    }
                                }
                            ) {
                                Text("Save")
                            }
                        }
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    
                    if (reportItems.isEmpty() && selectedLedgerId != null) {
                        item {
                            Text("No transactions found.", modifier = Modifier.padding(16.dp), color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

package com.sanship.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanship.data.AccountingRepository

@Composable
fun ChartOfAccountsScreen() {
    var accounts by remember { mutableStateOf<List<AccountingRepository.ChartOfAccountItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        accounts = AccountingRepository.getChartOfAccounts()
        loaded = true
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)
    ) {
        Text("Chart of Accounts", style = MaterialTheme.typography.h4)
        Spacer(Modifier.height(20.dp))
        
        if (!loaded) {
            CircularProgressIndicator()
        } else {
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth().weight(1f)) {
                
                val grouped = accounts.groupBy { it.groupName }
                
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    grouped.forEach { (groupName, ledgers) ->
                        item {
                            AccountGroupRow(groupName, ledgers)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountGroupRow(groupName: String, ledgers: List<AccountingRepository.ChartOfAccountItem>) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight, null)
            }
            Text(groupName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        
        if (expanded) {
            Column(modifier = Modifier.padding(start = 48.dp, top = 8.dp, bottom = 8.dp)) {
                // Table Header for Ledgers
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text("Ledger Name", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("Balance", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.5f))
                }
                Divider()
                
                ledgers.forEach { ledger ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(ledger.ledgerName, modifier = Modifier.weight(1f))
                        
                        // Format Balance: Positive = DR, Negative = CR
                        val bal = ledger.balance
                        val color = if (bal >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                        val text = if (bal >= 0) "₹ ${"%.2f".format(bal)} Dr" else "₹ ${"%.2f".format(Math.abs(bal))} Cr"
                        
                        Text(text, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.5f))
                    }
                }
            }
        }
    }
}

package com.sanship.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanship.data.AccountingRepository
import kotlinx.coroutines.launch

data class OutstandingEntry(
    val partyName: String,
    val totalAmount: Double,
    val bucket0to30: Double = 0.0,
    val bucket31to60: Double = 0.0,
    val bucket61to90: Double = 0.0,
    val bucketOver90: Double = 0.0
)

@Composable
fun OutstandingScreen() {
    val scope = rememberCoroutineScope()
    
    var selectedTab by remember { mutableStateOf(0) }
    var receivables by remember { mutableStateOf(emptyList<OutstandingEntry>()) }
    var payables by remember { mutableStateOf(emptyList<OutstandingEntry>()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    fun loadData() {
        scope.launch {
            isLoading = true
            try {
                receivables = AccountingRepository.getOutstandingReceivables()
                payables = AccountingRepository.getOutstandingPayables()
                errorMsg = ""
            } catch (e: Exception) {
                errorMsg = "Error loading data: ${e.message}"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Outstanding Reports", style = MaterialTheme.typography.h4)
            IconButton(onClick = { loadData() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        Spacer(Modifier.height(16.dp))
        
        // Tabs
        TabRow(selectedTabIndex = selectedTab, backgroundColor = Color.White) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Receivables (Debtors)", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Payables (Creditors)", modifier = Modifier.padding(12.dp))
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (errorMsg.isNotBlank()) {
            Text(errorMsg, color = MaterialTheme.colors.error)
        } else {
            val data = if (selectedTab == 0) receivables else payables
            val totalOutstanding = data.sumOf { it.totalAmount }
            
            // Summary Card
            Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (selectedTab == 0) "Total Receivable" else "Total Payable",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "₹ ${"%.2f".format(totalOutstanding)}",
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Table Header
            Card(elevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFE3F2FD)).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Party Name", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
                        Text("0-30 Days", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("31-60 Days", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("61-90 Days", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("90+ Days", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("Total", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    }
                    
                    Divider()
                    
                    if (data.isEmpty()) {
                        Text(
                            "No outstanding entries found.",
                            modifier = Modifier.padding(16.dp),
                            color = Color.Gray
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 500.dp)) {
                            items(data) { entry ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(entry.partyName, modifier = Modifier.weight(2f))
                                    Text("%.2f".format(entry.bucket0to30), modifier = Modifier.weight(1f))
                                    Text("%.2f".format(entry.bucket31to60), modifier = Modifier.weight(1f))
                                    Text("%.2f".format(entry.bucket61to90), modifier = Modifier.weight(1f))
                                    Text(
                                        "%.2f".format(entry.bucketOver90),
                                        modifier = Modifier.weight(1f),
                                        color = if (entry.bucketOver90 > 0) Color(0xFFD32F2F) else Color.Black
                                    )
                                    Text(
                                        "%.2f".format(entry.totalAmount),
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}

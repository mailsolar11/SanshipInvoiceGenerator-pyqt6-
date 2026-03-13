package com.sanship.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanship.data.AccountingRepository

@Composable
fun JobProfitabilityScreen() {
    var jobItems by remember { mutableStateOf(emptyList<AccountingRepository.JobProfitabilityItem>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Load data in background
        try {
            jobItems = AccountingRepository.getJobProfitability()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)
    ) {
        Text("Job Profitability Report", style = MaterialTheme.typography.h4)
        Spacer(Modifier.height(8.dp))
        Text("Income vs Expense Analysis per Job", style = MaterialTheme.typography.subtitle1, color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        
        Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFE0E0E0)).padding(8.dp)) {
                    Text("Job No", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Shipper", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
                    Text("Revenue (Income)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Cost (Expense)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Profit / Margin", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }
                
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        jobItems.forEach { item ->
                            Divider()
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Text(item.jobNo, modifier = Modifier.weight(1f))
                                Text(item.shipper, modifier = Modifier.weight(2f))
                                Text(formatCurrency(item.income), modifier = Modifier.weight(1f), color = Color(0xFF2E7D32)) // Green
                                Text(formatCurrency(item.expense), modifier = Modifier.weight(1f), color = Color(0xFFC62828)) // Red
                                
                                val profitColor = if (item.profit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                Text(formatCurrency(item.profit), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = profitColor)
                            }
                        }
                        
                        if (jobItems.isEmpty()) {
                            Text("No job data found.", modifier = Modifier.padding(16.dp))
                        } else {
                            // Grand Total Row
                            Divider(thickness = 2.dp)
                            val totalIncome = jobItems.sumOf { it.income }
                            val totalExpense = jobItems.sumOf { it.expense }
                            val totalProfit = jobItems.sumOf { it.profit }
                            
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp).background(Color(0xFFEEEEEE))) {
                                Text("TOTAL", modifier = Modifier.weight(3f), fontWeight = FontWeight.Bold)
                                Text(formatCurrency(totalIncome), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                Text(formatCurrency(totalExpense), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                Text(formatCurrency(totalProfit), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = if (totalProfit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatCurrency(amount: Double): String {
    return "%.2f".format(amount)
}

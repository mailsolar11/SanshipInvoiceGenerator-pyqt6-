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
fun ProfitLossScreen() {
    var tbItems by remember { mutableStateOf(emptyList<AccountingRepository.TrialBalanceItem>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            tbItems = AccountingRepository.getTrialBalanceReport()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)
    ) {
        Text("Profit & Loss Account", style = MaterialTheme.typography.h4)
        Spacer(Modifier.height(8.dp))
        Text("For the year ending ${java.time.LocalDate.now()}", style = MaterialTheme.typography.subtitle1, color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        
        Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth().weight(1f)) {
            // T-Format: Left = Expenses, Right = Income
            Row(modifier = Modifier.fillMaxSize()) {
                
                // --- EXPENSES (Dr) ---
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                    Text("EXPENSES", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    Divider(thickness = 2.dp, color = Color.Gray)
                    
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        val expenseItems = tbItems.filter { it.nature == "EXPENSE" }
                        expenseItems.forEach { item ->
                            val amount = if(item.netBalance > 0) item.netBalance else -item.netBalance
                            // Typically Expense is Dr (Pos), so use Pos
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(item.ledgerName, modifier = Modifier.weight(1f))
                                Text(formatCurrency(amount))
                            }
                        }
                    }
                    
                    // Total Expenses
                    val totalExpense = tbItems.filter { it.nature == "EXPENSE" }.sumOf { kotlin.math.abs(it.netBalance) }
                    val totalIncome = tbItems.filter { it.nature == "INCOME" }.sumOf { kotlin.math.abs(it.netBalance) }
                    val netProfit = totalIncome - totalExpense
                    
                    if (netProfit >= 0) {
                        Divider()
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text("Net Profit", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(formatCurrency(netProfit), fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Divider(thickness = 2.dp)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("TOTAL", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(formatCurrency(if(netProfit >= 0) totalExpense + netProfit else totalExpense), fontWeight = FontWeight.Bold)
                    }
                }
                
                Divider(modifier = Modifier.fillMaxHeight().width(1.dp))
                
                // --- INCOME (Cr) ---
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                    Text("INCOME", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    Divider(thickness = 2.dp, color = Color.Gray)
                    
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        val incomeItems = tbItems.filter { it.nature == "INCOME" }
                        incomeItems.forEach { item ->
                            val amount = kotlin.math.abs(item.netBalance)
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(item.ledgerName, modifier = Modifier.weight(1f))
                                Text(formatCurrency(amount))
                            }
                        }
                    }
                    
                    val totalIncome = tbItems.filter { it.nature == "INCOME" }.sumOf { kotlin.math.abs(it.netBalance) }
                    val totalExpense = tbItems.filter { it.nature == "EXPENSE" }.sumOf { kotlin.math.abs(it.netBalance) }
                    val netProfit = totalIncome - totalExpense

                    if (netProfit < 0) {
                        Divider()
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text("Net Loss", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(formatCurrency(-netProfit), fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Divider(thickness = 2.dp)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("TOTAL", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(formatCurrency(if(netProfit < 0) totalIncome - netProfit else totalIncome), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

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
fun BalanceSheetScreen() {
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
        Text("Balance Sheet", style = MaterialTheme.typography.h4)
        Spacer(Modifier.height(8.dp))
        Text("As of ${java.time.LocalDate.now()}", style = MaterialTheme.typography.subtitle1, color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        
        Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth().weight(1f)) {
            // T-Format: Left = Liabilities, Right = Assets
            Row(modifier = Modifier.fillMaxSize()) {
                
                // --- LIABILITIES (Cr) ---
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                    Text("LIABILITIES", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    Divider(thickness = 2.dp, color = Color.Gray)
                    
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        val liabilityItems = tbItems.filter { it.nature == "LIABILITY" }
                        liabilityItems.forEach { item ->
                            val amount = kotlin.math.abs(item.netBalance)
                            // Liabilities are Credit balances, so negative in our system. But display positive.
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(item.ledgerName, modifier = Modifier.weight(1f))
                                Text(formatCurrency(amount))
                            }
                        }
                    }

                    // Net Profit (From P&L)
                    val totalIncome = tbItems.filter { it.nature == "INCOME" }.sumOf { it.netBalance } // Cr is negative
                    val totalExpense = tbItems.filter { it.nature == "EXPENSE" }.sumOf { it.netBalance } // Dr is positive
                    // P&L Net Balance = Sum(Income + Expense). Since Income is -ve and Expense is +ve:
                    // If Sum < 0 -> Profit (Cr). If Sum > 0 -> Loss (Dr).
                    // Actually, Net Profit logic:
                    // Income (Cr) - Expense (Dr) as absolute values?
                    // Let's use signed Logic:
                    // Profit = -(Sum(All Income) + Sum(All Expense))
                    // Example: Income -1000, Expense +200. Sum = -800. Profit is 800.
                    val pnlBalance = totalIncome + totalExpense
                    val netProfit = -pnlBalance // Convert to standard sign (Positive = Profit)

                    // Add Profit to Liabilities (Retained Earnings)
                     Divider()
                     Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("Profit & Loss A/c", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(formatCurrency(netProfit), fontWeight = FontWeight.SemiBold)
                     }
                    
                    val totalLiabilities = tbItems.filter { it.nature == "LIABILITY" }.sumOf { kotlin.math.abs(it.netBalance) }
                    val grandTotal = totalLiabilities + netProfit
                    
                    Divider(thickness = 2.dp)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("TOTAL", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(formatCurrency(grandTotal), fontWeight = FontWeight.Bold)
                    }
                }
                
                Divider(modifier = Modifier.fillMaxHeight().width(1.dp))
                
                // --- ASSETS (Dr) ---
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                    Text("ASSETS", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    Divider(thickness = 2.dp, color = Color.Gray)
                    
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        val assetItems = tbItems.filter { it.nature == "ASSET" }
                        assetItems.forEach { item ->
                            val amount = kotlin.math.abs(item.netBalance)
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(item.ledgerName, modifier = Modifier.weight(1f))
                                Text(formatCurrency(amount))
                            }
                        }
                    }
                    
                    val totalAssets = tbItems.filter { it.nature == "ASSET" }.sumOf { kotlin.math.abs(it.netBalance) }
                    
                    Divider(thickness = 2.dp)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("TOTAL", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(formatCurrency(totalAssets), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

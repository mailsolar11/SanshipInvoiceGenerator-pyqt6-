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
fun TrialBalanceScreen() {
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
        Text("Trial Balance", style = MaterialTheme.typography.h4)
        Spacer(Modifier.height(8.dp))
        Text("As of ${java.time.LocalDate.now()}", style = MaterialTheme.typography.subtitle1, color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        
        Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFE0E0E0)).padding(8.dp)) {
                    Text("Particulars", modifier = Modifier.weight(3f), fontWeight = FontWeight.Bold)
                    Text("Debit", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Credit", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }
                
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        var currentGroup = ""
                        
                        tbItems.forEach { item ->
                            if (item.groupName != currentGroup) {
                                currentGroup = item.groupName
                                Spacer(Modifier.height(8.dp))
                                Text(currentGroup.uppercase(), fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp))
                                Divider(color = Color.LightGray)
                            }
                            
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Text(item.ledgerName, modifier = Modifier.weight(3f))
                                
                                val drText = if (item.netBalance > 0) formatCurrency(item.netBalance) else ""
                                val crText = if (item.netBalance < 0) formatCurrency(-item.netBalance) else ""
                                
                                Text(drText, modifier = Modifier.weight(1f))
                                Text(crText, modifier = Modifier.weight(1f))
                            }
                        }
                        
                        // Grand Total
                        Divider(thickness = 2.dp)
                        val totalDr = tbItems.filter { it.netBalance > 0 }.sumOf { it.netBalance }
                        val totalCr = tbItems.filter { it.netBalance < 0 }.sumOf { -it.netBalance }
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp).background(Color(0xFFEEEEEE))) {
                            Text("GRAND TOTAL", modifier = Modifier.weight(3f), fontWeight = FontWeight.Bold)
                            Text(formatCurrency(totalDr), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(formatCurrency(totalCr), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        }
                        
                        // Status
                        val diff = kotlin.math.abs(totalDr - totalCr)
                        if (diff < 0.01) {
                            Text("✓ Balance Matches", color = Color(0xFF2E7D32), modifier = Modifier.padding(8.dp))
                        } else {
                            Text("⚠ Difference: ${formatCurrency(diff)}", color = Color.Red, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}

package com.sanship.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanship.data.AccountingRepository
import com.sanship.data.AccountingRepository.SalesRegisterItem
import com.sanship.services.GstReportService
import com.sanship.ui.components.DatePickerField
import java.time.LocalDate
import java.io.File
import com.sanship.utils.DocumentPaths
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.launch

@Composable
fun ReportScreen() {
    var salesItems by remember { mutableStateOf(emptyList<SalesRegisterItem>()) }
    var startDate by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) } // 1st of current Month
    var endDate by remember { mutableStateOf(LocalDate.now().toString()) } // Today
    var exportStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        salesItems = AccountingRepository.getSalesRegister()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            
            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reports & Compliance", style = MaterialTheme.typography.h4, color = Color(0xFF2C3E50))
            }
            
            // EXPORT GSTR-1 CARD
            Card(
                elevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("GSTR-1 Export (Government Excel Format)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            DatePickerField(
                                label = "Start Date", 
                                value = startDate, 
                                onValueChange = { startDate = it }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            DatePickerField(
                                label = "End Date", 
                                value = endDate, 
                                onValueChange = { endDate = it }
                            )
                        }
                        
                        Button(
                            onClick = {
                                val fileName = "GSTR1_Export_${startDate}_to_${endDate}.xlsx"
                                val path = DocumentPaths.getGstReportPath(fileName)
                                val result = GstReportService.generateGstr1(startDate, endDate, path)
                                exportStatus = result
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2E7D32)),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("EXPORT EXCEL", color = Color.White)
                        }
                    }
                    
                    if (exportStatus.isNotEmpty()) {
                        Text(
                            text = exportStatus,
                            color = if(exportStatus.startsWith("ERROR")) Color.Red else Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Sales Register Header with Export Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sales Register Preview", style = MaterialTheme.typography.h5, color = Color.Gray)
                
                Button(
                    onClick = {
                        val fileName = "Sales_Register_${startDate}_to_${endDate}.xlsx"
                        val path = DocumentPaths.getPath("Sales Registers", fileName) // Use general or add to DocumentPaths
                        val result = GstReportService.generateSalesRegisterExcel(salesItems, path)
                        exportStatus = result
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("EXPORT REGISTER", color = Color.White)
                }
            }

            // Table Header
            Row(modifier = Modifier.fillMaxWidth().background(Color.LightGray).padding(12.dp)) {
                Text("Date", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Invoice No", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Party / Narration", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
                Text("Amount", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }

            // List
            LazyColumn {
                items(salesItems) { item ->
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(item.date, modifier = Modifier.weight(1f))
                        Text(item.invoiceNo, modifier = Modifier.weight(1f))
                        Text(item.partyName, modifier = Modifier.weight(2f)) 
                        Text("₹ %.2f".format(item.totalAmount), modifier = Modifier.weight(1f))
                    }
                    Divider()
                }
                
                item {
                    val grandTotal = salesItems.sumOf { it.totalAmount }
                    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFE0E0E0)).padding(16.dp)) {
                        Text("TOTAL SALES", modifier = Modifier.weight(4f), fontWeight = FontWeight.Bold)
                        Text("₹ %.2f".format(grandTotal), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                }
            }
        }
    }
}

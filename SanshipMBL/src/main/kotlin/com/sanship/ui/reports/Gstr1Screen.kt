package com.sanship.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanship.services.GstReportService
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

@Composable
fun Gstr1Screen() {
    val scope = rememberCoroutineScope()
    
    var startDate by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var endDate by remember { mutableStateOf(LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toString()) }
    
    var resultMsg by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("GSTR-1 Report Export", style = MaterialTheme.typography.h4)
        }
        Spacer(Modifier.height(20.dp))
        
        Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                Text(
                    "Generate GSTR-1 Offline Excel Template", 
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
                Text(
                    "This will export B2B, B2CL, B2CS, and HSN Summary sheets suitable for the offline GST tool.",
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Start Date (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f),
                        trailingIcon = { Icon(Icons.Default.DateRange, null) }
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("End Date (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f),
                        trailingIcon = { Icon(Icons.Default.DateRange, null) }
                    )
                }
                
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            scope.launch {
                                val userHome = System.getProperty("user.home")
                                val outDir = File(userHome, "Downloads/Sanship/GST Reports")
                                if (!outDir.exists()) outDir.mkdirs()
                                
                                val fileName = "GSTR1_${startDate}_to_${endDate}.xlsx"
                                val outPath = File(outDir, fileName).absolutePath
                                
                                val msg = GstReportService.generateGstr1(startDate, endDate, outPath)
                                isError = msg.startsWith("Error")
                                resultMsg = msg
                            }
                        }
                    ) {
                        Icon(Icons.Default.List, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate GSTR-1")
                    }
                }
            }
        }
        
        if (resultMsg.isNotBlank()) {
            Spacer(Modifier.height(24.dp))
            Card(
                backgroundColor = if (isError) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                elevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    resultMsg, 
                    color = if (isError) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

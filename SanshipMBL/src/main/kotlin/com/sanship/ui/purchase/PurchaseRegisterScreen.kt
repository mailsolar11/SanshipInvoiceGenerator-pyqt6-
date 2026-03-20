package com.sanship.ui.purchase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanship.data.PurchaseHeader
import com.sanship.data.PurchaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PurchaseRegisterScreen() {
    val scope = rememberCoroutineScope()
    var purchases by remember { mutableStateOf(emptyList<PurchaseHeader>()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val list = PurchaseRepository.getAllPurchases()
            withContext(Dispatchers.Main) {
                purchases = list
                isLoading = false
            }
        }
    }

    val filteredPurchases = purchases.filter {
        it.purchaseNo.contains(searchQuery, ignoreCase = true) ||
        it.vendorName.contains(searchQuery, ignoreCase = true) ||
        it.jobNo.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Purchase Register", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E))
                Text("History of all inward invoices", fontSize = 14.sp, color = Color.Gray)
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by No, Vendor, Job...") },
                modifier = Modifier.width(300.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = Color.White)
            )
        }

        Spacer(Modifier.height(24.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF6C5CE7))
            }
        } else if (filteredPurchases.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No purchase invoices found.", color = Color.Gray)
            }
        } else {
            Card(elevation = 2.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FA)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Date", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text("Invoice No", Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text("Vendor", Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text("Job No", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text("Amount", Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.width(48.dp)) // Action button space
                    }

                    filteredPurchases.forEach { p ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(p.date, Modifier.weight(1f), fontSize = 14.sp)
                            Text(p.purchaseNo, Modifier.weight(1.2f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(p.vendorName, Modifier.weight(2f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(p.jobNo.ifEmpty { "—" }, Modifier.weight(1f), fontSize = 14.sp)
                            Text(currencyFormat.format(p.grandTotal), Modifier.weight(1.2f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E))
                            
                            IconButton(onClick = { /* TODO: View/Edit Details */ }) {
                                Icon(Icons.Default.Visibility, null, tint = Color(0xFF6C5CE7), modifier = Modifier.size(20.dp))
                            }
                        }
                        Divider(color = Color(0xFFF0F0F0))
                    }
                }
            }
        }
    }
}

package com.sanship.ui.invoice

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
import com.sanship.data.InvoiceModels
import com.sanship.data.InvoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

@Composable
fun InvoiceSearchScreen() {
    val scope = rememberCoroutineScope()
    var invoices by remember { mutableStateOf(emptyList<InvoiceModels.InvoiceHeader>()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedInvoice by remember { mutableStateOf<InvoiceModels.InvoiceHeader?>(null) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val list = InvoiceRepository.getAllInvoices()
            withContext(Dispatchers.Main) {
                invoices = list
                isLoading = false
            }
        }
    }

    val filteredInvoices = if (selectedInvoice != null) listOf(selectedInvoice!!) else invoices

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
                Text("Sales Register & Invoice Search", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E))
                Text("History of all sales documents (Tax Invoice, Debit/Credit Notes)", fontSize = 14.sp, color = Color.Gray)
            }

            com.sanship.ui.components.SearchableDropdown(
                label = "Search by Inv No, Type, Customer...",
                items = invoices,
                selectedItem = selectedInvoice,
                itemToString = { "${it.invoiceNo} | ${it.documentType} | ${it.customerName.take(15)}" },
                onItemSelected = { selectedInvoice = it },
                modifier = Modifier.width(350.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF6C5CE7))
            }
        } else if (filteredInvoices.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No sales invoices found.", color = Color.Gray)
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
                        Text("Type", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text("Customer", Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text("Job No", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Text("Amount", Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.width(48.dp)) // Action button space
                    }

                    filteredInvoices.forEach { inv ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(inv.invoiceDate, Modifier.weight(1f), fontSize = 14.sp)
                            Text(inv.invoiceNo, Modifier.weight(1.2f), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6C5CE7))
                            Text(inv.documentType, Modifier.weight(1f), fontSize = 12.sp)
                            Text(inv.customerName.ifEmpty { "—" }, Modifier.weight(2f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(inv.jobNo.ifEmpty { "—" }, Modifier.weight(1f), fontSize = 14.sp)
                            Text(currencyFormat.format(inv.grandTotal), Modifier.weight(1.2f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E))
                            
                            IconButton(onClick = { /* TODO: View/Edit Details */ }) {
                                Icon(Icons.Default.Visibility, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }
                        Divider(color = Color(0xFFF0F0F0))
                    }
                }
            }
        }
    }
}

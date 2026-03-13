package com.sanship.ui.invoice

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanship.data.InvoiceModels.InvoiceItem

@Composable
fun InvoiceScreen() {
    val viewModel = remember { InvoiceViewModel() }
    val data = viewModel.invoiceData
    
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9))) {
        val scrollState = rememberScrollState()
        
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            
            // --- HEADER ---
            Text("Tax Invoice", style = MaterialTheme.typography.h4, color = Color(0xFF333333))
            Spacer(Modifier.height(16.dp))
            
            // --- TOP CARD (Basic Info) ---
            Card(elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = data.invoiceNo,
                            onValueChange = { viewModel.updateField(invoiceNo = it) },
                            label = { Text("Invoice No") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = data.date,
                            onValueChange = { viewModel.updateField(date = it) },
                            label = { Text("Date (YYYY-MM-DD)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = data.customerName,
                            onValueChange = { viewModel.updateField(customerName = it) },
                            label = { Text("Customer Name") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = data.gstin,
                            onValueChange = { viewModel.updateField(gstin = it) },
                            label = { Text("GSTIN") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = data.billingAddress,
                        onValueChange = { viewModel.updateField(billingAddress = it) },
                        label = { Text("Billing Address") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // --- ITEMS TABLE HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE0E0E0))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Description", modifier = Modifier.weight(3f))
                Text("HSN", modifier = Modifier.weight(1f))
                Text("Qty", modifier = Modifier.weight(0.8f))
                Text("Rate", modifier = Modifier.weight(1f))
                Text("Total", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(40.dp)) // Action space
            }
            
            // --- ITEMS LIST ---
            data.items.forEachIndexed { index, item ->
                InvoiceItemRow(
                    item = item,
                    onUpdate = { updated -> viewModel.updateItem(index, updated) },
                    onDelete = { viewModel.removeItem(index) }
                )
                Divider()
            }
            
            Spacer(Modifier.height(10.dp))
            Button(onClick = { viewModel.addItem() }) {
                Icon(Icons.Default.Add, "Add Item")
                Spacer(Modifier.width(4.dp))
                Text("Add Row")
            }
            
            Spacer(Modifier.height(20.dp))
            
            // --- TOTALS ---
            Card(modifier = Modifier.fillMaxWidth().background(Color.White), elevation = 4.dp) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                    Text("Taxable Amount:  ₹ ${"%.2f".format(data.taxableAmount)}")
                    Text("CGST:  ₹ ${"%.2f".format(data.cgstAmount)}")
                    Text("SGST:  ₹ ${"%.2f".format(data.sgstAmount)}")
                    Text("IGST:  ₹ ${"%.2f".format(data.igstAmount)}")
                    Divider(Modifier.padding(vertical = 8.dp))
                    Text(
                        "Grand Total:  ₹ ${"%.2f".format(data.grandTotal)}",
                        style = MaterialTheme.typography.h5,
                        color = Color(0xFF0078D4)
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // --- ACTIONS ---
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { viewModel.save() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF0078D4), contentColor = Color.White),
                    modifier = Modifier.height(50.dp).width(150.dp)
                ) {
                    Text("SAVE INVOICE")
                }
            }
            
            if (viewModel.showSuccessMessage) {
                AlertDialog(
                    onDismissRequest = { viewModel.showSuccessMessage = false },
                    title = { Text("Success") },
                    text = { Text("Invoice Saved Successfully!") },
                    confirmButton = {
                        Button(onClick = { viewModel.showSuccessMessage = false }) { Text("OK") }
                    }
                )
            }
            
            if (viewModel.errorMessage.isNotBlank()) {
                Text(viewModel.errorMessage, color = Color.Red, modifier = Modifier.padding(top = 10.dp))
            }
            
            Spacer(Modifier.height(50.dp))
        }
        
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}

@Composable
fun InvoiceItemRow(
    item: InvoiceItem,
    onUpdate: (InvoiceItem) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Description
        OutlinedTextField(
            value = item.description,
            onValueChange = { onUpdate(item.copy(description = it)) },
            modifier = Modifier.weight(3f),
            singleLine = true
        )
        
        // HSN
        OutlinedTextField(
            value = item.hsnSac,
            onValueChange = { onUpdate(item.copy(hsnSac = it)) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        
        // Qty
        OutlinedTextField(
            value = if (item.qty == 0.0) "" else item.qty.toString(),
            onValueChange = { onUpdate(item.copy(qty = it.toDoubleOrNull() ?: 0.0)) },
            modifier = Modifier.weight(0.8f),
            singleLine = true
        )
        
        // Rate
        OutlinedTextField(
            value = if (item.rate == 0.0) "" else item.rate.toString(),
            onValueChange = { onUpdate(item.copy(rate = it.toDoubleOrNull() ?: 0.0)) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        
        // Total (Read Only)
        Text(
            text = "₹ ${"%2f".format(item.totalAmt)}",
            modifier = Modifier.weight(1f),
            color = Color.DarkGray
        )
        
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
        }
    }
}

package com.sanship.ui.debitnote

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sanship.ui.invoice.InvoiceItemRow
import com.sanship.ui.invoice.InvoiceViewModel

@Composable
fun DebitNoteScreen() {
    // Reuse InvoiceViewModel but we might want to flag it as Debit Note in DB later
    // For now, using same logic as requested
    val viewModel = remember { InvoiceViewModel() } 
    val data = viewModel.invoiceData
    
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFF4F4))) { // Slight Red tint for Debit Note
        val scrollState = rememberScrollState()
        
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            
            Text("Debit Note", style = MaterialTheme.typography.h4, color = Color(0xFFC62828))
            Text("Supplementary Invoice", style = MaterialTheme.typography.caption)
            Spacer(Modifier.height(16.dp))
            
            // --- TOP CARD ---
            Card(elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = data.invoiceNo,
                            onValueChange = { viewModel.updateField(invoiceNo = it) },
                            label = { Text("Debit Note No") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = data.date,
                            onValueChange = { viewModel.updateField(date = it) },
                            label = { Text("Date") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = data.customerName,
                        onValueChange = { viewModel.updateField(customerName = it) },
                        label = { Text("Customer / Party") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = data.gstin,
                        onValueChange = { viewModel.updateField(gstin = it) },
                        label = { Text("Original Invoice No (optional)") }, // Debit specific
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Reusing Invoice Rows logic
            data.items.forEachIndexed { index, item ->
                InvoiceItemRow(
                    item = item,
                    onUpdate = { updated -> viewModel.updateItem(index, updated) },
                    onDelete = { viewModel.removeItem(index) }
                )
                Divider()
            }
            
            Spacer(Modifier.height(10.dp))
            Button(onClick = { viewModel.addItem() }, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFC62828), contentColor = Color.White)) {
                Icon(Icons.Default.Add, "Add Item")
                Text("Add Row")
            }
            
            Spacer(Modifier.height(20.dp))
            
            // --- TOTALS ---
            Card(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                    Text("Grand Total:  ₹ ${"%.2f".format(data.grandTotal)}", style = MaterialTheme.typography.h5, color = Color(0xFFC62828))
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { viewModel.save() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFC62828), contentColor = Color.White),
                    modifier = Modifier.height(50.dp).width(200.dp)
                ) {
                    Text("SAVE DEBIT NOTE")
                }
            }
        }
        
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}

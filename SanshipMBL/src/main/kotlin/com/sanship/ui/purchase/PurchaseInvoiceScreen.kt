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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanship.models.Job
import com.sanship.data.VendorMaster
import com.sanship.data.PurchaseItem
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PurchaseInvoiceScreen() {
    val scope = rememberCoroutineScope()
    val viewModel = remember { PurchaseInvoiceViewModel(scope) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- Header Section ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("New Purchase Invoice", style = MaterialTheme.typography.h4, color = Color(0xFF1A1A2E))
                Text("Manage inward bills with multi-currency support", fontSize = 14.sp, color = Color.Gray)
            }
            
            Button(
                onClick = { viewModel.save() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF6C5CE7), contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                enabled = !viewModel.isSaving
            ) {
                if (viewModel.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Purchase")
                }
            }
        }

        if (viewModel.errorMessage != null) {
            Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp)) {
                Text(viewModel.errorMessage!!, color = Color.Red, modifier = Modifier.padding(16.dp))
            }
        }

        if (viewModel.successMessage != null) {
            Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp)) {
                Text(viewModel.successMessage!!, color = Color(0xFF2E7D32), modifier = Modifier.padding(16.dp))
            }
        }

        // --- Details Section ---
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Card(elevation = 2.dp, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Transaction Details", fontWeight = FontWeight.SemiBold, color = Color(0xFF6C5CE7))
                    OutlinedTextField(
                        value = viewModel.purchaseNo,
                        onValueChange = { viewModel.purchaseNo = it },
                        label = { Text("Vendor Invoice No") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = viewModel.date,
                        onValueChange = { viewModel.date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val selectedCurrencyObj = viewModel.currencies.find { it.code == viewModel.selectedCurrency }
                        com.sanship.ui.components.SearchableDropdown(
                            label = "Currency",
                            items = viewModel.currencies,
                            selectedItem = selectedCurrencyObj,
                            itemToString = { "${it.code} - ${it.name}" },
                            onItemSelected = { c -> if (c != null) viewModel.onCurrencyChange(c.code) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = viewModel.exchangeRate.toString(),
                            onValueChange = { viewModel.exchangeRate = it.toDoubleOrNull() ?: 1.0 },
                            label = { Text("Ex. Rate") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = viewModel.narration,
                        onValueChange = { viewModel.narration = it },
                        label = { Text("Narration") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            }

            Card(elevation = 2.dp, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Vendor & Job Mapping", fontWeight = FontWeight.SemiBold, color = Color(0xFF6C5CE7))
                    
                    com.sanship.ui.components.SearchableDropdown(
                        label = "Vendor Search",
                        items = viewModel.vendors,
                        selectedItem = viewModel.selectedVendor,
                        itemToString = { it.fullName },
                        onItemSelected = { v -> viewModel.selectedVendor = v },
                        modifier = Modifier.fillMaxWidth()
                    )

                    com.sanship.ui.components.SearchableDropdown(
                        label = "Job No",
                        items = viewModel.jobs,
                        selectedItem = viewModel.selectedJob,
                        itemToString = { it.jobNo },
                        onItemSelected = { j -> viewModel.selectedJob = j },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // --- Line Items Table ---
        Card(elevation = 2.dp, shape = RoundedCornerShape(8.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Voucher Line Items", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)).padding(8.dp)) {
                    Text("Description", modifier = Modifier.weight(3f), fontWeight = FontWeight.Bold)
                    Text("HSN", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Qty", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text("Rate (${viewModel.selectedCurrency})", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text("Taxable (INR)", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text("Total (INR)", modifier = Modifier.weight(1.4f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Spacer(Modifier.width(40.dp))
                }

                viewModel.items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = item.description,
                            onValueChange = { s: String -> viewModel.updateItem(index, item.copy(description = s)) },
                            modifier = Modifier.weight(3f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = item.hsnSac,
                            onValueChange = { s: String -> viewModel.updateItem(index, item.copy(hsnSac = s)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = if (item.qty == 0.0) "" else item.qty.toString(),
                            onValueChange = { s: String -> viewModel.updateItem(index, item.copy(qty = s.toDoubleOrNull() ?: 1.0)) },
                            modifier = Modifier.weight(0.8f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = if (item.rate == 0.0) "" else item.rate.toString(),
                            onValueChange = { s: String -> viewModel.updateItem(index, item.copy(rate = s.toDoubleOrNull() ?: 0.0)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Text(currencyFormat.format(item.taxableAmount), Modifier.weight(1.2f), textAlign = TextAlign.End, fontSize = 14.sp)
                        Text(currencyFormat.format(item.totalAmount), Modifier.weight(1.4f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        IconButton(onClick = { viewModel.removeItem(index) }) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.6f))
                        }
                    }
                    Divider()
                }

                TextButton(onClick = { viewModel.addItem() }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Row")
                }
            }
        }

        // --- Totals ---
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            val totalTaxable = viewModel.items.sumOf { it.taxableAmount }
            val totalCGST = viewModel.items.sumOf { it.cgstAmount }
            val totalSGST = viewModel.items.sumOf { it.sgstAmount }
            val totalIGST = viewModel.items.sumOf { it.igstAmount }
            val grandTotal = viewModel.items.sumOf { it.totalAmount }

            Text("Total Taxable (INR): ${currencyFormat.format(totalTaxable)}")
            if (totalCGST > 0) Text("CGST Input: ${currencyFormat.format(totalCGST)}")
            if (totalSGST > 0) Text("SGST Input: ${currencyFormat.format(totalSGST)}")
            if (totalIGST > 0) Text("IGST Input: ${currencyFormat.format(totalIGST)}")
            Divider(Modifier.padding(vertical = 8.dp).width(250.dp))
            Text(
                "Grand Total (INR): ${currencyFormat.format(grandTotal)}",
                style = MaterialTheme.typography.h5,
                color = Color(0xFF6C5CE7)
            )
        }
        
        Spacer(Modifier.height(50.dp))
    }
}

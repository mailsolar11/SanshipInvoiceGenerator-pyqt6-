package com.sanship.ui.invoice

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanship.data.InvoiceModels.InvoiceItem
import com.sanship.models.ChargeMaster
import com.sanship.viewmodels.InvoiceFormViewModel
import java.text.DecimalFormat

/**
 * Invoice Form Components Part 2
 * Item row, totals, and helper components
 */

@Composable
fun ItemRow(
    index: Int,
    item: InvoiceItem,
    charges: List<ChargeMaster>,
    onItemUpdate: (InvoiceItem) -> Unit,
    onChargeSelected: (String) -> Unit,
    onDelete: () -> Unit,
    df: DecimalFormat
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // SR. NO.
        Text("${index + 1}", modifier = Modifier.width(40.dp), fontSize = 12.sp)
        
        // CHARGES DETAILS (Description)
        Box(modifier = Modifier.width(250.dp)) {
            var expanded by remember { mutableStateOf(false) }
            var searchText by remember { mutableStateOf(item.description) }
            
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    if (it.length <= 120) {
                        searchText = it
                        onItemUpdate(item.copy(description = it))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(45.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                trailingIcon = {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
            )
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                charges.filter { it.chargeName.contains(searchText, ignoreCase = true) }.forEach { charge ->
                    DropdownMenuItem(onClick = {
                        searchText = charge.chargeName
                        onChargeSelected(charge.chargeName)
                        expanded = false
                    }) {
                        Text(charge.chargeName, fontSize = 12.sp)
                    }
                }
            }
        }
        
        // HSN/SAC
        OutlinedTextField(
            value = item.hsnSac,
            onValueChange = { if (it.length <= 8) onItemUpdate(item.copy(hsnSac = it)) },
            modifier = Modifier.width(80.dp).height(45.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
        )
        
        // CUR
        OutlinedTextField(
            value = item.currency,
            onValueChange = { if (it.length <= 3) onItemUpdate(item.copy(currency = it)) },
            modifier = Modifier.width(50.dp).height(45.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
        )
        
        // RATE
        OutlinedTextField(
            value = if (item.rate == 0.0) "" else item.rate.toString(),
            onValueChange = { 
                if (it.length <= 12) {
                    val rate = it.toDoubleOrNull() ?: 0.0
                    onItemUpdate(item.copy(rate = rate))
                }
            },
            modifier = Modifier.width(80.dp).height(45.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
        )
        
        // QTY
        OutlinedTextField(
            value = if (item.qty == 0.0) "" else item.qty.toString(),
            onValueChange = { 
                if (it.length <= 12) {
                    val qty = it.toDoubleOrNull() ?: 0.0
                    onItemUpdate(item.copy(qty = qty))
                }
            },
            modifier = Modifier.width(60.dp).height(45.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
        )
        
        // AMOUNT In(CUR)
        Text(
            df.format(item.amount),
            modifier = Modifier.width(110.dp),
            fontSize = 12.sp,
            color = Color.DarkGray
        )

        // TAXABLE AMOUNT In(CUR) (Same as Amount)
        Text(
            df.format(item.amount),
            modifier = Modifier.width(140.dp),
            fontSize = 12.sp,
            color = Color.DarkGray
        )
        
        // CGST Rate
        OutlinedTextField(
            value = if (item.cgstRate == 0.0) "" else item.cgstRate.toString(),
            onValueChange = { 
                if (it.length <= 6) {
                    val rate = it.toDoubleOrNull() ?: 0.0
                    onItemUpdate(item.copy(cgstRate = rate))
                }
            },
            modifier = Modifier.width(70.dp).height(45.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
        )
        
        // CGST Amt
        Text(
            df.format(item.cgstAmt),
            modifier = Modifier.width(80.dp),
            fontSize = 12.sp,
            color = Color.DarkGray
        )
        
        // SGST Rate
        OutlinedTextField(
            value = if (item.sgstRate == 0.0) "" else item.sgstRate.toString(),
            onValueChange = { 
                if (it.length <= 6) {
                    val rate = it.toDoubleOrNull() ?: 0.0
                    onItemUpdate(item.copy(sgstRate = rate))
                }
            },
            modifier = Modifier.width(70.dp).height(45.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
        )
        
        // SGST Amt
        Text(
            df.format(item.sgstAmt),
            modifier = Modifier.width(80.dp),
            fontSize = 12.sp,
            color = Color.DarkGray
        )
        
        // TOTAL AMT(INR)
        Text(
            df.format(item.totalAmt),
            modifier = Modifier.width(110.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C3E50)
        )
        
        // Delete
        IconButton(
            onClick = onDelete,
            modifier = Modifier.width(40.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
        }
    }
}

@Composable
fun TotalsCard(viewModel: InvoiceFormViewModel, df: DecimalFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        backgroundColor = Color(0xFFF5F5F5)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(0.4f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Taxable Amount:", fontSize = 14.sp)
                Text("₹ ${df.format(viewModel.totalTaxable)}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(0.4f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("CGST:", fontSize = 14.sp)
                Text("₹ ${df.format(viewModel.totalCgst)}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(0.4f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("SGST:", fontSize = 14.sp)
                Text("₹ ${df.format(viewModel.totalSgst)}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(0.4f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("IGST:", fontSize = 14.sp)
                Text("₹ ${df.format(viewModel.totalIgst)}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            
            Divider(modifier = Modifier.fillMaxWidth(0.4f), thickness = 2.dp)
            
            Row(
                modifier = Modifier.fillMaxWidth(0.4f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Grand Total:", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                Text(
                    "₹ ${df.format(viewModel.grandTotal)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50)
                )
            }
        }
    }
}

@Composable
fun <T> DropdownField(
    label: String,
    items: List<Pair<T?, String>>,
    selectedValue: T?,
    onValueChange: (T?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = items.find { it.first == selectedValue }?.second ?: items.firstOrNull()?.second ?: ""
    
    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            trailingIcon = {
                IconButton(onClick = { if (enabled) expanded = !expanded }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
        )
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            items.forEach { (value, text) ->
                DropdownMenuItem(onClick = {
                    onValueChange(value)
                    expanded = false
                }) {
                    Text(text)
                }
            }
        }
    }
}

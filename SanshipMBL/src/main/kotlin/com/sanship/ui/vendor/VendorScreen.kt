package com.sanship.ui.vendor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sanship.data.VendorMaster
import com.sanship.data.VendorRepository

@Composable
fun VendorScreen() {
    val repository = remember { VendorRepository() }
    var vendors by remember { mutableStateOf(emptyList<VendorMaster>()) }
    var selectedVendor by remember { mutableStateOf<VendorMaster?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    // Load Data
    LaunchedEffect(Unit) {
        repository.getAllVendors().collect {
            vendors = it
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Vendor / Agent Master", style = MaterialTheme.typography.h4)
                Button(onClick = { 
                    selectedVendor = null
                    showDialog = true 
                }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Vendor")
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vendors) { vendor ->
                    VendorCard(vendor, onClick = { 
                        selectedVendor = vendor
                        showDialog = true
                    })
                }
            }
        }
        
        if (showDialog) {
            VendorDialog(
                vendor = selectedVendor,
                onDismiss = { showDialog = false },
                onSave = { newVendor ->
                    repository.addVendor(newVendor)
                    showDialog = false
                },
                onDelete = if (selectedVendor != null) {
                    {
                        repository.deleteVendor(selectedVendor!!.id)
                        showDialog = false
                    }
                } else null
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun VendorCard(vendor: VendorMaster, onClick: () -> Unit) {
    Card(onClick = onClick, elevation = 2.dp) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Icon(Icons.Default.Person, null, tint = Color.Gray)
            Spacer(Modifier.width(16.dp))
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(vendor.fullName, style = MaterialTheme.typography.h6)
                    Text(
                        vendor.type, 
                        style = MaterialTheme.typography.caption, 
                        modifier = Modifier.background(Color(0xFFE3F2FD)).padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Text(vendor.shortName, style = MaterialTheme.typography.caption)
                if (vendor.gstin.isNotBlank()) Text("GSTIN: ${vendor.gstin}", style = MaterialTheme.typography.body2)
            }
        }
    }
}

@Composable
fun VendorDialog(
    vendor: VendorMaster?,
    onDismiss: () -> Unit,
    onSave: (VendorMaster) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var short by remember { mutableStateOf(vendor?.shortName ?: "") }
    var full by remember { mutableStateOf(vendor?.fullName ?: "") }
    var addr by remember { mutableStateOf(vendor?.fullAddress ?: "") }
    var gst by remember { mutableStateOf(vendor?.gstin ?: "") }
    var stCode by remember { mutableStateOf(vendor?.stateCode ?: "") }
    var mail by remember { mutableStateOf(vendor?.email ?: "") }
    var type by remember { mutableStateOf(vendor?.type ?: "Shipping Line") }
    var typeExpanded by remember { mutableStateOf(false) }

    val isValid = short.isNotBlank() || full.isNotBlank() || addr.isNotBlank() || gst.isNotBlank() || stCode.isNotBlank() || mail.isNotBlank()
    val types = listOf("Shipping Line", "CHA", "Transporter", "Agent", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (vendor == null) "Add Vendor" else "Edit Vendor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Type Dropdown
                com.sanship.ui.components.SearchableDropdown(
                    label = "Vendor Type",
                    items = types,
                    selectedItem = type,
                    itemToString = { it },
                    onItemSelected = { if (it != null) type = it },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(value = short, onValueChange = { short = it }, label = { Text("Short Name") })
                OutlinedTextField(value = full, onValueChange = { full = it }, label = { Text("Full Name") })
                OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text("Address") }, maxLines = 3)
                OutlinedTextField(value = gst, onValueChange = { gst = it }, label = { Text("GSTIN") })
                OutlinedTextField(value = stCode, onValueChange = { stCode = it }, label = { Text("State Code") })
                OutlinedTextField(value = mail, onValueChange = { mail = it }, label = { Text("Email") })
            }
        },
        buttons = {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                if (onDelete != null) {
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Button(
                    onClick = {
                        onSave(VendorMaster(
                            id = vendor?.id ?: 0,
                            shortName = short.trim(),
                            fullName = full.trim(),
                            fullAddress = addr.trim(),
                            gstin = gst.trim(),
                            stateCode = stCode.trim(),
                            email = mail.trim(),
                            type = type.trim()
                        ))
                    },
                    enabled = isValid
                ) {
                    Text("Save")
                }
            }
        }
    )
}

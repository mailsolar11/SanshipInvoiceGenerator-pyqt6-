package com.sanship.ui.customer

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
import com.sanship.data.ClientMaster
import com.sanship.data.ClientRepository

@Composable
fun CustomerScreen() {
    val repository = remember { ClientRepository() }
    // Ideally use a ViewModel, but for simple CRUD, state here is fine
    var clients by remember { mutableStateOf(emptyList<ClientMaster>()) }
    var selectedClient by remember { mutableStateOf<ClientMaster?>(null) } // For Edit
    var showDialog by remember { mutableStateOf(false) }

    // Load Data
    LaunchedEffect(Unit) {
        repository.getAllClients().collect {
            clients = it
        }
    }
    
    // Refresh Helper
    fun refresh() {
        // Compose triggers re-collect on key change or manual fetch
        // For now, simpler to just rely on the Flow if we had a ViewModel
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Customer Manager", style = MaterialTheme.typography.h4)
                Button(onClick = { 
                    selectedClient = null // New
                    showDialog = true 
                }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Customer")
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // List
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(clients) { client ->
                    CustomerCard(client, onClick = { 
                        selectedClient = client
                        showDialog = true
                    })
                }
            }
        }
        
        if (showDialog) {
            CustomerDialog(
                client = selectedClient,
                onDismiss = { showDialog = false },
                onSave = { newClient ->
                    repository.addClient(newClient)
                    showDialog = false
                    // Ideally trigger refresh or rely on reactive flow
                },
                onDelete = if (selectedClient != null) {
                    {
                        repository.deleteClient(selectedClient!!.id)
                        showDialog = false
                    }
                } else null
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CustomerCard(client: ClientMaster, onClick: () -> Unit) {
    Card(onClick = onClick, elevation = 2.dp) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Icon(Icons.Default.Person, null, tint = Color.Gray)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(client.fullName, style = MaterialTheme.typography.h6)
                Text(client.shortName, style = MaterialTheme.typography.caption)
                if (client.gstin.isNotBlank()) Text("GSTIN: ${client.gstin}", style = MaterialTheme.typography.body2)
            }
        }
    }
}

@Composable
fun CustomerDialog(
    client: ClientMaster?,
    onDismiss: () -> Unit,
    onSave: (ClientMaster) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var short by remember { mutableStateOf(client?.shortName ?: "") }
    var full by remember { mutableStateOf(client?.fullName ?: "") }
    var addr by remember { mutableStateOf(client?.fullAddress ?: "") }
    var gst by remember { mutableStateOf(client?.gstin ?: "") }
    var stCode by remember { mutableStateOf(client?.stateCode ?: "") }
    var mail by remember { mutableStateOf(client?.email ?: "") }

    val isValid = short.isNotBlank() || full.isNotBlank() || addr.isNotBlank() || gst.isNotBlank() || stCode.isNotBlank() || mail.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (client == null) "Add Customer" else "Edit Customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        onSave(ClientMaster(
                            id = client?.id ?: 0,
                            shortName = short.trim(),
                            fullName = full.trim(),
                            fullAddress = addr.trim(),
                            gstin = gst.trim(),
                            stateCode = stCode.trim(),
                            email = mail.trim()
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

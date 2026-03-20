package com.sanship.ui.job

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sanship.data.ClientMaster
import com.sanship.data.ClientRepository
import com.sanship.models.Job
import com.sanship.repositories.JobRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun JobScreen() {
    val scope = rememberCoroutineScope()
    var jobs by remember { mutableStateOf(emptyList<Job>()) }
    var showDialog by remember { mutableStateOf(false) }
    var selectedJob by remember { mutableStateOf<Job?>(null) }
    
    // Clients for dropdown
    var clients by remember { mutableStateOf(emptyList<ClientMaster>()) }
    val clientRepo = remember { ClientRepository() }

    LaunchedEffect(Unit) {
        jobs = JobRepository.listOpenJobs()
        clientRepo.getAllClients().collectLatest { clients = it }
    }

    fun refresh() {
        jobs = JobRepository.listOpenJobs()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)) {
        Column {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Job Management", style = MaterialTheme.typography.h4)
                Button(onClick = { 
                    selectedJob = null
                    showDialog = true 
                }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("New Job")
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Job List
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(jobs) { job ->
                    JobCard(job, onEdit = {
                        selectedJob = job
                        showDialog = true
                    })
                }
            }
        }
        
        if (showDialog) {
            JobDialog(
                job = selectedJob,
                clients = clients,
                onDismiss = { showDialog = false },
                onSave = { savedJob ->
                    if (savedJob.id == 0) {
                        JobRepository.createJob(savedJob)
                    } else {
                        JobRepository.updateJob(savedJob)
                    }
                    showDialog = false
                    refresh()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun JobCard(job: Job, onEdit: () -> Unit) {
    Card(onClick = onEdit, elevation = 2.dp) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(job.jobNo, style = MaterialTheme.typography.h6, color = MaterialTheme.colors.primary)
                Text("C: ${job.consignee}", style = MaterialTheme.typography.body2)
                Text("S: ${job.shipper}", style = MaterialTheme.typography.body2)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(job.status, style = MaterialTheme.typography.caption)
                Text("ETA: ${job.eta}", style = MaterialTheme.typography.caption)
                Text(job.mblNo, style = MaterialTheme.typography.body2)
            }
        }
    }
}

@Composable
fun JobDialog(
    job: Job?,
    clients: List<ClientMaster>,
    onDismiss: () -> Unit,
    onSave: (Job) -> Unit
) {
    // State holders
    var jobNo by remember { mutableStateOf(job?.jobNo ?: "") }
    var customerId by remember { mutableStateOf(job?.customerId ?: 0) }
    var shipper by remember { mutableStateOf(job?.shipper ?: "") }
    var consignee by remember { mutableStateOf(job?.consignee ?: "") }
    
    var pol by remember { mutableStateOf(job?.pol ?: "") }
    var pod by remember { mutableStateOf(job?.pod ?: "") }
    var vessel by remember { mutableStateOf(job?.vesselFlight ?: "") }
    var etd by remember { mutableStateOf(job?.etd ?: "") }
    var eta by remember { mutableStateOf(job?.eta ?: "") }
    
    var mbl by remember { mutableStateOf(job?.mblNo ?: "") }
    var gross by remember { mutableStateOf(job?.grossWeight ?: "") }
    var net by remember { mutableStateOf(job?.netWeight ?: "") }
    var vol by remember { mutableStateOf(job?.volumeCbm ?: "") }
    var pkgs by remember { mutableStateOf(job?.packages ?: "") }
    var exRate by remember { mutableStateOf(job?.exchangeRate?.toString() ?: "1.0") }
    var ref by remember { mutableStateOf(job?.refNo ?: "") }

    // Logic to auto-fill Shipper/Consignee from Customer Selection could be added,
    // but for now we keep it free text or manual.
    
    // Tab State
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("General", "Shipment", "Cargo")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (job == null) "Create New Job" else "Edit Job ${job.jobNo}") },
        text = {
            Column(modifier = Modifier.width(600.dp)) {
                // Customer Dropdown
                val selectedClient = clients.find { it.id == customerId }

                com.sanship.ui.components.SearchableDropdown(
                    label = "Customer",
                    items = clients,
                    selectedItem = selectedClient,
                    itemToString = { it.fullName },
                    onItemSelected = { client ->
                        customerId = client?.id ?: 0
                        // Auto-fill Consignee if empty?
                        if (client != null && consignee.isEmpty()) consignee = client.fullName
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                
                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(text = { Text(title) }, selected = tabIndex == index, onClick = { tabIndex = index })
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                when(tabIndex) {
                    0 -> { // General
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = jobNo, onValueChange = { jobNo = it }, label = { Text("Job No *") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = ref, onValueChange = { ref = it }, label = { Text("Ref No") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = shipper, onValueChange = { shipper = it }, label = { Text("Shipper") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = consignee, onValueChange = { consignee = it }, label = { Text("Consignee") }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    1 -> { // Shipment
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = pol, onValueChange = { pol = it }, label = { Text("POL") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = pod, onValueChange = { pod = it }, label = { Text("POD") }, modifier = Modifier.weight(1f))
                            }
                            OutlinedTextField(value = vessel, onValueChange = { vessel = it }, label = { Text("Vessel/Flight") }, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = etd, onValueChange = { etd = it }, label = { Text("ETD") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = eta, onValueChange = { eta = it }, label = { Text("ETA") }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    2 -> { // Cargo
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = mbl, onValueChange = { mbl = it }, label = { Text("MBL/HBL No") }, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = gross, onValueChange = { gross = it }, label = { Text("Gross Wt") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = net, onValueChange = { net = it }, label = { Text("Net Wt") }, modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = vol, onValueChange = { vol = it }, label = { Text("CBM") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = pkgs, onValueChange = { pkgs = it }, label = { Text("Packages") }, modifier = Modifier.weight(1f))
                            }
                            OutlinedTextField(
                                value = exRate, 
                                onValueChange = { s: String -> exRate = s }, 
                                label = { Text("Exchange Rate") }, 
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        buttons = {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(Job(
                            id = job?.id ?: 0,
                            jobNo = jobNo,
                            customerId = customerId,
                            shipper = shipper,
                            consignee = consignee,
                            pol = pol,
                            pod = pod,
                            vesselFlight = vessel,
                            etd = etd,
                            eta = eta,
                            mblNo = mbl,
                            grossWeight = gross,
                            netWeight = net,
                            volumeCbm = vol,
                            packages = pkgs,
                            exchangeRate = exRate.toDoubleOrNull() ?: 1.0,
                            refNo = ref
                        ))
                    },
                    enabled = jobNo.isNotBlank() && customerId != 0
                ) {
                    Text("Save Job")
                }
            }
        }
    )
}

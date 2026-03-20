package com.sanship.ui.bl

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sanship.models.BLData
import com.sanship.models.Container
import com.sanship.models.HBLInstruction
import com.sanship.repositories.BLRepository
import com.sanship.repositories.JobRepository
import com.sanship.services.BLPDFGenerator
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun BLScreen() {
    val scope = rememberCoroutineScope()
    
    // State
    var jobs by remember { mutableStateOf(JobRepository.listOpenJobs()) }
    var selectedJobId by remember { mutableStateOf<Int?>(null) }
    var jobExpanded by remember { mutableStateOf(false) }
    
    var hbl by remember { mutableStateOf(HBLInstruction()) }
    var containers by remember { mutableStateOf(emptyList<Container>()) }
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Parties & Routing", "Containers & Cargo", "Terms & Confirm")
    
    var successMsg by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    
    // Container Dialog State
    var showContainerDialog by remember { mutableStateOf(false) }
    var editingContainer by remember { mutableStateOf<Container?>(null) }

    // Load Data when Job Selected
    LaunchedEffect(selectedJobId) {
        if (selectedJobId != null) {
            val loadedHBL = BLRepository.getHBLByJobId(selectedJobId!!)
            if (loadedHBL != null) {
                hbl = loadedHBL
            } else {
                // Initialize default from Job
                val job = jobs.find { it.id == selectedJobId }
                if (job != null) {
                    hbl = HBLInstruction(
                        jobId = job.id,
                        hblNo = "HBL/${job.jobNo}/${System.currentTimeMillis() % 1000}",
                        mblNo = job.mblNo,
                        shipperText = job.shipper,
                        consigneeText = job.consignee,
                        portOfLoading = job.pol,
                        portOfDischarge = job.pod
                    )
                }
            }
            containers = BLRepository.getContainersByJobId(selectedJobId!!)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF2C3E50)).padding(16.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text("Bill of Lading Details", style = MaterialTheme.typography.h5, color = Color.White, modifier = Modifier.weight(1f))
            
            // Job Selector
            com.sanship.ui.components.SearchableDropdown(
                label = "Select Job",
                items = jobs,
                selectedItem = jobs.find { it.id == selectedJobId },
                itemToString = { "${it.jobNo} - ${it.shipper}" },
                onItemSelected = { selectedJobId = it?.id }
            )
        }
        
        if (selectedJobId == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Please select a Job to proceed", color = Color.Gray)
            }
            return
        }
        
        // Tabs
        TabRow(selectedTabIndex = selectedTab, backgroundColor = Color.White, contentColor = MaterialTheme.colors.primary) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.White, RoundedCornerShape(4.dp)).padding(16.dp)) {
            when(selectedTab) {
                0 -> PartiesTab(hbl) { hbl = it }
                1 -> ContainersTab(
                    containers = containers,
                    onAdd = { 
                        editingContainer = null
                        showContainerDialog = true 
                    },
                    onEdit = { 
                        editingContainer = it
                        showContainerDialog = true
                    },
                    onDelete = { 
                        BLRepository.deleteContainer(it.id)
                        containers = BLRepository.getContainersByJobId(selectedJobId!!)
                    },
                    hbl = hbl,
                    onUpdateHBL = { hbl = it }
                )
                2 -> TermsTab(hbl) { hbl = it }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Text(if(successMsg.isNotEmpty()) successMsg else errorMsg, 
                 color = if(successMsg.isNotEmpty()) Color(0xFF81C784) else Color(0xFFE57373),
                 modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
            )
            
            Button(onClick = {
                scope.launch {
                    try {
                         BLRepository.saveHBLInstruction(hbl)
                         successMsg = "Draft Saved Successfully!"
                         errorMsg = ""
                    } catch(e: Exception) {
                        errorMsg = "Error saving: ${e.message}"
                    }
                }
            }, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFFA000))) {
                Text("Save Draft", color = Color.White)
            }
            
            Button(onClick = {
                scope.launch {
                    try {
                        BLRepository.saveHBLInstruction(hbl)
                        val pdfPath = "hbl/${hbl.hblNo.replace("/","_")}.pdf"
                        java.io.File("hbl").mkdirs()
                        
                        val totalPkgs = containers.sumOf { it.packages }
                        val totalGw = containers.sumOf { it.grossWeight }
                        val totalVol = containers.sumOf { it.volumeCbm }
                        
                        val blData = BLData(hbl, containers, totalPkgs, totalGw, totalVol)
                        
                        BLPDFGenerator.generateBL(blData, pdfPath)
                        successMsg = "BL Generated: $pdfPath"
                        errorMsg = ""
                    } catch(e: Exception) {
                        e.printStackTrace()
                        errorMsg = "Gen Failed: ${e.message}"
                    }
                }
            }) {
                Text("Generate BL PDF")
            }
        }
    }
    
    if (showContainerDialog && selectedJobId != null) {
        ContainerDialog(
            jobId = selectedJobId!!,
            existing = editingContainer,
            onDismiss = { showContainerDialog = false },
            onSave = { c ->
                BLRepository.saveContainer(c)
                containers = BLRepository.getContainersByJobId(selectedJobId!!)
                showContainerDialog = false
            }
        )
    }
}

@Composable
fun PartiesTab(hbl: HBLInstruction, onUpdate: (HBLInstruction) -> Unit) {
    Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                OutlinedTextField(value = hbl.hblNo, onValueChange = { onUpdate(hbl.copy(hblNo = it)) }, label = { Text("HBL Number") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = hbl.shipperText, onValueChange = { onUpdate(hbl.copy(shipperText = it)) }, label = { Text("Shipper (Full Text)") }, modifier = Modifier.fillMaxWidth().height(100.dp), maxLines = 5)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = hbl.consigneeText, onValueChange = { onUpdate(hbl.copy(consigneeText = it)) }, label = { Text("Consignee (Full Text)") }, modifier = Modifier.fillMaxWidth().height(100.dp), maxLines = 5)
            }
            Column(Modifier.weight(1f)) {
                OutlinedTextField(value = hbl.mblNo, onValueChange = { onUpdate(hbl.copy(mblNo = it)) }, label = { Text("MBL Number") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = hbl.notifyPartyText, onValueChange = { onUpdate(hbl.copy(notifyPartyText = it)) }, label = { Text("Notify Party") }, modifier = Modifier.fillMaxWidth().height(100.dp), maxLines = 5)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = hbl.deliveryAgentText, onValueChange = { onUpdate(hbl.copy(deliveryAgentText = it)) }, label = { Text("Delivery Agent") }, modifier = Modifier.fillMaxWidth().height(100.dp), maxLines = 5)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Routing info", style = MaterialTheme.typography.subtitle1)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = hbl.placeOfReceipt, onValueChange = { onUpdate(hbl.copy(placeOfReceipt = it)) }, label = { Text("Place of Receipt") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = hbl.portOfLoading, onValueChange = { onUpdate(hbl.copy(portOfLoading = it)) }, label = { Text("Port of Loading") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = hbl.portOfDischarge, onValueChange = { onUpdate(hbl.copy(portOfDischarge = it)) }, label = { Text("Port of Discharge") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = hbl.placeOfDelivery, onValueChange = { onUpdate(hbl.copy(placeOfDelivery = it)) }, label = { Text("Place of Delivery") }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ContainersTab(
    containers: List<Container>, 
    onAdd: () -> Unit, 
    onEdit: (Container) -> Unit, 
    onDelete: (Container) -> Unit,
    hbl: HBLInstruction,
    onUpdateHBL: (HBLInstruction) -> Unit
) {
    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Containers List", style = MaterialTheme.typography.subtitle1)
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Container")
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // List
        LazyColumn(modifier = Modifier.weight(1f).border(1.dp, Color.LightGray)) {
            item {
                Row(Modifier.background(Color.LightGray).padding(8.dp)) {
                     Text("Container No", Modifier.weight(1.5f))
                     Text("Seal No", Modifier.weight(1f))
                     Text("Type", Modifier.weight(0.5f))
                     Text("Pkgs", Modifier.weight(0.5f))
                     Text("Gross Wt", Modifier.weight(1f))
                     Text("Actions", Modifier.weight(1f))
                }
            }
            items(containers) { c ->
                Row(Modifier.padding(8.dp).clickable { onEdit(c) }) {
                     Text(c.containerNo, Modifier.weight(1.5f))
                     Text(c.sealNo, Modifier.weight(1f))
                     Text(c.containerType, Modifier.weight(0.5f))
                     Text("${c.packages}", Modifier.weight(0.5f))
                     Text("${c.grossWeight}", Modifier.weight(1f))
                     Row(Modifier.weight(1f)) {
                         IconButton(onClick = { onEdit(c) }) { Icon(Icons.Default.Edit, null, tint = Color.Blue) }
                         IconButton(onClick = { onDelete(c) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                     }
                }
                Divider()
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text("Overview Description (BL Body)", style = MaterialTheme.typography.subtitle1)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = hbl.marksAndNumbers, onValueChange = { onUpdateHBL(hbl.copy(marksAndNumbers = it)) }, label = { Text("Marks & Numbers") }, modifier = Modifier.weight(1f).height(120.dp), maxLines = 10)
            OutlinedTextField(value = hbl.descriptionOfGoods, onValueChange = { onUpdateHBL(hbl.copy(descriptionOfGoods = it)) }, label = { Text("Description of Goods") }, modifier = Modifier.weight(2f).height(120.dp), maxLines = 10)
        }
    }
}

@Composable
fun TermsTab(hbl: HBLInstruction, onUpdate: (HBLInstruction) -> Unit) {
    Column {
        Text("Terms & Conditions", style = MaterialTheme.typography.subtitle1)
        Spacer(Modifier.height(16.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Freight Terms
            Column {
                Text("Freight Terms")
                Row {
                    RadioButton(selected = hbl.freightTerms == "PREPAID", onClick = { onUpdate(hbl.copy(freightTerms = "PREPAID")) })
                    Text("Prepaid", Modifier.align(Alignment.CenterVertically))
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = hbl.freightTerms == "COLLECT", onClick = { onUpdate(hbl.copy(freightTerms = "COLLECT")) })
                    Text("Collect", Modifier.align(Alignment.CenterVertically))
                }
            }
            
            // BL Type
             Column {
                Text("BL Type")
                Row {
                    RadioButton(selected = hbl.blType == "ORIGINAL", onClick = { onUpdate(hbl.copy(blType = "ORIGINAL")) })
                    Text("Original", Modifier.align(Alignment.CenterVertically))
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = hbl.blType == "TELEX", onClick = { onUpdate(hbl.copy(blType = "TELEX")) })
                    Text("Telex Release", Modifier.align(Alignment.CenterVertically))
                     Spacer(Modifier.width(16.dp))
                    RadioButton(selected = hbl.blType == "SEAWAY", onClick = { onUpdate(hbl.copy(blType = "SEAWAY")) })
                    Text("Seaway Bill", Modifier.align(Alignment.CenterVertically))
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(value = hbl.shippedOnBoardDate, onValueChange = { onUpdate(hbl.copy(shippedOnBoardDate = it)) }, label = { Text("Shipped On Board Date (YYYY-MM-DD)") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = hbl.noOfOriginals.toString(), onValueChange = { onUpdate(hbl.copy(noOfOriginals = it.toIntOrNull() ?: 3)) }, label = { Text("No. of Originals") })
    }
}

@Composable
fun ContainerDialog(jobId: Int, existing: Container?, onDismiss: () -> Unit, onSave: (Container) -> Unit) {
    var containerNo by remember { mutableStateOf(existing?.containerNo ?: "") }
    var sealNo by remember { mutableStateOf(existing?.sealNo ?: "") }
    var type by remember { mutableStateOf(existing?.containerType ?: "40HC") }
    var packages by remember { mutableStateOf(existing?.packages?.toString() ?: "0") }
    var gw by remember { mutableStateOf(existing?.grossWeight?.toString() ?: "0.0") }
    var cbm by remember { mutableStateOf(existing?.volumeCbm?.toString() ?: "0.0") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Container" else "Edit Container") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = containerNo, onValueChange = { containerNo = it }, label = { Text("Container No") })
                OutlinedTextField(value = sealNo, onValueChange = { sealNo = it }, label = { Text("Seal No") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                     OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type") }, modifier = Modifier.weight(1f))
                     OutlinedTextField(value = packages, onValueChange = { packages = it }, label = { Text("Pkgs") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                     OutlinedTextField(value = gw, onValueChange = { gw = it }, label = { Text("Gross Wt (KGS)") }, modifier = Modifier.weight(1f))
                     OutlinedTextField(value = cbm, onValueChange = { cbm = it }, label = { Text("Vol (CBM)") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val c = Container(
                    id = existing?.id ?: 0,
                    jobId = jobId,
                    containerNo = containerNo,
                    sealNo = sealNo,
                    containerType = type,
                    packages = packages.toIntOrNull() ?: 0,
                    grossWeight = gw.toDoubleOrNull() ?: 0.0,
                    volumeCbm = cbm.toDoubleOrNull() ?: 0.0
                )
                onSave(c)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

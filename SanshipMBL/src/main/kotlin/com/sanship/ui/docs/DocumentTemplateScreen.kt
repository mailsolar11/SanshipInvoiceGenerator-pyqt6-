package com.sanship.ui.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanship.repositories.JobRepository
import com.sanship.services.DocumentPdfGenerator
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DocumentTemplateScreen() {
    val scope = rememberCoroutineScope()
    var jobNo by remember { mutableStateOf("") }
    var loadedJobData by remember { mutableStateOf<Map<String, String>?>(null) }
    
    var errorMsg by remember { mutableStateOf("") }
    var successMsg by remember { mutableStateOf("") }
    
    var jobs by remember { mutableStateOf(emptyList<com.sanship.models.Job>()) }
    var selectedJob by remember { mutableStateOf<com.sanship.models.Job?>(null) }

    LaunchedEffect(Unit) {
        jobs = JobRepository.listOpenJobs()
    }

    fun searchJob(job: com.sanship.models.Job?) {
        errorMsg = ""
        successMsg = ""
        if (job == null) {
            loadedJobData = null
            return
        }
        
        try {
            // Map the Job object to a Map<String, String> for the generator
            val map = mapOf(
                "job_no" to job.jobNo,
                "consignee" to job.consignee,
                "mbl_no" to job.mblNo,
                "vessel_flight" to job.vesselFlight,
                "pol" to job.pol,
                "pod" to job.pod,
                "eta" to job.eta,
                "gross_weight" to job.grossWeight,
                "net_weight" to job.netWeight,
                "volume_cbm" to job.volumeCbm,
                "packages" to job.packages
            )
            loadedJobData = map
            successMsg = "Job Selected: ${job.consignee}"
        } catch (e: Exception) {
            errorMsg = "Error mapping job: ${e.message}"
        }
    }

    fun generateDoc(type: String) {
        if (loadedJobData == null) {
            errorMsg = "Please search and load a job first."
            return
        }
        
        scope.launch {
            try {
                val fileName = "${type}_${jobNo.replace("/", "_")}.pdf"
                val outputPath = if (type == "Arrival_Notice") {
                    com.sanship.utils.DocumentPaths.getArrivalNoticePath(fileName)
                } else {
                    com.sanship.utils.DocumentPaths.getDeliveryNoticePath(fileName)
                }
                
                if (type == "Arrival_Notice") {
                    DocumentPdfGenerator.generateArrivalNotice(loadedJobData!!, outputPath)
                } else {
                    DocumentPdfGenerator.generateDeliveryOrder(loadedJobData!!, outputPath)
                }
                
                successMsg = "$type generated successfully at: $outputPath"
                errorMsg = ""
            } catch (e: Exception) {
                errorMsg = "Failed to generate document: ${e.message}"
                successMsg = ""
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)
    ) {
        Text("Document Templates (DO & Arrival Notice)", style = MaterialTheme.typography.h4)
        Spacer(Modifier.height(20.dp))
        
        Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                Text("Select Job", fontWeight = FontWeight.Bold)
                
                com.sanship.ui.components.SearchableDropdown(
                    label = "Job No.",
                    items = jobs,
                    selectedItem = selectedJob,
                    itemToString = { "${it.jobNo} - ${it.consignee.take(20)}" },
                    onItemSelected = { 
                        selectedJob = it
                        if (it != null) jobNo = it.jobNo
                        searchJob(it)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (successMsg.isNotBlank()) {
                    Text(successMsg, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                }
                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = MaterialTheme.colors.error)
                }
                
                if (loadedJobData != null) {
                    Divider(modifier = Modifier.padding(vertical = 16.dp))
                    Text("Generate Documents", fontWeight = FontWeight.Bold)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { generateDoc("Arrival_Notice") },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1976D2), contentColor = Color.White)
                        ) {
                            Text("Generate Arrival Notice")
                        }
                        
                        Button(
                            onClick = { generateDoc("Delivery_Order") },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFE64A19), contentColor = Color.White)
                        ) {
                            Text("Generate Delivery Order")
                        }
                    }
                }
            }
        }
    }
}

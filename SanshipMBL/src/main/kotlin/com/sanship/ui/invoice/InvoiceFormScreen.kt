package com.sanship.ui.invoice

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanship.viewmodels.InvoiceFormViewModel
import java.text.DecimalFormat

/**
 * Invoice Form Screen
 * Exact replica of Python base_invoice_form.py UI
 */
@Composable
fun InvoiceFormScreen(
    documentType: String = "INVOICE", // or "DEBIT_NOTE"
    onNavigateBack: () -> Unit = {}
) {
    val viewModel = remember { InvoiceFormViewModel(documentType) }
    val df = remember { DecimalFormat("#,##0.00") }
    
    val title = when (documentType) {
        "INVOICE" -> "TAX INVOICE"
        "DEBIT_NOTE" -> "DEBIT NOTE"
        "CREDIT_NOTE" -> "CREDIT NOTE"
        "QUOTATION" -> "QUOTATION"
        else -> "TAX INVOICE"
    }
    
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp,
                color = Color(0xFF2C3E50)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    Row {
                        // e-Invoice Button (Sandbox)
                        if (viewModel.invoiceHeader.irn.isBlank()) {
                             Button(
                                onClick = { viewModel.generateEInvoice() },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF673AB7) // Deep Purple
                                ),
                                enabled = !viewModel.isLoading
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text("GEN IRN", color = Color.White)
                            }
                            Spacer(Modifier.width(8.dp))
                        } else {
                            // Show Status if generated
                            Surface(
                                color = Color(0xFFE8F5E9),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                                modifier = Modifier.height(36.dp).align(Alignment.CenterVertically)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("IRN GENERATED", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                        }

                        Button(
                            onClick = { viewModel.saveInvoice() },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF4CAF50)
                            ),
                            enabled = !viewModel.isLoading
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("SAVE", color = Color.White)
                        }
                        
                        Spacer(Modifier.width(8.dp))
                        
                        Button(
                            onClick = { viewModel.exportPDF() },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFFFF5722)
                            ),
                            enabled = !viewModel.isLoading
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("PDF", color = Color.White)
                        }
                    }
                }
            }
            
            // Scrollable Content
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Top Info Card
                    TopInfoCard(viewModel)
                    
                    // Customer & Job Selection
                    CustomerJobCard(viewModel)
                    
                    // Shipment Details
                    ShipmentDetailsCard(viewModel)
                    
                    // Consignment Details
                    ConsignmentDetailsCard(viewModel)
                    
                    // Consignee Preview
                    ConsigneePreviewCard(viewModel)
                    
                    // Items Table
                    ItemsTableCard(viewModel, df)
                    
                    // Totals
                    TotalsCard(viewModel, df)
                }
                
                // Scrollbar
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState)
                )
            }
        }
        
        // Loading Overlay
        if (viewModel.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
    
    // Success Dialog
    if (viewModel.showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccessDialog() },
            title = { Text("Success") },
            text = { Text("$title saved successfully!") },
            confirmButton = {
                Button(onClick = { viewModel.dismissSuccessDialog() }) {
                    Text("OK")
                }
            }
        )
    }
    
    // Error Dialog
    if (viewModel.showErrorDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissErrorDialog() },
            title = { Text("Error") },
            text = { Text(viewModel.errorMessage) },
            confirmButton = {
                Button(onClick = { viewModel.dismissErrorDialog() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun TopInfoCard(viewModel: InvoiceFormViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Invoice Number (Read-only)
            OutlinedTextField(
                value = viewModel.invoiceHeader.invoiceNo,
                onValueChange = {},
                label = { Text("Invoice No") },
                readOnly = true,
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    disabledTextColor = Color.Black
                )
            )
            
            // Date with Date Picker
            com.sanship.ui.components.DatePickerField(
                label = "Date",
                value = viewModel.invoiceHeader.invoiceDate,
                onValueChange = { viewModel.updateHeaderField("invoiceDate", it) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun CustomerJobCard(viewModel: InvoiceFormViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Customer & Job", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { viewModel.refreshData() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Data")
                }
            }
            
            // Job Dropdown (Primary - triggers auto-fill)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    label = "Select Job",
                    items = listOf(Pair(null, "— Select OPEN Job —")) + viewModel.jobs.map { Pair(it.id, it.jobNo) },
                    selectedValue = viewModel.selectedJobId,
                    onValueChange = { viewModel.onJobSelected(it) },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Divider()
            
            // Customer Dropdown
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    label = "Customer",
                    items = listOf(Pair(null, "-- Select --")) + viewModel.customers.map { Pair(it.first, it.second) },
                    selectedValue = viewModel.selectedCustomerId,
                    onValueChange = { viewModel.onCustomerSelected(it) },
                    modifier = Modifier.weight(1f),
                    enabled = !viewModel.jobFieldsLocked
                )
                
                Button(
                    onClick = { /* TODO: Open Customer Manager */ },
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add")
                }
            }
            
            // Address Dropdown
            DropdownField(
                label = "Billing Address",
                items = listOf(Pair(null, "-- Select --")) + viewModel.addresses.map { 
                    Pair(it.id, "${it.label}${if (it.isDefault) " (Default)" else ""}")
                },
                selectedValue = viewModel.selectedAddressId,
                onValueChange = { viewModel.onAddressSelected(it) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.jobFieldsLocked
            )
            
            if (viewModel.invoiceHeader.billingAddress.isNotBlank()) {
                OutlinedTextField(
                    value = viewModel.invoiceHeader.billingAddress,
                    onValueChange = {},
                    label = { Text("Bill To") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    readOnly = true,
                    maxLines = 4
                )
            }
            
            // PAN, State Code, GSTIN row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val panMax = 10
                OutlinedTextField(
                    value = viewModel.invoiceHeader.pan,
                    onValueChange = { if (it.length <= panMax) viewModel.updateHeaderField("pan", it.uppercase()) },
                    label = { Text("PAN/IT No (${viewModel.invoiceHeader.pan.length}/$panMax)") },
                    modifier = Modifier.weight(1f)
                )
                
                val stateCodeMax = 2
                OutlinedTextField(
                    value = viewModel.invoiceHeader.stateCode,
                    onValueChange = { if (it.length <= stateCodeMax) viewModel.updateHeaderField("stateCode", it) },
                    label = { Text("State Code (${viewModel.invoiceHeader.stateCode.length}/$stateCodeMax)") },
                    modifier = Modifier.weight(0.5f)
                )
                
                OutlinedTextField(
                    value = viewModel.invoiceHeader.gstin,
                    onValueChange = {},
                    label = { Text("GSTIN/UIN") },
                    modifier = Modifier.weight(1f),
                    readOnly = true
                )
            }
        }
    }
}

@Composable
fun ShipmentDetailsCard(viewModel: InvoiceFormViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Shipment Details", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Shipper
                val shipperMax = 80
                OutlinedTextField(
                    value = viewModel.invoiceHeader.shipper,
                    onValueChange = { if (it.length <= shipperMax) viewModel.updateHeaderField("shipper", it) },
                    label = { Text("Shipper (${viewModel.invoiceHeader.shipper.length}/$shipperMax)") },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.outlinedTextFieldColors()
                )
                
                // Consignee
                val consigneeMax = 80
                OutlinedTextField(
                    value = viewModel.invoiceHeader.consignee,
                    onValueChange = { if (it.length <= consigneeMax) viewModel.updateHeaderField("consignee", it) },
                    label = { Text("Consignee (${viewModel.invoiceHeader.consignee.length}/$consigneeMax)") },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.outlinedTextFieldColors()
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // POL
                val polMax = 50
                OutlinedTextField(
                    value = viewModel.invoiceHeader.pol,
                    onValueChange = { if (it.length <= polMax) viewModel.updateHeaderField("pol", it) },
                    label = { Text("POL (${viewModel.invoiceHeader.pol.length}/$polMax)") },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.outlinedTextFieldColors()
                )
                
                // POD
                val podMax = 50
                OutlinedTextField(
                    value = viewModel.invoiceHeader.pod,
                    onValueChange = { if (it.length <= podMax) viewModel.updateHeaderField("pod", it) },
                    label = { Text("POD (${viewModel.invoiceHeader.pod.length}/$podMax)") },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.outlinedTextFieldColors()
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val vesselMax = 50
                OutlinedTextField(
                    value = viewModel.invoiceHeader.vesselFlight,
                    onValueChange = { if (it.length <= vesselMax) viewModel.updateHeaderField("vesselFlight", it) },
                    label = { Text("Vessel/Flight (${viewModel.invoiceHeader.vesselFlight.length}/$vesselMax)") },
                    modifier = Modifier.weight(1f)
                )
                
                com.sanship.ui.components.DatePickerField(
                    label = "ETD",
                    value = viewModel.invoiceHeader.etd,
                    onValueChange = { viewModel.updateHeaderField("etd", it) },
                    modifier = Modifier.weight(1f)
                )
                
                com.sanship.ui.components.DatePickerField(
                    label = "ETA",
                    value = viewModel.invoiceHeader.eta,
                    onValueChange = { viewModel.updateHeaderField("eta", it) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ConsignmentDetailsCard(viewModel: InvoiceFormViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Details About Consignment", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            
            // Row 1: Date (read-only from top)
            com.sanship.ui.components.DatePickerField(
                label = "Date",
                value = viewModel.invoiceHeader.invoiceDate,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            )
            
            // Row 2: Invoice number (read-only)
            OutlinedTextField(
                value = viewModel.invoiceHeader.invoiceNo,
                onValueChange = {},
                label = { Text("Invoice number") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
            )
            
            // Row 3: MBL no + HBL no
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val mblMax = 30
                OutlinedTextField(
                    value = viewModel.invoiceHeader.mblNo,
                    onValueChange = { if (it.length <= mblMax) viewModel.updateHeaderField("mblNo", it) },
                    label = { Text("MBL no (${viewModel.invoiceHeader.mblNo.length}/$mblMax)") },
                    modifier = Modifier.weight(1f)
                )
                val hblMax = 30
                OutlinedTextField(
                    value = viewModel.invoiceHeader.hblNo,
                    onValueChange = { if (it.length <= hblMax) viewModel.updateHeaderField("hblNo", it) },
                    label = { Text("HBL no (${viewModel.invoiceHeader.hblNo.length}/$hblMax)") },
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Category
            val catMax = 20
            OutlinedTextField(
                value = viewModel.invoiceHeader.category,
                onValueChange = { if (it.length <= catMax) viewModel.updateHeaderField("category", it) },
                label = { Text("Category (${viewModel.invoiceHeader.category.length}/$catMax)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Container Nos
            val contMax = 100
            OutlinedTextField(
                value = viewModel.invoiceHeader.containerNos,
                onValueChange = { if (it.length <= contMax) viewModel.updateHeaderField("containerNos", it) },
                label = { Text("Container No(s) (${viewModel.invoiceHeader.containerNos.length}/$contMax)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Shipper Invoice No + Date
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val invMax = 30
                OutlinedTextField(
                    value = viewModel.invoiceHeader.shipperInvoiceNo,
                    onValueChange = { if (it.length <= invMax) viewModel.updateHeaderField("shipperInvoiceNo", it) },
                    label = { Text("Shipper Inv No (${viewModel.invoiceHeader.shipperInvoiceNo.length}/$invMax)") },
                    modifier = Modifier.weight(1f)
                )
                com.sanship.ui.components.DatePickerField(
                    label = "Shipper Inv Date",
                    value = viewModel.invoiceHeader.shipperInvoiceDate,
                    onValueChange = { viewModel.updateHeaderField("shipperInvoiceDate", it) },
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Row 4: Job no (read-only if from job)
            OutlinedTextField(
                value = viewModel.invoiceHeader.jobNo,
                onValueChange = {},
                label = { Text("Job no") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
            )
            
            // Row 5: Gross weight
            val weightMax = 20
            OutlinedTextField(
                value = viewModel.invoiceHeader.grossWeight,
                onValueChange = { if (it.length <= weightMax) viewModel.updateHeaderField("grossWeight", it) },
                label = { Text("Gross weight ($weightMax)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Row 6: Net weight + Unit
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = viewModel.invoiceHeader.netWeight,
                    onValueChange = { if (it.length <= weightMax) viewModel.updateHeaderField("netWeight", it) },
                    label = { Text("Net weight ($weightMax)") },
                    modifier = Modifier.weight(1f)
                )
                val unitMax = 10
                OutlinedTextField(
                    value = viewModel.invoiceHeader.netWeightUnit,
                    onValueChange = { if (it.length <= unitMax) viewModel.updateHeaderField("netWeightUnit", it) },
                    label = { Text("Unit (${viewModel.invoiceHeader.netWeightUnit.length}/$unitMax)") },
                    modifier = Modifier.weight(0.5f),
                    placeholder = { Text("KGS, MT") }
                )
            }
            
            // Row 7: Volume (CBM)
            OutlinedTextField(
                value = viewModel.invoiceHeader.volumeCbm,
                onValueChange = { if (it.length <= weightMax) viewModel.updateHeaderField("volumeCbm", it) },
                label = { Text("Volume (CBM) ($weightMax)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Row 8: Packages
            OutlinedTextField(
                value = viewModel.invoiceHeader.packages,
                onValueChange = { if (it.length <= weightMax) viewModel.updateHeaderField("packages", it) },
                label = { Text("Packages ($weightMax)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Row 9: BE no / SB no
            val beIgmMax = 30
            OutlinedTextField(
                value = viewModel.invoiceHeader.beNo,
                onValueChange = { if (it.length <= beIgmMax) viewModel.updateHeaderField("beNo", it) },
                label = { Text("BE no / SB no ($beIgmMax)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Row 10: BE Date
            com.sanship.ui.components.DatePickerField(
                label = "BE/SB Date",
                value = viewModel.invoiceHeader.beDate,
                onValueChange = { viewModel.updateHeaderField("beDate", it) },
                modifier = Modifier.fillMaxWidth()
            )

            // Row 11: IGM no
            OutlinedTextField(
                value = viewModel.invoiceHeader.igmNo,
                onValueChange = { if (it.length <= beIgmMax) viewModel.updateHeaderField("igmNo", it) },
                label = { Text("IGM no ($beIgmMax)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Row 12: IGM Date
            com.sanship.ui.components.DatePickerField(
                label = "IGM Date",
                value = viewModel.invoiceHeader.igmDate,
                onValueChange = { viewModel.updateHeaderField("igmDate", it) },
                modifier = Modifier.fillMaxWidth()
            )

            // Row 13: Item no
            val itemNoMax = 20
            OutlinedTextField(
                value = viewModel.invoiceHeader.itemNo,
                onValueChange = { if (it.length <= itemNoMax) viewModel.updateHeaderField("itemNo", it) },
                label = { Text("Item no ($itemNoMax)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Row 14: Ex. Rate
            val rateMax = 12
            OutlinedTextField(
                value = viewModel.invoiceHeader.exchangeRate,
                onValueChange = { if (it.length <= rateMax) viewModel.updateHeaderField("exchangeRate", it) },
                label = { Text("Ex. Rate ($rateMax)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Row 15: Ref no (optional)
            val refMax = 50
            OutlinedTextField(
                value = viewModel.invoiceHeader.refNo,
                onValueChange = { if (it.length <= refMax) viewModel.updateHeaderField("refNo", it) },
                label = { Text("Ref no (${viewModel.invoiceHeader.refNo.length}/$refMax)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Row 16: Other Ref no (Optional)
            val otherRefMax = 50
            OutlinedTextField(
                value = viewModel.invoiceHeader.otherRefNo,
                onValueChange = { if (it.length <= otherRefMax) viewModel.updateHeaderField("otherRefNo", it) },
                label = { Text("Other Ref no (${viewModel.invoiceHeader.otherRefNo.length}/$otherRefMax) — Optional") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Add Consignee Preview Card
@Composable
fun ConsigneePreviewCard(viewModel: InvoiceFormViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Consignee (preview):", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = viewModel.invoiceHeader.consigneeAddress,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth().height(120.dp),
                readOnly = true,
                maxLines = 6
            )
        }
    }
}

@Composable
fun ItemsTableCard(viewModel: InvoiceFormViewModel, df: DecimalFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        // Enforce horizontal scrolling for the table
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Charges", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    
                    Button(
                        onClick = { viewModel.addItem() },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Text("Add Row", color = Color.White)
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Table Header
                Row(
                    modifier = Modifier.background(Color(0xFFE0E0E0)).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SR. NO.", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("CHARGES DETAILS", modifier = Modifier.width(250.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("HSN/SAC", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("CUR", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("RATE", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("QTY", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("AMOUNT In(CUR)", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("TAXABLE AMOUNT In(CUR)", modifier = Modifier.width(140.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("CGST Rate", modifier = Modifier.width(70.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("CGST Amt", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("SGST Rate", modifier = Modifier.width(70.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("SGST Amt", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("TOTAL AMT(INR)", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Del", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                
                Divider()
                
                // Items
                viewModel.items.forEachIndexed { index, item ->
                    ItemRow(
                        index = index,
                        item = item,
                        charges = viewModel.charges,
                        onItemUpdate = { viewModel.updateItem(index, it) },
                        onChargeSelected = { chargeName -> viewModel.applyChargeToItem(index, chargeName) },
                        onDelete = { viewModel.removeItem(index) },
                        df = df
                    )
                    Divider()
                }
            }
        }
    }
}

// Continue in next file due to length...

package com.sanship.ui.mbl.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanship.data.CargoItem
import com.sanship.ui.mbl.AddressFieldType
import com.sanship.ui.mbl.MblViewModel
import com.sanship.ui.mbl.PortFieldType

// =========================================================================
//  MAIN SCREEN LAYOUT
// =========================================================================
@Composable
fun MblModernForm(viewModel: MblViewModel) {
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- SECTION 1: HEADER (Client Search Enabled) ---
            SectionTitle("1. Parties & Reference")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ClientSearchField(
                    label = "Consignor",
                    value = viewModel.mblData.consignor,
                    onValueChange = { viewModel.updateConsignor(it) },
                    viewModel = viewModel,
                    fieldType = AddressFieldType.CONSIGNOR,
                    modifier = Modifier.weight(1f),
                    maxChars = 280
                )
                ClientSearchField(
                    label = "Consignee",
                    value = viewModel.mblData.consignee,
                    onValueChange = { viewModel.updateConsignee(it) },
                    viewModel = viewModel,
                    fieldType = AddressFieldType.CONSIGNEE,
                    modifier = Modifier.weight(1f),
                    maxChars = 280
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ClientSearchField(
                    label = "Notify Address",
                    value = viewModel.mblData.notifyAddress,
                    onValueChange = { viewModel.updateNotify(it) },
                    viewModel = viewModel,
                    fieldType = AddressFieldType.NOTIFY,
                    modifier = Modifier.weight(1f),
                    maxChars = 280
                )
                ClientSearchField(
                    label = "Delivery Agent",
                    value = viewModel.mblData.deliveryAgent,
                    onValueChange = { viewModel.updateAgent(it) },
                    viewModel = viewModel,
                    fieldType = AddressFieldType.AGENT,
                    modifier = Modifier.weight(1f),
                    maxChars = 250
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SimpleTextField("MTD Number", viewModel.mblData.mtdNumber, { viewModel.updateMtd(it) }, Modifier.weight(1f), maxChars = 26)
                SimpleTextField("Ref Number", viewModel.mblData.refNumber, { viewModel.updateRef(it) }, Modifier.weight(1f), maxChars = 26)
            }

            // --- SECTION 2: ROUTING (Port Search Enabled) ---
            SectionTitle("2. Routing Details")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SimpleTextField("Pre-Carriage", viewModel.mblData.preCarriage, { viewModel.updatePreCarriage(it) }, Modifier.weight(1f), maxChars = 40)

                PortSearchField(
                    label = "Place of Receipt",
                    value = viewModel.mblData.placeReceipt,
                    viewModel = viewModel,
                    fieldType = PortFieldType.RECEIPT,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SimpleTextField("Ocean Vessel", viewModel.mblData.vessel, { viewModel.updateVessel(it) }, Modifier.weight(1f), maxChars = 40)

                // FIXED: Max Chars set to 7 to fit the "Green Line" constraint in PDF
                SimpleTextField("Voyage No", viewModel.mblData.voyNumber, { viewModel.updateVoy(it) }, Modifier.weight(0.5f), maxChars = 7)

                // Port of Loading with Dropdown
                PortSearchField(
                    label = "Port of Loading",
                    value = viewModel.mblData.portLoading,
                    viewModel = viewModel,
                    fieldType = PortFieldType.LOADING,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Port of Discharge with Dropdown
                PortSearchField(
                    label = "Port of Discharge",
                    value = viewModel.mblData.portDischarge,
                    viewModel = viewModel,
                    fieldType = PortFieldType.DISCHARGE,
                    modifier = Modifier.weight(1f)
                )

                PortSearchField(
                    label = "Place of Delivery",
                    value = viewModel.mblData.placeDelivery,
                    viewModel = viewModel,
                    fieldType = PortFieldType.DELIVERY,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SimpleTextField("Mode", viewModel.mblData.mode, { viewModel.updateMode(it) }, Modifier.weight(1f), maxChars = 20)
                SimpleTextField("Route", viewModel.mblData.route, { viewModel.updateRoute(it) }, Modifier.weight(1f), maxChars = 40)
            }

            // --- SECTION 3: CARGO ---
            SectionTitle("3. Cargo Details")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SimpleTextField("Main Customs Seal", viewModel.mblData.mainCustomsSeal, { viewModel.updateMainCustoms(it) }, Modifier.weight(1f), maxChars = 30)
                SimpleTextField("Main Agent Seal", viewModel.mblData.mainAgentSeal, { viewModel.updateMainAgent(it) }, Modifier.weight(1f), maxChars = 30)
            }

            // Goods Description (Multi-line with limit)
            Column {
                OutlinedTextField(
                    value = viewModel.mblData.goodsDescription,
                    onValueChange = { if(it.length <= 1500) viewModel.updateGoodsDesc(it) },
                    label = { Text("Goods Description") },
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
                Text(
                    text = "${viewModel.mblData.goodsDescription.length} / 1500",
                    style = MaterialTheme.typography.caption,
                    color = if(viewModel.mblData.goodsDescription.length > 1400) Color.Red else Color.Gray,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            // NEW: Marks & Numbers (Global Field moved here)
            // UPDATED CHAR LIMIT: 600 (Based on new column dimensions)
            Column {
                OutlinedTextField(
                    value = viewModel.mblData.marksNumbers,
                    onValueChange = { if(it.length <= 600) viewModel.updateMarksNumbers(it) },
                    label = { Text("Marks & Numbers") },
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
                Text(
                    text = "${viewModel.mblData.marksNumbers.length} / 600",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            // Cargo Grid (Containers)
            Text("Container List", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            viewModel.mblData.cargoItems.forEachIndexed { index, item ->
                CargoRow(index, item, viewModel)
            }
            Button(onClick = { viewModel.addCargoRow() }) {
                Icon(Icons.Default.Add, "Add")
                Text("Add Container")
            }

            // --- SECTION 4: FOOTER ---
            SectionTitle("4. Financials & Footer")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SimpleTextField("Freight Amount", viewModel.mblData.freightAmount, { viewModel.updateFreight(it) }, Modifier.weight(1f), maxChars = 40)
                SimpleTextField("Payable At", viewModel.mblData.freightPayableAt, { viewModel.updatePayable(it) }, Modifier.weight(1f), maxChars = 40)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SimpleTextField("Originals", viewModel.mblData.originalMtds, { viewModel.updateOriginals(it) }, Modifier.weight(1f), maxChars = 20)
                SimpleTextField("Place & Date", viewModel.mblData.placeDateIssue, { viewModel.updatePlaceDate(it) }, Modifier.weight(1f), maxChars = 60)
            }
            SimpleTextField("Other Particulars", viewModel.mblData.otherParticulars, { viewModel.updateOthers(it) }, Modifier.fillMaxWidth(), maxChars = 100)

            // --- ACTION BUTTONS (Split) ---
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { viewModel.generateDigitalPdf() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
                ) {
                    Text("Generate Digital PDF", color = Color.White)
                }

                Button(
                    onClick = { viewModel.generatePrintOverlay() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)) // Green
                ) {
                    Text("Generate Print Overlay", color = Color.White)
                }
            }
        }

        // --- DIALOG OVERLAY ---
        if (viewModel.showAddClientDialog) {
            AddClientDialog(viewModel)
        }
    }
}

// =========================================================================
//  HELPER COMPONENTS (WITH CHAR LIMITS)
// =========================================================================

// --- UPDATED HELPER: ClientSearchField with Max Chars ---
@Composable
fun ClientSearchField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    viewModel: MblViewModel,
    fieldType: AddressFieldType,
    modifier: Modifier = Modifier,
    maxChars: Int = 200 // Default Limit
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        if (it.length <= maxChars) {
                            onValueChange(it)
                            if (viewModel.activeAddressField == fieldType) {
                                viewModel.onClientQueryChanged(it)
                            }
                        }
                    },
                    label = { Text(label) },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.Black,
                        cursorColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                viewModel.openClientDropdown(fieldType)
                            }
                        },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.openClientDropdown(fieldType) }) {
                            Icon(Icons.Default.ArrowDropDown, "Select")
                        }
                    }
                )

                if (viewModel.activeAddressField == fieldType && viewModel.isClientDropdownOpen) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { viewModel.isClientDropdownOpen = false },
                        properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        viewModel.filteredClientList.forEach { client ->
                            DropdownMenuItem(onClick = { viewModel.onClientSelected(client) }) {
                                Column {
                                    Text(text = client.searchLabel, fontWeight = FontWeight.Bold)
                                    Text(text = client.fullName, fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                        }
                        if (viewModel.filteredClientList.isEmpty()) {
                            DropdownMenuItem(enabled = false, onClick = {}) {
                                Text("No clients found.", color = Color.Gray)
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = { viewModel.showAddClientDialog = true },
                modifier = Modifier.padding(start = 4.dp).align(Alignment.CenterVertically)
            ) {
                Icon(Icons.Default.Add, "Add Client", tint = MaterialTheme.colors.primary)
            }
        }
        // Character Counter
        Text(
            text = "${value.length} / $maxChars",
            style = MaterialTheme.typography.caption,
            color = if (value.length > maxChars * 0.9) Color.Red else Color.Gray,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
fun SimpleTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxChars: Int = 100 // Default Limit
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                if (it.length <= maxChars) onValueChange(it)
            },
            label = { Text(label) },
            colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "${value.length} / $maxChars",
            style = MaterialTheme.typography.caption,
            color = if (value.length > maxChars * 0.9) Color.Red else Color.Gray,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

// --- UPDATED HELPER: PortSearchField ---
@Composable
fun PortSearchField(
    label: String,
    value: String,
    viewModel: MblViewModel,
    fieldType: PortFieldType,
    modifier: Modifier = Modifier,
    maxChars: Int = 60
) {
    Column(modifier = modifier) {
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    if (it.length <= maxChars) {
                        if (viewModel.activePortField == fieldType) {
                            viewModel.onPortSearchQueryChange(it)
                        } else {
                            viewModel.setTargetPortField(fieldType)
                            viewModel.onPortSearchQueryChange(it)
                        }
                    }
                },
                label = { Text(label) },
                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            viewModel.setTargetPortField(fieldType)
                        }
                    },
                trailingIcon = {
                    IconButton(onClick = {
                        viewModel.setTargetPortField(fieldType)
                        // Trigger list to show even if empty query (shows all if logic allows, or just focus)
                        viewModel.onPortSearchQueryChange(value)
                    }) {
                        Icon(Icons.Default.ArrowDropDown, "Select Port")
                    }
                }
            )

            if (viewModel.activePortField == fieldType && viewModel.isPortSearchExpanded) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { viewModel.isPortSearchExpanded = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                    modifier = Modifier.heightIn(max = 300.dp) // Scrollable list
                ) {
                    if (viewModel.portSearchResults.isEmpty()) {
                        DropdownMenuItem(enabled = false, onClick = {}) { Text("No ports found") }
                    } else {
                        viewModel.portSearchResults.forEach { port ->
                            DropdownMenuItem(onClick = { viewModel.onPortSelected(port) }) {
                                Text(text = port)
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = "${value.length} / $maxChars",
            style = MaterialTheme.typography.caption,
            color = if (value.length > maxChars * 0.9) Color.Red else Color.Gray,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun CargoRow(index: Int, item: CargoItem, viewModel: MblViewModel) {
    Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Container #${index + 1}", fontWeight = FontWeight.Bold)
                IconButton(onClick = { viewModel.removeCargoRow(index) }) {
                    Icon(Icons.Default.Delete, "Remove", tint = Color.Red)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleTextField("Container No", item.containerNo, { viewModel.updateCargoItem(index, item.copy(containerNo = it)) }, Modifier.weight(1f), maxChars = 20)
                SimpleTextField("Pkgs", item.pkgCount, { viewModel.updateCargoItem(index, item.copy(pkgCount = it)) }, Modifier.weight(1f), maxChars = 10)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleTextField("Gross Wgt", item.grossWeight, { viewModel.updateCargoItem(index, item.copy(grossWeight = it)) }, Modifier.weight(1f), maxChars = 15)
                SimpleTextField("Measure", item.measurement, { viewModel.updateCargoItem(index, item.copy(measurement = it)) }, Modifier.weight(1f), maxChars = 15)
            }
            // Annexure Details
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleTextField("Net Wgt", item.netWeight, { viewModel.updateCargoItem(index, item.copy(netWeight = it)) }, Modifier.weight(1f), maxChars = 15)
                SimpleTextField("SB No", item.sbNumber, { viewModel.updateCargoItem(index, item.copy(sbNumber = it)) }, Modifier.weight(1f), maxChars = 20)
                SimpleTextField("SB Date", item.sbDate, { viewModel.updateCargoItem(index, item.copy(sbDate = it)) }, Modifier.weight(1f), maxChars = 15)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleTextField("Customs Seal (Self)", item.customsSeal, { viewModel.updateCargoItem(index, item.copy(customsSeal = it)) }, Modifier.weight(1f), maxChars = 20)
                SimpleTextField("Agent Seal (Ship)", item.agentSeal, { viewModel.updateCargoItem(index, item.copy(agentSeal = it)) }, Modifier.weight(1f), maxChars = 20)
            }
        }
    }
}

// --- DIALOG ---
@Composable
fun AddClientDialog(viewModel: MblViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.showAddClientDialog = false },
        title = { Text("Add New Client") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = viewModel.newClientShortName,
                    onValueChange = { if(it.length <= 10) viewModel.newClientShortName = it },
                    label = { Text("Short Name (e.g., BKK) - Max 10") }
                )
                OutlinedTextField(
                    value = viewModel.newClientFullName,
                    onValueChange = { if(it.length <= 50) viewModel.newClientFullName = it },
                    label = { Text("Full Company Name - Max 50") }
                )
                OutlinedTextField(
                    value = viewModel.newClientAddress,
                    onValueChange = { if(it.length <= 200) viewModel.newClientAddress = it },
                    label = { Text("Full Address (Multi-line) - Max 200") },
                    modifier = Modifier.height(100.dp)
                )
                Text("Code will be auto-generated (e.g., 101-BKK)", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.saveNewClient() }) {
                Text("Save Client")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.showAddClientDialog = false }) {
                Text("Cancel")
            }
        }
    )
}
package com.sanship.ui.mbl

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sanship.data.ClientRepository
import com.sanship.ui.mbl.layout.MblModernForm

@Composable
fun MblScreen() {
    // 1. Initialize Repository and ViewModel
    // We use 'remember' to keep the same instance across recompositions
    val repository = remember { ClientRepository() }
    val viewModel = remember { MblViewModel(repository) }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- TOP BAR (BL SEARCH & REUSE) ---
        TopAppBar(
            title = { Text("Bill of Lading (MBL)") },
            actions = {
                // Reuse Button (Create Similar) - Visible only if a BL is loaded
                if (viewModel.mblData.mtdNumber.isNotBlank()) {
                    Button(
                        onClick = { viewModel.createSimilarBl() },
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Reuse")
                        Spacer(Modifier.width(4.dp))
                        Text("Reuse BL")
                    }
                    Spacer(Modifier.width(16.dp))
                }

                // BL Search Box
                Box(modifier = Modifier.width(300.dp)) {
                    OutlinedTextField(
                        value = viewModel.blSearchQuery,
                        onValueChange = { viewModel.onBlSearchQueryChange(it) },
                        placeholder = { Text("Search BL No...") },
                        trailingIcon = { Icon(Icons.Default.Search, "Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = MaterialTheme.colors.surface,
                            textColor = androidx.compose.ui.graphics.Color.Black
                        )
                    )

                    // Search Results Dropdown
                    DropdownMenu(
                        expanded = viewModel.isBlSearchExpanded,
                        onDismissRequest = { viewModel.isBlSearchExpanded = false },
                        modifier = Modifier.width(300.dp),
                        properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                    ) {
                        viewModel.blSearchResults.forEach { mtd ->
                            DropdownMenuItem(onClick = { viewModel.loadBill(mtd) }) {
                                Text(text = mtd)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
            }
        )

        // --- MAIN FORM CONTENT ---
        // Pass the initialized viewModel to the form layout
        MblModernForm(viewModel)
    }
}
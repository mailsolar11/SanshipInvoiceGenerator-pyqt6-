package com.sanship.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager

@Composable
fun <T> SearchableDropdown(
    label: String,
    items: List<T>,
    selectedItem: T?,
    itemToString: (T) -> String,
    onItemSelected: (T?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf(selectedItem?.let { itemToString(it) } ?: "") }
    val focusManager = LocalFocusManager.current

    // Keep search text in sync if external selectedItem changes
    LaunchedEffect(selectedItem) {
        val newText = selectedItem?.let { itemToString(it) } ?: ""
        if (newText != searchText && !expanded) {
            searchText = newText
        }
    }

    val filteredItems = remember(searchText, items) {
        if (searchText.isEmpty() || (selectedItem != null && itemToString(selectedItem) == searchText)) {
            items
        } else {
            items.filter { itemToString(it).contains(searchText, ignoreCase = true) }
        }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = searchText,
            onValueChange = {
                searchText = it
                expanded = true
                if (it.isEmpty()) {
                    onItemSelected(null)
                }
            },
            label = { Text(label) },
            isError = isError,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focusState ->
                if (focusState.isFocused && enabled) {
                    expanded = true
                } else if (!focusState.isFocused && expanded) {
                    expanded = false
                    // Reset text to selected item on unfocus if not matching
                    searchText = selectedItem?.let { itemToString(it) } ?: ""
                }
            },
            enabled = enabled,
            trailingIcon = {
                IconButton(onClick = { 
                    if (enabled) {
                        expanded = !expanded 
                        if (!expanded) {
                            focusManager.clearFocus()
                            searchText = selectedItem?.let { itemToString(it) } ?: ""
                        }
                    }
                }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Toggle Dropdown")
                }
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { 
                expanded = false 
                searchText = selectedItem?.let { itemToString(it) } ?: ""
                focusManager.clearFocus()
            },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            filteredItems.forEach { item ->
                DropdownMenuItem(onClick = {
                    val text = itemToString(item)
                    searchText = text
                    onItemSelected(item)
                    expanded = false
                    focusManager.clearFocus()
                }) {
                    Text(text = itemToString(item))
                }
            }
            if (filteredItems.isEmpty()) {
                DropdownMenuItem(onClick = { }, enabled = false) {
                    Text("No matches found")
                }
            }
        }
    }
}

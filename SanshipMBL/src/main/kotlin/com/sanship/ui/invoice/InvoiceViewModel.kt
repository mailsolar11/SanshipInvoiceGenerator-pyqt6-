package com.sanship.ui.invoice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sanship.data.AccountingRepository
import com.sanship.data.DatabaseManager
import com.sanship.data.InvoiceData
import com.sanship.data.InvoiceModels.InvoiceItem
import java.text.DecimalFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState

class InvoiceViewModel {
    
    // State
    var invoiceData by mutableStateOf(InvoiceData())
    
    // UI Helpers
    var showSuccessMessage by mutableStateOf(false)
    var errorMessage by mutableStateOf("")

    init {
        // Auto-generate invoice number (simple logic for now, ideally fetch from DB)
        // For migration, we'll start empty or fetch max
        createNew()
    }

    fun createNew() {
        invoiceData = InvoiceData(
            invoiceNo = generateInvoiceNo(),
            date = java.time.LocalDate.now().toString(),
            items = listOf(InvoiceItem()) // Start with one empty row
        )
    }

    private fun generateInvoiceNo(): String {
        // Simple distinct generator or timestamp based
        return "INV-" + System.currentTimeMillis().toString().takeLast(6)
    }

    // --- FIELD UPDATES ---
    fun updateField(
        customerName: String? = null,
        invoiceNo: String? = null,
        date: String? = null,
        gstin: String? = null,
        billingAddress: String? = null,
        shipper: String? = null,
        consignee: String? = null,
        vessel: String? = null,
        pol: String? = null,
        pod: String? = null
    ) {
        invoiceData = invoiceData.copy(
            customerName = customerName ?: invoiceData.customerName,
            invoiceNo = invoiceNo ?: invoiceData.invoiceNo,
            date = date ?: invoiceData.date,
            gstin = gstin ?: invoiceData.gstin,
            billingAddress = billingAddress ?: invoiceData.billingAddress,
            shipper = shipper ?: invoiceData.shipper,
            consignee = consignee ?: invoiceData.consignee,
            vessel = vessel ?: invoiceData.vessel,
            pol = pol ?: invoiceData.pol,
            pod = pod ?: invoiceData.pod
        )
    }

    // --- ITEM UPDATES ---
    fun updateItem(index: Int, item: InvoiceItem) {
        // Recalculate item totals
        val taxable = item.qty * item.rate
        val cgst = taxable * 0.09 // Assuming 18% split, ideal dynamic later
        val sgst = taxable * 0.09
        val total = taxable + cgst + sgst

        val newItem = item.copy(
            amount = taxable,
            cgstAmt = cgst,
            sgstAmt = sgst,
            totalAmt = total
        )

        val newList = invoiceData.items.toMutableList()
        newList[index] = newItem
        invoiceData = invoiceData.copy(items = newList)
        
        recalculateGrandTotal()
    }

    fun addItem() {
        val newList = invoiceData.items + InvoiceItem()
        invoiceData = invoiceData.copy(items = newList)
    }

    fun removeItem(index: Int) {
        val newList = invoiceData.items.toMutableList()
        if (index in newList.indices) {
            newList.removeAt(index)
            invoiceData = invoiceData.copy(items = newList)
            recalculateGrandTotal()
        }
    }

    private fun recalculateGrandTotal() {
        var taxable = 0.0
        var cgst = 0.0
        var sgst = 0.0
        var grand = 0.0

        invoiceData.items.forEach { 
            taxable += it.amount
            cgst += it.cgstAmt
            sgst += it.sgstAmt
            grand += it.totalAmt
        }

        invoiceData = invoiceData.copy(
            taxableAmount = taxable,
            cgstAmount = cgst,
            sgstAmount = sgst,
            grandTotal = grand
        )
    }

    // --- SAVE ---
    fun save() {
        if (invoiceData.customerName.isBlank()) {
            errorMessage = "Customer Name is required"
            return
        }
        
        try {
            DatabaseManager.saveInvoice(invoiceData)
            showSuccessMessage = true
            errorMessage = ""
        } catch (e: Exception) {
            errorMessage = "Failed to save: ${e.message}"
        }
    }
}

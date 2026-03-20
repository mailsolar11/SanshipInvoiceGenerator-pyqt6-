package com.sanship.ui.invoice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sanship.data.DatabaseManager
import com.sanship.data.InvoiceModels.InvoiceItem
import com.sanship.data.InvoiceModels.InvoiceHeader
import com.sanship.data.Charge
import com.sanship.data.ClientMaster
import com.sanship.data.ClientRepository
import com.sanship.models.Job
import com.sanship.data.InvoiceRepository
import com.sanship.data.AccountingRepository
import com.sanship.repositories.JobRepository
import com.sanship.repositories.ChargeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class EnhancedInvoiceViewModel {
    
    // Document Type
    var documentType by mutableStateOf("INVOICE") // or "DEBIT_NOTE"
    
    // Header
    var invoiceNo by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
    
    // Customer & Job
    var customers = mutableStateListOf<ClientMaster>()
    var selectedCustomer by mutableStateOf<ClientMaster?>(null)
    var jobs = mutableStateListOf<Job>()
    var selectedJob by mutableStateOf<Job?>(null)
    var isJobSelected by mutableStateOf(false)
    
    // Addresses
    var billTo by mutableStateOf("")
    var consigneePreview by mutableStateOf("")
    
    // Shipment Details
    var shipper by mutableStateOf("")
    var consignee by mutableStateOf("")
    var pol by mutableStateOf("")
    var pod by mutableStateOf("")
    var vesselFlight by mutableStateOf("")
    var etd by mutableStateOf("")
    var eta by mutableStateOf("")
    
    // Consignment Details
    var jobNo by mutableStateOf("")
    var mblNo by mutableStateOf("")
    var grossWeight by mutableStateOf("")
    var netWeight by mutableStateOf("")
    var volumeCbm by mutableStateOf("")
    var packages by mutableStateOf("")
    var exchangeRate by mutableStateOf(1.0)
    var refNo by mutableStateOf("")
    
    // Items
    var items = mutableStateListOf<InvoiceItem>()
    var charges = mutableStateListOf<Charge>()
    
    // UI State
    var errorMessage by mutableStateOf("")
    var showSuccessMessage by mutableStateOf(false)
    
    init {
        loadInitialData()
    }
    
    private fun loadInitialData() {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            // Load customers
            ClientRepository().getAllClients().collect { list ->
                customers.clear()
                customers.addAll(list)
            }
            
            // Load jobs
            jobs.clear()
            jobs.addAll(JobRepository.listOpenJobs())
            
            // Load charges
            charges.clear()
            charges.addAll(com.sanship.data.ChargeRepository.getAllCharges())
            
            // Generate invoice number
            invoiceNo = getNextInvoiceNumber()
        }
    }
    
    fun onJobSelected(job: Job?) {
        if (job == null) {
            clearJobFields()
            isJobSelected = false
            return
        }
        
        selectedJob = job
        isJobSelected = true
        
        // Auto-select customer
        selectedCustomer = customers.find { it.id == job.customerId }
        
        // Populate shipment fields
        shipper = job.shipper
        consignee = job.consignee
        pol = job.pol
        pod = job.pod
        vesselFlight = job.vesselFlight
        etd = job.etd
        eta = job.eta
        
        // Populate consignment fields
        jobNo = job.jobNo
        mblNo = job.mblNo
        grossWeight = job.grossWeight
        netWeight = job.netWeight
        volumeCbm = job.volumeCbm
        packages = job.packages
        exchangeRate = job.exchangeRate
        refNo = job.refNo
        
        // Update consignee preview
        consigneePreview = job.consignee
    }
    
    private fun clearJobFields() {
        shipper = ""
        consignee = ""
        pol = ""
        pod = ""
        vesselFlight = ""
        etd = ""
        eta = ""
        jobNo = ""
        mblNo = ""
        grossWeight = ""
        netWeight = ""
        volumeCbm = ""
        packages = ""
        exchangeRate = 1.0
        refNo = ""
        consigneePreview = ""
    }
    
    fun addItem() {
        items.add(InvoiceItem(
            srNo = items.size + 1,
            currency = "INR"
        ))
    }
    
    fun deleteItem(index: Int) {
        if (index in items.indices) {
            items.removeAt(index)
            // Renumber items
            items.forEachIndexed { idx, item ->
                items[idx] = item.copy(srNo = idx + 1)
            }
        }
    }
    
    fun updateItem(index: Int, updatedItem: InvoiceItem) {
        if (index in items.indices) {
            // Calculate amounts
            val amount = updatedItem.rate * updatedItem.qty
            val taxableAmount = amount
            val cgstAmt = taxableAmount * updatedItem.cgstRate / 100
            val sgstAmt = taxableAmount * updatedItem.sgstRate / 100
            val igstAmt = taxableAmount * updatedItem.igstRate / 100
            val totalAmt = taxableAmount + cgstAmt + sgstAmt + igstAmt
            
            items[index] = updatedItem.copy(
                amount = amount,
                taxableAmount = taxableAmount,
                cgstAmt = cgstAmt,
                sgstAmt = sgstAmt,
                igstAmt = igstAmt,
                totalAmt = totalAmt
            )
        }
    }
    
    fun applyChargeToItem(index: Int, charge: Charge) {
        if (index in items.indices) {
            val currentItem = items[index]
            items[index] = currentItem.copy(
                hsnSac = charge.hsnSac,
                currency = charge.currency,
                cgstRate = charge.cgstRate,
                sgstRate = charge.sgstRate,
                igstRate = charge.igstRate,
                rate = charge.defaultRate
            )
            // Recalculate
            updateItem(index, items[index])
        }
    }
    
    fun save() {
        errorMessage = ""
        
        // Validation
        if (items.isEmpty()) {
            errorMessage = "Please add at least one item"
            return
        }
        
        // Check for zero rates/quantities
        val hasZeroValues = items.any { it.rate <= 0 || it.qty <= 0 }
        if (hasZeroValues) {
            errorMessage = "All items must have rate and quantity greater than 0"
            return
        }
        
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val totalAmount = items.sumOf { it.totalAmt }
                
                val header = InvoiceHeader(
                    invoiceNo = invoiceNo,
                    invoiceDate = date,
                    documentType = documentType,
                    customerId = selectedCustomer?.id ?: 0,
                    customerName = selectedCustomer?.shortName ?: "",
                    billingAddress = billTo,
                    consigneeAddress = consigneePreview,
                    placeOfSupply = "",
                    gstin = "",
                    jobId = selectedJob?.id ?: 0,
                    jobNo = jobNo,
                    shipper = shipper,
                    consignee = consignee,
                    pol = pol,
                    pod = pod,
                    vesselFlight = vesselFlight,
                    etd = etd,
                    eta = eta,
                    mblNo = mblNo,
                    grossWeight = grossWeight,
                    netWeight = netWeight,
                    volumeCbm = volumeCbm,
                    packages = packages,
                    exchangeRate = exchangeRate,
                    refNo = refNo,
                    taxableAmount = items.sumOf { it.taxableAmount },
                    cgstAmount = items.sumOf { it.cgstAmt },
                    sgstAmount = items.sumOf { it.sgstAmt },
                    igstAmount = items.sumOf { it.igstAmt },
                    grandTotal = totalAmount,
                    narration = "$documentType $invoiceNo"
                )
                
                // Save invoice
                val invoiceId = InvoiceRepository.saveInvoice(header, items)
                
                // Post to accounting
                AccountingRepository.ensureSystemLedgers()
                
                val totalTaxable = items.sumOf { it.taxableAmount }
                val totalCgst = items.sumOf { it.cgstAmt }
                val totalSgst = items.sumOf { it.sgstAmt }
                val totalIgst = items.sumOf { it.igstAmt }
                
                AccountingRepository.postSalesVoucher(
                    voucherNo = invoiceNo,
                    date = date,
                    partyName = selectedCustomer?.fullName ?: "Unknown",
                    gstin = selectedCustomer?.gstin,
                    taxable = totalTaxable,
                    cgst = totalCgst,
                    sgst = totalSgst,
                    igst = totalIgst,
                    narration = header.narration,
                    voucherType = if (documentType == "INVOICE") "SALES" else "DEBIT_NOTE"
                )
                
                showSuccessMessage = true
                errorMessage = ""
            } catch (e: Exception) {
                errorMessage = "Failed to save: ${e.message}"
                e.printStackTrace()
            }
        }
    }
    
    private fun getNextInvoiceNumber(): String {
        val prefix = if (documentType == "INVOICE") "SAN/INV/" else "SAN/DN/"
        val year = LocalDate.now().year.toString().substring(2)
        // Simple implementation - in production, query database for last number
        return "$prefix$year/0001"
    }
}

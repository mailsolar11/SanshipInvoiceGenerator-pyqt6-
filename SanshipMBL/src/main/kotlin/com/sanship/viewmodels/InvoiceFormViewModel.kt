package com.sanship.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sanship.data.InvoiceModels.InvoiceHeader
import com.sanship.data.InvoiceModels.InvoiceItem
import com.sanship.models.Job
import com.sanship.models.ChargeMaster
import com.sanship.models.CustomerAddress
import com.sanship.repositories.JobRepository
import com.sanship.repositories.ChargeRepository
import com.sanship.repositories.AddressRepository
import com.sanship.data.DatabaseManager
import com.sanship.services.InvoiceService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Invoice Form ViewModel
 * Exact replica of Python base_invoice_form.py logic
 */
class InvoiceFormViewModel(
    private val documentType: String = "INVOICE" // or "DEBIT_NOTE"
) {
    
    // State
    var invoiceHeader by mutableStateOf(InvoiceHeader())
        private set
    
    var items by mutableStateOf<List<InvoiceItem>>(emptyList())
        private set
    
    var customers by mutableStateOf<List<Pair<Int, String>>>(emptyList())
        private set
    
    // Lookup map: customerId -> (gstin, stateCode)
    private var customerInfoMap = mutableMapOf<Int, Pair<String, String>>()
    
    var addresses by mutableStateOf<List<CustomerAddress>>(emptyList())
        private set
    
    var jobs by mutableStateOf<List<Job>>(emptyList())
        private set
    
    var charges by mutableStateOf<List<ChargeMaster>>(emptyList())
        private set
    
    
    var selectedCustomerId by mutableStateOf<Int?>(null)
        private set
    
    var selectedAddressId by mutableStateOf<Int?>(null)
        private set
    
    var selectedJobId by mutableStateOf<Int?>(null)
        private set
    
    var currencies by mutableStateOf<List<com.sanship.repositories.Currency>>(emptyList())
        private set
    
    var selectedCurrency by mutableStateOf("INR")
        private set
    
    var jobFieldsLocked by mutableStateOf(false)
        private set
    
    var showSuccessDialog by mutableStateOf(false)
        private set
    
    var showErrorDialog by mutableStateOf(false)
        private set
    
    var errorMessage by mutableStateOf("")
        private set
    
    var isLoading by mutableStateOf(false)
        private set
    
    // Calculated totals
    var totalTaxable by mutableStateOf(0.0)
        private set
    
    var totalCgst by mutableStateOf(0.0)
        private set
    
    var totalSgst by mutableStateOf(0.0)
        private set
    
    var totalIgst by mutableStateOf(0.0)
        private set
    
    var grandTotal by mutableStateOf(0.0)
        private set
    
    init {
        initDocument()
        loadCustomers()
        loadJobs()
        loadCharges()
        loadCurrencies()
    }
    
    // ========================================
    // INITIALIZATION
    // ========================================
    
    private fun initDocument() {
        val nextInvoiceNo = getNextInvoiceNumber()
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        invoiceHeader = invoiceHeader.copy(
            invoiceNo = nextInvoiceNo,
            invoiceDate = today,
            documentType = documentType
        )
    }
    
    private fun getNextInvoiceNumber(): String {
        try {
            DatabaseManager.connect()?.use { conn ->
                // Try to get from settings table
                try {
                    val query = "SELECT value FROM settings WHERE key = 'next_invoice_number'"
                    conn.createStatement().use { stmt ->
                        val rs = stmt.executeQuery(query)
                        if (rs.next()) {
                            val current = rs.getInt("value")
                            return "SANSHIP/${LocalDate.now().year}/$current"
                        }
                    }
                } catch (e: Exception) {
                    // Settings table doesn't exist, create it
                    conn.createStatement().use { stmt ->
                        stmt.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS settings (
                                key TEXT PRIMARY KEY,
                                value INTEGER
                            )
                        """)
                        stmt.executeUpdate("""
                            INSERT OR IGNORE INTO settings (key, value) 
                            VALUES ('next_invoice_number', 1)
                        """)
                    }
                }
            }
        } catch (e: Exception) {
            println("Error getting invoice number: ${e.message}")
        }
        return "SANSHIP/${LocalDate.now().year}/1"
    }
    
    // ========================================
    // DATA LOADING
    // ========================================
    
    fun loadCustomers() {
        GlobalScope.launch {
            try {
                val customerList = mutableListOf<Pair<Int, String>>()
                val infoMap = mutableMapOf<Int, Pair<String, String>>()
                DatabaseManager.connect()?.use { conn ->
                    val query = "SELECT id, fullName, gstin, stateCode FROM client_master ORDER BY fullName"
                    conn.createStatement().use { stmt ->
                        val rs = stmt.executeQuery(query)
                        while (rs.next()) {
                            val id = rs.getInt("id")
                            val name = rs.getString("fullName")
                            val gstin = try { rs.getString("gstin") ?: "" } catch (_: Exception) { "" }
                            val stateCode = try { rs.getString("stateCode") ?: "" } catch (_: Exception) { "" }
                            customerList.add(id to name)
                            infoMap[id] = Pair(gstin, stateCode)
                        }
                    }
                }
                customers = customerList
                customerInfoMap = infoMap
            } catch (e: Exception) {
                errorMessage = "Failed to load customers: ${e.message}"
                showErrorDialog = true
            }
        }
    }
    
    fun loadJobs() {
        GlobalScope.launch {
            try {
                jobs = JobRepository.listOpenJobs()
            } catch (e: Exception) {
                errorMessage = "Failed to load jobs: ${e.message}"
                showErrorDialog = true
            }
        }
    }
    
    fun loadCharges() {
        GlobalScope.launch {
            try {
                charges = ChargeRepository.listCharges()
            } catch (e: Exception) {
                errorMessage = "Failed to load charges: ${e.message}"
                showErrorDialog = true
            }
        }
    }

    fun loadCurrencies() {
        GlobalScope.launch {
            try {
                com.sanship.repositories.CurrencyRepository.getAllCurrencies().collect { list ->
                    currencies = list
                }
            } catch (e: Exception) {
                errorMessage = "Failed to load currencies: ${e.message}"
                showErrorDialog = true
            }
        }
    }

    fun refreshData() {
        loadCustomers()
        loadJobs()
        loadCharges()
        // If customer is selected, reload addresses too
        selectedCustomerId?.let { loadAddresses(it) }
    }
    
    fun loadAddresses(customerId: Int) {
        GlobalScope.launch {
            try {
                addresses = AddressRepository.getAddressesForCustomer(customerId)
            } catch (e: Exception) {
                errorMessage = "Failed to load addresses: ${e.message}"
                showErrorDialog = true
            }
        }
    }
    
    // ========================================
    // CUSTOMER & ADDRESS SELECTION
    // ========================================
    
    fun onCustomerSelected(customerId: Int?) {
        selectedCustomerId = customerId
        selectedAddressId = null
        addresses = emptyList()
        
        if (customerId != null) {
            loadAddresses(customerId)
            
            // Update header with name, GSTIN, state code, and derived PAN
            val customerName = customers.find { it.first == customerId }?.second ?: ""
            val info = customerInfoMap[customerId]
            val gstin = info?.first ?: ""
            val stateCode = info?.second ?: ""
            // PAN is characters 3-12 of a 15-char GSTIN (e.g. 27AABCS1234D1ZK -> AABCS1234D)
            val pan = if (gstin.length >= 12) gstin.substring(2, 12) else ""
            
            invoiceHeader = invoiceHeader.copy(
                customerId = customerId,
                customerName = customerName,
                gstin = gstin,
                pan = pan,
                stateCode = stateCode
            )
        }
    }
    
    fun onAddressSelected(addressId: Int?) {
        selectedAddressId = addressId
        
        val address = addresses.find { it.id == addressId }
        if (address != null) {
            val addressText = buildString {
                append(address.label)
                append("\n")
                append(address.address)
                append("\n")
                append("${address.state} - ${address.pincode}")
                append("\n")
                append(address.country)
            }
            
            invoiceHeader = invoiceHeader.copy(
                billingAddress = addressText,
                consigneeAddress = addressText,
                placeOfSupply = address.state
            )
        }
    }
    
    // ========================================
    // JOB SELECTION & AUTO-FILL
    // ========================================
    
    fun onJobSelected(jobId: Int?) {
        selectedJobId = jobId
        
        if (jobId == null) {
            jobFieldsLocked = false
            clearJobFields()
            return
        }
        
        val job = JobRepository.getJobById(jobId)
        if (job == null) {
            jobFieldsLocked = false
            return
        }
        
        // Auto-fill customer
        if (job.customerId > 0) {
            onCustomerSelected(job.customerId)
        }

        
        // Auto-fill all job fields
        invoiceHeader = invoiceHeader.copy(
            jobId = job.id,
            jobNo = job.jobNo,
            
            // Shipment details
            shipper = job.shipper,
            consignee = job.consignee,
            pol = job.pol,
            pod = job.pod,
            vesselFlight = job.vesselFlight,
            etd = job.etd,
            eta = job.eta,
            
            // Consignment details
            mblNo = job.mblNo,
            grossWeight = job.grossWeight,
            netWeight = job.netWeight,
            volumeCbm = job.volumeCbm,
            packages = job.packages,
            exchangeRate = job.exchangeRate,
            refNo = job.refNo
        )
        
        jobFieldsLocked = true
    }
    
    
    private fun clearJobFields() {
        invoiceHeader = invoiceHeader.copy(
            jobId = 0,
            jobNo = "",
            shipper = "",
            consignee = "",
            pol = "",
            pod = "",
            vesselFlight = "",
            etd = "",
            eta = "",
            mblNo = "",
            hblNo = "",
            containerNos = "",
            shipperInvoiceNo = "",
            shipperInvoiceDate = "",
            category = "",
            grossWeight = "",
            netWeight = "",
            netWeightUnit = "",
            volumeCbm = "",
            packages = "",
            beNo = "",
            beDate = "",
            igmNo = "",
            igmDate = "",
            itemNo = "",
            exchangeRate = 1.0,
            refNo = "",
            otherRefNo = ""
        )
    }
    
    // ========================================
    // HEADER FIELD UPDATES
    // ========================================
    
    fun updateHeaderField(field: String, value: String) {
        // Removed locking logic to allow manual override
        
        invoiceHeader = when (field) {
            "invoiceDate" -> invoiceHeader.copy(invoiceDate = value)
            "vesselFlight" -> invoiceHeader.copy(vesselFlight = value)
            "etd" -> invoiceHeader.copy(etd = value)
            "eta" -> invoiceHeader.copy(eta = value)
            "mblNo" -> invoiceHeader.copy(mblNo = value)
            "grossWeight" -> invoiceHeader.copy(grossWeight = value)
            "netWeight" -> invoiceHeader.copy(netWeight = value)
            "netWeightUnit" -> invoiceHeader.copy(netWeightUnit = value)
            "volumeCbm" -> invoiceHeader.copy(volumeCbm = value)
            "packages" -> invoiceHeader.copy(packages = value)
            "beNo" -> invoiceHeader.copy(beNo = value)
            "beDate" -> invoiceHeader.copy(beDate = value)
            "igmNo" -> invoiceHeader.copy(igmNo = value)
            "igmDate" -> invoiceHeader.copy(igmDate = value)
            "itemNo" -> invoiceHeader.copy(itemNo = value)
            "exchangeRate" -> {
                val rate = value.toDoubleOrNull() ?: 1.0
                invoiceHeader = invoiceHeader.copy(exchangeRate = rate)
                // Recalculate ALL items whenever rate changes
                items = items.map { calculateItemAmounts(it) }
                recalculateTotals()
                invoiceHeader
            }
            "refNo" -> invoiceHeader.copy(refNo = value)
            "otherRefNo" -> invoiceHeader.copy(otherRefNo = value)
            "pan" -> invoiceHeader.copy(pan = value)
            "stateCode" -> invoiceHeader.copy(stateCode = value)
            "shipper" -> invoiceHeader.copy(shipper = value)
            "consignee" -> invoiceHeader.copy(consignee = value)
            "pol" -> invoiceHeader.copy(pol = value)
            "pod" -> invoiceHeader.copy(pod = value)
            "category" -> invoiceHeader.copy(category = value)
            "hblNo" -> invoiceHeader.copy(hblNo = value)
            "containerNos" -> invoiceHeader.copy(containerNos = value)
            "shipperInvoiceNo" -> invoiceHeader.copy(shipperInvoiceNo = value)
            "shipperInvoiceDate" -> invoiceHeader.copy(shipperInvoiceDate = value)
            else -> invoiceHeader
        }
    }

    fun onCurrencyChange(currency: String) {
        selectedCurrency = currency
        // Fetch rate from master
        GlobalScope.launch {
            val rate = com.sanship.repositories.CurrencyRepository.getRateForCurrency(currency)
            invoiceHeader = invoiceHeader.copy(
                currency = currency,
                exchangeRate = rate
            )
            recalculateTotals()
        }
    }
    
    // ========================================
    // ITEM MANAGEMENT
    // ========================================
    
    fun addItem() {
        val newItem = InvoiceItem(
            id = 0,
            invoiceId = 0,
            srNo = items.size + 1,
            description = "",
            hsnSac = "",
            currency = "INR",
            rate = 0.0,
            qty = 0.0,
            amount = 0.0,
            taxableAmount = 0.0,
            cgstRate = 0.0,
            cgstAmt = 0.0,
            sgstRate = 0.0,
            sgstAmt = 0.0,
            igstRate = 0.0,
            igstAmt = 0.0,
            totalAmt = 0.0
        )
        items = items + newItem
    }
    
    fun removeItem(index: Int) {
        if (index in items.indices) {
            items = items.toMutableList().apply { removeAt(index) }
            recalculateTotals()
        }
    }
    
    fun updateItem(index: Int, updatedItem: InvoiceItem) {
        if (index in items.indices) {
            val recalculated = calculateItemAmounts(updatedItem)
            items = items.toMutableList().apply { set(index, recalculated) }
            recalculateTotals()
        }
    }
    
    // ========================================
    // CHARGE AUTO-FILL
    // ========================================
    
    fun applyChargeToItem(index: Int, chargeName: String) {
        val charge = charges.find { it.chargeName == chargeName }
        if (charge != null && index in items.indices) {
            val item = items[index]
            val updated = item.copy(
                description = charge.chargeName,
                hsnSac = charge.hsnSac,
                currency = charge.currency,
                cgstRate = charge.cgstRate,
                sgstRate = charge.sgstRate,
                igstRate = charge.igstRate
            )
            updateItem(index, updated)
        }
    }
    
    // ========================================
    // CALCULATIONS
    // ========================================
    
    private fun calculateItemAmounts(item: InvoiceItem): InvoiceItem {
        val amountFcy = item.rate * item.qty
        val taxableBase = amountFcy * invoiceHeader.exchangeRate
        val cgst = taxableBase * (item.cgstRate / 100.0)
        val sgst = taxableBase * (item.sgstRate / 100.0)
        val igst = taxableBase * (item.igstRate / 100.0)
        val totalBase = taxableBase + cgst + sgst + igst
        
        return item.copy(
            amount = amountFcy,
            taxableAmount = taxableBase,
            cgstAmt = cgst,
            sgstAmt = sgst,
            igstAmt = igst,
            totalAmt = totalBase
        )
    }
    
    private fun recalculateTotals() {
        totalTaxable = items.sumOf { it.taxableAmount }
        totalCgst = items.sumOf { it.cgstAmt }
        totalSgst = items.sumOf { it.sgstAmt }
        totalIgst = items.sumOf { it.igstAmt }
        grandTotal = items.sumOf { it.totalAmt }
    }
    
    // ========================================
    // VALIDATION
    // ========================================
    
    private fun validateInvoice(): String? {
        // Customer and address are now optional
        
        if (items.isEmpty()) {
            return "Please add at least one item"
        }
        
        if (items.any { it.description.isBlank() }) {
            return "All items must have a description"
        }
        
        if (items.any { it.rate <= 0 || it.qty <= 0 }) {
            return "All items must have valid rate and quantity"
        }
        
        return null
    }
    
    // ========================================
    // SAVE
    // ========================================
    
    fun saveInvoice() {
        GlobalScope.launch {
            try {
                isLoading = true
                
                // Validate
                val validationError = validateInvoice()
                if (validationError != null) {
                    errorMessage = validationError
                    showErrorDialog = true
                    isLoading = false
                    return@launch
                }
                
                // Update header with totals
                val headerToSave = invoiceHeader.copy(
                    taxableAmount = totalTaxable,
                    cgstAmount = totalCgst,
                    sgstAmount = totalSgst,
                    igstAmount = totalIgst,
                    grandTotal = grandTotal
                )
                
                // ===========================================
                // SAVE using Transaction Manager (Dual-DB 2PC)
                // ===========================================
                val invoiceId = when (documentType) {
                    "CREDIT_NOTE" -> com.sanship.services.TransactionManager.saveCreditNote(
                        header = headerToSave,
                        items = items
                    )
                    "DEBIT_NOTE" -> com.sanship.services.TransactionManager.saveDebitNote(
                        header = headerToSave,
                        items = items
                    )
                    "QUOTATION" -> com.sanship.services.TransactionManager.saveQuotation(
                        header = headerToSave,
                        items = items
                    )
                    else -> com.sanship.services.TransactionManager.saveInvoice(
                        header = headerToSave,
                        items = items
                    )
                }

                isLoading = false
                
                if (invoiceId > 0) {
                    showSuccessDialog = true
                    // Reset form
                    resetForm()
                     
                    // Generate PDF immediately? 
                    // Usually user might want to export after saving.
                    // For now, we rely on the manual "Export PDF" button or we can auto-export here.
                    // If we want auto-export, we need to do it BEFORE resetForm or pass data.
                    // Current flow: User saves -> Success Dialog -> User clicks Export PDF manually if needed?
                    // actually code below shows resetForm() is called. 
                    // If user wants to export PDF of just saved invoice, they need to do it BEFORE saving or 
                    // we need to keep the data or reload it. 
                    // The "Export PDF" button usually exports CURRENT form data. 
                    // If we reset form, we lose data.
                    // Ideally, we should perform exportPDF() inside here if "Save & Print" is desired.
                    // But we keep it simple: Save -> Success. User can go to report to print or print before reset?
                    // Wait, if resetForm() is called, items are gone. 
                    // Let's assume user flow is: Fill -> Save -> Done.
                    // If they want PDF, they should click Export PDF *before* Save or go to List View.
                } else {
                    errorMessage = "Transaction Failed. Please check logs."
                    showErrorDialog = true
                }
                
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "Failed to save invoice: ${e.message}"
                showErrorDialog = true
            }
        }
    }
    
    private fun incrementInvoiceNumber() {
        DatabaseManager.connect()?.use { conn ->
            val query = "UPDATE settings SET value = value + 1 WHERE key = 'next_invoice_number'"
            conn.createStatement().executeUpdate(query)
        }
    }
    
    private fun resetForm() {
        initDocument()
        items = emptyList()
        selectedCustomerId = null
        selectedAddressId = null
        selectedJobId = null
        jobFieldsLocked = false
        addresses = emptyList()
        recalculateTotals()
    }
    
    // ========================================
    // DIALOG MANAGEMENT
    // ========================================
    
    fun dismissSuccessDialog() {
        showSuccessDialog = false
    }
    
    fun dismissErrorDialog() {
        showErrorDialog = false
    }
    
    // ========================================
    // PDF EXPORT
    // ========================================
    
    fun exportPDF() {
        try {
            isLoading = true
            
            // Update header with current totals
            val headerToExport = invoiceHeader.copy(
                taxableAmount = totalTaxable,
                cgstAmount = totalCgst,
                sgstAmount = totalSgst,
                igstAmount = totalIgst,
                grandTotal = grandTotal
            )
            
            // Generate filename
            val filename = "${documentType}_${invoiceHeader.invoiceNo.replace("/", "_")}.pdf"
            val outputPath = when (documentType) {
                "DEBIT_NOTE"   -> com.sanship.utils.DocumentPaths.getDebitNotePath(filename)
                "CREDIT_NOTE"  -> com.sanship.utils.DocumentPaths.getCreditNotePath(filename)
                else           -> com.sanship.utils.DocumentPaths.getTaxInvoicePath(filename)
            }
            
            // Generate PDF
            com.sanship.services.HtmlPdfService.generateInvoicePdf(
                header = headerToExport,
                items = items,
                outputPath = outputPath
            )
            
            isLoading = false
            errorMessage = "PDF exported to: $outputPath"
            showSuccessDialog = true
            
        } catch (e: Exception) {
            isLoading = false
            errorMessage = "Failed to export PDF: ${e.message}"
            showErrorDialog = true
        }
    }

    // ========================================
    // E-INVOICE GENERATION
    // ========================================
    
    fun generateEInvoice() {
        GlobalScope.launch {
            try {
                isLoading = true
                
                // 1. Validate
                if (invoiceHeader.gstin.isBlank()) {
                    throw RuntimeException("Customer GSTIN is required for e-Invoice")
                }
                
                val validationError = validateInvoice()
                if (validationError != null) {
                    throw RuntimeException(validationError)
                }
                
                // 2. Prepare Data (Ensure totals are up to date)
                recalculateTotals()
                val headerToSave = invoiceHeader.copy(
                    taxableAmount = totalTaxable,
                    cgstAmount = totalCgst,
                    sgstAmount = totalSgst,
                    igstAmount = totalIgst,
                    grandTotal = grandTotal
                )
                
                // 3. Auto-save the invoice first (if not already saved)
                var invoiceId = invoiceHeader.id
                if (invoiceId == 0) {
                    invoiceId = com.sanship.services.TransactionManager.saveInvoice(
                        header = headerToSave,
                        items = items
                    )
                    if (invoiceId <= 0) {
                        throw RuntimeException("Failed to save invoice before IRN generation")
                    }
                    // Update the in-memory header with the saved ID
                    invoiceHeader = invoiceHeader.copy(id = invoiceId)
                }

                // 4. Call e-Invoice API
                val response = com.sanship.services.EInvoiceService.generateIrn(headerToSave, items)
                
                if (response.Status == "1" && response.Data != null) {
                    val irn = response.Data.Irn
                    val ackNo = response.Data.AckNo.toString()
                    val ackDate = response.Data.AckDt
                    val signedQr = response.Data.SignedQRCode
                    val signedInvoice = response.Data.SignedInvoice
                    
                    // 5. Persist IRN data back to database
                    DatabaseManager.updateIrnFields(
                        invoiceNo = invoiceHeader.invoiceNo,
                        irn = irn,
                        ackNo = ackNo,
                        ackDate = ackDate,
                        signedQr = signedQr,
                        signedInvoice = signedInvoice
                    )
                    
                    // 6. Update in-memory header (so PDF export can use IRN/QR)
                    invoiceHeader = invoiceHeader.copy(
                        irn = irn,
                        ackNo = ackNo,
                        ackDate = ackDate,
                        signedQr = signedQr,
                        signedInvoice = signedInvoice
                    )
                    
                    // 7. Generate e-Invoice PDF with QR code layout
                    val eInvoiceHeader = invoiceHeader.copy(
                        taxableAmount = totalTaxable,
                        cgstAmount = totalCgst,
                        sgstAmount = totalSgst,
                        igstAmount = totalIgst,
                        grandTotal = grandTotal
                    )
                    
                    val eInvoiceDir = java.io.File(System.getProperty("user.home"), "Downloads/Sanship/E-Invoice")
                    if (!eInvoiceDir.exists()) eInvoiceDir.mkdirs()
                    val eInvoiceFilename = "eInvoice_${invoiceHeader.invoiceNo.replace("/", "_")}.pdf"
                    val eInvoicePath = java.io.File(eInvoiceDir, eInvoiceFilename).absolutePath
                    
                    com.sanship.services.HtmlPdfService.generateInvoicePdf(
                        header = eInvoiceHeader,
                        items = items,
                        outputPath = eInvoicePath
                    )
                    
                    isLoading = false
                    errorMessage = "e-Invoice generated!\nIRN: ${irn.take(20)}...\nPDF saved to: $eInvoicePath"
                    showSuccessDialog = true
                } else {
                    // Handle API Error
                    val errorMsg = response.ErrorDetails?.joinToString("\n") { "${it.ErrorCode}: ${it.ErrorMessage}" } ?: "Unknown Error"
                    throw RuntimeException(errorMsg)
                }
                
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "e-Invoice Failed: ${e.message}"
                showErrorDialog = true
            }
        }
    }
}

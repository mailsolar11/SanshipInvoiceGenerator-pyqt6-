package com.sanship.ui.purchase

import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.sanship.data.*
import com.sanship.models.Job
import com.sanship.repositories.JobRepository
import com.sanship.repositories.CurrencyRepository
import com.sanship.repositories.Currency
import com.sanship.services.AccountingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class PurchaseInvoiceViewModel(private val scope: CoroutineScope) {

    var purchaseNo by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now().toString())
    var selectedVendor by mutableStateOf<VendorMaster?>(null)
    var placeOfSupply by mutableStateOf("Maharashtra")
    var reverseCharge by mutableStateOf(false)
    var selectedJob by mutableStateOf<Job?>(null)
    var narration by mutableStateOf("")
    
    // Multi-Currency
    var selectedCurrency by mutableStateOf("INR")
    var exchangeRate by mutableStateOf(1.0)
    var currencies = mutableStateListOf<Currency>()

    var items = mutableStateListOf<PurchaseItem>()
    
    var vendors = mutableStateListOf<VendorMaster>()
    var jobs = mutableStateListOf<Job>()
    
    var isSaving by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var successMessage by mutableStateOf<String?>(null)

    private val vendorRepo = VendorRepository()

    init {
        loadInitialData()
        addItem()
    }

    private fun loadInitialData() {
        scope.launch(Dispatchers.IO) {
            try {
                vendorRepo.getAllVendors().collect { list ->
                    withContext(Dispatchers.Main) {
                        vendors.clear()
                        vendors.addAll(list)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                val jobList = JobRepository.listOpenJobs()
                withContext(Dispatchers.Main) {
                    jobs.clear()
                    jobs.addAll(jobList)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        scope.launch(Dispatchers.IO) {
            try {
                CurrencyRepository.getAllCurrencies().collect { list ->
                    withContext(Dispatchers.Main) {
                        currencies.clear()
                        currencies.addAll(list)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onCurrencyChange(currencyCode: String) {
        selectedCurrency = currencyCode
        scope.launch(Dispatchers.IO) {
            val rate = CurrencyRepository.getRateForCurrency(currencyCode)
            withContext(Dispatchers.Main) {
                exchangeRate = rate
                recalculateItems()
            }
        }
    }

    fun addItem() {
        items.add(
            PurchaseItem(
                srNo = items.size + 1,
                description = "",
                hsnSac = "",
                qty = 1.0,
                rate = 0.0,
                amount = 0.0,
                taxableAmount = 0.0,
                totalAmount = 0.0,
                currency = selectedCurrency,
                exchangeRate = exchangeRate
            )
        )
    }

    fun removeItem(index: Int) {
        if (items.size > 1) {
            items.removeAt(index)
            recalculateItems()
        }
    }

    fun updateItem(index: Int, updatedItem: PurchaseItem) {
        items[index] = updatedItem
        recalculateItems()
    }

    private fun recalculateItems() {
        val isInterstate = selectedVendor?.stateCode != "27" && selectedVendor?.stateCode != null && selectedVendor?.stateCode != ""
        
        for (i in items.indices) {
            val item = items[i]
            // Taxable in Base Currency (INR) = qty * rate * exchangeRate
            val taxableBase = item.qty * item.rate * exchangeRate
            
            var cgstR = 0.0; var cgstA = 0.0
            var sgstR = 0.0; var sgstA = 0.0
            var igstR = 0.0; var igstA = 0.0
            
            val totalGstRate = 18.0
            
            if (isInterstate) {
                igstR = totalGstRate
                igstA = taxableBase * (igstR / 100.0)
            } else {
                cgstR = totalGstRate / 2.0
                cgstA = taxableBase * (cgstR / 100.0)
                sgstR = totalGstRate / 2.0
                sgstA = taxableBase * (sgstR / 100.0)
            }
            
            items[i] = item.copy(
                amount = item.qty * item.rate, // FCY amount
                taxableAmount = taxableBase,   // Base currency taxable
                cgstRate = cgstR,
                cgstAmount = cgstA,
                sgstRate = sgstR,
                sgstAmount = sgstA,
                igstRate = igstR,
                igstAmount = igstA,
                totalAmount = taxableBase + cgstA + sgstA + igstA, // Base currency total
                currency = selectedCurrency,
                exchangeRate = exchangeRate
            )
        }
    }

    fun save() {
        if (purchaseNo.isBlank()) { errorMessage = "Purchase No is required"; return }
        if (selectedVendor == null) { errorMessage = "Vendor is required"; return }
        
        isSaving = true
        errorMessage = null
        
        scope.launch(Dispatchers.IO) {
            try {
                val header = PurchaseHeader(
                    purchaseNo = purchaseNo,
                    date = date,
                    vendorId = selectedVendor!!.id,
                    vendorName = selectedVendor!!.fullName,
                    vendorGstin = selectedVendor!!.gstin,
                    vendorAddress = selectedVendor!!.fullAddress,
                    placeOfSupply = placeOfSupply,
                    reverseCharge = reverseCharge,
                    jobId = selectedJob?.id ?: 0,
                    jobNo = selectedJob?.jobNo ?: "",
                    currency = selectedCurrency,
                    exchangeRate = exchangeRate,
                    taxableAmount = items.sumOf { it.taxableAmount },
                    cgstAmount = items.sumOf { it.cgstAmount },
                    sgstAmount = items.sumOf { it.sgstAmount },
                    igstAmount = items.sumOf { it.igstAmount },
                    grandTotal = items.sumOf { it.totalAmount },
                    narration = narration
                )
                
                PurchaseRepository.savePurchase(header, items.toList())
                
                DatabaseManager.connect()?.use { conn ->
                    AccountingEngine.postPurchaseInternal(conn, header, items.toList())
                }
                
                withContext(Dispatchers.Main) {
                    successMessage = "Purchase voucher saved successfully!"
                    isSaving = false
                    reset()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    errorMessage = "Error: ${e.message}"
                    isSaving = false
                }
            }
        }
    }

    private fun reset() {
        purchaseNo = ""
        selectedVendor = null
        selectedJob = null
        narration = ""
        items.clear()
        addItem()
    }
}

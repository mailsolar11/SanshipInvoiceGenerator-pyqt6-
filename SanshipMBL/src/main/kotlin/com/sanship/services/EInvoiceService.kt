package com.sanship.services

import com.sanship.data.InvoiceModels.InvoiceHeader
import com.sanship.data.InvoiceModels.InvoiceItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import java.io.IOException

object EInvoiceService {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // SANDBOX URL (Replace with actual GSP Sandbox URL)
    // For now, we will use a mock structure unless user provides specific GSP 
    // Most GSPs (ClearTax, Masters India) have specific auth flows.
    // We will implement a generic "Mock" first to prove the flow, then plug in the URL.
    
    private const val BASE_URL = "https://einv-apisandbox.nic.in/eivital/v1.03" 
    // NOTE: Direct NIC access requires static IP and encryption. 
    // GSPs simplify this. 
    
    // Credentials (To be populated from Settings)
    var clientId = ""
    var clientSecret = ""
    var userName = ""
    var password = ""
    var gstin = ""
    
    data class AuthResponse(val status: String, val data: AuthData?, val error: ErrorDetails?)
    data class AuthData(val AuthToken: String, val Sek: String) // Sek = Session Encryption Key
    data class ErrorDetails(val ErrorCode: String, val ErrorMessage: String)

    data class EInvoiceResponse(val Status: String, val Data: EInvoiceData?, val ErrorDetails: List<ErrorDetails>?)
    data class EInvoiceData(val AckNo: Long, val AckDt: String, val Irn: String, val SignedInvoice: String, val SignedQRCode: String)

    fun authenticate(): String {
        // Mock Authentication for Sandbox Demo
        // In real GSP, this calls /auth endpoint
        println("Authenticating with Sandbox...")
        return "MOCK_AUTH_TOKEN_12345"
    }

    fun generateIrn(header: InvoiceHeader, items: List<InvoiceItem>): EInvoiceResponse {
        // 1. Authenticate
        // val token = authenticate() 
        
        // 2. Build Payload (Schema v1.1)
        val payload = buildPayload(header, items)
        val jsonBody = gson.toJson(payload)
        
        // --- REAL API CALL (Commented out until credentials provided) ---
        /*
        val request = Request.Builder()
            .url("$BASE_URL/auth") // Placeholder
            .post(jsonBody.toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            return gson.fromJson(response.body!!.string(), EInvoiceResponse::class.java)
        }
        */
        
        // --- MOCK RESPONSE FOR TESTING FLOW ---
        // Simulates a successful response from NIC/GSP
        return EInvoiceResponse(
            Status = "1",
            Data = EInvoiceData(
                AckNo = 123456789012345,
                AckDt = "2024-02-18 14:30:00",
                Irn = "7b1d4719602058309d4719602058309d7b1d4719602058309d4719602058309d",
                SignedInvoice = "jwt_signed_invoice_string",
                SignedQRCode = "signed_qr_code_string_normally_very_long" 
                // We need a real QR string structure to test ZXing later, 
                // but for now, any string will generate a QR.
            ),
            ErrorDetails = null
        )
    }
    
    private fun buildPayload(header: InvoiceHeader, items: List<InvoiceItem>): Map<String, Any> {
        // Maps internal logic to e-Invoice JSON Schema
        return mapOf(
            "Version" to "1.1",
            "TranDtls" to mapOf(
                "TaxSch" to "GST",
                "SupTyp" to "B2B",
                "RegRev" to (if(header.reverseCharge) "Y" else "N"),
                "EcmGstin" to null,
                "IgstOnIntra" to "N"
            ),
            "DocDtls" to mapOf(
                "Typ" to "INV",
                "No" to header.invoiceNo,
                "Dt" to header.invoiceDate.replace("-", "/") // dd/mm/yyyy
            ),
            "SellerDtls" to mapOf(
                "Gstin" to "27TESTGSTIN01", // Replace with settings
                "LglNm" to "SANSHIP LOGISTICS",
                "Addr1" to "Mumbai",
                "Loc" to "Mumbai",
                "Pin" to 400001,
                "Stcd" to "27"
            ),
            "BuyerDtls" to mapOf(
                "Gstin" to header.gstin,
                "LglNm" to header.customerName,
                "Pos" to (header.placeOfSupply ?: "27"),
                "Addr1" to header.billingAddress,
                "Loc" to header.placeOfSupply, // Simplify
                "Pin" to 400001, // Need Pin in DB!
                "Stcd" to (header.placeOfSupply ?: "27")
            ),
            "ItemList" to items.map { item ->
                mapOf(
                    "SlNo" to item.srNo.toString(),
                    "PrdDesc" to item.description,
                    "IsServc" to "Y", // Assuming Logistics = Service
                    "HsnCd" to item.hsnSac,
                    "Qty" to item.qty,
                    "Unit" to "OTH",
                    "UnitPrice" to item.rate,
                    "TotAmt" to item.amount,
                    "AssAmt" to item.taxableAmount,
                    "GstRt" to (item.cgstRate + item.sgstRate + item.igstRate),
                    "IgstAmt" to item.igstAmt,
                    "CgstAmt" to item.cgstAmt,
                    "SgstAmt" to item.sgstAmt,
                    "TotItemVal" to item.totalAmt
                )
            },
            "ValDtls" to mapOf(
                "AssVal" to header.taxableAmount,
                "CgstVal" to header.cgstAmount,
                "SgstVal" to header.sgstAmount,
                "IgstVal" to header.igstAmount,
                "CesVal" to 0,
                "StCesVal" to 0,
                "RndOffAmt" to 0,
                "TotInvVal" to header.grandTotal
            )
        )
    }
}

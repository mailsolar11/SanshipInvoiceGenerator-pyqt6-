package com.sanship.utils

import java.io.File

object DocumentPaths {
    private val BASE_DIR = File(System.getProperty("user.home"), "Downloads/Sanship").absolutePath

    fun getPath(folderName: String, fileName: String): String {
        val dir = File(BASE_DIR, folderName)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, fileName).absolutePath
    }

    fun getSalesRegisterPath(fileName: String) = getPath("Sales Registers", fileName)
    fun getTaxInvoicePath(fileName: String) = getPath("Tax Invoices", fileName)
    fun getDeliveryNoticePath(fileName: String) = getPath("Delivery Notice", fileName)
    fun getArrivalNoticePath(fileName: String) = getPath("Arrival Notice", fileName)
    fun getDebitNotePath(fileName: String) = getPath("Debit Note", fileName)
    fun getCreditNotePath(fileName: String) = getPath("Credit Note", fileName)
    fun getEWayBillPath(fileName: String) = getPath("E-Way Bill", fileName)
    fun getGstReportPath(fileName: String) = getPath("GST Reports", fileName)
    fun getMblPath(fileName: String) = getPath("Bill of Lading", fileName)
    fun getQuotationPath(fileName: String) = getPath("Quotations", fileName)
}

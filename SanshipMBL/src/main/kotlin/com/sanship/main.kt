package com.sanship

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Divider
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import com.sanship.data.DatabaseManager
import com.sanship.ui.mbl.MblScreen
import com.sanship.ui.invoice.InvoiceFormScreen
import com.sanship.ui.debitnote.DebitNoteScreen
import com.sanship.ui.customer.CustomerScreen
import com.sanship.ui.job.JobScreen
import com.sanship.ui.reports.StatementScreen
import com.sanship.ui.dashboard.DashboardScreen
import com.sanship.ui.purchase.PurchaseInvoiceScreen
import com.sanship.ui.purchase.PurchaseRegisterScreen

fun main() = application {
    // FIX: Force Database Initialization before the UI loads
    // This prevents the "Please call Database.connect()" error
    DatabaseManager.initDatabase()

    // Initialize Accounting Database (Double-Entry Bookkeeping System)
    com.sanship.data.AccountingDatabaseManager.initAccountingDb()

    val windowState = rememberWindowState(
        position = WindowPosition(Alignment.Center),
        width = 1200.dp,
        height = 900.dp
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Sanship - Logistics Software"
    ) {
        // App Content
        MainAppContainer()
    }
}

enum class Screen {
    DASHBOARD, MBL, INVOICE, DEBIT_NOTE, CREDIT_NOTE, QUOTATION, EWAY_BILL, JOBS, DOCS, EXPENSE, RECEIPT, PAYMENT, JOURNAL, CUSTOMER, VENDOR, STATEMENTS, CHART_OF_ACCOUNTS, CASH_BANK, BANK_RECON, REPORTS, JOB_PNL, TRIAL_BALANCE, PNL, BALANCE_SHEET, OUTSTANDING, GSTR_1, GSTR_3B, BL, BACKUP_RESTORE, PURCHASE_INVOICE, PURCHASE_REGISTER, INVOICE_SEARCH
}

@Composable
fun MainAppContainer() {
    var user by remember { mutableStateOf<String?>(null) }
    var role by remember { mutableStateOf<String?>(null) }

    if (user == null) {
        com.sanship.ui.settings.LoginScreen(onLoginSuccess = { u, r ->
            user = u
            role = r
        })
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFE0E0E0)).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("Logged in as: $user ($role)", style = androidx.compose.material.MaterialTheme.typography.subtitle2)
                Spacer(Modifier.width(16.dp))
                Button(onClick = { user = null; role = null }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.height(30.dp)) {
                    Text("Logout", fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)
                }
            }
            MainApp(isAdmin = role == "ADMIN")
        }
    }
}

@Composable
fun MainApp(isAdmin: Boolean) {
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

    Row(modifier = Modifier.fillMaxSize()) {

        // --- SIDEBAR ---
        Column(
            modifier = Modifier
                .width(250.dp)
                .fillMaxHeight()
                .background(Color(0xFF2C3E50))
                .padding(vertical = 20.dp)
                .verticalScroll(rememberScrollState()) // Allow scrolling for many items
        ) {
            NavButton("Dashboard", currentScreen == Screen.DASHBOARD) { currentScreen = Screen.DASHBOARD }

            Spacer(Modifier.height(6.dp))
            Text("OPERATIONS", color = Color.Gray, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp), fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)
            NavButton("Bill of Lading", currentScreen == Screen.MBL) { currentScreen = Screen.MBL }
            NavButton("Create Job", currentScreen == Screen.JOBS) { currentScreen = Screen.JOBS }
            NavButton("Generate Docs", currentScreen == Screen.DOCS) { currentScreen = Screen.DOCS }
            NavButton("Tax Invoice", currentScreen == Screen.INVOICE) { currentScreen = Screen.INVOICE }
            NavButton("Quotation", currentScreen == Screen.QUOTATION) { currentScreen = Screen.QUOTATION }
            NavButton("Debit Notes", currentScreen == Screen.DEBIT_NOTE) { currentScreen = Screen.DEBIT_NOTE }
            NavButton("Credit Notes", currentScreen == Screen.CREDIT_NOTE) { currentScreen = Screen.CREDIT_NOTE }
            NavButton("E-Way Bill", currentScreen == Screen.EWAY_BILL) { currentScreen = Screen.EWAY_BILL }

            Spacer(Modifier.height(6.dp))
            Text("PURCHASE", color = Color.Gray, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp), fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)
            NavButton("Purchase Invoice", currentScreen == Screen.PURCHASE_INVOICE) { currentScreen = Screen.PURCHASE_INVOICE }
            NavButton("Purchase Register", currentScreen == Screen.PURCHASE_REGISTER) { currentScreen = Screen.PURCHASE_REGISTER }

            Spacer(Modifier.height(6.dp))
            Text("ACCOUNTING", color = Color.Gray, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp), fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)
            NavButton("Expense Entry", currentScreen == Screen.EXPENSE) { currentScreen = Screen.EXPENSE }
            NavButton("Receipt Entry", currentScreen == Screen.RECEIPT) { currentScreen = Screen.RECEIPT }
            NavButton("Payment Entry", currentScreen == Screen.PAYMENT) { currentScreen = Screen.PAYMENT }
            NavButton("Journal Entry", currentScreen == Screen.JOURNAL) { currentScreen = Screen.JOURNAL }

            Spacer(Modifier.height(6.dp))
            Text("REPORTS", color = Color.Gray, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp), fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)

            NavButton("Invoice Search", currentScreen == Screen.INVOICE_SEARCH) { currentScreen = Screen.INVOICE_SEARCH }
            NavButton("GSTR-1 Export", currentScreen == Screen.GSTR_1) { currentScreen = Screen.GSTR_1 }
            NavButton("GSTR-3B Summary", currentScreen == Screen.GSTR_3B) { currentScreen = Screen.GSTR_3B }
            NavButton("Job Profitability", currentScreen == Screen.JOB_PNL) { currentScreen = Screen.JOB_PNL }
            NavButton("Chart of Accounts", currentScreen == Screen.CHART_OF_ACCOUNTS) { currentScreen = Screen.CHART_OF_ACCOUNTS }
            NavButton("Cash & Bank Book", currentScreen == Screen.CASH_BANK) { currentScreen = Screen.CASH_BANK }
            NavButton("Bank Reconciliation", currentScreen == Screen.BANK_RECON) { currentScreen = Screen.BANK_RECON }
            NavButton("Trial Balance", currentScreen == Screen.TRIAL_BALANCE) { currentScreen = Screen.TRIAL_BALANCE }
            NavButton("Profit & Loss", currentScreen == Screen.PNL) { currentScreen = Screen.PNL }
            NavButton("Balance Sheet", currentScreen == Screen.BALANCE_SHEET) { currentScreen = Screen.BALANCE_SHEET }
            NavButton("Ledger Statement", currentScreen == Screen.STATEMENTS) { currentScreen = Screen.STATEMENTS }
            NavButton("Outstanding", currentScreen == Screen.OUTSTANDING) { currentScreen = Screen.OUTSTANDING }
            NavButton("Customer Master", currentScreen == Screen.CUSTOMER) { currentScreen = Screen.CUSTOMER }
            NavButton("Vendor Master", currentScreen == Screen.VENDOR) { currentScreen = Screen.VENDOR }
            NavButton("Sales Register", currentScreen == Screen.REPORTS) { currentScreen = Screen.REPORTS }

            if (isAdmin) {
                Spacer(Modifier.height(10.dp))
                Text("SETTINGS", color = Color.Gray, modifier = Modifier.padding(start = 16.dp), fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)
                NavButton("Backup & Restore", currentScreen == Screen.BACKUP_RESTORE) { currentScreen = Screen.BACKUP_RESTORE }
            }
        }

        // --- CONTENT ---
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when(currentScreen) {
                Screen.DASHBOARD -> com.sanship.ui.dashboard.DashboardScreen()
                Screen.MBL -> MblScreen()
                Screen.INVOICE -> InvoiceFormScreen(documentType = "INVOICE")
                Screen.QUOTATION -> com.sanship.ui.quotation.QuotationScreen()
                Screen.DEBIT_NOTE -> InvoiceFormScreen(documentType = "DEBIT_NOTE")
                Screen.JOBS -> JobScreen()
                Screen.DOCS -> com.sanship.ui.docs.DocumentTemplateScreen()
                Screen.EXPENSE -> com.sanship.ui.expense.ExpenseVoucherScreen()
                Screen.RECEIPT -> com.sanship.ui.receipt.ReceiptVoucherScreen()
                Screen.PAYMENT -> com.sanship.ui.payment.PaymentVoucherScreen()
                Screen.JOURNAL -> com.sanship.ui.journal.JournalEntryScreen()
                Screen.CREDIT_NOTE -> com.sanship.ui.creditnote.CreditNoteScreen()
                Screen.EWAY_BILL -> com.sanship.ui.ewaybill.EWayBillScreen()
                Screen.CUSTOMER -> CustomerScreen()
                Screen.VENDOR -> com.sanship.ui.vendor.VendorScreen()
                Screen.STATEMENTS -> StatementScreen()
                Screen.CHART_OF_ACCOUNTS -> com.sanship.ui.reports.ChartOfAccountsScreen()
                Screen.CASH_BANK -> com.sanship.ui.reports.CashBankBookScreen()
                Screen.BANK_RECON -> com.sanship.ui.reports.BankReconScreen()
                Screen.BACKUP_RESTORE -> com.sanship.ui.settings.BackupRestoreScreen()
                Screen.REPORTS -> com.sanship.ui.reports.ReportScreen()
                Screen.GSTR_1 -> com.sanship.ui.reports.Gstr1Screen()
                Screen.GSTR_3B -> com.sanship.ui.reports.Gstr3bScreen()
                Screen.JOB_PNL -> com.sanship.ui.reports.JobProfitabilityScreen()
                Screen.TRIAL_BALANCE -> com.sanship.ui.reports.TrialBalanceScreen()
                Screen.PNL -> com.sanship.ui.reports.ProfitLossScreen()
                Screen.BALANCE_SHEET -> com.sanship.ui.reports.BalanceSheetScreen()
                Screen.OUTSTANDING -> com.sanship.ui.reports.OutstandingScreen()
                Screen.BL -> com.sanship.ui.bl.BLScreen()
                Screen.PURCHASE_INVOICE -> PurchaseInvoiceScreen()
                Screen.PURCHASE_REGISTER -> PurchaseRegisterScreen()
                Screen.INVOICE_SEARCH -> com.sanship.ui.invoice.InvoiceSearchScreen()
            }
        }
    }
}

@Composable
fun NavButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        colors = ButtonDefaults.textButtonColors(
            backgroundColor = if (isSelected) Color(0xFF34495E) else Color.Transparent,
            contentColor = Color.White
        )
    ) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(8.dp), color = Color.White)
    }
}
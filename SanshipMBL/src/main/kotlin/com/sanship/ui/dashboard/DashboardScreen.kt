package com.sanship.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanship.data.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DashboardKpis(
    val monthlyRevenue: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val totalReceivables: Double = 0.0,
    val totalPayables: Double = 0.0,
    val totalInvoices: Int = 0,
    val totalJobs: Int = 0,
    val totalBLs: Int = 0,
    val totalCustomers: Int = 0
)

data class RecentInvoice(
    val invoiceNo: String,
    val date: String,
    val customerName: String,
    val amount: Double,
    val type: String
)

data class RecentJob(
    val jobNo: String,
    val shipper: String,
    val consignee: String,
    val pol: String,
    val pod: String
)

@Composable
fun DashboardScreen() {
    val scope = rememberCoroutineScope()
    var kpis by remember { mutableStateOf(DashboardKpis()) }
    var recentInvoices by remember { mutableStateOf(emptyList<RecentInvoice>()) }
    var recentJobs by remember { mutableStateOf(emptyList<RecentJob>()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load dashboard data
    LaunchedEffect(Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val now = LocalDate.now()
                    val monthStart = now.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val monthEnd = now.format(DateTimeFormatter.ISO_LOCAL_DATE)

                    DatabaseManager.connect()?.use { conn ->
                        // --- KPIs ---
                        // Monthly revenue (sum of grandTotal from invoices this month)
                        var monthlyRevenue = 0.0
                        try {
                            conn.prepareStatement(
                                "SELECT COALESCE(SUM(grandTotal), 0) as total FROM invoices WHERE date >= ? AND date <= ?"
                            ).use { ps ->
                                ps.setString(1, monthStart)
                                ps.setString(2, monthEnd)
                                val rs = ps.executeQuery()
                                if (rs.next()) monthlyRevenue = rs.getDouble("total")
                            }
                        } catch (_: Exception) {}

                        // Monthly expenses (sum from ledger_entries with expense type this month)
                        var monthlyExpenses = 0.0
                        try {
                            conn.prepareStatement(
                                """SELECT COALESCE(SUM(le.dr_amount), 0) as total 
                                   FROM ledger_entries le 
                                   JOIN vouchers v ON le.voucher_id = v.id
                                   JOIN ledgers l ON le.ledger_id = l.id
                                   JOIN ledger_groups lg ON l.group_id = lg.id
                                   WHERE lg.nature = 'EXPENSE' AND v.voucher_date >= ? AND v.voucher_date <= ?"""
                            ).use { ps ->
                                ps.setString(1, monthStart)
                                ps.setString(2, monthEnd)
                                val rs = ps.executeQuery()
                                if (rs.next()) monthlyExpenses = rs.getDouble("total")
                            }
                        } catch (_: Exception) {}

                        // Total outstanding receivables (sum of grandTotal from all invoices - sum of receipts)
                        var totalReceivables = 0.0
                        try {
                            conn.prepareStatement(
                                "SELECT COALESCE(SUM(grandTotal), 0) as total FROM invoices"
                            ).use { ps ->
                                val rs = ps.executeQuery()
                                if (rs.next()) totalReceivables = rs.getDouble("total")
                            }
                        } catch (_: Exception) {}

                        // Total counts
                        var totalInvoices = 0
                        try {
                            conn.prepareStatement("SELECT COUNT(*) as cnt FROM invoices").use { ps ->
                                val rs = ps.executeQuery()
                                if (rs.next()) totalInvoices = rs.getInt("cnt")
                            }
                        } catch (_: Exception) {}

                        var totalJobs = 0
                        try {
                            conn.prepareStatement("SELECT COUNT(*) as cnt FROM jobs").use { ps ->
                                val rs = ps.executeQuery()
                                if (rs.next()) totalJobs = rs.getInt("cnt")
                            }
                        } catch (_: Exception) {}

                        var totalBLs = 0
                        try {
                            conn.prepareStatement("SELECT COUNT(*) as cnt FROM mbl_headers").use { ps ->
                                val rs = ps.executeQuery()
                                if (rs.next()) totalBLs = rs.getInt("cnt")
                            }
                        } catch (_: Exception) {}

                        var totalCustomers = 0
                        try {
                            conn.prepareStatement("SELECT COUNT(*) as cnt FROM client_master").use { ps ->
                                val rs = ps.executeQuery()
                                if (rs.next()) totalCustomers = rs.getInt("cnt")
                            }
                        } catch (_: Exception) {}

                        kpis = DashboardKpis(
                            monthlyRevenue = monthlyRevenue,
                            monthlyExpenses = monthlyExpenses,
                            totalReceivables = totalReceivables,
                            totalPayables = 0.0,
                            totalInvoices = totalInvoices,
                            totalJobs = totalJobs,
                            totalBLs = totalBLs,
                            totalCustomers = totalCustomers
                        )

                        // --- Recent Invoices (last 10) ---
                        val invoices = mutableListOf<RecentInvoice>()
                        try {
                            conn.prepareStatement(
                                "SELECT invoiceNo, date, customerName, grandTotal, type FROM invoices ORDER BY date DESC, id DESC LIMIT 10"
                            ).use { ps ->
                                val rs = ps.executeQuery()
                                while (rs.next()) {
                                    invoices.add(
                                        RecentInvoice(
                                            invoiceNo = rs.getString("invoiceNo") ?: "",
                                            date = rs.getString("date") ?: "",
                                            customerName = rs.getString("customerName") ?: "",
                                            amount = rs.getDouble("grandTotal"),
                                            type = rs.getString("type") ?: "INVOICE"
                                        )
                                    )
                                }
                            }
                        } catch (_: Exception) {}
                        recentInvoices = invoices

                        // --- Recent Jobs (last 10) ---
                        val jobsList = mutableListOf<RecentJob>()
                        try {
                            conn.prepareStatement(
                                "SELECT job_no, shipper, consignee, pol, pod FROM jobs ORDER BY id DESC LIMIT 10"
                            ).use { ps ->
                                val rs = ps.executeQuery()
                                while (rs.next()) {
                                    jobsList.add(
                                        RecentJob(
                                            jobNo = rs.getString("job_no") ?: "",
                                            shipper = rs.getString("shipper") ?: "",
                                            consignee = rs.getString("consignee") ?: "",
                                            pol = rs.getString("pol") ?: "",
                                            pod = rs.getString("pod") ?: ""
                                        )
                                    )
                                }
                            }
                        } catch (_: Exception) {}
                        recentJobs = jobsList
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Dashboard",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A2E)
                )
                Text(
                    "Welcome back! Here's your business overview.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                fontSize = 14.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // ============ KPI CARDS ROW 1 (Financial) ============
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                KpiCard(
                    title = "Monthly Revenue",
                    value = currencyFormat.format(kpis.monthlyRevenue),
                    icon = Icons.Default.TrendingUp,
                    gradientStart = Color(0xFF667EEA),
                    gradientEnd = Color(0xFF764BA2),
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Monthly Expenses",
                    value = currencyFormat.format(kpis.monthlyExpenses),
                    icon = Icons.Default.TrendingDown,
                    gradientStart = Color(0xFFFF6B6B),
                    gradientEnd = Color(0xFFEE5A24),
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Outstanding",
                    value = currencyFormat.format(kpis.totalReceivables),
                    icon = Icons.Default.AccountBalance,
                    gradientStart = Color(0xFF11998E),
                    gradientEnd = Color(0xFF38EF7D),
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Net Profit (Month)",
                    value = currencyFormat.format(kpis.monthlyRevenue - kpis.monthlyExpenses),
                    icon = Icons.Default.Star,
                    gradientStart = Color(0xFFF093FB),
                    gradientEnd = Color(0xFFF5576C),
                    modifier = Modifier.weight(1f)
                )
            }

            // ============ KPI CARDS ROW 2 (Counts) ============
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CountCard("Total Invoices", kpis.totalInvoices, Icons.Default.Receipt, Color(0xFF6C5CE7), Modifier.weight(1f))
                CountCard("Active Jobs", kpis.totalJobs, Icons.Default.Work, Color(0xFF00B894), Modifier.weight(1f))
                CountCard("Bills of Lading", kpis.totalBLs, Icons.Default.Description, Color(0xFF0984E3), Modifier.weight(1f))
                CountCard("Customers", kpis.totalCustomers, Icons.Default.People, Color(0xFFFDAB3D), Modifier.weight(1f))
            }

            // ============ RECENT ACTIVITY TABLES ============
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Recent Invoices
                Card(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Recent Invoices",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1A1A2E)
                        )
                        Spacer(Modifier.height(12.dp))

                        // Table Header
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FA)).padding(8.dp)
                        ) {
                            Text("Date", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                            Text("Invoice No", Modifier.weight(1.2f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                            Text("Customer", Modifier.weight(1.5f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                            Text("Amount", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                        }

                        if (recentInvoices.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No invoices yet", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            recentInvoices.forEach { inv ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp)
                                ) {
                                    Text(inv.date, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1)
                                    Text(inv.invoiceNo, Modifier.weight(1.2f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(inv.customerName, Modifier.weight(1.5f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(currencyFormat.format(inv.amount), Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                                }
                                Divider(color = Color(0xFFF0F0F0))
                            }
                        }
                    }
                }

                // Recent Jobs
                Card(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Recent Jobs",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1A1A2E)
                        )
                        Spacer(Modifier.height(12.dp))

                        // Table Header
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FA)).padding(8.dp)
                        ) {
                            Text("Job No", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                            Text("Shipper", Modifier.weight(1.2f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                            Text("POL", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                            Text("POD", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                        }

                        if (recentJobs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No jobs yet", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            recentJobs.forEach { job ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp)
                                ) {
                                    Text(job.jobNo, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1)
                                    Text(job.shipper, Modifier.weight(1.2f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(job.pol, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(job.pod, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Divider(color = Color(0xFFF0F0F0))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================================
// KPI CARD COMPONENTS
// ======================================

@Composable
private fun KpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    gradientStart: Color,
    gradientEnd: Color,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = 4.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.height(130.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(listOf(gradientStart, gradientEnd))
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Icon(icon, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                }
                Text(
                    value,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CountCard(
    title: String,
    count: Int,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(90.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(24.dp))
            }
            Column {
                Text("$count", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E))
                Text(title, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

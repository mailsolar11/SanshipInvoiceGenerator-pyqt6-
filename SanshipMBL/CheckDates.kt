import java.sql.DriverManager
fun main() {
    val url = "jdbc:sqlite:sanship.db"
    val conn = DriverManager.getConnection(url)
    val rs = conn.createStatement().executeQuery("SELECT date FROM invoices")
    var isoCount = 0
    var strCount = 0
    while(rs.next()) {
        val d = rs.getString(1)
        if (d != null && d.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) isoCount++
        else if (d != null) strCount++
    }
    println("ISO Dates: $isoCount, Old string formats: $strCount")
    val rs2 = conn.createStatement().executeQuery("SELECT date, customerName, taxableAmount, gstin FROM invoices LIMIT 5")
    while(rs2.next()) {
        println("Inv: "+rs2.getString(1)+" - "+rs2.getString(2)+" - "+rs2.getDouble(3)+" - GSTIN: "+rs2.getString(4))
    }
}

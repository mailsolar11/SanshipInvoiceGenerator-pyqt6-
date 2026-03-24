import sqlite3
import codecs

def verify():
    db = sqlite3.connect('sanship.db')
    
    tables = {}
    for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'"):
        table_name = row[0]
        if table_name == 'sqlite_sequence':
            continue
        cols = []
        for col_row in db.execute(f"PRAGMA table_info({table_name})"):
            cols.append(col_row[1])
        tables[table_name] = cols
    
    lines = []
    lines.append("=== UNIFIED sanship.db SCHEMA ===\n")
    
    critical_tables = ['ledger_groups', 'ledgers', 'voucher_types', 'vouchers', 'ledger_entries']
    for t in critical_tables:
        if t in tables:
            count = list(db.execute(f"SELECT COUNT(*) FROM {t}"))[0][0]
            lines.append(f"Table: {t} ({count} rows)")
            lines.append(f"  Columns: {', '.join(tables[t])}")
            lines.append("")
    
    lines.append("\n=== CRITICAL COLUMN CHECKS ===\n")
    
    checks = [
        ("vouchers", "voucher_type_id"),
        ("vouchers", "voucher_type"),
        ("vouchers", "voucher_date"),
        ("vouchers", "narration"),
        ("vouchers", "job_id"),
        ("vouchers", "created_at"),
        ("ledgers", "party_id"),
        ("ledgers", "created_at"),
        ("ledgers", "gstin"),
        ("ledgers", "opening_balance"),
        ("ledgers", "opening_type"),
        ("ledgers", "is_system"),
        ("ledger_groups", "nature"),
        ("ledger_groups", "parent_id"),
        ("ledger_groups", "created_at"),
        ("ledger_entries", "dr_amount"),
        ("ledger_entries", "cr_amount"),
        ("ledger_entries", "bank_date"),
        ("ledger_entries", "container_number"),
        ("ledger_entries", "created_at"),
    ]
    
    failures = []
    for table, col in checks:
        if col in tables.get(table, []):
            lines.append(f"  PASS {table}.{col}")
        else:
            lines.append(f"  FAIL {table}.{col}")
            failures.append(f"{table}.{col}")
    
    # Sample data
    lines.append("\n=== SAMPLE DATA ===\n")
    r = list(db.execute("SELECT id, name, group_id FROM ledgers WHERE name = 'Bank HDFC'"))
    lines.append(f"Bank HDFC: {r}")
    r = list(db.execute("SELECT id, name FROM ledgers WHERE name = 'Maersk Line India'"))
    lines.append(f"Maersk Line India: {r}")
    vts = list(db.execute("SELECT id, name FROM voucher_types ORDER BY id"))
    lines.append(f"Voucher Types: {vts}")
    vs = list(db.execute("SELECT voucher_no, voucher_type, voucher_type_id FROM vouchers ORDER BY id DESC LIMIT 5"))
    lines.append(f"Recent Vouchers: {vs}")
    
    lines.append(f"\nFAILURES: {failures if failures else 'NONE'}")
    
    db.close()
    
    with codecs.open('verify_result.txt', 'w', 'utf-8') as f:
        f.write('\n'.join(lines))
    
    for l in lines:
        print(l)

verify()

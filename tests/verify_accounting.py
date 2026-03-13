import sys
import os
import sqlite3

# Add src to path
sys.path.append(os.path.join(os.path.dirname(__file__), "..", "src"))

from services.invoice_service import save_and_post_invoice
from database import init_db
from init_db import init_accounting_db

def verify():
    # 1. Setup
    print("Initializing DB...")
    init_db()
    init_accounting_db()

    # 2. Mock Data
    header = {
        "invoice_number": "TEST-INV-002",
        "date": "2023-10-27",
        "type": "INVOICE",
        "bill_to": "Test Customer Ltd",
        "narration": "Test Invoice for Accounting Verification",
        "total_amount": 118.0
    }

    items = [
        {
            "sr_no": 1,
            "description": "Consulting",
            "hsn_sac": "9983",
            "cur": "INR",
            "rate": 100.0,
            "qty": 1.0,
            "amount": 100.0,
            "taxable_amount": 100.0,
            "cgst_rate": 9.0,
            "cgst_amt": 9.0,
            "sgst_rate": 9.0,
            "sgst_amt": 9.0,
            "total_amt": 118.0
        }
    ]

    # 3. Execution
    print("Saving Invoice...")
    try:
        inv_id = save_and_post_invoice(header=header, items=items)
        print(f"✅ Invoice saved with ID: {inv_id}")
    except Exception as e:
        print(f"❌ Failed to save invoice: {e}")
        return

    # 4. Verification
    # DB is in Project Root (Sanship/data.db)
    conn = sqlite3.connect(os.path.join(os.path.dirname(__file__), "..", "data.db"))
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    # Check Voucher
    cur.execute("SELECT * FROM vouchers WHERE voucher_no='TEST-INV-002'")
    voucher = cur.fetchone()
    if voucher:
        print(f"✅ Voucher Found! ID: {voucher['id']}, TypeID: {voucher['voucher_type_id']}")
    else:
        print("❌ Voucher NOT Found!")
        return

    # Check Entries
    cur.execute(f"SELECT * FROM ledger_entries WHERE voucher_id={voucher['id']}")
    entries = cur.fetchall()
    
    total_dr = 0
    total_cr = 0
    
    print("\n--- Ledger Entries ---")
    for e in entries:
        # Get ledger name
        cur.execute(f"SELECT name FROM ledgers WHERE id={e['ledger_id']}")
        name = cur.fetchone()['name']
        print(f"Ledger: {name:<20} | DR: {e['dr_amount']:<10} | CR: {e['cr_amount']:<10}")
        total_dr += e['dr_amount']
        total_cr += e['cr_amount']
    
    print("-" * 50)
    print(f"TOTAL                | DR: {total_dr:<10} | CR: {total_cr:<10}")

    if abs(total_dr - total_cr) < 0.01:
        print("✅ Accounting Equation Balanced (DR = CR)")
    else:
        print("❌ Accounting Equation IMBALANCE!")

    conn.close()

if __name__ == "__main__":
    verify()

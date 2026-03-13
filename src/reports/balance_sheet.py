# src/reports/balance_sheet.py

from accounting.db import get_accounting_conn


def get_balance_sheet(as_on: str) -> dict:
    conn = get_accounting_conn()
    cur = conn.cursor()

    def fetch(nature):
        cur.execute("""
            SELECT l.name AS ledger, SUM(le.dr_amount - le.cr_amount) AS amount
            FROM ledger_entries le
            JOIN ledgers l ON l.id = le.ledger_id
            JOIN ledger_groups g ON g.id = l.group_id
            JOIN vouchers v ON v.id = le.voucher_id
            WHERE g.nature = ?
              AND v.voucher_date <= ?
            GROUP BY l.name
        """, (nature, as_on))
        return [
            {"ledger": r["ledger"], "amount": float(r["amount"] or 0)}
            for r in cur.fetchall()
        ]

    assets = fetch("ASSET")
    liabilities = fetch("LIABILITY")

    conn.close()
    return {
        "assets": assets,
        "liabilities": liabilities
    }

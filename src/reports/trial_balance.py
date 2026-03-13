# src/reports/trial_balance.py

from decimal import Decimal
from accounting.db import get_accounting_conn


def get_trial_balance(as_on: str | None = None) -> list[dict]:
    conn = get_accounting_conn()
    cur = conn.cursor()

    date_filter = ""
    params = ()

    if as_on:
        date_filter = "AND v.voucher_date <= ?"
        params = (as_on,)

    cur.execute(
        f"""
        SELECT
            l.id AS ledger_id,
            l.name AS ledger_name,
            g.nature,
            SUM(le.dr_amount) AS total_dr,
            SUM(le.cr_amount) AS total_cr
        FROM ledgers l
        JOIN ledger_groups g ON g.id = l.group_id
        LEFT JOIN ledger_entries le ON le.ledger_id = l.id
        LEFT JOIN vouchers v ON v.id = le.voucher_id
        WHERE 1=1 {date_filter}
        GROUP BY l.id, l.name, g.nature
        ORDER BY l.name
        """,
        params
    )

    rows = []
    for r in cur.fetchall():
        dr = Decimal(r["total_dr"] or 0)
        cr = Decimal(r["total_cr"] or 0)
        balance = dr - cr

        rows.append({
            "ledger_id": r["ledger_id"],
            "ledger_name": r["ledger_name"],
            "nature": r["nature"],
            "dr_balance": balance if balance > 0 else Decimal("0.00"),
            "cr_balance": -balance if balance < 0 else Decimal("0.00"),
        })

    conn.close()
    return rows

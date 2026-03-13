# src/reports/pnl.py

from accounting.db import get_accounting_conn


def get_profit_and_loss(from_date: str, to_date: str) -> dict:
    conn = get_accounting_conn()
    cur = conn.cursor()

    # Income
    cur.execute("""
        SELECT SUM(le.cr_amount - le.dr_amount) AS amount
        FROM ledger_entries le
        JOIN ledgers l ON l.id = le.ledger_id
        JOIN ledger_groups g ON g.id = l.group_id
        JOIN vouchers v ON v.id = le.voucher_id
        WHERE g.nature = 'INCOME'
          AND v.voucher_date BETWEEN ? AND ?
    """, (from_date, to_date))

    total_income = cur.fetchone()["amount"] or 0

    # Expense
    cur.execute("""
        SELECT SUM(le.dr_amount - le.cr_amount) AS amount
        FROM ledger_entries le
        JOIN ledgers l ON l.id = le.ledger_id
        JOIN ledger_groups g ON g.id = l.group_id
        JOIN vouchers v ON v.id = le.voucher_id
        WHERE g.nature = 'EXPENSE'
          AND v.voucher_date BETWEEN ? AND ?
    """, (from_date, to_date))

    total_expense = cur.fetchone()["amount"] or 0

    conn.close()

    return {
        "total_income": float(total_income),
        "total_expense": float(total_expense),
        "net_profit": float(total_income - total_expense)
    }

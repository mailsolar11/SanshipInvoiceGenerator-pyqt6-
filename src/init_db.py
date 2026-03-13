# src/init_db.py

import sqlite3
import os
from accounting.ledgers import ensure_system_ledgers
from accounting.db import DB_PATH


def init_accounting_db():
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # ---------------- Ledger Groups ----------------
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ledger_groups (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL UNIQUE,
        nature TEXT CHECK (
            nature IN ('ASSET', 'LIABILITY', 'INCOME', 'EXPENSE')
        ) NOT NULL,
        parent_id INTEGER,
        created_at TEXT DEFAULT CURRENT_TIMESTAMP
    )
    """)

    # ---------------- Ledgers ----------------
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ledgers (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL UNIQUE,
        group_id INTEGER NOT NULL,
        opening_balance REAL DEFAULT 0,
        opening_type TEXT CHECK (opening_type IN ('DR','CR')),
        gstin TEXT,
        is_system INTEGER DEFAULT 0,
        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (group_id) REFERENCES ledger_groups(id)
    )
    """)

    # ---------------- Voucher Types ----------------
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS voucher_types (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT UNIQUE NOT NULL,
        affects_inventory INTEGER DEFAULT 0,
        is_system INTEGER DEFAULT 1
    )
    """)

    # ---------------- Vouchers ----------------
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS vouchers (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        voucher_no TEXT UNIQUE NOT NULL,
        voucher_type_id INTEGER NOT NULL,
        voucher_date TEXT NOT NULL,
        narration TEXT,
        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (voucher_type_id) REFERENCES voucher_types(id)
    )
    """)

    # ---------------- Ledger Entries ----------------
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ledger_entries (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        voucher_id INTEGER NOT NULL,
        ledger_id INTEGER NOT NULL,
        dr_amount REAL DEFAULT 0,
        cr_amount REAL DEFAULT 0,
        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (voucher_id) REFERENCES vouchers(id),
        FOREIGN KEY (ledger_id) REFERENCES ledgers(id)
    )
    """)

    # ---------------- Seed Ledger Groups ----------------
    cursor.executemany("""
        INSERT OR IGNORE INTO ledger_groups (name, nature)
        VALUES (?, ?)
    """, [
        ("Assets", "ASSET"),
        ("Liabilities", "LIABILITY"),
        ("Income", "INCOME"),
        ("Expenses", "EXPENSE"),
        ("Duties & Taxes", "LIABILITY"),
    ])

    # ---------------- Seed Voucher Types ----------------
    cursor.executemany("""
        INSERT OR IGNORE INTO voucher_types (name, affects_inventory, is_system)
        VALUES (?, 0, 1)
    """, [
        ("SALES",),
        ("DEBIT_NOTE",),
        ("RECEIPT",),
        ("PAYMENT",),
        ("JOURNAL",),
    ])

    conn.commit()
    conn.close()

    # ---------------- System Ledgers ----------------
    ensure_system_ledgers()

    print("✅ Accounting database initialized successfully")


if __name__ == "__main__":
    init_accounting_db()

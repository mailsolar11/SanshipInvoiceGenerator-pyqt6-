# src/accounting/ledgers.py

"""
LEDGER ENGINE
=============
Single source of truth for:
- Ledger creation
- Ledger lookup
- System ledger protection

This module is SELF-HEALING:
It ensures required tables exist before use.
"""

from typing import Optional
from accounting.db import get_accounting_conn


# ======================================================
# INTERNAL: ENSURE TABLES EXIST
# ======================================================
def _ensure_tables():
    conn = get_accounting_conn()
    cur = conn.cursor()

    # Ledger Groups
    cur.execute("""
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

    # Ledgers
    cur.execute("""
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

    conn.commit()
    conn.close()


# ======================================================
# INTERNAL HELPERS
# ======================================================
def _fetch_one(query, params=()):
    _ensure_tables()
    conn = get_accounting_conn()
    cur = conn.cursor()
    cur.execute(query, params)
    row = cur.fetchone()
    conn.close()
    return dict(row) if row else None


def _execute(query, params=()):
    _ensure_tables()
    conn = get_accounting_conn()
    cur = conn.cursor()
    cur.execute(query, params)
    conn.commit()
    lid = cur.lastrowid
    conn.close()
    return lid


# ======================================================
# LEDGER GROUP
# ======================================================
def get_ledger_group_id(name: str) -> int:
    _ensure_tables()
    row = _fetch_one(
        "SELECT id FROM ledger_groups WHERE name=?",
        (name,)
    )
    if not row:
        raise RuntimeError(f"Ledger group missing: {name}")
    return row["id"]


# ======================================================
# LEDGER LOOKUP
# ======================================================
def get_ledger_by_name(name: str) -> Optional[dict]:
    _ensure_tables()
    return _fetch_one(
        "SELECT * FROM ledgers WHERE name=?",
        (name,)
    )


def get_ledger_id(name: str) -> int:
    ledger = get_ledger_by_name(name)
    if not ledger:
        raise RuntimeError(f"Ledger not found: {name}")
    return ledger["id"]


# ======================================================
# LEDGER CREATION
# ======================================================
def create_ledger(
    *,
    name: str,
    group_name: str,
    opening_balance: float = 0.0,
    opening_type: Optional[str] = None,
    gstin: Optional[str] = None,
    is_system: int = 0
) -> int:

    if not name:
        raise ValueError("Ledger name required")

    existing = get_ledger_by_name(name)
    if existing:
        return existing["id"]

    if opening_balance and opening_type not in ("DR", "CR"):
        raise ValueError("Opening type must be DR or CR")

    gid = get_ledger_group_id(group_name)

    return _execute(
        """
        INSERT INTO ledgers
        (name, group_id, opening_balance, opening_type, gstin, is_system)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (
            name,
            gid,
            opening_balance,
            opening_type,
            gstin,
            is_system
        )
    )


# ======================================================
# SYSTEM LEDGERS
# ======================================================
def ensure_system_ledgers():
    _ensure_tables()

    # Core income
    create_ledger(name="SALES", group_name="Income", is_system=1)

    # GST Output
    create_ledger(name="CGST OUTPUT", group_name="Duties & Taxes", is_system=1)
    create_ledger(name="SGST OUTPUT", group_name="Duties & Taxes", is_system=1)
    create_ledger(name="IGST OUTPUT", group_name="Duties & Taxes", is_system=1)

    # Rounding
    create_ledger(name="ROUND OFF", group_name="Income", is_system=1)


# ======================================================
# PARTY LEDGER
# ======================================================
def get_or_create_party_ledger(*, party_name: str, gstin: Optional[str] = None) -> int:
    if not party_name:
        raise ValueError("Party name required")

    return create_ledger(
        name=party_name.strip(),
        group_name="Assets",
        gstin=gstin,
        is_system=0
    )


# ======================================================
# LEDGER LISTING
# ======================================================
def list_ledgers(text: Optional[str] = None) -> list:
    _ensure_tables()
    conn = get_accounting_conn()
    cur = conn.cursor()
    
    query = "SELECT * FROM ledgers"
    params = []
    
    if text:
        query += " WHERE name LIKE ?"
        params.append(f"%{text}%")
        
    query += " ORDER BY name ASC"
    
    cur.execute(query, params)
    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    
    return rows

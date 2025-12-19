# src/database.py
import sqlite3
import os
from datetime import datetime

BASE_DIR = os.path.dirname(os.path.dirname(__file__))
DB_PATH = os.path.join(BASE_DIR, "data.db")


# =====================================================
# CONNECTION
# =====================================================
def get_conn():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


# =====================================================
# INIT DATABASE (CANONICAL)
# =====================================================
def init_db():
    conn = get_conn()
    cur = conn.cursor()

    # ---------------- SETTINGS ----------------
    cur.execute("""
        CREATE TABLE IF NOT EXISTS settings (
            key TEXT PRIMARY KEY,
            value TEXT
        )
    """)

    # ---------------- CONSIGNEES ----------------
    cur.execute("""
        CREATE TABLE IF NOT EXISTS consignees (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            gstin TEXT,
            pan TEXT
        )
    """)

    # ---------------- ADDRESSES ----------------
    cur.execute("""
        CREATE TABLE IF NOT EXISTS consignee_addresses (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            consignee_id INTEGER,
            label TEXT,
            address TEXT,
            state TEXT,
            state_code TEXT,
            pincode TEXT,
            country TEXT,
            is_default INTEGER DEFAULT 0,
            FOREIGN KEY (consignee_id) REFERENCES consignees(id)
        )
    """)

    # ---------------- JOBS (SINGLE SOURCE OF TRUTH) ----------------
    cur.execute("""
        CREATE TABLE IF NOT EXISTS jobs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            job_no TEXT UNIQUE,
            customer_id INTEGER,
            shipper TEXT,
            consignee TEXT,
            pol TEXT,
            pod TEXT,
            vessel_flight TEXT,
            etd TEXT,
            eta TEXT,
            mbl_no TEXT,
            hbl_no TEXT,
            gross_weight TEXT,
            net_weight TEXT,
            volume_cbm TEXT,
            packages TEXT,
            be_no TEXT,
            be_date TEXT,
            igm_no TEXT,
            igm_date TEXT,
            item_no TEXT,
            exchange_rate TEXT,
            ref_no TEXT,
            status TEXT DEFAULT 'OPEN',
            created_at TEXT,
            FOREIGN KEY (customer_id) REFERENCES consignees(id)
        )
    """)

    # ---------------- INVOICES ----------------
    cur.execute("""
        CREATE TABLE IF NOT EXISTS invoices (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            invoice_number TEXT,
            date TEXT,
            type TEXT,
            job_id INTEGER,
            job_no TEXT,
            bill_to TEXT,
            consignee_preview TEXT,
            shipper TEXT,
            ship_consigne TEXT,
            pol TEXT,
            pod TEXT,
            vessel_flight TEXT,
            etd TEXT,
            eta TEXT,
            c_date TEXT,
            c_invoice_no TEXT,
            mbl_no TEXT,
            gross_weight TEXT,
            net_weight TEXT,
            volume_cbm TEXT,
            packages TEXT,
            be_no TEXT,
            be_date TEXT,
            igm_no TEXT,
            igm_date TEXT,
            item_no TEXT,
            exchange_rate TEXT,
            ref_no TEXT,
            total_amount REAL,
            FOREIGN KEY (job_id) REFERENCES jobs(id)
        )
    """)

    # ---------------- INVOICE ITEMS ----------------
    cur.execute("""
        CREATE TABLE IF NOT EXISTS invoice_items (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            invoice_id INTEGER,
            sr_no INTEGER,
            description TEXT,
            hsn_sac TEXT,
            cur TEXT,
            rate REAL,
            qty REAL,
            amount REAL,
            taxable_amount REAL,
            cgst_rate REAL,
            cgst_amt REAL,
            sgst_rate REAL,
            sgst_amt REAL,
            total_amt REAL,
            FOREIGN KEY (invoice_id) REFERENCES invoices(id)
        )
    """)

    conn.commit()
    conn.close()


# =====================================================
# SETTINGS
# =====================================================
def get_setting(key):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("SELECT value FROM settings WHERE key=?", (key,))
    r = cur.fetchone()
    conn.close()
    return r["value"] if r else None


def set_setting(key, value):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("""
        INSERT INTO settings(key, value)
        VALUES (?, ?)
        ON CONFLICT(key) DO UPDATE SET value=excluded.value
    """, (key, str(value)))
    conn.commit()
    conn.close()


# =====================================================
# JOB CRUD
# =====================================================
def insert_job(data):
    conn = get_conn()
    cur = conn.cursor()

    data = data.copy()
    data["created_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    cols = ",".join(data.keys())
    placeholders = ",".join(["?"] * len(data))

    cur.execute(
        f"INSERT INTO jobs ({cols}) VALUES ({placeholders})",
        list(data.values())
    )

    jid = cur.lastrowid
    conn.commit()
    conn.close()
    return jid


def list_jobs():
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("SELECT * FROM jobs ORDER BY id DESC")
    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    return rows


def get_job(job_id):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("SELECT * FROM jobs WHERE id=?", (job_id,))
    r = cur.fetchone()
    conn.close()
    return dict(r) if r else None


def close_job(job_id):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("UPDATE jobs SET status='CLOSED' WHERE id=?", (job_id,))
    conn.commit()
    conn.close()


def list_jobs_for_dropdown():
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("""
        SELECT id, job_no
        FROM jobs
        ORDER BY id DESC
    """)
    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    return rows


def list_open_jobs_for_dropdown():
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("""
        SELECT id, job_no
        FROM jobs
        WHERE status='OPEN'
        ORDER BY id DESC
    """)
    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    return rows


# =====================================================
# CONSIGNEE CRUD
# =====================================================
def add_consignee(name, gstin=None, pan=None):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute(
        "INSERT INTO consignees (name, gstin, pan) VALUES (?, ?, ?)",
        (name, gstin, pan)
    )
    conn.commit()
    cid = cur.lastrowid
    conn.close()
    return cid


def list_consignees(search=None):
    conn = get_conn()
    cur = conn.cursor()

    if search:
        q = f"%{search}%"
        cur.execute("""
            SELECT * FROM consignees
            WHERE name LIKE ? OR gstin LIKE ? OR pan LIKE ?
            ORDER BY name
        """, (q, q, q))
    else:
        cur.execute("SELECT * FROM consignees ORDER BY name")

    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    return rows


def get_consignee(consignee_id):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("SELECT * FROM consignees WHERE id=?", (consignee_id,))
    r = cur.fetchone()
    conn.close()
    return dict(r) if r else None


def update_consignee(consignee_id, name, gstin=None, pan=None):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("""
        UPDATE consignees
        SET name=?, gstin=?, pan=?
        WHERE id=?
    """, (name, gstin, pan, consignee_id))
    conn.commit()
    conn.close()


def delete_consignee(consignee_id):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("DELETE FROM consignee_addresses WHERE consignee_id=?", (consignee_id,))
    cur.execute("DELETE FROM consignees WHERE id=?", (consignee_id,))
    conn.commit()
    conn.close()


# =====================================================
# ADDRESS CRUD
# =====================================================
def add_consignee_address(consignee_id, label, address, state, state_code, pincode, country, is_default):
    conn = get_conn()
    cur = conn.cursor()

    if is_default:
        cur.execute(
            "UPDATE consignee_addresses SET is_default=0 WHERE consignee_id=?",
            (consignee_id,)
        )

    cur.execute("""
        INSERT INTO consignee_addresses
        (consignee_id, label, address, state, state_code, pincode, country, is_default)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """, (consignee_id, label, address, state, state_code, pincode, country, is_default))

    conn.commit()
    conn.close()


def get_addresses_for_consignee(consignee_id):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("""
        SELECT * FROM consignee_addresses
        WHERE consignee_id=?
        ORDER BY is_default DESC
    """, (consignee_id,))
    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    return rows


def update_address(address_id, label, address, state, state_code, pincode, country, is_default):
    conn = get_conn()
    cur = conn.cursor()

    if is_default:
        cur.execute("""
            UPDATE consignee_addresses
            SET is_default=0
            WHERE consignee_id = (
                SELECT consignee_id FROM consignee_addresses WHERE id=?
            )
        """, (address_id,))

    cur.execute("""
        UPDATE consignee_addresses
        SET label=?, address=?, state=?, state_code=?, pincode=?, country=?, is_default=?
        WHERE id=?
    """, (label, address, state, state_code, pincode, country, is_default, address_id))

    conn.commit()
    conn.close()


def delete_address(address_id):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("DELETE FROM consignee_addresses WHERE id=?", (address_id,))
    conn.commit()
    conn.close()


# =====================================================
# INVOICE SAVE
# =====================================================
def insert_invoice(header, items):
    conn = get_conn()
    cur = conn.cursor()

    cols = ",".join(header.keys())
    placeholders = ",".join(["?"] * len(header))

    cur.execute(
        f"INSERT INTO invoices ({cols}) VALUES ({placeholders})",
        list(header.values())
    )

    invoice_id = cur.lastrowid

    for it in items:
        cur.execute("""
            INSERT INTO invoice_items
            (invoice_id, sr_no, description, hsn_sac, cur, rate, qty, amount,
             taxable_amount, cgst_rate, cgst_amt, sgst_rate, sgst_amt, total_amt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            invoice_id,
            it["sr_no"],
            it["description"],
            it["hsn_sac"],
            it["cur"],
            it["rate"],
            it["qty"],
            it["amount"],
            it["taxable_amount"],
            it["cgst_rate"],
            it["cgst_amt"],
            it["sgst_rate"],
            it["sgst_amt"],
            it["total_amt"]
        ))

    conn.commit()
    conn.close()
    return invoice_id


# =====================================================
# CUSTOMER ALIAS
# =====================================================
def list_customers():
    return list_consignees()


def get_customer(customer_id):
    return get_consignee(customer_id)


def get_addresses_for_customer(customer_id):
    return get_addresses_for_consignee(customer_id)

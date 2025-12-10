import sqlite3
import os
import json

BASE_DIR = os.path.dirname(os.path.dirname(__file__))
DB_PATH = os.path.join(BASE_DIR, "db", "invoices.db")
os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)


def get_conn():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("""
        CREATE TABLE IF NOT EXISTS invoices (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            invoice_number TEXT,
            type TEXT,
            date TEXT,
            total REAL,
            raw_json TEXT
        )
    """)
    cur.execute("""
        CREATE TABLE IF NOT EXISTS items (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            invoice_id INTEGER,
            sr_no INTEGER,
            description TEXT,
            hsn TEXT,
            cur TEXT,
            rate REAL,
            qty REAL,
            amount REAL,
            taxable REAL,
            cgst REAL,
            cgst_amt REAL,
            sgst REAL,
            sgst_amt REAL,
            total_amt REAL,
            FOREIGN KEY(invoice_id) REFERENCES invoices(id)
        )
    """)
    conn.commit()
    conn.close()


def insert_invoice(header, items):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("""
        INSERT INTO invoices (invoice_number, type, date, total, raw_json)
        VALUES (?, ?, ?, ?, ?)
    """, (
        header["invoice_number"],
        header["type"],
        header["date"],
        header["total_amount"],
        json.dumps(header)
    ))
    inv_id = cur.lastrowid

    for it in items:
        cur.execute("""
            INSERT INTO items (invoice_id, sr_no, description, hsn, cur, rate,
                               qty, amount, taxable, cgst, cgst_amt, sgst,
                               sgst_amt, total_amt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            inv_id, it["sr_no"], it["description"], it["hsn_sac"], it["cur"],
            it["rate"], it["qty"], it["amount"], it["taxable_amount"],
            it["cgst_rate"], it["cgst_amt"],
            it["sgst_rate"], it["sgst_amt"],
            it["total_amt"]
        ))
    conn.commit()
    conn.close()
    return inv_id


def fetch_invoices():
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("SELECT * FROM invoices ORDER BY id DESC")
    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    return rows


def delete_invoice(invoice_id: int):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("DELETE FROM items WHERE invoice_id=?", (invoice_id,))
    cur.execute("DELETE FROM invoices WHERE id=?", (invoice_id,))
    conn.commit()
    conn.close()

# src/services/invoice_service.py

"""
INVOICE SERVICE (APPLICATION LAYER)
-----------------------------------

This file wires together:

UI → Business DB → Accounting Engine

Responsibilities:
- Save invoice + items
- Post accounting voucher
- Ensure atomic consistency (business + accounting)
"""

from contextlib import contextmanager

from database import get_conn, insert_invoice
from services.accounting_engine import (
    post_invoice,
    post_debit_note,
)


# =====================================================
# TRANSACTION CONTEXT
# =====================================================
@contextmanager
def db_transaction():
    """
    Business DB transaction.
    Accounting DB is handled independently by accounting layer.
    """
    conn = get_conn()
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


# =====================================================
# INVOICE SAVE + POST
# =====================================================
def save_and_post_invoice(
    *,
    header: dict,
    items: list
) -> int:
    """
    Saves invoice to business DB and posts accounting voucher.

    If accounting fails → invoice is NOT saved.
    """

    with db_transaction():
        # ---------------------------------------------
        # 1. Save Invoice (Business DB)
        # ---------------------------------------------
        invoice_id = insert_invoice(header, items)

        # ---------------------------------------------
        # 2. Post to Accounting (authoritative)
        # ---------------------------------------------
        post_invoice(
            header=header,
            items=items
        )

    return invoice_id


# =====================================================
# DEBIT NOTE SAVE + POST
# =====================================================
def save_and_post_debit_note(
    *,
    header: dict,
    items: list
) -> int:
    """
    Saves debit note and posts accounting voucher.
    """

    with db_transaction():
        invoice_id = insert_invoice(header, items)

        post_debit_note(
            header=header,
            items=items
        )

    return invoice_id

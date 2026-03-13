# src/accounting/validation.py

"""
ACCOUNTING VALIDATION ENGINE
============================

This module ENFORCES accounting law.

If anything violates:
- DR ≠ CR
- Invalid amounts
- Empty entries
- Wrong voucher shape

→ it MUST FAIL.

No UI, no DB writes, no side-effects.
Pure validation only.
"""

from decimal import Decimal
from typing import Dict, List


# ======================================================
# INTERNAL HELPERS
# ======================================================
def _d(v) -> Decimal:
    try:
        return Decimal(str(v)).quantize(Decimal("0.01"))
    except Exception:
        return Decimal("0.00")


# ======================================================
# CORE VALIDATION
# ======================================================
def validate_voucher(voucher: Dict):
    """
    Validates a voucher BEFORE it touches the database.

    Required structure:
    {
        voucher_type: str,
        voucher_date: str,
        narration: str,
        entries: [
            {
                ledger_id: int,
                dr: Decimal,
                cr: Decimal
            }
        ]
    }
    """

    # --------------------------------------------------
    # Basic shape validation
    # --------------------------------------------------
    if not voucher:
        raise ValueError("Voucher object missing")

    for key in ("voucher_type", "voucher_date", "entries"):
        if key not in voucher:
            raise ValueError(f"Voucher missing field: {key}")

    entries: List[Dict] = voucher["entries"]

    if not entries or len(entries) < 2:
        raise ValueError("Voucher must have at least 2 ledger entries")

    # --------------------------------------------------
    # Entry-level validation
    # --------------------------------------------------
    total_dr = Decimal("0.00")
    total_cr = Decimal("0.00")

    for idx, e in enumerate(entries, start=1):

        if "ledger_id" not in e:
            raise ValueError(f"Entry {idx}: ledger_id missing")

        if not isinstance(e["ledger_id"], int):
            raise ValueError(f"Entry {idx}: invalid ledger_id")

        dr = _d(e.get("dr", 0))
        cr = _d(e.get("cr", 0))

        if dr < 0 or cr < 0:
            raise ValueError(f"Entry {idx}: negative amounts not allowed")

        if dr == 0 and cr == 0:
            raise ValueError(f"Entry {idx}: both DR and CR are zero")

        if dr > 0 and cr > 0:
            raise ValueError(f"Entry {idx}: both DR and CR present")

        total_dr += dr
        total_cr += cr

    # --------------------------------------------------
    # Fundamental accounting law
    # --------------------------------------------------
    if total_dr != total_cr:
        raise ValueError(
            f"DR/CR mismatch — DR={total_dr} CR={total_cr}"
        )

    # --------------------------------------------------
    # Voucher-type specific rules (future-safe)
    # --------------------------------------------------
    vt = voucher["voucher_type"]

    if vt in ("SALES", "DEBIT_NOTE"):
        # Party must be DR (at least one DR entry)
        if not any(_d(e.get("dr", 0)) > 0 for e in entries):
            raise ValueError("Sales voucher must have DR entry")

    # Additional voucher types can be enforced here:
    # PURCHASE, RECEIPT, PAYMENT, JOURNAL, CREDIT_NOTE

    return True

    # src/accounting/validation.py

"""
ACCOUNTING VALIDATION
=====================
Hard rules:
- Total DR must equal total CR
- No zero-value vouchers
"""

def validate_entries(entries: list[dict]):
    total_dr = sum(e.get("dr", 0) for e in entries)
    total_cr = sum(e.get("cr", 0) for e in entries)

    if round(total_dr, 2) != round(total_cr, 2):
        raise RuntimeError(
            f"Accounting mismatch: DR={total_dr:.2f} CR={total_cr:.2f}"
        )

    if total_dr <= 0:
        raise RuntimeError("Voucher has zero value")

    return True

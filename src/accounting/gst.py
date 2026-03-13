# src/accounting/gst.py
"""
GST computation engine for SANSHIP ERP

Responsibilities:
- Determine GST type (CGST/SGST vs IGST)
- Aggregate GST amounts from invoice line items
- Produce ledger-ready tax breakup
- No DB writes
- No UI coupling
"""

from decimal import Decimal, ROUND_HALF_UP


# =====================================================
# HELPERS
# =====================================================
def _d(val) -> Decimal:
    try:
        return Decimal(str(val)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    except Exception:
        raise ValueError(f"Invalid numeric value: {val}")


# =====================================================
# GST CORE
# =====================================================
def compute_gst(
    items: list,
    supplier_state_code: str,
    customer_state_code: str
) -> dict:
    """
    Computes GST breakup for given invoice items.

    Args:
        items: list of invoice item dicts
        supplier_state_code: e.g. '27'
        customer_state_code: e.g. '27'

    Returns:
        {
            taxable_total,
            cgst: {rate, amount} | None,
            sgst: {rate, amount} | None,
            igst: {rate, amount} | None
        }
    """

    if not items:
        raise ValueError("No items provided for GST computation")

    same_state = supplier_state_code == customer_state_code

    taxable_total = Decimal("0.00")
    cgst_amt = Decimal("0.00")
    sgst_amt = Decimal("0.00")
    igst_amt = Decimal("0.00")

    cgst_rate = None
    sgst_rate = None
    igst_rate = None

    for it in items:
        taxable = _d(it.get("taxable_amount", 0))
        taxable_total += taxable

        if same_state:
            cgst_amt += _d(it.get("cgst_amt", 0))
            sgst_amt += _d(it.get("sgst_amt", 0))

            # capture rate once (audit reference)
            if cgst_rate is None:
                cgst_rate = _d(it.get("cgst_rate", 0))
            if sgst_rate is None:
                sgst_rate = _d(it.get("sgst_rate", 0))
        else:
            igst_amt += _d(it.get("igst_amt", 0))
            if igst_rate is None:
                igst_rate = _d(it.get("igst_rate", 0))

    result = {
        "taxable_total": float(taxable_total),
        "cgst": None,
        "sgst": None,
        "igst": None
    }

    if same_state:
        if cgst_amt > 0:
            result["cgst"] = {
                "rate": float(cgst_rate or 0),
                "amount": float(cgst_amt)
            }
        if sgst_amt > 0:
            result["sgst"] = {
                "rate": float(sgst_rate or 0),
                "amount": float(sgst_amt)
            }
    else:
        if igst_amt > 0:
            result["igst"] = {
                "rate": float(igst_rate or 0),
                "amount": float(igst_amt)
            }

    return result

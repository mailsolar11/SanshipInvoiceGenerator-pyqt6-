# src/services/accounting_engine.py

"""
ACCOUNTING ENGINE
=================

Authoritative bridge between business documents
(INVOICE / DEBIT NOTE) and accounting vouchers.

Responsibilities:
- Decide voucher intent (SALES vs DEBIT_NOTE)
- Enforce GST correctness
- Aggregate values deterministically
- Build auditable voucher payloads
- Delegate persistence to voucher layer ONLY

⚠ This layer NEVER writes ledger entries directly.
"""

from decimal import Decimal, ROUND_HALF_UP
from typing import List, Dict, Optional

from accounting.gst import compute_gst
from accounting.vouchers import post_sales_voucher


# ==========================================================
# INTERNAL UTILS
# ==========================================================
def _d(v) -> Decimal:
    return Decimal(str(v or 0)).quantize(
        Decimal("0.01"), rounding=ROUND_HALF_UP
    )


# ==========================================================
# CORE BUILDER (PURE – NO DB)
# ==========================================================
def build_accounting_payload(
    *,
    document_type: str,
    document_number: str,
    document_date: str,
    party_name: str,
    party_gstin: Optional[str],
    narration: Optional[str],
    items: List[Dict],
    supplier_state_code: Optional[str] = None,
    customer_state_code: Optional[str] = None,
) -> Dict:
    """
    Builds and validates accounting payload WITHOUT persistence.

    This function is PURE, DETERMINISTIC and AUDITABLE.
    """

    # ------------------------------
    # Document intent
    # ------------------------------
    if document_type not in ("INVOICE", "DEBIT_NOTE"):
        raise ValueError(
            f"Unsupported document type: {document_type}"
        )

    voucher_type = (
        "SALES" if document_type == "INVOICE" else "DEBIT_NOTE"
    )

    if not items:
        raise RuntimeError(
            "Accounting aborted: no invoice items"
        )

    # ------------------------------
    # GST computation (authoritative)
    # ------------------------------
    gst_result = compute_gst(
        items=items,
        supplier_state_code=supplier_state_code,
        customer_state_code=customer_state_code,
    )

    taxable_total = _d(gst_result["taxable_total"])

    cgst_total = _d(
        gst_result["cgst"]["amount"]
        if gst_result.get("cgst")
        else 0
    )
    sgst_total = _d(
        gst_result["sgst"]["amount"]
        if gst_result.get("sgst")
        else 0
    )
    igst_total = _d(
        gst_result["igst"]["amount"]
        if gst_result.get("igst")
        else 0
    )

    if taxable_total <= 0:
        raise RuntimeError(
            "Accounting aborted: taxable amount is zero"
        )

    # ------------------------------
    # Auditable narration
    # ------------------------------
    full_narration = (
        f"{document_type} {document_number} | {narration}"
        if narration
        else f"{document_type} {document_number}"
    )

    # ------------------------------
    # Payload (NO LEDGER LOGIC HERE)
    # ------------------------------
    return {
        "voucher_type": voucher_type,
        "posting_data": {
            "voucher_no": document_number,
            "voucher_date": document_date,
            "party_name": party_name.strip(),
            "party_gstin": party_gstin,
            "narration": full_narration,
            "taxable_amount": float(taxable_total),
            "cgst_amount": float(cgst_total),
            "sgst_amount": float(sgst_total),
            "igst_amount": float(igst_total),
        },
        "summary": {
            "taxable": float(taxable_total),
            "cgst": float(cgst_total),
            "sgst": float(sgst_total),
            "igst": float(igst_total),
            "grand_total": float(
                taxable_total + cgst_total + sgst_total + igst_total
            ),
        },
    }


# ==========================================================
# POSTING ENTRY POINT (WITH DB)
# ==========================================================
def post_document_to_accounting(
    *,
    document_type: str,
    document_number: str,
    document_date: str,
    party_name: str,
    party_gstin: Optional[str],
    narration: Optional[str],
    items: List[Dict],
    supplier_state_code: Optional[str] = None,
    customer_state_code: Optional[str] = None,
) -> int:
    """
    Validates and POSTS accounting voucher.

    This is the ONLY DB-touching entry point
    from services → accounting.
    """

    payload = build_accounting_payload(
        document_type=document_type,
        document_number=document_number,
        document_date=document_date,
        party_name=party_name,
        party_gstin=party_gstin,
        narration=narration,
        items=items,
        supplier_state_code=supplier_state_code,
        customer_state_code=customer_state_code,
    )

    voucher_id = post_sales_voucher(
        voucher_type=payload["voucher_type"],
        **payload["posting_data"],
    )

    return voucher_id


# ==========================================================
# SEMANTIC WRAPPERS (PUBLIC API)
# ==========================================================
def post_invoice(*, header: dict, items: list) -> dict:
    voucher_id = post_document_to_accounting(
        document_type="INVOICE",
        document_number=header["invoice_number"],
        document_date=header["date"],
        party_name=header.get("bill_to", "").split("\n")[0],
        party_gstin=None,
        narration=header.get("narration"),
        items=items,
    )

    return {
        "voucher_id": voucher_id,
        "document_type": "INVOICE",
    }


def post_debit_note(*, header: dict, items: list) -> dict:
    voucher_id = post_document_to_accounting(
        document_type="DEBIT_NOTE",
        document_number=header["invoice_number"],
        document_date=header["date"],
        party_name=header.get("bill_to", "").split("\n")[0],
        party_gstin=None,
        narration=header.get("narration"),
        items=items,
    )

    return {
        "voucher_id": voucher_id,
        "document_type": "DEBIT_NOTE",
    }

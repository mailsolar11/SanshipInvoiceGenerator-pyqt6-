
"""
RECEIPT SERVICE
===============

Handles posting of Receipt Vouchers.
"""

from accounting.vouchers import post_voucher_with_entries

def post_receipt_voucher(
    *,
    date: str,
    amount: float,
    dr_ledger_id: int,
    cr_ledger_id: int,
    narration: str
) -> int:
    """
    Creates a 'RECEIPT' voucher and posts Debit/Credit entries.
    
    Dr: Cash/Bank (dr_ledger_id)
    Cr: Customer (cr_ledger_id)
    """
    
    # 1. Prepare entries
    # Debiting the receiving account (Cash/Bank)
    # Crediting the giving account (Customer)
    entries = [
        # Dr entry
        {
            "ledger_id": dr_ledger_id,
            "dr_amount": amount,
            "cr_amount": 0.0
        },
        # Cr entry
        {
            "ledger_id": cr_ledger_id,
            "dr_amount": 0.0,
            "cr_amount": amount
        }
    ]

    # 2. Post using accounting primitive
    voucher_id = post_voucher_with_entries(
        voucher_type="RECEIPT",
        voucher_date=date,
        narration=narration,
        entries=entries
    )

    return voucher_id

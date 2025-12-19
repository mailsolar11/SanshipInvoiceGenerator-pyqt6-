# src/debitnote_form.py
from base_invoice_form import BaseInvoiceForm


class DebitNoteForm(BaseInvoiceForm):
    UI_FILE = "debitnote_form.ui"
    DOCUMENT_TYPE = "DEBIT_NOTE"
    DOCUMENT_TITLE = "DEBIT NOTE"

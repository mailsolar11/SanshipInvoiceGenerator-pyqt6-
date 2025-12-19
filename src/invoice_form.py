# src/invoice_form.py
from base_invoice_form import BaseInvoiceForm


class InvoiceForm(BaseInvoiceForm):
    UI_FILE = "invoice_form.ui"
    DOCUMENT_TYPE = "INVOICE"
    DOCUMENT_TITLE = "TAX INVOICE"

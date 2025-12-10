import os
from datetime import datetime
from PyQt6 import QtWidgets, uic
from PyQt6.QtWidgets import QTableWidgetItem, QMessageBox
from database import insert_invoice, init_db
from pdf_generator import generate_invoice_pdf

BASE_DIR = os.path.dirname(os.path.dirname(__file__))


class InvoiceForm(QtWidgets.QWidget):
    def __init__(self):
        super().__init__()
        uic.loadUi(os.path.join(BASE_DIR, "ui", "invoice_form.ui"), self)
        init_db()

        self.table = self.findChild(QtWidgets.QTableWidget, "tableItems")
        self.leInvoiceNo = self.findChild(QtWidgets.QLineEdit, "leInvoiceNo")
        self.leDate = self.findChild(QtWidgets.QLineEdit, "leDate")
        self.teBillTo = self.findChild(QtWidgets.QTextEdit, "teBillTo")
        self.teConsignee = self.findChild(QtWidgets.QTextEdit, "teConsignee")

        self.btnAddRow = self.findChild(QtWidgets.QPushButton, "btnAddRow")
        self.btnDelRow = self.findChild(QtWidgets.QPushButton, "btnDelRow")
        self.btnSave = self.findChild(QtWidgets.QPushButton, "btnSave")
        self.btnExportPDF = self.findChild(QtWidgets.QPushButton, "btnExportPDF")

        self.btnAddRow.clicked.connect(self.add_row)
        self.btnDelRow.clicked.connect(self.del_row)
        self.btnSave.clicked.connect(self.save_invoice)
        self.btnExportPDF.clicked.connect(self.export_pdf)

        self.leDate.setText(datetime.now().strftime("%Y-%m-%d"))

    def add_row(self):
        r = self.table.rowCount()
        self.table.insertRow(r)
        self.table.setItem(r, 0, QTableWidgetItem(str(r + 1)))

    def del_row(self):
        r = self.table.currentRow()
        if r >= 0:
            self.table.removeRow(r)

    def collect_items(self):
        items = []
        for r in range(self.table.rowCount()):
            def text(col):
                item = self.table.item(r, col)
                return item.text() if item else ""

            try:
                rate = float(text(4) or 0)
            except ValueError:
                rate = 0.0
            try:
                qty = float(text(5) or 0)
            except ValueError:
                qty = 0.0

            amount = rate * qty
            try:
                taxable = float(text(7) or amount)
            except ValueError:
                taxable = amount
            try:
                cgst = float(text(8) or 0)
            except ValueError:
                cgst = 0.0
            try:
                sgst = float(text(10) or 0)
            except ValueError:
                sgst = 0.0

            cgst_amt = taxable * cgst / 100.0
            sgst_amt = taxable * sgst / 100.0
            total = taxable + cgst_amt + sgst_amt

            it = {
                "sr_no": r + 1,
                "description": text(1),
                "hsn_sac": text(2),
                "cur": text(3) or "INR",
                "rate": rate,
                "qty": qty,
                "amount": amount,
                "taxable_amount": taxable,
                "cgst_rate": cgst,
                "cgst_amt": cgst_amt,
                "sgst_rate": sgst,
                "sgst_amt": sgst_amt,
                "total_amt": total,
            }

            # write computed cells back
            self.table.setItem(r, 6, QTableWidgetItem(f"{amount:.2f}"))
            self.table.setItem(r, 9, QTableWidgetItem(f"{cgst_amt:.2f}"))
            self.table.setItem(r, 11, QTableWidgetItem(f"{sgst_amt:.2f}"))
            self.table.setItem(r, 12, QTableWidgetItem(f"{total:.2f}"))
            items.append(it)

        return items

    def save_invoice(self):
        items = self.collect_items()
        total_amount = sum(i["total_amt"] for i in items)
        inv_no = self.leInvoiceNo.text() or f"INV-{int(datetime.now().timestamp())}"

        header = {
            "invoice_number": inv_no,
            "date": self.leDate.text(),
            "type": "TAX INVOICE",
            "bill_to": self.teBillTo.toPlainText(),
            "consignee": self.teConsignee.toPlainText(),
            "total_amount": total_amount,
        }

        insert_invoice(header, items)
        QMessageBox.information(self, "Saved", "Invoice saved.")

    def export_pdf(self):
        items = self.collect_items()
        inv_no = self.leInvoiceNo.text() or f"INV-{int(datetime.now().timestamp())}"
        header = {
            "invoice_number": inv_no,
            "date": self.leDate.text(),
            "bill_to": self.teBillTo.toPlainText(),
            "consignee": self.teConsignee.toPlainText(),
        }
        path = generate_invoice_pdf(header, items)
        QMessageBox.information(self, "PDF", f"PDF saved at:\n{path}")

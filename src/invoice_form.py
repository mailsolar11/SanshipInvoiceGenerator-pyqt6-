import os
import json
from datetime import datetime
from PyQt6 import QtWidgets, uic
from PyQt6.QtWidgets import QTableWidgetItem, QMessageBox
from database import insert_invoice, init_db
from pdf_generator import generate_invoice_pdf

BASE_DIR = os.path.dirname(os.path.dirname(__file__))


class InvoiceForm(QtWidgets.QWidget):
    """
    Invoice form widget: reads all the fields from the UI and
    saves to DB or exports to PDF using pdf_generator.
    """

    def __init__(self):
        super().__init__()
        uic.loadUi(os.path.join(BASE_DIR, "ui", "invoice_form.ui"), self)
        init_db()

        # Basic fields
        self.leInvoiceNo = self.findChild(QtWidgets.QLineEdit, "leInvoiceNo")
        self.leDate = self.findChild(QtWidgets.QLineEdit, "leDate")
        self.teBillTo = self.findChild(QtWidgets.QTextEdit, "teBillTo")
        self.teConsignee = self.findChild(QtWidgets.QTextEdit, "teConsignee")

        # Shipping fields (left)
        self.leShipper = self.findChild(QtWidgets.QLineEdit, "leShipper")
        self.leShipConsignee = self.findChild(QtWidgets.QLineEdit, "leShipConsignee")
        self.lePOL = self.findChild(QtWidgets.QLineEdit, "lePOL")
        self.lePOD = self.findChild(QtWidgets.QLineEdit, "lePOD")
        self.leVessel = self.findChild(QtWidgets.QLineEdit, "leVessel")
        self.leETD = self.findChild(QtWidgets.QLineEdit, "leETD")
        self.leETA = self.findChild(QtWidgets.QLineEdit, "leETA")

        # Consignment fields (right)
        self.leCDate = self.findChild(QtWidgets.QLineEdit, "leCDate")
        self.leCInvNo = self.findChild(QtWidgets.QLineEdit, "leCInvNo")
        self.leMBL = self.findChild(QtWidgets.QLineEdit, "leMBL")
        self.leJob = self.findChild(QtWidgets.QLineEdit, "leJob")
        self.leGross = self.findChild(QtWidgets.QLineEdit, "leGross")
        self.leNet = self.findChild(QtWidgets.QLineEdit, "leNet")
        self.leVolume = self.findChild(QtWidgets.QLineEdit, "leVolume")
        self.lePackages = self.findChild(QtWidgets.QLineEdit, "lePackages")
        self.leBE = self.findChild(QtWidgets.QLineEdit, "leBE")
        self.leBEDate = self.findChild(QtWidgets.QLineEdit, "leBEDate")
        self.leIGM = self.findChild(QtWidgets.QLineEdit, "leIGM")
        self.leIGMDate = self.findChild(QtWidgets.QLineEdit, "leIGMDate")
        self.leItem = self.findChild(QtWidgets.QLineEdit, "leItem")
        self.leExRate = self.findChild(QtWidgets.QLineEdit, "leExRate")
        self.leRef = self.findChild(QtWidgets.QLineEdit, "leRef")

        # Table and buttons
        self.table = self.findChild(QtWidgets.QTableWidget, "tableItems")
        self.btnAddRow = self.findChild(QtWidgets.QPushButton, "btnAddRow")
        self.btnDelRow = self.findChild(QtWidgets.QPushButton, "btnDelRow")
        self.btnSave = self.findChild(QtWidgets.QPushButton, "btnSave")
        self.btnExportPDF = self.findChild(QtWidgets.QPushButton, "btnExportPDF")

        # Connect signals
        self.btnAddRow.clicked.connect(self.add_row)
        self.btnDelRow.clicked.connect(self.del_row)
        self.btnSave.clicked.connect(self.save_invoice)
        self.btnExportPDF.clicked.connect(self.export_pdf)

        # default date
        if self.leDate:
            self.leDate.setText(datetime.now().strftime("%Y-%m-%d"))
        if self.leCDate and not self.leCDate.text():
            self.leCDate.setText(datetime.now().strftime("%Y-%m-%d"))

        # Make sure table has expected columns (13)
        self._ensure_table_columns()

        # Connect editing finished events for rate/qty to recalc row
        # We'll use a safe approach: recalc all rows when Add/Delete/Export/Save
        # But also connect cellChanged to update computed cells (careful to avoid recursion)
        self._cell_change_enabled = True
        self.table.cellChanged.connect(self._on_cell_changed)

    def _ensure_table_columns(self):
        # column labels already set in UI, ensure column count is 13
        try:
            self.table.setColumnCount(13)
        except Exception:
            pass

    def add_row(self):
        r = self.table.rowCount()
        self.table.insertRow(r)
        # Sr no
        self.table.setItem(r, 0, QTableWidgetItem(str(r + 1)))
        # create empty cells for other columns to allow editing
        for c in range(1, 13):
            if not self.table.item(r, c):
                self.table.setItem(r, c, QTableWidgetItem(""))

    def del_row(self):
        r = self.table.currentRow()
        if r >= 0:
            self.table.removeRow(r)
            # update sr no for remaining rows
            for i in range(self.table.rowCount()):
                self.table.setItem(i, 0, QTableWidgetItem(str(i + 1)))

    def _on_cell_changed(self, row, column):
        # avoid recursion
        if not self._cell_change_enabled:
            return

        # if rate or qty or taxable/cgst/sgst changed, recompute that row
        try:
            self._cell_change_enabled = False
            # columns: 4 -> Rate, 5 -> Qty, 7 -> Taxable, 8 -> CGST%, 10 -> SGST%
            if column in (4, 5, 7, 8, 10):
                self._recompute_row(row)
        finally:
            self._cell_change_enabled = True

    def _safe_text(self, r, c):
        it = self.table.item(r, c)
        return it.text().strip() if it and it.text() is not None else ""

    def _to_float(self, val, default=0.0):
        try:
            if val is None or val == "":
                return float(default)
            return float(str(val).replace(',', ''))
        except Exception:
            return float(default)

    def _recompute_row(self, r):
        # read rate, qty, taxable, cgst, sgst
        rate = self._to_float(self._safe_text(r, 4))
        qty = self._to_float(self._safe_text(r, 5))
        amount = rate * qty
        taxable = self._to_float(self._safe_text(r, 7), amount)
        cgst_perc = self._to_float(self._safe_text(r, 8))
        sgst_perc = self._to_float(self._safe_text(r, 10))
        cgst_amt = taxable * cgst_perc / 100.0
        sgst_amt = taxable * sgst_perc / 100.0
        total = taxable + cgst_amt + sgst_amt

        # write computed back safely (disable cellChanged temporarily)
        self._cell_change_enabled = False
        try:
            self.table.setItem(r, 6, QTableWidgetItem(f"{amount:.2f}"))
            self.table.setItem(r, 9, QTableWidgetItem(f"{cgst_amt:.2f}"))
            self.table.setItem(r, 11, QTableWidgetItem(f"{sgst_amt:.2f}"))
            self.table.setItem(r, 12, QTableWidgetItem(f"{total:.2f}"))
        finally:
            self._cell_change_enabled = True

    def collect_items(self):
        items = []
        for r in range(self.table.rowCount()):
            def txt(col):
                return self._safe_text(r, col)

            rate = self._to_float(txt(4))
            qty = self._to_float(txt(5))
            amount = rate * qty
            taxable = self._to_float(txt(7), amount)
            cgst_rate = self._to_float(txt(8))
            sgst_rate = self._to_float(txt(10))
            cgst_amt = taxable * cgst_rate / 100.0
            sgst_amt = taxable * sgst_rate / 100.0
            total_amt = taxable + cgst_amt + sgst_amt

            item = {
                "sr_no": int(txt(0)) if txt(0).isdigit() else r + 1,
                "description": txt(1),
                "hsn_sac": txt(2),
                "cur": txt(3) or "INR",
                "rate": rate,
                "qty": qty,
                "amount": amount,
                "taxable_amount": taxable,
                "cgst_rate": cgst_rate,
                "cgst_amt": cgst_amt,
                "sgst_rate": sgst_rate,
                "sgst_amt": sgst_amt,
                "total_amt": total_amt
            }

            # update computed columns so user sees them
            # use QTableWidgetItem with formatted numbers
            self._cell_change_enabled = False
            try:
                self.table.setItem(r, 6, QTableWidgetItem(f"{amount:.2f}"))
                self.table.setItem(r, 9, QTableWidgetItem(f"{cgst_amt:.2f}"))
                self.table.setItem(r, 11, QTableWidgetItem(f"{sgst_amt:.2f}"))
                self.table.setItem(r, 12, QTableWidgetItem(f"{total_amt:.2f}"))
            finally:
                self._cell_change_enabled = True

            items.append(item)
        return items

    def _build_header_dict(self, total_amount):
        # Collect all header info including shipping and consignment fields
        header = {
            "invoice_number": self.leInvoiceNo.text().strip() or f"INV-{int(datetime.now().timestamp())}",
            "date": self.leDate.text().strip(),
            "type": "TAX INVOICE",
            "bill_to": self.teBillTo.toPlainText().strip(),
            "consignee_preview": self.teConsignee.toPlainText().strip(),
            # shipping details
            "shipper": self.leShipper.text().strip(),
            "ship_consigne": self.leShipConsignee.text().strip(),
            "pol": self.lePOL.text().strip(),
            "pod": self.lePOD.text().strip(),
            "vessel_flight": self.leVessel.text().strip(),
            "etd": self.leETD.text().strip(),
            "eta": self.leETA.text().strip(),
            # consignment details
            "c_date": self.leCDate.text().strip(),
            "c_invoice_no": self.leCInvNo.text().strip(),
            "mbl_no": self.leMBL.text().strip(),
            "job_no": self.leJob.text().strip(),
            "gross_weight": self.leGross.text().strip(),
            "net_weight": self.leNet.text().strip(),
            "volume_cbm": self.leVolume.text().strip(),
            "packages": self.lePackages.text().strip(),
            "be_no": self.leBE.text().strip(),
            "be_date": self.leBEDate.text().strip(),
            "igm_no": self.leIGM.text().strip(),
            "igm_date": self.leIGMDate.text().strip(),
            "item_no": self.leItem.text().strip(),
            "exchange_rate": self.leExRate.text().strip(),
            "ref_no": self.leRef.text().strip(),
            "total_amount": total_amount
        }
        return header

    def save_invoice(self):
        items = self.collect_items()
        if not items:
            QMessageBox.warning(self, "No items", "Please add at least one item before saving.")
            return

        total_amount = sum(i["total_amt"] for i in items)
        header = self._build_header_dict(total_amount)

        # insert into DB (insert_invoice will store raw_json)
        inv_id = insert_invoice(header, items)

        QMessageBox.information(self, "Saved", f"Invoice saved successfully (ID: {inv_id})")

    def export_pdf(self):
        items = self.collect_items()
        if not items:
            QMessageBox.warning(self, "No items", "Please add at least one item before exporting.")
            return

        total_amount = sum(i["total_amt"] for i in items)
        header = self._build_header_dict(total_amount)

        # generate PDF
        try:
            out_path = generate_invoice_pdf(header, items)
            QMessageBox.information(self, "PDF Saved", f"PDF generated:\n{out_path}")
        except Exception as e:
            QMessageBox.critical(self, "PDF Error", f"Failed to create PDF:\n{str(e)}")

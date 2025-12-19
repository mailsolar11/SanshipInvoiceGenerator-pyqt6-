# src/base_invoice_form.py
import os
from datetime import datetime
from PyQt6 import QtWidgets, uic, QtCore
from PyQt6.QtWidgets import QTableWidgetItem, QMessageBox

from database import (
    insert_invoice,
    list_customers,
    get_addresses_for_customer,
    list_open_jobs_for_dropdown,
    get_job
)

from settings_manager import get_next_invoice_number
from pdf_generator import generate_invoice_pdf

BASE_DIR = os.path.dirname(os.path.dirname(__file__))


class BaseInvoiceForm(QtWidgets.QWidget):
    openCustomerManager = QtCore.pyqtSignal()

    DOCUMENT_TYPE = "INVOICE"
    DOCUMENT_TITLE = "TAX INVOICE"
    UI_FILE = None   # must be set by child

    def __init__(self):
        super().__init__()

        if not self.UI_FILE:
            raise RuntimeError("UI_FILE not defined in subclass")

        uic.loadUi(os.path.join(BASE_DIR, "ui", self.UI_FILE), self)

        # -------------------------------
        # Header
        # -------------------------------
        self.leInvoiceNo = self.findChild(QtWidgets.QLineEdit, "leInvoiceNo")
        self.leDate = self.findChild(QtWidgets.QLineEdit, "leDate")

        self.cbCustomer = self.findChild(QtWidgets.QComboBox, "cbCustomer")
        self.cbAddress = self.findChild(QtWidgets.QComboBox, "cbAddress")
        self.cbJob = self.findChild(QtWidgets.QComboBox, "cbJob")

        self.btnAddCustomer = self.findChild(QtWidgets.QPushButton, "btnAddCustomer")

        self.teBillTo = self.findChild(QtWidgets.QTextEdit, "teBillTo")
        self.teConsignee = self.findChild(QtWidgets.QTextEdit, "teConsignee")

        # Shipment fields
        self.ship_fields = {
            "shipper": self.findChild(QtWidgets.QLineEdit, "leShipper"),
            "consignee": self.findChild(QtWidgets.QLineEdit, "leShipConsignee"),
            "pol": self.findChild(QtWidgets.QLineEdit, "lePOL"),
            "pod": self.findChild(QtWidgets.QLineEdit, "lePOD"),
            "vessel_flight": self.findChild(QtWidgets.QLineEdit, "leVessel"),
            "etd": self.findChild(QtWidgets.QLineEdit, "leETD"),
            "eta": self.findChild(QtWidgets.QLineEdit, "leETA"),
        }

        # Consignment fields
        self.cons_fields = {
            "job_no": self.findChild(QtWidgets.QLineEdit, "leJob"),
            "mbl_no": self.findChild(QtWidgets.QLineEdit, "leMBL"),
            "gross_weight": self.findChild(QtWidgets.QLineEdit, "leGross"),
            "net_weight": self.findChild(QtWidgets.QLineEdit, "leNet"),
            "volume_cbm": self.findChild(QtWidgets.QLineEdit, "leVolume"),
            "packages": self.findChild(QtWidgets.QLineEdit, "lePackages"),
            "exchange_rate": self.findChild(QtWidgets.QLineEdit, "leExRate"),
            "ref_no": self.findChild(QtWidgets.QLineEdit, "leRef"),
        }

        # Items
        self.table = self.findChild(QtWidgets.QTableWidget, "tableItems")
        self.btnAddRow = self.findChild(QtWidgets.QPushButton, "btnAddRow")
        self.btnDelRow = self.findChild(QtWidgets.QPushButton, "btnDelRow")
        self.btnSave = self.findChild(QtWidgets.QPushButton, "btnSave")
        self.btnPDF = self.findChild(QtWidgets.QPushButton, "btnExportPDF")

        # -------------------------------
        # Init
        # -------------------------------
        self.init_document()
        self.load_customers()
        self.load_jobs()

        # -------------------------------
        # Signals
        # -------------------------------
        self.btnAddCustomer.clicked.connect(
            lambda: self.openCustomerManager.emit()
        )

        self.cbCustomer.currentIndexChanged.connect(self.load_addresses)
        self.cbAddress.currentIndexChanged.connect(self.apply_address)

        if self.cbJob:
            self.cbJob.currentIndexChanged.connect(self.apply_job)

        self.btnAddRow.clicked.connect(self.add_row)
        self.btnDelRow.clicked.connect(self.delete_row)
        self.table.itemChanged.connect(self.recalculate_row)

        self.btnSave.clicked.connect(self.save_document)
        self.btnPDF.clicked.connect(self.export_pdf)

    # ==================================================
    def init_document(self):
        self.leInvoiceNo.setText(get_next_invoice_number())
        self.leInvoiceNo.setReadOnly(True)
        self.leDate.setText(datetime.now().strftime("%Y-%m-%d"))

    # ==================================================
    def load_customers(self):
        self.cbCustomer.clear()
        self.cbCustomer.addItem("-- Select --", None)
        for c in list_customers():
            self.cbCustomer.addItem(c["name"], c["id"])

    def load_addresses(self):
        self.cbAddress.clear()
        self.cbAddress.addItem("-- Select --", None)
        cid = self.cbCustomer.currentData()
        if not cid:
            return
        for a in get_addresses_for_customer(cid):
            label = f'{a["label"]}{" (Default)" if a["is_default"] else ""}'
            self.cbAddress.addItem(label, a)

    # ==================================================
    def load_jobs(self):
        self.cbJob.clear()
        self.cbJob.addItem("— Select OPEN Job —", None)
        for j in list_open_jobs_for_dropdown():
            self.cbJob.addItem(j["job_no"], j["id"])

    # ==================================================
    def lock_job_fields(self, locked: bool):
        for w in self.ship_fields.values():
            w.setReadOnly(locked)
        for w in self.cons_fields.values():
            w.setReadOnly(locked)
        self.teConsignee.setReadOnly(locked)
        self.cbCustomer.setEnabled(not locked)
        self.cbAddress.setEnabled(not locked)

    # ==================================================
    def clear_job_fields(self):
        for w in self.ship_fields.values():
            w.clear()
        for w in self.cons_fields.values():
            w.clear()
        self.teConsignee.clear()

    # ==================================================
    def apply_job(self):
        job_id = self.cbJob.currentData()

        if not job_id:
            self.lock_job_fields(False)
            self.clear_job_fields()
            return

        job = get_job(job_id)
        if not job:
            self.lock_job_fields(False)
            return

        # Customer
        if job.get("customer_id"):
            idx = self.cbCustomer.findData(job["customer_id"])
            if idx >= 0:
                self.cbCustomer.setCurrentIndex(idx)

        # Shipment
        for k, w in self.ship_fields.items():
            w.setText(str(job.get(k, "") or ""))

        # Consignment
        self.cons_fields["job_no"].setText(job.get("job_no", ""))
        self.cons_fields["mbl_no"].setText(job.get("mbl_no", ""))
        self.cons_fields["gross_weight"].setText(job.get("gross_weight", ""))

        if job.get("consignee"):
            self.teConsignee.setPlainText(job["consignee"])

        self.lock_job_fields(True)

    # ==================================================
    def apply_address(self):
        addr = self.cbAddress.currentData()
        if not addr:
            return
        text = (
            f"{addr['label']}\n"
            f"{addr['address']}\n"
            f"{addr['state']} - {addr['pincode']}\n"
            f"{addr['country']}"
        )
        self.teBillTo.setPlainText(text)
        self.teConsignee.setPlainText(text)

    # ==================================================
    # TABLE LOGIC (UNCHANGED)
    # ==================================================
    def add_row(self):
        r = self.table.rowCount()
        self.table.insertRow(r)
        self.table.setItem(r, 0, QTableWidgetItem(str(r + 1)))
        for c in range(1, 13):
            self.table.setItem(r, c, QTableWidgetItem(""))

    def delete_row(self):
        r = self.table.currentRow()
        if r >= 0:
            self.table.removeRow(r)

    def recalculate_row(self, item):
        r = item.row()
        try:
            rate = float(self.table.item(r, 4).text() or 0)
            qty = float(self.table.item(r, 5).text() or 0)
            cgst = float(self.table.item(r, 8).text() or 0)
            sgst = float(self.table.item(r, 10).text() or 0)
        except Exception:
            return

        amt = rate * qty
        cgst_amt = amt * cgst / 100
        sgst_amt = amt * sgst / 100
        total = amt + cgst_amt + sgst_amt

        self._set(r, 6, amt)
        self._set(r, 7, amt)
        self._set(r, 9, cgst_amt)
        self._set(r, 11, sgst_amt)
        self._set(r, 12, total)

    def _set(self, r, c, v):
        self.table.blockSignals(True)
        self.table.setItem(r, c, QTableWidgetItem(f"{v:.2f}"))
        self.table.blockSignals(False)

    # ==================================================
    def collect_items(self):
        items = []
        for r in range(self.table.rowCount()):
            if not self.table.item(r, 1) or not self.table.item(r, 1).text().strip():
                continue
            items.append({
                "sr_no": r + 1,
                "description": self.table.item(r, 1).text(),
                "hsn_sac": self.table.item(r, 2).text(),
                "cur": self.table.item(r, 3).text(),
                "rate": float(self.table.item(r, 4).text() or 0),
                "qty": float(self.table.item(r, 5).text() or 0),
                "amount": float(self.table.item(r, 6).text() or 0),
                "taxable_amount": float(self.table.item(r, 7).text() or 0),
                "cgst_rate": float(self.table.item(r, 8).text() or 0),
                "cgst_amt": float(self.table.item(r, 9).text() or 0),
                "sgst_rate": float(self.table.item(r, 10).text() or 0),
                "sgst_amt": float(self.table.item(r, 11).text() or 0),
                "total_amt": float(self.table.item(r, 12).text() or 0),
            })
        return items

    # ==================================================
    def save_document(self):
        job_id = self.cbJob.currentData()
        job = get_job(job_id) if job_id else None

        items = self.collect_items()
        if not items:
            QMessageBox.warning(self, "No Items", "Please add at least one item.")
            return

        header = {
            "invoice_number": self.leInvoiceNo.text(),
            "date": self.leDate.text(),
            "type": self.DOCUMENT_TYPE,

            "job_id": job_id,
            "job_no": job.get("job_no") if job else None,

            "bill_to": self.teBillTo.toPlainText(),
            "consignee_preview": self.teConsignee.toPlainText(),

            **{k: v.text() for k, v in self.ship_fields.items()},
            **{k: v.text() for k, v in self.cons_fields.items()},

            "total_amount": sum(i["total_amt"] for i in items),
        }

        insert_invoice(header, items)
        QMessageBox.information(self, "Saved", f"{self.DOCUMENT_TITLE} saved successfully")

    # ==================================================
    def export_pdf(self):
        path = generate_invoice_pdf(
            header={
                "invoice_number": self.leInvoiceNo.text(),
                "date": self.leDate.text(),
                "bill_to": self.teBillTo.toPlainText(),
                "consignee_preview": self.teConsignee.toPlainText(),
            },
            items=self.collect_items(),
            title=self.DOCUMENT_TITLE
        )
        QMessageBox.information(self, "PDF", f"PDF generated:\n{path}")

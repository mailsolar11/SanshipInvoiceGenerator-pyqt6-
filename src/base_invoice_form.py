# src/base_invoice_form.py
from email import header
import os
from datetime import datetime
from PyQt6 import QtWidgets, uic, QtCore
from PyQt6.QtWidgets import QTableWidgetItem, QMessageBox
from pyparsing import col
from sqlalchemy import desc
from tomlkit import value

from database import (
    insert_invoice,
    list_customers,
    get_addresses_for_customer,
    list_open_jobs_for_dropdown,
    get_job, list_charges
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
            "etd": self.findChild(QtWidgets.QDateEdit, "leETD"),
            "eta": self.findChild(QtWidgets.QDateEdit, "leETA"),
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
    def load_charge_dropdown(self, row):
        combo = QtWidgets.QComboBox()
        combo.setEditable(True)
        combo.setInsertPolicy(QtWidgets.QComboBox.InsertPolicy.NoInsert)
        combo.addItem("-- Type or Select Charge --", None)


        for c in list_charges():
            combo.addItem(
                c["charge_name"],
                c
            )
        combo.currentIndexChanged.connect(
            lambda _, r=row, cb=combo: self.apply_charge_to_row(r, cb)
        )

        self.table.setCellWidget(row, 1, combo)


    # ==================================================
    def apply_charge_to_row(self, row, combo):
        combo = self.table.cellWidget(row, 1)
        charge = combo.currentData()

        if not charge:
            # Manual entry – do NOT auto-fill anything
            return

        def set_col(col, value):
            item = self.table.item(row, col)
            if not item:
                item = QTableWidgetItem()
                self.table.setItem(row, col, item)
            item.setText(str(value))
        set_col(2, charge.get("hsn_sac", ""))        # HSN/SAC
        set_col(3, charge.get("currency", "INR"))   # CUR
        set_col(8, charge.get("cgst_rate", 0))      # CGST %
        set_col(10, charge.get("sgst_rate", 0))     # SGST %

    # DO NOT manually call recalculate_row
    # itemChanged signal will auto-trigger calculations

    # ==================================================
    def lock_job_fields(self, locked: bool):
        job_locked_fields = {"shipper", "consignee", "pol", "pod"}

    # Shipment fields
        for key, widget in self.ship_fields.items():
            if widget is None:
                continue

            if key in job_locked_fields:
            # Job-controlled fields
                if isinstance(widget, QtWidgets.QLineEdit):
                    widget.setReadOnly(locked)
                else:
                    widget.setEnabled(not locked)
            else:
            # User-editable fields (vessel, ETD, ETA)
                if isinstance(widget, QtWidgets.QLineEdit):
                    widget.setReadOnly(False)
                else:
                    widget.setEnabled(True)

    # Consignment fields (mostly job driven)
        for widget in self.cons_fields.values():
            if widget is None:
                continue
            widget.setReadOnly(locked)

    # Consignee preview text
        if self.teConsignee:
            self.teConsignee.setReadOnly(locked)

    # Customer & Address should not change once job is selected
        if self.cbCustomer:
            self.cbCustomer.setEnabled(not locked)
        if self.cbAddress:
            self.cbAddress.setEnabled(not locked)

    # ==================================================
    def clear_job_fields(self):
        for w in self.ship_fields.values():
            if isinstance(w, QtWidgets.QDateEdit):
                w.setDate(QtCore.QDate.currentDate())
            elif w is not None:
                w.clear()

        for w in self.cons_fields.values():
            if w is not None:
                w.clear()
        if self.teConsignee:
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

        # -------------------------------
        # Customer
        # -------------------------------
        if job.get("customer_id"):
            idx = self.cbCustomer.findData(job["customer_id"])
            if idx >= 0:
                self.cbCustomer.setCurrentIndex(idx)

        # -------------------------------
        # Shipment (TEXT + DATE SAFE)
        # -------------------------------
        for key, widget in self.ship_fields.items():
            if widget is None:
                continue
            val = job.get(key)

            # Date fields
            if isinstance(widget, QtWidgets.QDateEdit):
                if val:
                    d = QtCore.QDate.fromString(val, "yyyy-MM-dd")
                    if d.isValid():
                        widget.setDate(d)
                continue

            # Text fields
            widget.setText(str(val or ""))

        # -------------------------------
        # Consignment
        # -------------------------------
        for key, widget in self.cons_fields.items():
            if widget is None:
                continue
            widget.setText(str(job.get(key) or ""))

        # -------------------------------
        # Consignee preview
        # -------------------------------
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

        for c in range(2, 13):
            self.table.setItem(r, c, QTableWidgetItem(""))

        self.load_charge_dropdown(r)


    def delete_row(self):
        r = self.table.currentRow()
        if r >= 0:
            self.table.removeRow(r)

    def recalculate_row(self, item):
        r = item.row()

        def val(col):
            try:
                return float(self.table.item(r, col).text() or 0)
            except Exception:
                return 0.0

        rate = val(4)
        qty = val(5)
        cgst_rate = val(8)
        sgst_rate = val(10)

        taxable = rate * qty
        cgst_amt = taxable * cgst_rate / 100
        sgst_amt = taxable * sgst_rate / 100
        total = taxable + cgst_amt + sgst_amt

        self._set(r, 6, taxable)       # Amount
        self._set(r, 7, taxable)       # Taxable Amount
        self._set(r, 9, cgst_amt)      # CGST Amount
        self._set(r, 11, sgst_amt)     # SGST Amount
        self._set(r, 12, total)        # Total


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
    def validate_items_before_save(self, items):
        issues = []

        for i, it in enumerate(items, start=1):
            if it["rate"] <= 0 or it["qty"] <= 0:
                issues.append(
                    f"Row {i}: Rate or Quantity is zero"
                )
            if it["cgst_rate"] <= 0 and it["sgst_rate"] <= 0:
                issues.append(
                    f"Row {i}: CGST and SGST rates are empty"
                )

        return issues

    # ==================================================    
    def validate_items(self, items):
        issues = []

        for i, it in enumerate(items, start=1):
            if it["rate"] == 0 or it["qty"] == 0:
                issues.append(f"Row {i}: Rate or Quantity is zero")
            if it["cgst_rate"] == 0 and it["sgst_rate"] == 0:
                issues.append(f"Row {i}: GST rates are empty")

        return issues
    
    # ==================================================
    def save_document(self):
        job_id = self.cbJob.currentData()
        job = get_job(job_id) if job_id else None

        items = self.collect_items()
        if not items:
            QMessageBox.warning(self, "No Items", "Please add at least one item.")
            return

    # -------------------------------
    # Soft validation (warnings)
    # -------------------------------
        issues = self.validate_items_before_save(items)
        if issues:
            msg = "The following issues were found:\n\n"
            msg += "\n".join(f"• {i}" for i in issues)
            msg += "\n\nDo you want to continue saving?"

            reply = QMessageBox.warning(
                self,
                "Validation Warning",
                msg,
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
            )

            if reply == QMessageBox.StandardButton.No:
                return

    # -------------------------------
    # Hard validation (must fix)
    # -------------------------------
        issues = self.validate_items(items)
        if issues:
            QMessageBox.warning(
                self,
                "Validation Error",
                "Please fix the following issues before saving:\n\n" + "\n".join(issues)
            )
            return
    # -------------------------------
    # Per-row safety checks
    # -------------------------------
        for r in range(self.table.rowCount()):
            desc = self.table.item(r, 1)
            if not desc or not desc.text().strip():
                continue

            rate = float(self.table.item(r, 4).text() or 0)
            qty = float(self.table.item(r, 5).text() or 0)
            cgst = float(self.table.item(r, 8).text() or 0)
            sgst = float(self.table.item(r, 10).text() or 0)

            if rate == 0 or qty == 0:
                QMessageBox.warning(
                    self,
                    "Invalid Item",
                    f"Row {r+1}: Rate or Quantity cannot be zero"
                )
                return
            if cgst == 0 and sgst == 0:
                QMessageBox.warning(
                    self,
                    "GST Missing",
                    f"Row {r+1}: CGST and SGST cannot both be zero"
                )
                return
    # -------------------------------
    # Save invoice
    # -------------------------------
        header = {
            "invoice_number": self.leInvoiceNo.text(),
            "date": self.leDate.text(),
            "type": self.DOCUMENT_TYPE,

            "job_id": job_id,
            "job_no": job.get("job_no") if job else None,
            "bill_to": self.teBillTo.toPlainText(),
            "consignee_preview": self.teConsignee.toPlainText(),

            **{
                k: (
                v.date().toString("yyyy-MM-dd")
                if isinstance(v, QtWidgets.QDateEdit)
                else v.text()
            )
            for k, v in self.ship_fields.items()
        },

            **{k: v.text() for k, v in self.cons_fields.items()},

            "total_amount": sum(i["total_amt"] for i in items),
        }

        insert_invoice(header, items)
        QMessageBox.information(
            self,
            "Saved",
            f"{self.DOCUMENT_TITLE} saved successfully"
        )


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

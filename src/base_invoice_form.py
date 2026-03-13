# src/base_invoice_form.py
from email import header
import os
from datetime import datetime
from PyQt6 import QtWidgets, uic, QtCore, QtGui
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
        # PROGRAMMATIC LAYOUT REFACTOR (Vertical Scroll)
        # -------------------------------
        # 1. Widgets to move
        self.frameTopInfo = self.findChild(QtWidgets.QFrame, "frameTopInfo")
        self.frameLeft = self.findChild(QtWidgets.QFrame, "frameLeftColumn")
        self.frameRight = self.findChild(QtWidgets.QFrame, "frameRightColumn")
        self.frameTable = self.findChild(QtWidgets.QFrame, "frameRightColumn2")

        if self.frameTopInfo and self.frameLeft and self.frameRight and self.frameTable:
            # 2. Create Scroll Area Structure
            self.scrollArea = QtWidgets.QScrollArea()
            self.scrollArea.setWidgetResizable(True)
            self.scrollArea.setFrameShape(QtWidgets.QFrame.Shape.NoFrame)
            
            self.scrollWidget = QtWidgets.QWidget()
            self.scrollLayout = QtWidgets.QVBoxLayout(self.scrollWidget)
            self.scrollLayout.setContentsMargins(20, 20, 20, 60) # Increased margins
            self.scrollLayout.setSpacing(50) # Increased spacing between sections

            # 3. Move them into Scroll Layout (Vertical Stack)
            # Remove from their original parents implicitly by adding to new layout
            self.scrollLayout.addWidget(self.frameTopInfo)
            self.scrollLayout.addWidget(self.frameLeft)
            self.scrollLayout.addWidget(self.frameRight)
            self.scrollLayout.addWidget(self.frameTable)
            
            self.scrollArea.setWidget(self.scrollWidget)

            # 4. Reset Main Window Layout to ONLY contain the ScrollArea
            # We create a new VBox for 'self' and replace the old one if possible, 
            # OR just clear the old one. simpler is to just hide old layout's container if it was a widget, 
            # but 'self' is the widget.
            
            # Use a brute force approach: Delete old layout reference? No, dangerous.
            # Safe way: Create a temporary container for the old layout? No.
            # Best way: Just set a NEW layout. QWidget.setLayout() triggers a warning if one exists.
            # But we can reparent the frames, leaving the old layout empty/useless.
            
            # Existing layout on 'self' is 'verticalLayout_root'.
            # We can just clear it.
            existing_layout = self.layout()
            if existing_layout:
                # Remove items from existing layout just to be clean (though reparenting often handles this)
                pass 
                
            # Create a completely new Overlay Layout?
            # Actually, since we repurposed all the content widgets, the old layout is effectively empty except for empty containers.
            # Let's just create a new root layout for self.
            new_root_layout = QtWidgets.QVBoxLayout()
            new_root_layout.setContentsMargins(0, 0, 0, 0)
            new_root_layout.addWidget(self.scrollArea)
            
            # Initial Layout is created by uic.loadUi into self.
            # We need to nuke it.
            if self.layout():
                QtWidgets.QWidget().setLayout(self.layout()) # Re-parent old layout to a dummy widget to garbage collect it
                
            self.setLayout(new_root_layout)

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
        if self.btnAddCustomer:
            self.btnAddCustomer.clicked.connect(
                lambda: self.openCustomerManager.emit()
            )

        if self.cbCustomer:
            self.cbCustomer.currentIndexChanged.connect(self.load_addresses)
        if self.cbAddress:
            self.cbAddress.currentIndexChanged.connect(self.apply_address)

        if self.cbJob:
            self.cbJob.currentIndexChanged.connect(self.apply_job)

        if self.btnAddRow: self.btnAddRow.clicked.connect(self.add_row)
        if self.btnDelRow: self.btnDelRow.clicked.connect(self.delete_row)
        if self.table: self.table.itemChanged.connect(self.recalculate_row)

        if self.btnSave: self.btnSave.clicked.connect(self.save_document)
        if self.btnPDF: self.btnPDF.clicked.connect(self.export_pdf)

        # Initial Layout adjustment
        if self.table:
            # We need to ensure columns are laid out to get header height, 
            # sometimes a single processEvents helps, but usually just calling it works.
            self.adjust_table_height()

    # ==================================================
    def init_document(self):
        self.leInvoiceNo.setText(get_next_invoice_number())
        self.leInvoiceNo.setReadOnly(True)
        self.leDate.setText(datetime.now().strftime("%Y-%m-%d"))

        # Try loading logo
        self.load_logo()

    def load_logo(self):
        # Look for logo.png in the project root (BASE_DIR is 'Sanship' folder)
        logo_path = os.path.join(BASE_DIR, "logo.png")
        
        if os.path.exists(logo_path):
            lbl_logo = self.findChild(QtWidgets.QLabel, "labelLogo")
            if lbl_logo:
                pixmap = QtGui.QPixmap(logo_path)
                if not pixmap.isNull():
                     # Scale nicely to fit the label height, keeping aspect ratio
                    scaled = pixmap.scaled(
                        lbl_logo.width(), 
                        lbl_logo.height(), 
                        QtCore.Qt.AspectRatioMode.KeepAspectRatio, 
                        QtCore.Qt.TransformationMode.SmoothTransformation
                    )
                    lbl_logo.setPixmap(scaled)
                    lbl_logo.setText("") # Clear text if it had "LOGO"

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
    # TABLE LOGIC
    # ==================================================
    def adjust_table_height(self):
        """
        Auto-resize table widget to fit all rows + header,
        preventing internal scrollbars.
        """
        # Calculate height: Header + (Rows * RowHeight) + minor padding
        header_height = self.table.horizontalHeader().height()
        # Default row height is usually 30, or we can get it from rowHeight(0) if rows exist
        total_row_height = 0
        row_count = self.table.rowCount()
        
        if row_count > 0:
            for i in range(row_count):
                total_row_height += self.table.rowHeight(i)
        else:
            # If no rows, maybe show a minimum empty space? Or just header.
            # Let's show at least ample space for the "First Row" visual cue if user adds one
            pass

        # Add a buffer for borders + horizontal scrollbar height (usually ~15px)
        # We proactively add 20px extra to ensure the last row is fully above the scrollbar
        total_height = header_height + total_row_height + 25 
        
        # Enforce minimum height (e.g., enough for header + 1 empty row visual)
        if total_height < 80: 
            total_height = 80
            
        self.table.setFixedHeight(total_height)


    def add_row(self):
        r = self.table.rowCount()
        self.table.insertRow(r)
        self.table.setItem(r, 0, QTableWidgetItem(str(r + 1)))

        for c in range(2, 13):
            self.table.setItem(r, c, QTableWidgetItem(""))

        self.load_charge_dropdown(r)
        self.adjust_table_height()


    def delete_row(self):
        r = self.table.currentRow()
        if r >= 0:
            self.table.removeRow(r)
            self.adjust_table_height()

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

        # --------------------------------------------------
        # Soft validation (warnings)
        # --------------------------------------------------
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

        # --------------------------------------------------
        # Hard validation (must fix)
        # --------------------------------------------------
        issues = self.validate_items(items)
        if issues:
            QMessageBox.warning(
                self,
                "Validation Error",
                "Please fix the following issues before saving:\n\n"
                + "\n".join(issues)
            )
            return
        # --------------------------------------------------
        # Header build (PURE DATA)
        # --------------------------------------------------
        header = {
            "invoice_number": self.leInvoiceNo.text(),
            "date": self.leDate.text(),
            "type": self.DOCUMENT_TYPE,

            "job_id": job_id,
            "job_no": job.get("job_no") if job else None,
            "bill_to": self.teBillTo.toPlainText(),
            "consignee_preview": self.teConsignee.toPlainText(),
            "narration": f"{self.DOCUMENT_TITLE} {self.leInvoiceNo.text()}",

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

        # --------------------------------------------------
        # SAVE + ACCOUNTING (ATOMIC)
        # --------------------------------------------------
        try:
            if self.DOCUMENT_TYPE == "INVOICE":
                from services.invoice_service import save_and_post_invoice
                invoice_id = save_and_post_invoice(
                    header=header,
                    items=items
                )
            else:
                from services.invoice_service import save_and_post_debit_note
                invoice_id = save_and_post_debit_note(
                    header=header,
                    items=items
                )

        except Exception as e:
            QMessageBox.critical(
                self,
                "Accounting Error",
                f"Failed to save document:\n\n{str(e)}"
            )
            return
        QMessageBox.information(
            self,
                "Saved Successfully",
                f"{self.DOCUMENT_TITLE} saved and posted to accounts.\n\n"
                f"Document ID: {invoice_id}"
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

# src/debit_note_form.py
import os
from datetime import datetime
from PyQt6 import QtWidgets, uic
from PyQt6.QtWidgets import QTableWidgetItem, QMessageBox
from database import init_db, insert_invoice, list_customers, get_addresses_for_customer, get_customer
from settings_manager import get_next_debit_number
from pdf_generator import generate_invoice_pdf



BASE_DIR = os.path.dirname(os.path.dirname(__file__))


class DebitNoteForm(QtWidgets.QWidget):
    def __init__(self):
        super().__init__()
        uic.loadUi(os.path.join(BASE_DIR, "ui", "debit_note_form.ui"), self)
        init_db()

        # UI mappings
        self.leDebitNo = self.findChild(QtWidgets.QLineEdit, "leDebitNo")
        self.leDate = self.findChild(QtWidgets.QLineEdit, "leDate")
        self.teBillTo = self.findChild(QtWidgets.QTextEdit, "teBillTo")
        self.teConsignee = self.findChild(QtWidgets.QTextEdit, "teConsignee")

        self.cbCustomer = self.findChild(QtWidgets.QComboBox, "cbCustomer")
        self.cbAddress = self.findChild(QtWidgets.QComboBox, "cbAddress")
        self.btnAddCustomer = self.findChild(QtWidgets.QPushButton, "btnAddCustomer")

        # shipping
        self.leShipper = self.findChild(QtWidgets.QLineEdit, "leShipper")
        self.leShipConsignee = self.findChild(QtWidgets.QLineEdit, "leShipConsignee")
        self.lePOL = self.findChild(QtWidgets.QLineEdit, "lePOL")
        self.lePOD = self.findChild(QtWidgets.QLineEdit, "lePOD")
        self.leVessel = self.findChild(QtWidgets.QLineEdit, "leVessel")
        self.leETD = self.findChild(QtWidgets.QLineEdit, "leETD")
        self.leETA = self.findChild(QtWidgets.QLineEdit, "leETA")

        # consignment
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

        # table and buttons
        self.table = self.findChild(QtWidgets.QTableWidget, "tableItems")
        self.btnAddRow = self.findChild(QtWidgets.QPushButton, "btnAddRow")
        self.btnDelRow = self.findChild(QtWidgets.QPushButton, "btnDelRow")
        self.btnSave = self.findChild(QtWidgets.QPushButton, "btnSave")
        self.btnExportPDF = self.findChild(QtWidgets.QPushButton, "btnExportPDF")

        # signals
        self.btnAddRow.clicked.connect(self.add_row)
        self.btnDelRow.clicked.connect(self.del_row)
        self.btnSave.clicked.connect(self.save_debit)
        self.btnExportPDF.clicked.connect(self.export_pdf)
        self.btnAddCustomer.clicked.connect(self.open_customer_manager)
        self.cbCustomer.currentIndexChanged.connect(self.on_customer_selected)
        self.cbAddress.currentIndexChanged.connect(self.on_address_selected)

        if self.leDate:
            self.leDate.setText(datetime.now().strftime("%Y-%m-%d"))
        if self.leCDate and not self.leCDate.text():
            self.leCDate.setText(datetime.now().strftime("%Y-%m-%d"))

        self._ensure_table_columns()
        self._cell_change_enabled = True
        self.table.cellChanged.connect(self._on_cell_changed)

        # customers
        self.refresh_customers()

        # auto generate debit note number on load
        try:
            if self.leDebitNo and not self.leDebitNo.text().strip():
                self.leDebitNo.setText(get_next_debit_number())
        except Exception:
            self.leDebitNo.setText(f"DN-{int(datetime.now().timestamp())}")

    # reuse helper methods from invoice form (customer, address, table calculations)
    def refresh_customers(self):
        self.cbCustomer.blockSignals(True)
        self.cbCustomer.clear()
        self.cbCustomer.addItem("", "")
        for c in list_customers():
            self.cbCustomer.addItem(f"{c.get('name')} [{c.get('gstin') or ''}]", c.get("id"))
        self.cbCustomer.blockSignals(False)

    def on_customer_selected(self, idx):
        cid = self.cbCustomer.currentData()
        if not cid:
            return
        cust = get_customer(cid)
        if cust:
            self.leShipper.setText(cust.get("name",""))
        self.cbAddress.blockSignals(True)
        self.cbAddress.clear()
        self.cbAddress.addItem("", "")
        addrs = get_addresses_for_customer(cid)
        for a in addrs:
            short = (a.get("address") or "")[:60].replace("\n"," ")
            self.cbAddress.addItem(f"{a.get('label')} - {short}", a.get("id"))
        self.cbAddress.blockSignals(False)
        default = next((a for a in addrs if a.get("is_default")), None)
        if default:
            self.populate_billto_from_address(default)

    def on_address_selected(self, idx):
        aid = self.cbAddress.currentData()
        if not aid:
            return
        addrs = get_addresses_for_customer(self.cbCustomer.currentData())
        chosen = next((a for a in addrs if a["id"] == aid), None)
        if chosen:
            self.populate_billto_from_address(chosen)

    def populate_billto_from_address(self, addr):
        parts = []
        cid = self.cbCustomer.currentData()
        if cid:
            cust = get_customer(cid)
            if cust:
                parts.append(cust.get("name",""))
        if addr.get("address"):
            parts.append(addr.get("address"))
        state_line = []
        if addr.get("state"):
            state_line.append(addr.get("state"))
        if addr.get("pincode"):
            state_line.append(addr.get("pincode"))
        if state_line:
            parts.append(" - ".join(state_line))
        pan = ""
        gst = ""
        if cid:
            cust = get_customer(cid)
            if cust:
                pan = cust.get("pan") or ""
                gst = cust.get("gstin") or ""
        parts.append("")
        parts.append(f"PAN/IT No: {pan}")
        parts.append(f"GSTIN/UIN: {gst}")
        self.teBillTo.setPlainText("\n".join([p for p in parts if p is not None]))

    # table helpers (same calculations)
    def _ensure_table_columns(self):
        try:
            self.table.setColumnCount(13)
        except Exception:
            pass

    def add_row(self):
        r = self.table.rowCount()
        self.table.insertRow(r)
        self.table.setItem(r, 0, QTableWidgetItem(str(r + 1)))
        for c in range(1, 13):
            if not self.table.item(r, c):
                self.table.setItem(r, c, QTableWidgetItem(""))

    def del_row(self):
        r = self.table.currentRow()
        if r >= 0:
            self.table.removeRow(r)
            for i in range(self.table.rowCount()):
                self.table.setItem(i, 0, QTableWidgetItem(str(i + 1)))

    def _on_cell_changed(self, row, column):
        if not self._cell_change_enabled:
            return
        try:
            self._cell_change_enabled = False
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
        rate = self._to_float(self._safe_text(r, 4))
        qty = self._to_float(self._safe_text(r, 5))
        amount = rate * qty
        taxable = self._to_float(self._safe_text(r, 7), amount)
        cgst_perc = self._to_float(self._safe_text(r, 8))
        sgst_perc = self._to_float(self._safe_text(r, 10))
        cgst_amt = taxable * cgst_perc / 100.0
        sgst_amt = taxable * sgst_perc / 100.0
        total = taxable + cgst_amt + sgst_amt
        try:
            self.table.setItem(r, 6, QTableWidgetItem(f"{amount:.2f}"))
            self.table.setItem(r, 9, QTableWidgetItem(f"{cgst_amt:.2f}"))
            self.table.setItem(r, 11, QTableWidgetItem(f"{sgst_amt:.2f}"))
            self.table.setItem(r, 12, QTableWidgetItem(f"{total:.2f}"))
        except Exception:
            pass

    def collect_items(self):
        items = []
        for r in range(self.table.rowCount()):
            def txt(col): return self._safe_text(r, col)
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
            items.append(item)
        return items

    def _build_header_dict(self, total_amount):
        return {
            "invoice_number": self.leDebitNo.text().strip(),
            "date": self.leDate.text().strip(),
            "type": "DEBIT NOTE",
            "bill_to": self.teBillTo.toPlainText().strip(),
            "consignee_preview": self.teConsignee.toPlainText().strip(),
            "shipper": self.leShipper.text().strip(),
            "ship_consigne": self.leShipConsignee.text().strip(),
            "pol": self.lePOL.text().strip(),
            "pod": self.lePOD.text().strip(),
            "vessel_flight": self.leVessel.text().strip(),
            "etd": self.leETD.text().strip(),
            "eta": self.leETA.text().strip(),
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

    def save_debit(self):
        items = self.collect_items()
        if not items:
            QMessageBox.warning(self, "No items", "Please add at least one item.")
            return
        total_amount = sum(i["total_amt"] for i in items)
        header = self._build_header_dict(total_amount)
        inv_id = insert_invoice(header, items)
        QMessageBox.information(self, "Saved", f"Debit Note saved (ID: {inv_id})")

    def export_pdf(self):
        items = self.collect_items()
        if not items:
            QMessageBox.warning(self, "No items", "Please add at least one item.")
            return
        header = self._build_header_dict(sum(i["total_amt"] for i in items))
        path = generate_invoice_pdf(header, items, title="DEBIT NOTE")
        QMessageBox.information(self, "PDF saved", f"Saved: {path}")

    def open_customer_manager(self):
        from customer_manager import ConsigneeManager  # 🔥 LAZY IMPORT

        cm = ConsigneeManager()
        dlg = QtWidgets.QDialog(self)
        dlg.setWindowTitle("Customer Manager")
        dlg.resize(900, 600)

        layout = QtWidgets.QVBoxLayout(dlg)
        layout.addWidget(cm)
        dlg.exec()
        self.refresh_customers()


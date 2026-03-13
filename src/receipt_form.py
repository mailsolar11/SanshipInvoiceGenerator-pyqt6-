from PyQt6 import QtWidgets, QtCore, QtGui
import sqlite3
import os
from datetime import date

from accounting.ledgers import list_ledgers
from accounting.db import DB_PATH
from services.receipt_service import post_receipt_voucher

class ReceiptForm(QtWidgets.QWidget):
    def __init__(self):
        super().__init__()
        
        # Main Layout
        self.main_layout = QtWidgets.QVBoxLayout(self)
        self.main_layout.setContentsMargins(40, 40, 40, 40)
        self.main_layout.setSpacing(20)

        # ---------------------------
        # Header
        # ---------------------------
        lbl_title = QtWidgets.QLabel("Receipt Voucher")
        lbl_title.setStyleSheet("font-size: 28px; font-weight: bold;")
        self.main_layout.addWidget(lbl_title)
        
        lbl_subtitle = QtWidgets.QLabel("Record payment received from a customer")
        lbl_subtitle.setStyleSheet("font-size: 14px; color: #888;")
        self.main_layout.addWidget(lbl_subtitle)

        self.main_layout.addSpacing(20)

        # ---------------------------
        # Form Container (Card)
        # ---------------------------
        card = QtWidgets.QFrame()
        card.setObjectName("contentCard") # Use global theme
        card_layout = QtWidgets.QGridLayout(card)
        card_layout.setContentsMargins(30, 30, 30, 30)
        card_layout.setSpacing(20)

        # Row 1: Date & Voucher No (Auto)
        card_layout.addWidget(QtWidgets.QLabel("Date:"), 0, 0)
        self.dateEdit = QtWidgets.QDateEdit(date.today())
        self.dateEdit.setCalendarPopup(True)
        self.dateEdit.setDisplayFormat("yyyy-MM-dd")
        card_layout.addWidget(self.dateEdit, 0, 1)

        # Row 2: Debit Account (Cash/Bank)
        card_layout.addWidget(QtWidgets.QLabel("Received Into (Dr):"), 1, 0)
        self.cmbDebit = QtWidgets.QComboBox()
        self.cmbDebit.setEditable(True)
        self.load_cash_bank_accounts()
        card_layout.addWidget(self.cmbDebit, 1, 1)

        # Row 3: Credit Account (Customer)
        card_layout.addWidget(QtWidgets.QLabel("Received From (Cr):"), 2, 0)
        self.cmbCredit = QtWidgets.QComboBox()
        self.cmbCredit.setEditable(True)
        self.load_customers()
        card_layout.addWidget(self.cmbCredit, 2, 1)

        # Row 4: Amount
        card_layout.addWidget(QtWidgets.QLabel("Amount (₹):"), 3, 0)
        self.leAmount = QtWidgets.QLineEdit()
        self.leAmount.setPlaceholderText("0.00")
        self.leAmount.setValidator(QtGui.QDoubleValidator(0.00, 999999999.99, 2))
        card_layout.addWidget(self.leAmount, 3, 1)

        # Row 5: Reference / Narration
        card_layout.addWidget(QtWidgets.QLabel("Narration / Ref:"), 4, 0)
        self.leNarration = QtWidgets.QLineEdit()
        self.leNarration.setPlaceholderText("e.g. NEFT-123456 or Cheque No.")
        card_layout.addWidget(self.leNarration, 4, 1)

        self.main_layout.addWidget(card)
        
        # ---------------------------
        # Buttons
        # ---------------------------
        btn_layout = QtWidgets.QHBoxLayout()
        btn_layout.addStretch()
        
        self.btnSave = QtWidgets.QPushButton("Save Receipt")
        self.btnSave.setObjectName("ctaBtn") # Global CTA style
        self.btnSave.setFixedSize(160, 45)
        self.btnSave.setCursor(QtCore.Qt.CursorShape.PointingHandCursor)
        self.btnSave.clicked.connect(self.save_receipt)
        
        btn_layout.addWidget(self.btnSave)
        
        self.main_layout.addLayout(btn_layout)
        self.main_layout.addStretch()

    def load_cash_bank_accounts(self):
        # Ideally fetch groups 'Cash-in-Hand' and 'Bank Accounts'
        # For now, listing all, user can search. 
        # TODO: Filter by group_id if we had group mapping easily available
        ledgers = list_ledgers()
        self.cmbDebit.clear()
        # Ensure we have at least one Cash ledger
        has_cash = False
        for l in ledgers:
            # Simple heuristic or just list all
            self.cmbDebit.addItem(l["name"], l["id"])
            if "cash" in l["name"].lower():
                has_cash = True
        
        if not has_cash:
            self.cmbDebit.addItem("Cash", None) # Placeholder if logic fails

    def load_customers(self):
        ledgers = list_ledgers()
        self.cmbCredit.clear()
        for l in ledgers:
             self.cmbCredit.addItem(l["name"], l["id"])

    def save_receipt(self):
        dr_id = self.cmbDebit.currentData()
        cr_id = self.cmbCredit.currentData()
        amount_txt = self.leAmount.text().strip()
        narration = self.leNarration.text().strip()
        
        if not dr_id or not cr_id:
            QtWidgets.QMessageBox.warning(self, "Validation", "Please select both accounts.")
            return

        if not amount_txt:
            QtWidgets.QMessageBox.warning(self, "Validation", "Please enter an amount.")
            return

        try:
            amount = float(amount_txt)
        except ValueError:
             QtWidgets.QMessageBox.warning(self, "Validation", "Invalid amount.")
             return

        try:
            voucher_id = post_receipt_voucher(
                date=self.dateEdit.date().toString("yyyy-MM-dd"),
                amount=amount,
                dr_ledger_id=dr_id,
                cr_ledger_id=cr_id,
                narration=narration
            )
            QtWidgets.QMessageBox.information(self, "Success", f"Receipt Saved! Voucher ID: {voucher_id}")
            self.leAmount.clear()
            self.leNarration.clear()
        except Exception as e:
            QtWidgets.QMessageBox.critical(self, "Error", f"Failed to save:\n{str(e)}")

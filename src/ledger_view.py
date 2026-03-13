from PyQt6 import QtWidgets, QtCore, QtGui
import sqlite3
import os
from datetime import date

from accounting.ledgers import list_ledgers
from accounting.db import DB_PATH

class LedgerView(QtWidgets.QWidget):
    def __init__(self):
        super().__init__()
        
        # Main Layout
        self.main_layout = QtWidgets.QVBoxLayout(self)
        self.main_layout.setContentsMargins(20, 20, 20, 20)
        self.main_layout.setSpacing(15)

        # ---------------------------
        # Header
        # ---------------------------
        header_frame = QtWidgets.QFrame()
        header = QtWidgets.QHBoxLayout(header_frame)
        header.setContentsMargins(0, 0, 0, 0)
        
        lbl_title = QtWidgets.QLabel("Statement of Accounts")
        lbl_title.setStyleSheet("font-size: 24px; font-weight: bold;")
        header.addWidget(lbl_title)
        
        self.main_layout.addWidget(header_frame)

        # ---------------------------
        # Filters
        # ---------------------------
        filter_frame = QtWidgets.QFrame()
        # Use objectName to target in global theme if needed, but for now just rely on Card styling
        filter_frame.setObjectName("filterFrame")
        fl = QtWidgets.QHBoxLayout(filter_frame)
        fl.setContentsMargins(10, 10, 10, 10)
        fl.setSpacing(15)

        # Ledger Select
        fl.addWidget(QtWidgets.QLabel("Select Account:"))
        self.cmbLedgers = QtWidgets.QComboBox()
        self.cmbLedgers.setEditable(True)
        self.cmbLedgers.setMinimumWidth(250)
        fl.addWidget(self.cmbLedgers)

        # Date Range
        fl.addWidget(QtWidgets.QLabel("From:"))
        self.dateStart = QtWidgets.QDateEdit(date.today().replace(day=1))
        self.dateStart.setCalendarPopup(True)
        self.dateStart.setDisplayFormat("yyyy-MM-dd")
        fl.addWidget(self.dateStart)

        fl.addWidget(QtWidgets.QLabel("To:"))
        self.dateEnd = QtWidgets.QDateEdit(date.today())
        self.dateEnd.setCalendarPopup(True)
        self.dateEnd.setDisplayFormat("yyyy-MM-dd")
        fl.addWidget(self.dateEnd)

        # Go Button
        self.btnGo = QtWidgets.QPushButton("View Statement")
        self.btnGo.setCursor(QtCore.Qt.CursorShape.PointingHandCursor)
        # btnGo uses global button styling
        self.btnGo.setFixedSize(140, 36)
        fl.addWidget(self.btnGo)
        
        fl.addStretch()
        self.main_layout.addWidget(filter_frame)

        # ---------------------------
        # Table
        # ---------------------------
        self.table = QtWidgets.QTableWidget()
        self.table.setColumnCount(6)
        self.table.setHorizontalHeaderLabels(["Date", "Voucher No", "Particulars", "Debit", "Credit", "Balance"])
        self.table.horizontalHeader().setSectionResizeMode(QtWidgets.QHeaderView.ResizeMode.Stretch)
        self.table.verticalHeader().setVisible(False)
        self.table.setAlternatingRowColors(True)
        self.table.setEditTriggers(QtWidgets.QAbstractItemView.EditTrigger.NoEditTriggers)
        self.table.setSelectionBehavior(QtWidgets.QAbstractItemView.SelectionBehavior.SelectRows)
        self.table.setStyleSheet("QTableWidget { border: 1px solid #e0e0e0; border-radius: 4px; }")
        
        self.main_layout.addWidget(self.table)

        # Initialization
        self.load_ledgers()
        self.btnGo.clicked.connect(self.load_statement)

    def load_ledgers(self):
        ledgers = list_ledgers(text=None) # Get all
        self.cmbLedgers.clear()
        for l in ledgers:
            # Store ID in user data
            self.cmbLedgers.addItem(l["name"], l["id"])

    def load_statement(self):
        ledger_id = self.cmbLedgers.currentData()
        if not ledger_id:
            return

        start_date = self.dateStart.date().toString("yyyy-MM-dd")
        end_date = self.dateEnd.date().toString("yyyy-MM-dd")

        conn = sqlite3.connect(DB_PATH)
        conn.row_factory = sqlite3.Row
        cur = conn.cursor()

        # Query: Get all entries for this ledger in range
        # We need to JOIN vouchers to get date and number
        # We also need to calculate running balance
        
        # 1. Opening Balance (Sum of all previous entries)
        cur.execute("""
            SELECT SUM(dr_amount) - SUM(cr_amount) as opening
            FROM ledger_entries le
            JOIN vouchers v ON le.voucher_id = v.id
            WHERE le.ledger_id = ? AND v.voucher_date < ?
        """, (ledger_id, start_date))
        res = cur.fetchone()
        opening = res['opening'] if res and res['opening'] else 0.0

        # 2. Entries in range
        cur.execute("""
            SELECT v.voucher_date, v.voucher_no, v.narration, le.dr_amount, le.cr_amount
            FROM ledger_entries le
            JOIN vouchers v ON le.voucher_id = v.id
            WHERE le.ledger_id = ? AND v.voucher_date BETWEEN ? AND ?
            ORDER BY v.voucher_date, v.id
        """, (ledger_id, start_date, end_date))
        rows = cur.fetchall()
        
        conn.close()

        # Populate
        self.table.setRowCount(0)
        
        # Opening Row
        self.table.insertRow(0)
        self.table.setItem(0, 2, QtWidgets.QTableWidgetItem("Opening Balance"))
        self.table.setItem(0, 5, QtWidgets.QTableWidgetItem(f"{opening:.2f}"))
        
        running = opening
        for r in rows:
            idx = self.table.rowCount()
            self.table.insertRow(idx)
            
            dr = r['dr_amount']
            cr = r['cr_amount']
            running += (dr - cr)
            
            self.table.setItem(idx, 0, QtWidgets.QTableWidgetItem(r['voucher_date']))
            self.table.setItem(idx, 1, QtWidgets.QTableWidgetItem(r['voucher_no']))
            self.table.setItem(idx, 2, QtWidgets.QTableWidgetItem(r['narration'] or ""))
            self.table.setItem(idx, 3, QtWidgets.QTableWidgetItem(f"{dr:.2f}" if dr > 0 else ""))
            self.table.setItem(idx, 4, QtWidgets.QTableWidgetItem(f"{cr:.2f}" if cr > 0 else ""))
            self.table.setItem(idx, 5, QtWidgets.QTableWidgetItem(f"{running:.2f}"))

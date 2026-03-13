from PyQt6 import QtWidgets, QtCore, QtGui
import os
from datetime import datetime

from reports.trial_balance import get_trial_balance
from reports.pnl import get_profit_and_loss
from reports.balance_sheet import get_balance_sheet

BASE_DIR = os.path.dirname(os.path.dirname(__file__))

class ReportsWindow(QtWidgets.QWidget):
    def __init__(self):
        super().__init__()
        
        # Main Layout
        self.main_layout = QtWidgets.QVBoxLayout(self)
        self.main_layout.setContentsMargins(20, 20, 20, 20)
        self.main_layout.setSpacing(20)

        # Header
        self.header_frame = QtWidgets.QFrame()
        self.header_layout = QtWidgets.QHBoxLayout(self.header_frame)
        self.header_layout.setContentsMargins(0, 0, 0, 0)
        
        self.lbl_title = QtWidgets.QLabel("Financial Reports")
        self.lbl_title.setStyleSheet("font-size: 24px; font-weight: bold;")
        self.header_layout.addWidget(self.lbl_title)
        
        self.btn_refresh = QtWidgets.QPushButton("Refresh Data")
        self.btn_refresh.setCursor(QtCore.Qt.CursorShape.PointingHandCursor)
        self.btn_refresh.setFixedWidth(120)
        self.btn_refresh.clicked.connect(self.load_reports)
        self.header_layout.addWidget(self.btn_refresh)
        
        self.main_layout.addWidget(self.header_frame)

        # Tabs
        self.tabs = QtWidgets.QTabWidget()

        
        self.tab_trial = QtWidgets.QWidget()
        self.tab_pnl = QtWidgets.QWidget()
        self.tab_bs = QtWidgets.QWidget()
        
        self.tabs.addTab(self.tab_trial, "Trial Balance")
        self.tabs.addTab(self.tab_pnl, "Profit & Loss")
        self.tabs.addTab(self.tab_bs, "Balance Sheet")
        
        self.main_layout.addWidget(self.tabs)
        
        # Init Tabs
        self.init_trial_tab()
        self.init_pnl_tab()
        self.init_bs_tab()

        # Load Data
        self.load_reports()

    def init_trial_tab(self):
        layout = QtWidgets.QVBoxLayout(self.tab_trial)
        self.tableTrial = self.create_table(["Ledger Name", "Debit", "Credit"])
        layout.addWidget(self.tableTrial)

    def init_pnl_tab(self):
        layout = QtWidgets.QVBoxLayout(self.tab_pnl)
        
        # Summary Cards
        summary_frame = QtWidgets.QFrame()
        summary_frame.setObjectName("contentCard")
        summary_frame.setProperty("class", "contentCard") # for extra safety if referencing manually
        sum_layout = QtWidgets.QHBoxLayout(summary_frame)
        
        self.lblIncome = self.create_stat_label("Total Income", "green")
        self.lblExpense = self.create_stat_label("Total Expense", "red")
        self.lblProfit = self.create_stat_label("Net Profit", "blue")
        
        sum_layout.addWidget(self.lblIncome)
        sum_layout.addWidget(self.lblExpense)
        sum_layout.addWidget(self.lblProfit)
        
        layout.addWidget(summary_frame)
        layout.addStretch()

    def init_bs_tab(self):
        layout = QtWidgets.QHBoxLayout(self.tab_bs)
        
        # Assets
        grp_assets = QtWidgets.QGroupBox("Assets")
        l_assets = QtWidgets.QVBoxLayout(grp_assets)
        self.tableAssets = self.create_table(["Asset", "Amount"])
        l_assets.addWidget(self.tableAssets)
        
        # Liabilities
        grp_liabs = QtWidgets.QGroupBox("Liabilities")
        l_liabs = QtWidgets.QVBoxLayout(grp_liabs)
        self.tableLiabilities = self.create_table(["Liability", "Amount"])
        l_liabs.addWidget(self.tableLiabilities)
        
        layout.addWidget(grp_assets)
        layout.addWidget(grp_liabs)

    def create_table(self, headers):
        table = QtWidgets.QTableWidget()
        table.setColumnCount(len(headers))
        table.setHorizontalHeaderLabels(headers)
        table.horizontalHeader().setSectionResizeMode(QtWidgets.QHeaderView.ResizeMode.Stretch)
        table.verticalHeader().setVisible(False)
        table.setAlternatingRowColors(True)
        table.setEditTriggers(QtWidgets.QAbstractItemView.EditTrigger.NoEditTriggers)
        table.setSelectionBehavior(QtWidgets.QAbstractItemView.SelectionBehavior.SelectRows)
        return table

    def create_stat_label(self, title, color):
        lbl = QtWidgets.QLabel(f"{title}\n₹ 0.00")
        lbl.setAlignment(QtCore.Qt.AlignmentFlag.AlignCenter)
        lbl.setStyleSheet(f"font-size: 16px; font-weight: bold; color: {color}; padding: 10px;")
        return lbl

    # ----------------------------------
    def load_reports(self):
        self.load_trial_balance()
        self.load_pnl()
        self.load_balance_sheet()

    def load_trial_balance(self):
        rows = get_trial_balance()
        self.tableTrial.setRowCount(0)
        for r in rows:
            i = self.tableTrial.rowCount()
            self.tableTrial.insertRow(i)
            self.tableTrial.setItem(i, 0, QtWidgets.QTableWidgetItem(r["ledger_name"]))
            self.tableTrial.setItem(i, 1, QtWidgets.QTableWidgetItem(f"{r['dr_balance']:.2f}"))
            self.tableTrial.setItem(i, 2, QtWidgets.QTableWidgetItem(f"{r['cr_balance']:.2f}"))

    def load_pnl(self):
        data = get_profit_and_loss("2000-01-01", "2100-01-01")
        
        self.lblIncome.setText(f"Total Income\n₹ {data['total_income']:,.2f}")
        self.lblExpense.setText(f"Total Expense\n₹ {data['total_expense']:,.2f}")
        profit = data['net_profit']
        color = "green" if profit >= 0 else "red"
        self.lblProfit.setText(f"Net Profit\n₹ {profit:,.2f}")
        self.lblProfit.setStyleSheet(f"font-size: 18px; font-weight: bold; color: {color}; padding: 10px;")

    def load_balance_sheet(self):
        data = get_balance_sheet("2100-01-01")

        self.tableAssets.setRowCount(0)
        for a in data["assets"]:
            r = self.tableAssets.rowCount()
            self.tableAssets.insertRow(r)
            self.tableAssets.setItem(r, 0, QtWidgets.QTableWidgetItem(a["ledger"]))
            self.tableAssets.setItem(r, 1, QtWidgets.QTableWidgetItem(f"{a['amount']:.2f}"))

        self.tableLiabilities.setRowCount(0)
        for l in data["liabilities"]:
            r = self.tableLiabilities.rowCount()
            self.tableLiabilities.insertRow(r)
            self.tableLiabilities.setItem(r, 0, QtWidgets.QTableWidgetItem(l["ledger"]))
            self.tableLiabilities.setItem(r, 1, QtWidgets.QTableWidgetItem(f"{l['amount']:.2f}"))

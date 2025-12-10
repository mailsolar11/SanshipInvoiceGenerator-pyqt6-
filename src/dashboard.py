import os
from PyQt6 import QtWidgets, uic
from PyQt6.QtWidgets import QTableWidgetItem
from database import fetch_invoices, delete_invoice

BASE_DIR = os.path.dirname(os.path.dirname(__file__))


class Dashboard(QtWidgets.QWidget):
    def __init__(self):
        super().__init__()
        uic.loadUi(os.path.join(BASE_DIR, "ui", "dashboard.ui"), self)

        self.table = self.findChild(QtWidgets.QTableWidget, "tableInvoices")
        self.leSearch = self.findChild(QtWidgets.QLineEdit, "leSearch")
        self.btnSearch = self.findChild(QtWidgets.QPushButton, "btnSearch")
        self.btnRefresh = self.findChild(QtWidgets.QPushButton, "btnRefresh")
        self.btnOpen = self.findChild(QtWidgets.QPushButton, "btnOpen")
        self.btnDelete = self.findChild(QtWidgets.QPushButton, "btnDelete")

        self.btnRefresh.clicked.connect(self.load_data)
        self.btnSearch.clicked.connect(self.search)
        self.btnDelete.clicked.connect(self.delete_selected)

        self.load_data()

    def load_data(self):
        self.table.setRowCount(0)
        rows = fetch_invoices()
        for row in rows:
            r = self.table.rowCount()
            self.table.insertRow(r)
            self.table.setItem(r, 0, QTableWidgetItem(row["invoice_number"]))
            self.table.setItem(r, 1, QTableWidgetItem(row["type"]))
            self.table.setItem(r, 2, QTableWidgetItem(row["date"]))
            self.table.setItem(r, 3, QTableWidgetItem(str(row["total"])))
            self.table.item(r, 0).setData(1000, row["id"])

    def search(self):
        text = self.leSearch.text().lower().strip()
        for r in range(self.table.rowCount()):
            show = any(
                text in (self.table.item(r, c).text().lower() if self.table.item(r, c) else "")
                for c in range(self.table.columnCount())
            )
            self.table.setRowHidden(r, not show)

    def delete_selected(self):
        row = self.table.currentRow()
        if row < 0:
            return
        inv_id = self.table.item(row, 0).data(1000)
        delete_invoice(inv_id)
        QtWidgets.QMessageBox.information(self, "Deleted", "Invoice deleted.")
        self.load_data()

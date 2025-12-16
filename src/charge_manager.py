# src/charge_manager.py
import os
from PyQt6 import QtWidgets, uic
from PyQt6.QtWidgets import QTableWidgetItem, QMessageBox
from database import list_charges, add_charge, update_charge, delete_charge

BASE_DIR = os.path.dirname(os.path.dirname(__file__))


class ChargeManager(QtWidgets.QWidget):
    def __init__(self):
        super().__init__()
        uic.loadUi(os.path.join(BASE_DIR, "ui", "charge_manager.ui"), self)

        self.table = self.findChild(QtWidgets.QTableWidget, "tableCharges")
        self.btnAdd = self.findChild(QtWidgets.QPushButton, "btnAdd")
        self.btnEdit = self.findChild(QtWidgets.QPushButton, "btnEdit")
        self.btnDelete = self.findChild(QtWidgets.QPushButton, "btnDelete")

        self.btnAdd.clicked.connect(self.add_charge)
        self.btnEdit.clicked.connect(self.edit_charge)
        self.btnDelete.clicked.connect(self.delete_charge)

        self.load_data()

    def load_data(self):
        self.table.setRowCount(0)
        for row in list_charges():
            r = self.table.rowCount()
            self.table.insertRow(r)
            self.table.setItem(r, 0, QTableWidgetItem(row["charge_name"]))
            self.table.setItem(r, 1, QTableWidgetItem(row["hsn_sac"]))
            self.table.setItem(r, 2, QTableWidgetItem(row["currency"]))
            self.table.setItem(r, 3, QTableWidgetItem(str(row["cgst_rate"])))
            self.table.setItem(r, 4, QTableWidgetItem(str(row["sgst_rate"])))
            self.table.item(r, 0).setData(1000, row["id"])

    def add_charge(self):
        dlg = QtWidgets.QDialog(self)
        uic.loadUi(os.path.join(BASE_DIR, "ui", "charge_manager.ui"), dlg)

    def edit_charge(self):
        row = self.table.currentRow()
        if row < 0:
            return

        cid = self.table.item(row, 0).data(1000)
        name = self.table.item(row, 0).text()
        hsn = self.table.item(row, 1).text()
        cur = self.table.item(row, 2).text()
        cgst = self.table.item(row, 3).text()
        sgst = self.table.item(row, 4).text()

        name, ok = QtWidgets.QInputDialog.getText(self, "Edit Charge", "Charge Name:", text=name)
        if not ok:
            return
        hsn, _ = QtWidgets.QInputDialog.getText(self, "HSN/SAC", "HSN/SAC:", text=hsn)
        cur, _ = QtWidgets.QInputDialog.getText(self, "Currency", "Currency:", text=cur)
        cgst, _ = QtWidgets.QInputDialog.getDouble(self, "CGST %", "CGST %:", float(cgst))
        sgst, _ = QtWidgets.QInputDialog.getDouble(self, "SGST %", "SGST %:", float(sgst))

        update_charge(cid, name, hsn, cur, cgst, sgst)
        self.load_data()

    def delete_charge(self):
        row = self.table.currentRow()
        if row < 0:
            return

        cid = self.table.item(row, 0).data(1000)
        if QMessageBox.question(self, "Confirm", "Delete this charge?") == QMessageBox.StandardButton.Yes:
            delete_charge(cid)
            self.load_data()

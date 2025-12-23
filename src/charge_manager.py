import os
from PyQt6 import QtWidgets, uic
from database import (
    add_charge,
    list_charges,
    get_charge,
    update_charge,
    delete_charge
)

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

    # --------------------------------------------
    def load_data(self):
        self.table.setRowCount(0)
        charges = list_charges()

        for ch in charges:
            r = self.table.rowCount()
            self.table.insertRow(r)

            self.table.setItem(r, 0, QtWidgets.QTableWidgetItem(ch["charge_name"]))
            self.table.setItem(r, 1, QtWidgets.QTableWidgetItem(ch["hsn_sac"] or ""))
            self.table.setItem(r, 2, QtWidgets.QTableWidgetItem(ch["currency"] or ""))
            self.table.setItem(r, 3, QtWidgets.QTableWidgetItem(str(ch["cgst_rate"] or 0)))
            self.table.setItem(r, 4, QtWidgets.QTableWidgetItem(str(ch["sgst_rate"] or 0)))

            self.table.item(r, 0).setData(1000, ch["id"])

    # --------------------------------------------
    def add_charge(self):
        dialog = ChargeDialog(self)
        if dialog.exec():
            add_charge(dialog.get_data())
            self.load_data()

    # --------------------------------------------
    def edit_charge(self):
        row = self.table.currentRow()
        if row < 0:
            return

        charge_id = self.table.item(row, 0).data(1000)
        charge = get_charge(charge_id)

        dialog = ChargeDialog(self, charge)
        if dialog.exec():
            update_charge(charge_id, dialog.get_data())
            self.load_data()

    # --------------------------------------------
    def delete_charge(self):
        row = self.table.currentRow()
        if row < 0:
            return

        charge_id = self.table.item(row, 0).data(1000)
        delete_charge(charge_id)
        self.load_data()


# =====================================================
# CHARGE DIALOG
# =====================================================
class ChargeDialog(QtWidgets.QDialog):
    def __init__(self, parent=None, data=None):
        super().__init__(parent)
        self.setWindowTitle("Charge / HSN")

        layout = QtWidgets.QFormLayout(self)

        self.leName = QtWidgets.QLineEdit()
        self.leHSN = QtWidgets.QLineEdit()
        self.leCur = QtWidgets.QLineEdit("INR")
        self.leCGST = QtWidgets.QDoubleSpinBox()
        self.leSGST = QtWidgets.QDoubleSpinBox()

        self.leCGST.setMaximum(100)
        self.leSGST.setMaximum(100)

        layout.addRow("Charge Name", self.leName)
        layout.addRow("HSN / SAC", self.leHSN)
        layout.addRow("Currency", self.leCur)
        layout.addRow("CGST %", self.leCGST)
        layout.addRow("SGST %", self.leSGST)

        btn = QtWidgets.QPushButton("Save")
        btn.clicked.connect(self.accept)
        layout.addRow(btn)

        if data:
            self.leName.setText(data["charge_name"])
            self.leHSN.setText(data["hsn_sac"] or "")
            self.leCur.setText(data["currency"] or "")
            self.leCGST.setValue(data["cgst_rate"] or 0)
            self.leSGST.setValue(data["sgst_rate"] or 0)

    def get_data(self):
        return {
            "charge_name": self.leName.text(),
            "hsn_sac": self.leHSN.text(),
            "currency": self.leCur.text(),
            "cgst_rate": self.leCGST.value(),
            "sgst_rate": self.leSGST.value(),
            "is_active": 1
        }

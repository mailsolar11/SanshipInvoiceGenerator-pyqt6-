import os
from PyQt6 import QtWidgets, uic
from PyQt6.QtWidgets import QTableWidgetItem, QMessageBox

from database import (
    list_consignees,
    insert_consignee,
    update_consignee,
    delete_consignee
)

BASE_DIR = os.path.dirname(os.path.dirname(__file__))


class ConsigneeManager(QtWidgets.QWidget):
    def __init__(self):
        super().__init__()
        uic.loadUi(os.path.join(BASE_DIR, "ui", "consignee_manager.ui"), self)

        self.table = self.findChild(QtWidgets.QTableWidget, "tableConsignees")
        self.btnAdd = self.findChild(QtWidgets.QPushButton, "btnAddConsignee")
        self.btnEdit = self.findChild(QtWidgets.QPushButton, "btnEdit")
        self.btnDelete = self.findChild(QtWidgets.QPushButton, "btnDelete")

        self.btnAdd.clicked.connect(self.add_consignee)
        self.btnEdit.clicked.connect(self.edit_consignee)
        self.btnDelete.clicked.connect(self.delete_consignee)

        self.load_data()

    def load_data(self):
        self.table.setRowCount(0)
        for row in list_consignees():
            r = self.table.rowCount()
            self.table.insertRow(r)
            self.table.setItem(r, 0, QTableWidgetItem(row["name"]))
            self.table.setItem(r, 1, QTableWidgetItem(row.get("gstin") or ""))
            self.table.setItem(r, 2, QTableWidgetItem(row.get("pan") or ""))
            self.table.item(r, 0).setData(1000, row["id"])

    def add_consignee(self):
        name, ok = QtWidgets.QInputDialog.getText(self, "Add Consignee", "Consignee Name:")
        if not ok or not name.strip():
            return

        gst, _ = QtWidgets.QInputDialog.getText(self, "GSTIN", "GSTIN (optional):")
        pan, _ = QtWidgets.QInputDialog.getText(self, "PAN", "PAN (optional):")

        insert_consignee({
            "name": name.strip(),
            "gstin": gst.strip(),
            "pan": pan.strip()
        })

        self.load_data()

    def edit_consignee(self):
        row = self.table.currentRow()
        if row < 0:
            return

        cid = self.table.item(row, 0).data(1000)
        name = self.table.item(row, 0).text()
        gst = self.table.item(row, 1).text()
        pan = self.table.item(row, 2).text()

        name, ok = QtWidgets.QInputDialog.getText(self, "Edit Consignee", "Name:", text=name)
        if not ok:
            return

        gst, _ = QtWidgets.QInputDialog.getText(self, "Edit GSTIN", "GSTIN:", text=gst)
        pan, _ = QtWidgets.QInputDialog.getText(self, "Edit PAN", "PAN:", text=pan)

        update_consignee(cid, {
            "name": name.strip(),
            "gstin": gst.strip(),
            "pan": pan.strip()
        })

        self.load_data()

    def delete_consignee(self):
        row = self.table.currentRow()
        if row < 0:
            return

        cid = self.table.item(row, 0).data(1000)
        reply = QMessageBox.question(
            self,
            "Confirm Delete",
            "Delete selected consignee?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )

        if reply == QMessageBox.StandardButton.Yes:
            delete_consignee(cid)
            self.load_data()

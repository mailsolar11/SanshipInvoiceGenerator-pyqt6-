# src/consignee_manager.py
import os
from PyQt6 import QtWidgets, uic
from PyQt6.QtWidgets import QMessageBox, QTableWidgetItem

from database import (
    add_consignee, update_consignee, delete_consignee,
    list_consignees, get_consignee,
    add_consignee_address, get_addresses_for_consignee,
    update_address, delete_address
)

BASE_DIR = os.path.dirname(os.path.dirname(__file__))


class ConsigneeManager(QtWidgets.QWidget):
    def __init__(self):
        super().__init__()
        uic.loadUi(os.path.join(BASE_DIR, "ui", "customer_manager.ui"), self)

        # Rename title
        title = self.findChild(QtWidgets.QLabel, "labelTitle")
        if title:
            title.setText("Consignee Master")

        self.leSearch = self.findChild(QtWidgets.QLineEdit, "leSearch")
        self.btnAdd = self.findChild(QtWidgets.QPushButton, "btnAddCustomer")
        self.table = self.findChild(QtWidgets.QTableWidget, "tableCustomers")

        self.btnAdd.setText("Add Consignee")

        self.btnAdd.clicked.connect(self.open_add_dialog)
        self.leSearch.textChanged.connect(self.refresh_table)

        self.refresh_table()

    # --------------------------------------------------
    def refresh_table(self):
        search = self.leSearch.text().strip()
        consignees = list_consignees(search if search else None)

        self.table.setRowCount(0)
        for r, c in enumerate(consignees):
            self.table.insertRow(r)
            self.table.setItem(r, 0, QTableWidgetItem(str(c["id"])))
            self.table.setItem(r, 1, QTableWidgetItem(c.get("name", "")))
            self.table.setItem(r, 2, QTableWidgetItem(c.get("gstin", "")))
            self.table.setItem(r, 3, QTableWidgetItem(c.get("pan", "")))

            addrs = get_addresses_for_consignee(c["id"])
            summary = ", ".join(a["label"] for a in addrs) if addrs else ""
            self.table.setItem(r, 4, QTableWidgetItem(summary))

            btnAddr = QtWidgets.QPushButton("Addresses")
            btnEdit = QtWidgets.QPushButton("Edit")
            btnDel = QtWidgets.QPushButton("Delete")

            btnAddr.clicked.connect(lambda _, cid=c["id"]: self.open_address_manager(cid))
            btnEdit.clicked.connect(lambda _, cid=c["id"]: self.open_edit_dialog(cid))
            btnDel.clicked.connect(lambda _, cid=c["id"]: self.delete(cid))

            w = QtWidgets.QWidget()
            hl = QtWidgets.QHBoxLayout(w)
            hl.setContentsMargins(0, 0, 0, 0)
            hl.addWidget(btnAddr)
            hl.addWidget(btnEdit)
            hl.addWidget(btnDel)

            self.table.setCellWidget(r, 5, w)

        self.table.resizeColumnsToContents()

    # --------------------------------------------------
    def open_add_dialog(self):
        dlg = QtWidgets.QDialog(self)
        uic.loadUi(os.path.join(BASE_DIR, "ui", "customer_dialog.ui"), dlg)

        dlg.setWindowTitle("Add Consignee")
        dlg.findChild(QtWidgets.QLabel, "labelTitle").setText("Add Consignee")

        leName = dlg.findChild(QtWidgets.QLineEdit, "leName")
        leGST = dlg.findChild(QtWidgets.QLineEdit, "leGST")
        lePAN = dlg.findChild(QtWidgets.QLineEdit, "lePAN")
        btn = dlg.findChild(QtWidgets.QPushButton, "btnNext")

        def save():
            name = leName.text().strip()
            if not name:
                QMessageBox.warning(dlg, "Validation", "Consignee name required")
                return
            cid = add_consignee(name, leGST.text().strip() or None, lePAN.text().strip() or None)
            dlg.accept()
            self.open_address_manager(cid)
            self.refresh_table()

        btn.clicked.connect(save)
        dlg.exec()

    # --------------------------------------------------
    def open_edit_dialog(self, consignee_id):
        data = get_consignee(consignee_id)
        if not data:
            return

        dlg = QtWidgets.QDialog(self)
        uic.loadUi(os.path.join(BASE_DIR, "ui", "customer_dialog.ui"), dlg)
        dlg.findChild(QtWidgets.QLabel, "labelTitle").setText("Edit Consignee")

        leName = dlg.findChild(QtWidgets.QLineEdit, "leName")
        leGST = dlg.findChild(QtWidgets.QLineEdit, "leGST")
        lePAN = dlg.findChild(QtWidgets.QLineEdit, "lePAN")
        btn = dlg.findChild(QtWidgets.QPushButton, "btnNext")

        leName.setText(data["name"])
        leGST.setText(data.get("gstin", ""))
        lePAN.setText(data.get("pan", ""))
        btn.setText("Save")

        def save():
            update_consignee(
                consignee_id,
                name=leName.text().strip(),
                gstin=leGST.text().strip() or None,
                pan=lePAN.text().strip() or None
            )
            dlg.accept()
            self.refresh_table()

        btn.clicked.connect(save)
        dlg.exec()

    # --------------------------------------------------
    def delete(self, consignee_id):
        q = QMessageBox.question(self, "Delete", "Delete consignee and addresses?")
        if q != QMessageBox.StandardButton.Yes:
            return
        delete_consignee(consignee_id)
        self.refresh_table()

    # --------------------------------------------------
    def open_address_manager(self, consignee_id):
        dlg = QtWidgets.QDialog(self)
        dlg.setWindowTitle("Manage Consignee Addresses")
        dlg.resize(700, 420)

        layout = QtWidgets.QVBoxLayout(dlg)

        btnAdd = QtWidgets.QPushButton("Add Address")
        layout.addWidget(btnAdd)

        table = QtWidgets.QTableWidget()
        table.setColumnCount(6)
        table.setHorizontalHeaderLabels(["ID", "Label", "Address", "State", "Default", "Actions"])
        layout.addWidget(table)

        def refresh():
            table.setRowCount(0)
            addrs = get_addresses_for_consignee(consignee_id)
            for r, a in enumerate(addrs):
                table.insertRow(r)
                table.setItem(r, 0, QTableWidgetItem(str(a["id"])))
                table.setItem(r, 1, QTableWidgetItem(a["label"]))
                table.setItem(r, 2, QTableWidgetItem(a["address"]))
                table.setItem(r, 3, QTableWidgetItem(a.get("state", "")))
                table.setItem(r, 4, QTableWidgetItem("Yes" if a["is_default"] else ""))

                btnDel = QtWidgets.QPushButton("Delete")
                btnDel.clicked.connect(lambda _, aid=a["id"]: (delete_address(aid), refresh()))

                table.setCellWidget(r, 5, btnDel)

            table.resizeColumnsToContents()

        def add_addr():
            adlg = QtWidgets.QDialog(self)
            uic.loadUi(os.path.join(BASE_DIR, "ui", "address_dialog.ui"), adlg)

            def save():
                add_consignee_address(
                    consignee_id,
                    adlg.findChild(QtWidgets.QLineEdit, "leLabel").text(),
                    adlg.findChild(QtWidgets.QTextEdit, "teAddress").toPlainText(),
                    adlg.findChild(QtWidgets.QLineEdit, "leState").text(),
                    adlg.findChild(QtWidgets.QLineEdit, "leStateCode").text(),
                    adlg.findChild(QtWidgets.QLineEdit, "lePincode").text(),
                    adlg.findChild(QtWidgets.QLineEdit, "leCountry").text(),
                    1 if adlg.findChild(QtWidgets.QCheckBox, "chkDefault").isChecked() else 0
                )
                adlg.accept()
                refresh()

            adlg.findChild(QtWidgets.QPushButton, "btnSave").clicked.connect(save)
            adlg.exec()

        btnAdd.clicked.connect(add_addr)
        refresh()
        dlg.exec()

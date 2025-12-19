# src/job_form.py
import os
from datetime import datetime
from PyQt6 import QtWidgets, QtCore, uic
from PyQt6.QtWidgets import QMessageBox

from database import (
    list_customers,
    get_addresses_for_customer,
    insert_job
)

from settings_manager import get_next_job_number

BASE_DIR = os.path.dirname(os.path.dirname(__file__))


class JobForm(QtWidgets.QWidget):
    # 🔔 Emitted when a job is successfully saved
    jobSaved = QtCore.pyqtSignal()

    def __init__(self):
        super().__init__()
        uic.loadUi(os.path.join(BASE_DIR, "ui", "job_form.ui"), self)

        # -------------------------------
        # Widgets
        # -------------------------------
        self.leJobNo = self.findChild(QtWidgets.QLineEdit, "leJobNo")

        self.cbCustomer = self.findChild(QtWidgets.QComboBox, "cbCustomer")
        self.cbAddress = self.findChild(QtWidgets.QComboBox, "cbAddress")

        # Shipment
        self.leShipper = self.findChild(QtWidgets.QLineEdit, "leShipper")
        self.leConsignee = self.findChild(QtWidgets.QLineEdit, "leConsignee")
        self.lePOL = self.findChild(QtWidgets.QLineEdit, "lePOL")
        self.lePOD = self.findChild(QtWidgets.QLineEdit, "lePOD")

        # Consignment
        self.leMBL = self.findChild(QtWidgets.QLineEdit, "leMBL")
        self.leGross = self.findChild(QtWidgets.QLineEdit, "leGross")

        self.btnSave = self.findChild(QtWidgets.QPushButton, "btnSave")

        # -------------------------------
        # Init
        # -------------------------------
        self.init_job()
        self.load_customers()

        # -------------------------------
        # Signals
        # -------------------------------
        self.cbCustomer.currentIndexChanged.connect(self.load_addresses)
        self.btnSave.clicked.connect(self.save_job)

    # ==================================================
    def init_job(self):
        self.leJobNo.setText(get_next_job_number())
        self.leJobNo.setReadOnly(True)

    # ==================================================
    def load_customers(self):
        self.cbCustomer.clear()
        self.cbCustomer.addItem("-- Select --", None)

        for c in list_customers():
            self.cbCustomer.addItem(c["name"], c["id"])

    # ==================================================
    def load_addresses(self):
        self.cbAddress.clear()
        self.cbAddress.addItem("-- Select --", None)

        cid = self.cbCustomer.currentData()
        if not cid:
            return

        for a in get_addresses_for_customer(cid):
            self.cbAddress.addItem(a["label"], a["id"])

    # ==================================================
    def save_job(self):
        if not self.cbCustomer.currentData():
            QMessageBox.warning(self, "Missing Data", "Please select a Customer.")
            return

        job_data = {
            # Canonical identifier
            "job_no": self.leJobNo.text(),

            # Foreign key
            "customer_id": self.cbCustomer.currentData(),

            # Shipment
            "shipper": self.leShipper.text().strip(),
            "consignee": self.leConsignee.text().strip(),
            "pol": self.lePOL.text().strip(),
            "pod": self.lePOD.text().strip(),

            # Consignment
            "mbl_no": self.leMBL.text().strip(),
            "gross_weight": self.leGross.text().strip(),

            # System
            "status": "OPEN",
            "created_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        }

        try:
            insert_job(job_data)
        except Exception as e:
            QMessageBox.critical(self, "Error", f"Failed to save job:\n{e}")
            return

        QMessageBox.information(self, "Success", "Job created successfully")

        # 🔔 Notify invoice / debit note pages
        self.jobSaved.emit()

        # -------------------------------
        # Reset for next job
        # -------------------------------
        self.leJobNo.setText(get_next_job_number())
        self.cbCustomer.setCurrentIndex(0)

        self.cbAddress.clear()
        self.cbAddress.addItem("-- Select --", None)

        self.leShipper.clear()
        self.leConsignee.clear()
        self.lePOL.clear()
        self.lePOD.clear()
        self.leMBL.clear()
        self.leGross.clear()

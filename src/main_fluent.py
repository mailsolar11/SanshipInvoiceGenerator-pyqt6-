
# src/main_fluent.py
import sys
from PyQt6 import QtWidgets, QtCore, QtGui
from qfluentwidgets import (
    FluentWindow, 
    FluentIcon as FIF, 
    NavigationItemPosition, 
    setTheme, 
    Theme,
    SplashScreen
)

# Pages
from invoice_form import InvoiceForm
from debitnote_form import DebitNoteForm
from customer_manager import ConsigneeManager
from job_form import JobForm
from reports_window import ReportsWindow

# DB
from database import init_db
from init_db import init_accounting_db


class MainWindow(FluentWindow):
    def __init__(self):
        super().__init__()

        # 1. Database Init
        init_db()
        init_accounting_db()

        # 2. Window Setup
        self.setWindowTitle("SANSHIP — Invoice & Debit Note Generator")
        self.setWindowIcon(QtGui.QIcon("assets/logo.png"))  # Placeholder if exists, else ignores
        self.resize(1300, 850)
        
        # Center on screen
        self.move(QtWidgets.QApplication.primaryScreen().availableGeometry().center() - self.rect().center())

        # 3. Theme Setup (Force Dark for now to match user pref)
        setTheme(Theme.DARK)

        # 4. Create Sub-Interfaces
        self.dashboard_interface = QtWidgets.QWidget() # Placeholder for "Home" if needed
        self.dashboard_interface.setObjectName("dashboardInterface")
        
        self.page_invoice = InvoiceForm()
        self.page_invoice.setObjectName("invoiceInterface")

        self.page_debit = DebitNoteForm()
        self.page_debit.setObjectName("debitInterface")

        self.page_customers = ConsigneeManager()
        self.page_customers.setObjectName("customerInterface")
        
        self.page_reports = ReportsWindow()
        self.page_reports.setObjectName("reportsInterface")

        # 5. Initialize Navigation
        self.init_navigation()

        # 6. Signal Connections
        self.connect_signals()

    def init_navigation(self):
        # We add sub-interfaces. The first one added becomes the default home page.
        
        # A. Invoice (Home)
        self.addSubInterface(
            self.page_invoice,
            FIF.DOCUMENT,
            "Create Invoice",
            selectedIcon=FIF.DOCUMENT,
        )

        # B. Debit Note
        self.addSubInterface(
            self.page_debit,
            FIF.EDIT,
            "Debit Note",
        )

        # C. Customers
        self.addSubInterface(
            self.page_customers,
            FIF.PEOPLE,
            "Customers",
        )

        # D. Reports
        self.addSubInterface(
            self.page_reports,
            FIF.PIE_SINGLE,
            "Reports",
        )

        # E. Create Job (Action Button - Bottom)
        # We use a custom widget or just a navigation item that triggers a function?
        # FluentWindow supports adding items that trigger callbacks.
        self.navigationInterface.addItem(
            routeKey="create_job_btn",
            icon=FIF.ADD,
            text="Create New Job",
            onClick=self.open_job_form,
            position=NavigationItemPosition.BOTTOM
        )

    def connect_signals(self):
        # -------------------------
        # CROSS-MODULE NAVIGATION
        # -------------------------
        # If Invoice page asks to open Customer Manager
        if hasattr(self.page_invoice, "openCustomerManager"):
            self.page_invoice.openCustomerManager.connect(
                lambda: self.switchTo(self.page_customers)
            )

        if hasattr(self.page_debit, "openCustomerManager"):
            self.page_debit.openCustomerManager.connect(
                lambda: self.switchTo(self.page_customers)
            )

    def open_job_form(self):
        self.job_window = JobForm()
        # Auto-refresh logic (same as old main.py)
        self.job_window.jobSaved.connect(self.page_invoice.load_jobs)
        self.job_window.jobSaved.connect(self.page_debit.load_jobs)
        self.job_window.show()


def main():
    # Helper to support high DPI
    QtCore.QCoreApplication.setAttribute(QtCore.Qt.ApplicationAttribute.AA_EnableHighDpiScaling)
    QtCore.QCoreApplication.setAttribute(QtCore.Qt.ApplicationAttribute.AA_UseHighDpiPixmaps)

    app = QtWidgets.QApplication(sys.argv)
    win = MainWindow()
    win.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()

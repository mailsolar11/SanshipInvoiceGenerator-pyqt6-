# src/main.py
import sys
from PyQt6 import QtWidgets, QtGui, QtCore

# Pages
from invoice_form import InvoiceForm
from debitnote_form import DebitNoteForm
from customer_manager import ConsigneeManager
from job_form import JobForm

from database import init_db


class MainWindow(QtWidgets.QMainWindow):
    def __init__(self):
        super().__init__()

        # Init DB
        init_db()

        self.setWindowTitle("SANSHIP — Invoice & Debit Note Generator")
        self.setMinimumSize(1360, 820)

        self.enable_dark_theme()

        # -------------------------
        # CENTRAL LAYOUT
        # -------------------------
        central = QtWidgets.QWidget()
        self.setCentralWidget(central)
        main_layout = QtWidgets.QHBoxLayout(central)
        main_layout.setContentsMargins(0, 0, 0, 0)

        # -------------------------
        # LEFT SIDEBAR
        # -------------------------
        sidebar = QtWidgets.QFrame()
        sidebar.setFixedWidth(240)
        sidebar.setObjectName("leftSidebar")
        sidebar.setStyleSheet("""
            QFrame#leftSidebar {
                background-color: #111217;
                border-right: 1px solid #2b2b2b;
            }
            QPushButton.menuButton {
                padding: 12px 14px;
                text-align: left;
                border-radius: 8px;
                font-size: 14px;
                color: #eaeaea;
                background: transparent;
            }
            QPushButton.menuButton:hover {
                background: #1e1e2a;
            }
            QLabel#logoLabel {
                color: white;
                font-weight: 700;
                font-size: 20px;
                padding: 12px;
            }
        """)

        menu_layout = QtWidgets.QVBoxLayout(sidebar)
        menu_layout.setContentsMargins(12, 12, 12, 12)
        menu_layout.setSpacing(8)

        logo = QtWidgets.QLabel("SANSHIP")
        logo.setObjectName("logoLabel")
        logo.setAlignment(QtCore.Qt.AlignmentFlag.AlignCenter)
        menu_layout.addWidget(logo)

        # -------------------------
        # MENU BUTTONS
        # -------------------------
        btn_invoice = QtWidgets.QPushButton("📄  Create Invoice")
        btn_invoice.setProperty("class", "menuButton")

        btn_debit = QtWidgets.QPushButton("🧾  Create Debit Note")
        btn_debit.setProperty("class", "menuButton")

        btn_job = QtWidgets.QPushButton("📦  Create Job")
        btn_job.setProperty("class", "menuButton")

        btn_customers = QtWidgets.QPushButton("👥  Customer / Consignee Manager")
        btn_customers.setProperty("class", "menuButton")

        btn_exit = QtWidgets.QPushButton("❌  Exit")
        btn_exit.setProperty("class", "menuButton")

        menu_layout.addWidget(btn_invoice)
        menu_layout.addWidget(btn_debit)
        menu_layout.addWidget(btn_job)
        menu_layout.addWidget(btn_customers)
        menu_layout.addStretch()
        menu_layout.addWidget(btn_exit)

        # -------------------------
        # STACKED PAGES
        # -------------------------
        self.stack = QtWidgets.QStackedWidget()

        self.page_invoice = InvoiceForm()
        self.page_debit = DebitNoteForm()
        self.page_customers = ConsigneeManager()

        self.stack.addWidget(self.page_invoice)    # index 0
        self.stack.addWidget(self.page_debit)      # index 1
        self.stack.addWidget(self.page_customers)  # index 2

        main_layout.addWidget(sidebar)
        main_layout.addWidget(self.stack, stretch=1)

        # -------------------------
        # MENU NAVIGATION
        # -------------------------
        btn_invoice.clicked.connect(lambda: self.stack.setCurrentIndex(0))
        btn_debit.clicked.connect(lambda: self.stack.setCurrentIndex(1))
        btn_customers.clicked.connect(lambda: self.stack.setCurrentIndex(2))
        btn_exit.clicked.connect(self.close)

        btn_job.clicked.connect(self.open_job_form)

        # -------------------------
        # SIGNAL-BASED NAVIGATION
        # -------------------------
        if hasattr(self.page_invoice, "openCustomerManager"):
            self.page_invoice.openCustomerManager.connect(
                lambda: self.stack.setCurrentIndex(2)
            )

        if hasattr(self.page_debit, "openCustomerManager"):
            self.page_debit.openCustomerManager.connect(
                lambda: self.stack.setCurrentIndex(2)
            )

        self.stack.setCurrentIndex(0)

    # -------------------------
    # OPEN JOB FORM (FIXED)
    # -------------------------
    def open_job_form(self):
        self.job_window = JobForm()

        # 🔥 CRITICAL FIX: auto-refresh job dropdowns
        self.job_window.jobSaved.connect(self.page_invoice.load_jobs)
        self.job_window.jobSaved.connect(self.page_debit.load_jobs)

        self.job_window.show()

    # -------------------------
    # DARK THEME
    # -------------------------
    def enable_dark_theme(self):
        app = QtWidgets.QApplication.instance()
        if not app:
            return

        app.setStyle("Fusion")
        palette = QtGui.QPalette()

        palette.setColor(QtGui.QPalette.ColorRole.Window, QtGui.QColor(28, 28, 28))
        palette.setColor(QtGui.QPalette.ColorRole.WindowText, QtGui.QColor(230, 230, 230))
        palette.setColor(QtGui.QPalette.ColorRole.Base, QtGui.QColor(35, 35, 35))
        palette.setColor(QtGui.QPalette.ColorRole.Text, QtGui.QColor(230, 230, 230))
        palette.setColor(QtGui.QPalette.ColorRole.Button, QtGui.QColor(45, 45, 45))
        palette.setColor(QtGui.QPalette.ColorRole.ButtonText, QtGui.QColor(230, 230, 230))
        palette.setColor(QtGui.QPalette.ColorRole.Highlight, QtGui.QColor(70, 120, 230))
        palette.setColor(QtGui.QPalette.ColorRole.HighlightedText, QtGui.QColor(255, 255, 255))

        app.setPalette(palette)


def main():
    app = QtWidgets.QApplication(sys.argv)
    win = MainWindow()
    win.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()

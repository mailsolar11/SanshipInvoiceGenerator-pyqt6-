
# src/main_beautiful.py
import sys
from PyQt6 import QtWidgets, QtCore, QtGui

# Theme
from themes.beautiful_theme import get_qss

# Database
from database import init_db
from init_db import init_accounting_db

# Pages
from invoice_form import InvoiceForm
from debitnote_form import DebitNoteForm
from customer_manager import ConsigneeManager
from job_form import JobForm
from reports_window import ReportsWindow
from reports_window import ReportsWindow
from ledger_view import LedgerView
from receipt_form import ReceiptForm

class MainWindow(QtWidgets.QMainWindow):
    def __init__(self):
        super().__init__()
        
        # 1. Init DB
        init_db()
        init_accounting_db()

        # 2. Window Properties
        self.setWindowTitle("SANSHIP — Invoice Generator")
        self.resize(1380, 850)
        
        # 3. Apply Initial Theme
        self.current_mode = "LIGHT" 
        self.apply_theme()

        # 4. Main Layout (Split Pane)
        central_widget = QtWidgets.QWidget()
        self.setCentralWidget(central_widget)
        
        self.main_layout = QtWidgets.QHBoxLayout(central_widget)
        self.main_layout.setContentsMargins(0, 0, 0, 0)
        self.main_layout.setSpacing(0)

        # =================================================
        # LEFT SIDEBAR
        # =================================================
        self.sidebar_frame = QtWidgets.QFrame()
        self.sidebar_frame.setObjectName("sidebarFrame")
        self.sidebar_frame.setFixedWidth(260)
        
        self.sidebar_layout = QtWidgets.QVBoxLayout(self.sidebar_frame)
        self.sidebar_layout.setContentsMargins(20, 20, 20, 20)
        self.sidebar_layout.setSpacing(8)

        # Logo
        self.logo = QtWidgets.QLabel("SANSHIP")
        self.logo.setObjectName("logoLabel")
        self.sidebar_layout.addWidget(self.logo)

        # Navigation Buttons
        self.btn_group = QtWidgets.QButtonGroup(self)
        self.btn_group.setExclusive(True)

        self.btn_invoice = self.create_nav_btn("Create Invoice", 0, checked=True)
        self.btn_debit = self.create_nav_btn("Debit Note", 1)
        self.btn_customers = self.create_nav_btn("Customers", 2)
        self.btn_ledger = self.create_nav_btn("Statement", 3)
        self.btn_ledger = self.create_nav_btn("Statement", 3)
        self.btn_receipt = self.create_nav_btn("Receipt Entry", 4)
        self.btn_reports = self.create_nav_btn("Reports", 5)
        self.btn_exit = self.create_nav_btn("Exit", -1)
        self.btn_exit.clicked.connect(self.close)

        self.sidebar_layout.addStretch()
        
        # Theme Toggle
        self.btn_theme = QtWidgets.QPushButton("Toggle Theme")
        self.btn_theme.setProperty("class", "navBtn")
        self.btn_theme.setCursor(QtCore.Qt.CursorShape.PointingHandCursor)
        self.btn_theme.clicked.connect(self.toggle_theme)
        self.sidebar_layout.addWidget(self.btn_theme)

        # "CTA" Style Button for Create Job
        self.btn_create_job = QtWidgets.QPushButton("CREATE NEW JOB")
        self.btn_create_job.setObjectName("ctaBtn")
        self.btn_create_job.setCursor(QtCore.Qt.CursorShape.PointingHandCursor)
        self.btn_create_job.clicked.connect(self.open_job_form)
        self.sidebar_layout.addWidget(self.btn_create_job)

        # =================================================
        # MAIN CONTENT AREA
        # =================================================
        self.content_area = QtWidgets.QWidget()
        self.content_area.setObjectName("contentArea")
        
        # We use a layout with padding to create the "floating card" effect
        self.content_layout = QtWidgets.QVBoxLayout(self.content_area)
        self.content_layout.setContentsMargins(30, 30, 30, 30)

        # Card Container (White Box)
        self.card_frame = QtWidgets.QFrame()
        self.card_frame.setProperty("class", "contentCard")
        # Add Drop Shadow to Card
        shadow = QtWidgets.QGraphicsDropShadowEffect()
        shadow.setBlurRadius(20)
        shadow.setColor(QtGui.QColor(0, 0, 0, 20))
        shadow.setOffset(0, 4)
        self.card_frame.setGraphicsEffect(shadow)

        self.card_layout = QtWidgets.QVBoxLayout(self.card_frame)
        self.card_layout.setContentsMargins(0, 0, 0, 0) # Page content fills the card

        # Stacked Pages
        self.stack = QtWidgets.QStackedWidget()
        
        self.page_invoice = InvoiceForm()
        self.page_debit = DebitNoteForm()
        self.page_customers = ConsigneeManager()
        self.page_ledger = LedgerView()
        self.page_receipt = ReceiptForm()
        self.page_reports = ReportsWindow()

        self.stack.addWidget(self.page_invoice)   # 0
        self.stack.addWidget(self.page_debit)     # 1
        self.stack.addWidget(self.page_customers) # 2
        self.stack.addWidget(self.page_ledger)    # 3
        self.stack.addWidget(self.page_receipt)   # 4
        self.stack.addWidget(self.page_reports)   # 5

        self.card_layout.addWidget(self.stack)

        self.content_layout.addWidget(self.card_frame)

        # Assemble Main Layout
        self.main_layout.addWidget(self.sidebar_frame)
        self.main_layout.addWidget(self.content_area)

        # Connect Signals
        self.connect_signals()

    def apply_theme(self):
        app = QtWidgets.QApplication.instance()
        # Modes: "DARK" <-> "LIGHT"
        app.setStyleSheet(get_qss(self.current_mode))

    def toggle_theme(self):
        if self.current_mode == "LIGHT":
            self.current_mode = "DARK"
        else:
            self.current_mode = "LIGHT"
            
        self.apply_theme()


    def create_nav_btn(self, text, index, checked=False):
        btn = QtWidgets.QPushButton(text)
        btn.setProperty("class", "navBtn")
        btn.setCheckable(True)
        btn.setChecked(checked)
        btn.setCursor(QtCore.Qt.CursorShape.PointingHandCursor)
        
        if index != -1:
            btn.clicked.connect(lambda: self.stack.setCurrentIndex(index))
            self.btn_group.addButton(btn)
        
        self.sidebar_layout.addWidget(btn)
        return btn

    def connect_signals(self):
        # Cross-module nav
        if hasattr(self.page_invoice, "openCustomerManager"):
            self.page_invoice.openCustomerManager.connect(
                lambda: self.switch_tab(2)
            )
        if hasattr(self.page_debit, "openCustomerManager"):
            self.page_debit.openCustomerManager.connect(
                lambda: self.switch_tab(2)
            )

    def switch_tab(self, index):
        self.stack.setCurrentIndex(index)
        # Update button state manually if needed (ButtonGroup handles exclusive, but we need to toggle the right one)
        btns = [self.btn_invoice, self.btn_debit, self.btn_customers, self.btn_ledger, self.btn_receipt, self.btn_reports]
        if 0 <= index < len(btns):
            btns[index].setChecked(True)

    def open_job_form(self):
        self.job_window = JobForm()
        # Fix styling for popup (Popups are separate windows, might need theme applied directly if not global)
        # Global theme is already applied to 'app', so it should inherit.
        
        self.job_window.jobSaved.connect(self.page_invoice.load_jobs)
        self.job_window.jobSaved.connect(self.page_debit.load_jobs)
        self.job_window.show()

def main():
    # High DPI
    if hasattr(QtCore.Qt.ApplicationAttribute, "AA_EnableHighDpiScaling"):
        QtCore.QCoreApplication.setAttribute(QtCore.Qt.ApplicationAttribute.AA_EnableHighDpiScaling)
    if hasattr(QtCore.Qt.ApplicationAttribute, "AA_UseHighDpiPixmaps"):
        QtCore.QCoreApplication.setAttribute(QtCore.Qt.ApplicationAttribute.AA_UseHighDpiPixmaps)

    app = QtWidgets.QApplication(sys.argv)
    window = MainWindow()
    window.show()
    sys.exit(app.exec())

if __name__ == "__main__":
    main()

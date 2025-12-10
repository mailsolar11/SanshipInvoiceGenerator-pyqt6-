import os
import sys
from PyQt6 import QtWidgets, uic
from PyQt6.QtCore import Qt
from database import init_db

BASE_DIR = os.path.dirname(os.path.dirname(__file__))


class MainWindow(QtWidgets.QMainWindow):
    def __init__(self):
        super().__init__()
        uic.loadUi(os.path.join(BASE_DIR, "ui", "main_window.ui"), self)

        # Sidebar buttons
        self.btn_dashboard = self.findChild(QtWidgets.QPushButton, "btn_dashboard")
        self.btn_invoice = self.findChild(QtWidgets.QPushButton, "btn_invoice")
        self.btn_debitnote = self.findChild(QtWidgets.QPushButton, "btn_debitnote")

        # Make them exclusive checkable
        self.btn_group = QtWidgets.QButtonGroup(self)
        for btn in (self.btn_dashboard, self.btn_invoice, self.btn_debitnote):
            self.btn_group.addButton(btn)
        self.btn_group.setExclusive(True)

        # Stacked pages
        self.stacked = self.findChild(QtWidgets.QStackedWidget, "stackedWidget")

        from dashboard import Dashboard
        from invoice_form import InvoiceForm
        from debitnote_form import DebitNoteForm

        self.page_dashboard = Dashboard()
        self.page_invoice = InvoiceForm()
        self.page_debit = DebitNoteForm()

        # Clear placeholder pages and add real ones
        while self.stacked.count() > 0:
            w = self.stacked.widget(0)
            self.stacked.removeWidget(w)
            w.deleteLater()

        self.stacked.addWidget(self.page_dashboard)  # 0
        self.stacked.addWidget(self.page_invoice)    # 1
        self.stacked.addWidget(self.page_debit)      # 2

        self.btn_dashboard.clicked.connect(lambda: self.stacked.setCurrentIndex(0))
        self.btn_invoice.clicked.connect(lambda: self.stacked.setCurrentIndex(1))
        self.btn_debitnote.clicked.connect(lambda: self.stacked.setCurrentIndex(2))

        self.stacked.setCurrentIndex(0)


def main():
    init_db()
    app = QtWidgets.QApplication(sys.argv)

    # Apply dark theme
    qss_path = os.path.join(BASE_DIR, "themes", "dark.qss")
    if os.path.exists(qss_path):
        with open(qss_path, "r", encoding="utf-8") as f:
            app.setStyleSheet(f.read())

    window = MainWindow()
    window.setWindowFlag(Qt.WindowType.WindowMinimizeButtonHint, True)
    window.setWindowFlag(Qt.WindowType.WindowMaximizeButtonHint, True)
    window.showMaximized()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()

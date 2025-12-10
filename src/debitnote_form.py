import os
from PyQt6 import QtWidgets

# For now we reuse invoice layout; later we can customise.
from PyQt6 import uic

BASE_DIR = os.path.dirname(os.path.dirname(__file__))


class DebitNoteForm(QtWidgets.QWidget):
    def __init__(self):
        super().__init__()
        uic.loadUi(os.path.join(BASE_DIR, "ui", "invoice_form.ui"), self)

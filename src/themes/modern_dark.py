
# src/themes/modern_dark.py

QSS = """
/* =======================================================
   GLOBAL
   ======================================================= */
QWidget {
    background-color: #1e1e1e;
    color: #e0e0e0;
    font-family: 'Segoe UI', 'Roboto', sans-serif;
    font-size: 14px;
}

QMainWindow {
    background-color: #1e1e1e;
}

/* =======================================================
   INPUT CONTROLS
   ======================================================= */
QLineEdit, QDateEdit, QComboBox, QSpinBox, QDoubleSpinBox, QTextEdit {
    background-color: #252526;
    border: 1px solid #3e3e42;
    border-radius: 4px;
    padding: 8px 10px;
    color: #e0e0e0;
    selection-background-color: #264f78;
}

QLineEdit:focus, QDateEdit:focus, QComboBox:focus, QTextEdit:focus {
    border: 1px solid #007acc;
    background-color: #2d2d30;
}

QLineEdit:hover, QDateEdit:hover, QComboBox:hover {
    border: 1px solid #555555;
}

QComboBox::drop-down {
    subcontrol-origin: padding;
    subcontrol-position: top right;
    width: 20px;
    border-left-width: 0px;
    border-top-right-radius: 3px;
    border-bottom-right-radius: 3px;
}

/* =======================================================
   BUTTONS (Generic)
   ======================================================= */
QPushButton {
    background-color: #3a3a3c;
    color: #ffffff;
    border: 1px solid #3e3e42;
    padding: 8px 16px;
    border-radius: 4px;
    font-weight: 600;
}

QPushButton:hover {
    background-color: #454546;
    border-color: #555555;
}

QPushButton:pressed {
    background-color: #007acc;
    border-color: #007acc;
}

QPushButton:disabled {
    background-color: #252526;
    color: #666666;
    border-color: #333333;
}

/* Primary Action Button Style (Assign objectName='btnPrimary') */
QPushButton#btnPrimary {
    background-color: #007acc;
    border: 1px solid #007acc;
}
QPushButton#btnPrimary:hover {
    background-color: #0062a3;
}

/* =======================================================
   TABLES
   ======================================================= */
QTableWidget {
    background-color: #252526;
    gridline-color: #3e3e42;
    border: 1px solid #3e3e42;
    border-radius: 4px;
    selection-background-color: #264f78;
    selection-color: white;
}

QHeaderView::section {
    background-color: #333337;
    color: #cccccc;
    padding: 8px;
    border: none;
    border-right: 1px solid #3e3e42;
    border-bottom: 1px solid #3e3e42;
    font-weight: bold;
}

QTableCornerButton::section {
    background-color: #333337;
    border: none;
}

/* =======================================================
   SCROLLBARS
   ======================================================= */
QScrollBar:vertical {
    border: none;
    background: #1e1e1e;
    width: 12px;
    margin: 0px;
}
QScrollBar::handle:vertical {
    background: #424242;
    min-height: 20px;
    border-radius: 6px;
    margin: 2px;
}
QScrollBar::handle:vertical:hover {
    background: #686868;
}
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {
    height: 0px;
}
/* Horizontal */
QScrollBar:horizontal {
    border: none;
    background: #1e1e1e;
    height: 12px;
    margin: 0px;
}
QScrollBar::handle:horizontal {
    background: #424242;
    min-width: 20px;
    border-radius: 6px;
    margin: 2px;
}

/* =======================================================
   MENU / SIDEBAR (Special Ids)
   ======================================================= */
/* See main.py for Sidebar specific overrides */
"""

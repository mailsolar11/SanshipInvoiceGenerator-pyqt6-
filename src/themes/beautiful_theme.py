def get_qss(mode="LIGHT"):
    """
    mode: "DARK" or "LIGHT"
    """
    
    # =======================================================
    # STRICT PALETTE SEPARATION
    # =======================================================
    if mode == "DARK":
        # DARK MODE PALETTE
        bg_main       = "#121212"  # Deep Black
        bg_sidebar    = "#1E1E1E"  # Dark Grey
        bg_card       = "#1E1E1E"  # Dark Grey Card
        
        text_main     = "#FFFFFF"  # Pure White
        text_label    = "#B0B0B0"  # Light Grey
        text_inverse  = "#000000"  # Black (for buttons/highlights)
        
        border        = "#333333"  # Dark Border
        
        input_bg      = "#2D2D30"  # Lighter Grey Input
        input_text    = "#FFFFFF"  # White Text
        
        primary_color = "#BB86FC"  # Purple Accent
        primary_hover = "#3700B3"
        
        tab_selected_bg = "#333333"
        tab_selected_text = "#FFFFFF"
        
        table_header_bg = "#2D2D30"
        table_header_text = "#FFFFFF"
        table_alt_row = "#252526"
        
        sidebar_text       = "#A0A0A0"
        sidebar_hover      = "rgba(255, 255, 255, 0.08)"
        sidebar_checked_bg = "rgba(255, 255, 255, 0.12)"
        sidebar_checked_text = "#FFFFFF"

        scrollbar_handle = "#555555"
        scrollbar_hover  = "#777777"

    else: 
        # LIGHT MODE PALETTE
        bg_main       = "#F3F3F3"  # Light Grey Background
        bg_sidebar    = "#FFFFFF"  # White Sidebar
        bg_card       = "#FFFFFF"  # White Card
        
        text_main     = "#000000"  # Pure Black
        text_label    = "#555555"  # Dark Grey
        text_inverse  = "#FFFFFF"  # White
        
        border        = "#E0E0E0"  # Softer Light Border
        
        input_bg      = "#FFFFFF"  # White Input
        input_text    = "#000000"  # Black Text
        
        primary_color = "#0078D4"  # Blue Accent
        primary_hover = "#005A9E"
        
        tab_selected_bg = "#0078D4"
        tab_selected_text = "#FFFFFF"
        
        table_header_bg = "#F9F9F9"
        table_header_text = "#000000"
        table_alt_row = "#F3F3F3"
        
        sidebar_text       = "#333333"
        sidebar_hover      = "#F0F0F0"
        sidebar_checked_bg = "#E0E0E0"
        sidebar_checked_text = "#000000"

        scrollbar_handle = "#C0C0C0"
        scrollbar_hover  = "#A0A0A0"


    return f"""
/* =======================================================
   GLOBAL RESET
   ======================================================= */
* {{
    outline: none;
    font-family: 'Segoe UI', 'Roboto', sans-serif;
    font-size: 14px;
}}

QWidget {{
    color: {text_main};
    background-color: {bg_main};
}}

/* =======================================================
   SIDEBAR
   ======================================================= */
QFrame#sidebarFrame {{
    background-color: {bg_sidebar};
    border-right: 1px solid {border};
}}

QFrame#sidebarFrame QLabel {{
    background-color: transparent;
    color: {sidebar_text};
}}

QLabel#logoLabel {{
    color: {sidebar_text};
    font-size: 20px;
    font-weight: 700;
    padding: 24px 0;
    letter-spacing: 1px;
}}

QFrame#sidebarFrame QPushButton {{
    color: {sidebar_text};
    text-align: left;
    background-color: transparent;
    border: none;
    padding: 12px 16px;
    font-weight: 600;
    font-size: 13px;
    text-transform: uppercase;
}}

QFrame#sidebarFrame QPushButton:hover {{
    color: {sidebar_checked_text};
    background-color: {sidebar_hover};
    border-radius: 4px;
}}

QFrame#sidebarFrame QPushButton:checked {{
    color: {sidebar_checked_text};
    background-color: {sidebar_checked_bg};
    border-left: 4px solid {primary_color};
}}

/* CTA Button */
QPushButton#ctaBtn {{
    background: {primary_color};
    color: {text_inverse};
    font-weight: bold;
    border-radius: 4px;
    padding: 12px;
    text-align: center;
    font-size: 14px;
    text-transform: none;
}}
QPushButton#ctaBtn:hover {{
    background-color: {primary_hover};
}}

/* =======================================================
   CONTENT AREA
   ======================================================= */
QWidget#contentArea {{
    background-color: {bg_main}; 
}}

/* The Card Container */
QFrame.contentCard {{
    background-color: {bg_card};
    border-radius: 8px;
    border: 1px solid {border};
}}

QFrame.contentCard QWidget {{
    background-color: transparent;
    color: {text_main};
}}

/* =======================================================
   INPUTS (Text, Combo, Date)
   ======================================================= */
QLineEdit, QDateEdit, QComboBox, QSpinBox, QTextEdit, QPlainTextEdit {{
    background-color: {input_bg};
    border: 1px solid {border};
    color: {input_text};
    border-radius: 4px;
    padding: 6px;
    selection-background-color: {primary_color};
    selection-color: {text_inverse};
}}

QLineEdit:focus, QDateEdit:focus, QComboBox:focus {{
    border: 1px solid {primary_color};
}}

/* Dropdown List */
QComboBox QAbstractItemView {{
    background-color: {input_bg};
    color: {input_text};
    border: 1px solid {border};
    selection-background-color: {primary_color};
    selection-color: {text_inverse};
    outline: none;
}}

/* Combo Arrow */
QComboBox::drop-down {{
    subcontrol-origin: padding;
    subcontrol-position: top right;
    width: 20px;
    border-left-width: 0px;
    border-top-right-radius: 3px;
    border-bottom-right-radius: 3px;
}} 
QComboBox::down-arrow {{
    image: url(none);
    border-left: 5px solid transparent;
    border-right: 5px solid transparent;
    border-top: 5px solid {text_label};
    margin-right: 5px;
}}

/* =======================================================
   CALENDAR WIDGET (Popups)
   ======================================================= */
QCalendarWidget QWidget {{
    background-color: {bg_card}; 
    color: {text_main};
}}
QCalendarWidget QToolButton {{
    color: {text_main};
    icon-size: 20px;
    background-color: transparent;
}}
QCalendarWidget QMenu {{
    background-color: {bg_card};
    color: {text_main};
}}
QCalendarWidget QSpinBox {{
    background-color: {input_bg};
    color: {input_text};
}}
QCalendarWidget QAbstractItemView:enabled {{
    color: {text_main};
    background-color: {bg_card};
    selection-background-color: {primary_color};
    selection-color: {text_inverse};
}}

/* =======================================================
   MENUS
   ======================================================= */
QMenu {{
    background-color: {bg_card};
    color: {text_main};
    border: 1px solid {border};
}}
QMenu::item {{
    padding: 5px 20px;
}}
QMenu::item:selected {{
    background-color: {primary_color};
    color: {text_inverse};
}}

/* =======================================================
   TABS (QTabWidget)
   ======================================================= */
QTabWidget::pane {{
    border: 1px solid {border};
    background-color: {bg_card};
    border-radius: 4px;
}}
QTabBar::tab {{
    background: {bg_main};
    color: {text_label};
    padding: 8px 20px;
    border-top-left-radius: 4px;
    border-top-right-radius: 4px;
    margin-right: 2px;
}}
QTabBar::tab:selected {{
    background: {tab_selected_bg};
    color: {tab_selected_text};
    font-weight: bold;
}}
QTabBar::tab:!selected:hover {{
    background: {sidebar_hover};
    color: {text_main};
}}

/* =======================================================
   TABLES
   ======================================================= */
QTableWidget {{
    background-color: {input_bg};
    alternate-background-color: {table_alt_row};
    color: {text_main};
    gridline-color: {border};
    border: 1px solid {border};
    selection-background-color: {primary_color};
    selection-color: {text_inverse};
}}

QHeaderView::section {{
    background-color: {table_header_bg};
    color: {table_header_text};
    padding: 8px;
    border: none;
    border-bottom: 2px solid {border};
    font-weight: bold;
}}

QTableWidget::item {{
    padding: 4px;
}}

/* =======================================================
   DIALOGS & MESSAGE BOXES
   ======================================================= */
QDialog, QMessageBox {{
    background-color: {bg_card};
    color: {text_main};
}}

QMessageBox QWidget {{
    background-color: {bg_card};
    color: {text_main};
}}

QFrame#frameCard {{
    background-color: {bg_card};
    border-radius: 12px;
    border: 1px solid {border};
}}

QLabel {{
    color: {text_main};
    background-color: transparent;
}}

/* Standard Buttons */
QPushButton {{
    background-color: {input_bg};
    border: 1px solid {border};
    color: {text_main};
    border-radius: 4px;
    padding: 6px 12px;
    min-width: 80px;
}}
QPushButton:hover {{
    background-color: {sidebar_hover};
    border-color: {primary_color};
}}

/* =======================================================
   MODERN FADED SCROLLBARS
   ======================================================= */
QScrollBar:vertical {{
    border: none;
    background: transparent;
    width: 10px;
    margin: 0px 0px 0px 0px;
}}
QScrollBar::handle:vertical {{
    background: {scrollbar_handle};
    min-height: 20px;
    border-radius: 5px;
}}
QScrollBar::handle:vertical:hover {{
    background: {scrollbar_hover};
}}
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
    border: none;
    background: none;
}}
QScrollBar::add-page:vertical, QScrollBar::sub-page:vertical {{
    background: none;
}}

QScrollBar:horizontal {{
    border: none;
    background: transparent;
    height: 10px;
    margin: 0px 0px 0px 0px;
}}
QScrollBar::handle:horizontal {{
    background: {scrollbar_handle};
    min-width: 20px;
    border-radius: 5px;
}}
QScrollBar::handle:horizontal:hover {{
    background: {scrollbar_hover};
}}
QScrollBar::add-line:horizontal, QScrollBar::sub-line:horizontal {{
    border: none;
    background: none;
}}
QScrollBar::add-page:horizontal, QScrollBar::sub-page:horizontal {{
    background: none;
}}
"""

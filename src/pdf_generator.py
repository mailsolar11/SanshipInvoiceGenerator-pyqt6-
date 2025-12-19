# src/pdf_generator.py
# GRID-EXACT PDF Generator (Excel-aligned)
# SAN SHIPPING AND LOGISTICS (INDIA) PVT LTD

import os
from datetime import datetime
from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.pdfgen import canvas

BASE_DIR = os.path.dirname(os.path.dirname(__file__))
OUT_DIR = os.path.join(BASE_DIR, "exports")
os.makedirs(OUT_DIR, exist_ok=True)

PAGE_W, PAGE_H = A4
MARGIN = 36

# ===== GRID CONFIG =====
TABLE_ROWS = 12
ROW_HEIGHT = 20

COLS = [
    ("Sr", 30),
    ("Charges Details", 200),
    ("HSN", 45),
    ("CUR", 40),
    ("Rate", 55),
    ("Qty", 40),
    ("Amount", 70),
    ("Taxable", 70),
]

TOTALS_WIDTH = 240

# =======================

def money(v):
    try:
        return f"{float(v):,.2f}"
    except:
        return "0.00"


def generate_invoice_pdf(header, items, title="TAX INVOICE"):
    ts = int(datetime.now().timestamp())
    inv = header.get("invoice_number", f"INV-{ts}").replace("/", "_")
    path = os.path.join(OUT_DIR, f"{inv}.pdf")

    c = canvas.Canvas(path, pagesize=A4)

    x0 = MARGIN
    y = PAGE_H - MARGIN

    # ======================================================
    # HEADER
    # ======================================================
    c.setFont("Times-Bold", 14)
    c.drawCentredString(PAGE_W / 2, y, "SAN SHIPPING AND LOGISTICS (INDIA) PVT LTD")
    y -= 16

    c.setFont("Times-Roman", 9)
    c.drawCentredString(PAGE_W / 2, y, "International Freight Forwarding Company")
    y -= 18

    c.setFont("Times-Bold", 12)
    c.drawCentredString(PAGE_W / 2, y, title)
    y -= 20

    # Invoice No / Date
    c.setFont("Times-Bold", 9)
    c.rect(x0, y - 18, PAGE_W - 2 * MARGIN, 18)
    c.drawString(x0 + 6, y - 13, f"Invoice No: {header.get('invoice_number', '')}")
    c.drawRightString(PAGE_W - MARGIN - 6, y - 13, f"Date: {header.get('date', '')}")
    y -= 30

    # ======================================================
    # BILL TO / CONSIGNMENT
    # ======================================================
    box_h = 120
    left_w = (PAGE_W - 2 * MARGIN) * 0.55
    right_w = (PAGE_W - 2 * MARGIN) - left_w

    c.rect(x0, y - box_h, left_w, box_h)
    c.rect(x0 + left_w, y - box_h, right_w, box_h)

    c.setFont("Times-Bold", 9)
    c.drawString(x0 + 6, y - 14, "BILL TO")
    c.drawString(x0 + left_w + 6, y - 14, "CONSIGNMENT DETAILS")

    c.setFont("Times-Roman", 8)
    bt_y = y - 28
    for ln in (header.get("bill_to") or "").split("\n"):
        c.drawString(x0 + 6, bt_y, ln)
        bt_y -= 10

    cd_y = y - 28
    cons_fields = [
        ("Job No", header.get("job_no")),
        ("MBL No", header.get("mbl_no")),
        ("Gross Wt", header.get("gross_weight")),
        ("Net Wt", header.get("net_weight")),
        ("Packages", header.get("packages")),
        ("Volume", header.get("volume_cbm")),
        ("Ref No", header.get("ref_no")),
    ]
    for k, v in cons_fields:
        c.drawString(x0 + left_w + 6, cd_y, f"{k}: {v or ''}")
        cd_y -= 10

    y -= box_h + 14

    # ======================================================
    # TABLE GRID (FIXED HEIGHT)
    # ======================================================
    table_x = x0
    table_w = PAGE_W - 2 * MARGIN - TOTALS_WIDTH

    # Column X
    col_x = [table_x]
    for _, w in COLS:
        col_x.append(col_x[-1] + w)

    # Header
    c.setFont("Times-Bold", 8)
    c.rect(table_x, y - ROW_HEIGHT, table_w, ROW_HEIGHT)
    for i, (t, _) in enumerate(COLS):
        c.drawCentredString((col_x[i] + col_x[i + 1]) / 2, y - 14, t)
        c.line(col_x[i], y, col_x[i], y - ROW_HEIGHT)
    c.line(col_x[-1], y, col_x[-1], y - ROW_HEIGHT)

    # Rows (EXACT COUNT)
    c.setFont("Times-Roman", 8)
    start_y = y - ROW_HEIGHT
    for r in range(TABLE_ROWS):
        row_y = start_y - r * ROW_HEIGHT
        c.rect(table_x, row_y - ROW_HEIGHT, table_w, ROW_HEIGHT)
        for x in col_x:
            c.line(x, row_y, x, row_y - ROW_HEIGHT)

        if r < len(items):
            it = items[r]
            vals = [
                it.get("sr_no"),
                it.get("description"),
                it.get("hsn_sac"),
                it.get("cur"),
                money(it.get("rate")),
                money(it.get("qty")),
                money(it.get("amount")),
                money(it.get("taxable_amount")),
            ]
            for i, v in enumerate(vals):
                c.drawString(col_x[i] + 3, row_y - 14, str(v or ""))

    table_bottom = start_y - TABLE_ROWS * ROW_HEIGHT

    # ======================================================
    # TOTALS (GRID-LOCKED)
    # ======================================================
    tx = table_x + table_w
    ty = y - ROW_HEIGHT

    c.rect(tx, ty - 4 * ROW_HEIGHT, TOTALS_WIDTH, 4 * ROW_HEIGHT)

    totals = {
        "Taxable Value": sum(float(i.get("taxable_amount", 0) or 0) for i in items),
        "Total CGST": sum(float(i.get("cgst_amt", 0) or 0) for i in items),
        "Total SGST": sum(float(i.get("sgst_amt", 0) or 0) for i in items),
        "GRAND TOTAL": sum(float(i.get("total_amt", 0) or 0) for i in items),
    }

    c.setFont("Times-Bold", 9)
    yy = ty - 14
    for k, v in totals.items():
        c.drawString(tx + 6, yy, k)
        c.drawRightString(tx + TOTALS_WIDTH - 6, yy, money(v))
        yy -= ROW_HEIGHT

    # ======================================================
    # FOOTER
    # ======================================================
    fy = table_bottom - 40
    c.setFont("Times-Roman", 8)
    c.drawString(x0, fy, "This is a computer generated invoice and does not require signature.")
    fy -= 12
    c.drawString(x0, fy, "Bank Details (Sample):")
    fy -= 10
    c.drawString(x0, fy, "Bank: SAMPLE BANK | A/C No: XXXXXXXXXX | IFSC: SAMPLE0001")

    c.setFont("Times-Bold", 9)
    c.drawRightString(PAGE_W - MARGIN, fy, "For SAN SHIPPING AND LOGISTICS (INDIA) PVT LTD")
    c.drawRightString(PAGE_W - MARGIN, fy - 18, "Authorised Signatory")

    c.showPage()
    c.save()
    return path

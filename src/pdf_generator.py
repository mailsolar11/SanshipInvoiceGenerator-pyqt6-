import os
import math
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.units import mm

BASE_DIR = os.path.dirname(os.path.dirname(__file__))


def draw_box(c, x, y, w, h, stroke=1):
    c.setLineWidth(stroke)
    c.rect(x, y, w, h)


def draw_wrapped(c, text, x, y, max_width, leading=12, fontname="Helvetica", fontsize=9):
    """
    Draw multi-line text wrapped to max_width. Returns last Y used.
    """
    from reportlab.pdfbase.pdfmetrics import stringWidth
    words = text.split()
    line = ""
    cur_y = y
    c.setFont(fontname, fontsize)
    for word in words:
        test = (line + " " + word).strip()
        if stringWidth(test, fontname, fontsize) <= max_width:
            line = test
        else:
            c.drawString(x, cur_y, line)
            cur_y -= leading
            line = word
    if line:
        c.drawString(x, cur_y, line)
        cur_y -= leading
    return cur_y


def generate_invoice_pdf(header, items):
    """
    Generates a full invoice PDF with bill-to, shipment & consignment details above table,
    table with borders, totals, and footer. Supports multiple pages if many rows.
    """
    out_dir = os.path.join(BASE_DIR, "output")
    os.makedirs(out_dir, exist_ok=True)

    inv_no_safe = header.get("invoice_number", f"INV-{int(os.times()[4])}")
    filename = f"{inv_no_safe}.pdf"
    path = os.path.join(out_dir, filename)

    c = canvas.Canvas(path, pagesize=A4)
    width, height = A4

    # Margins
    left_margin = 18 * mm
    right_margin = 18 * mm
    top_margin = 18 * mm
    bottom_margin = 18 * mm

    # Page content area
    page_width = width - left_margin - right_margin
    page_height = height - top_margin - bottom_margin

    # Helper to start a new page and redraw header boxes (not repeating logo/title to keep simple)
    def new_page():
        c.showPage()
        # You can re-draw page outer border if desired
        draw_box(c, left_margin / 2, bottom_margin / 2, width - left_margin, height - bottom_margin, stroke=1)

    # Draw outer border once on first page
    draw_box(c, left_margin / 2, bottom_margin / 2, width - left_margin, height - bottom_margin, stroke=1)

    # Y start from top
    y = height - top_margin

    # Header area: logo (left), title center, invoice meta (right)
    header_height = 36 * mm
    logo_w = 36 * mm
    logo_h = 20 * mm

    # Logo box
    logo_x = left_margin + 4
    logo_y = y - logo_h
    draw_box(c, logo_x, logo_y - 2, logo_w, logo_h, stroke=1)
    c.setFont("Helvetica-Bold", 10)
    c.drawCentredString(logo_x + logo_w / 2, logo_y + logo_h / 2 - 4, "LOGO")

    # Title
    c.setFont("Helvetica-Bold", 18)
    c.drawCentredString(width / 2, y - 8, "TAX INVOICE")

    # Invoice meta box (right)
    meta_w = 60 * mm
    meta_h = logo_h + 6
    meta_x = width - right_margin - meta_w
    meta_y = y - 6
    draw_box(c, meta_x, meta_y - meta_h, meta_w, meta_h, stroke=1)
    c.setFont("Helvetica", 9)
    meta_text_x = meta_x + 6
    meta_text_y = meta_y - 12
    c.drawString(meta_text_x, meta_text_y, f"Invoice No: {header.get('invoice_number','')}")
    c.drawString(meta_text_x, meta_text_y - 12, f"Date: {header.get('date','')}")

    # Move Y below header
    y = logo_y - 8

    # Two-column area: Left (bill-to + shipping), Right (consignment)
    col_gap = 8 * mm
    left_col_w = (page_width - col_gap) * 0.55
    right_col_w = (page_width - col_gap) * 0.45
    left_x = left_margin
    right_x = left_x + left_col_w + col_gap

    # Draw Bill To box
    bill_h = 42 * mm
    draw_box(c, left_x, y - bill_h, left_col_w, bill_h, stroke=1)
    c.setFont("Helvetica-Bold", 10)
    c.drawString(left_x + 6, y - 14, "BILL TO")
    # Bill-to text (wrapped)
    bill_text = header.get("bill_to", "")
    cur_y = y - 28
    c.setFont("Helvetica", 9)
    # allow up to left_col_w - 12 width
    cur_y = draw_wrapped(c, bill_text, left_x + 6, cur_y, left_col_w - 12, leading=12, fontsize=9)
    # If draw_wrapped returns low y, ensure we don't overlap
    y_after_bill = y - bill_h

    # Draw Consignment box (right column top)
    cons_h = bill_h
    draw_box(c, right_x, y - cons_h, right_col_w, cons_h, stroke=1)
    c.setFont("Helvetica-Bold", 10)
    c.drawString(right_x + 6, y - 14, "DETAILS ABOUT CONSIGNMENT")
    c.setFont("Helvetica", 8)
    tx = right_x + 6
    ty = y - 28

    # write all consignment fields in the right box (two-column style inside box)
    cons_fields = [
        ("Date", header.get("c_date", "")),
        ("Invoice no", header.get("c_invoice_no", "")),
        ("MBL no", header.get("mbl_no", "")),
        ("Job no", header.get("job_no", "")),
        ("Gross wt", header.get("gross_weight", "")),
        ("Net wt", header.get("net_weight", "")),
        ("Volume (CBM)", header.get("volume_cbm", "")),
        ("Packages", header.get("packages", "")),
        ("BE/SB no", header.get("be_no", "")),
        ("BE Date", header.get("be_date", "")),
        ("IGM no", header.get("igm_no", "")),
        ("IGM Date", header.get("igm_date", "")),
        ("Item no", header.get("item_no", "")),
        ("Ex. Rate", header.get("exchange_rate", "")),
        ("Ref no", header.get("ref_no", ""))
    ]
    # print in two columns inside right box
    small_gap = 3 * mm
    col1_x = tx
    col2_x = tx + (right_col_w / 2)
    row_y = ty
    c.setFont("Helvetica", 8)
    for i, (label, val) in enumerate(cons_fields):
        cx = col1_x if (i % 2 == 0) else col2_x
        c.drawString(cx, row_y, f"{label}: {val}")
        if i % 2 == 1:
            row_y -= 12
    # if odd number, step one line
    if len(cons_fields) % 2 == 1:
        row_y -= 12

    # Move Y to below these boxes
    current_y = min(y_after_bill, row_y) - 12

    # Draw Shipment details box under Bill To (left column, below bill box)
    ship_h = 48 * mm
    draw_box(c, left_x, current_y - ship_h, left_col_w, ship_h, stroke=1)
    c.setFont("Helvetica-Bold", 10)
    c.drawString(left_x + 6, current_y - 12, "SHIPMENT DETAILS")
    c.setFont("Helvetica", 9)
    sx = left_x + 6
    sy = current_y - 28
    ship_fields = [
        ("Shipper", header.get("shipper", "")),
        ("Consignee", header.get("ship_consigne", "")),
        ("POL", header.get("pol", "")),
        ("POD", header.get("pod", "")),
        ("Vessel/Flight", header.get("vessel_flight", "")),
        ("ETD", header.get("etd", "")),
        ("ETA", header.get("eta", ""))
    ]
    # list them vertically
    for label, val in ship_fields:
        draw_wrapped(c, f"{label}: {val}", sx, sy, left_col_w - 12, leading=12, fontsize=9)
        sy -= 12

    # Draw Consignee preview below consignment box (right column, same level as shipping or slightly lower)
    cons_preview_h = 30 * mm
    right_preview_y = current_y - cons_preview_h
    draw_box(c, right_x, right_preview_y, right_col_w, cons_preview_h, stroke=1)
    c.setFont("Helvetica-Bold", 10)
    c.drawString(right_x + 6, right_preview_y + cons_preview_h - 12, "CONSIGNEE")
    c.setFont("Helvetica", 9)
    draw_wrapped(c, header.get("consignee_preview", ""), right_x + 6, right_preview_y + cons_preview_h - 26, right_col_w - 12, leading=12, fontsize=9)

    # Now prepare the table area starting below the shipment/consignee boxes
    table_top_y = current_y - ship_h - 12 - 10  # small gap
    # Table column widths (same as UI columns)
    col_widths = [18*mm, 60*mm, 24*mm, 18*mm, 22*mm, 18*mm, 26*mm, 30*mm, 20*mm, 26*mm, 20*mm, 26*mm, 30*mm]
    table_x = left_x
    table_w = sum(col_widths)
    # If table width exceeds right side, we keep it starting at left_x; it may overflow into right area on wide pages - this mimics earlier behavior.
    # Draw header row box
    header_h = 10 * mm
    draw_box(c, table_x, table_top_y - header_h, table_w, header_h, stroke=1)
    # header labels
    labels = ["Sr", "Charges Details", "HSN/SAC", "CUR", "Rate", "Qty", "Amount (CUR)", "Taxable Amount", "CGST %", "CGST Amt", "SGST %", "SGST Amt", "Total Amount"]
    c.setFont("Helvetica-Bold", 8)
    hx = table_x + 4
    hy = table_top_y - 7 * mm
    for i, lab in enumerate(labels):
        c.drawString(hx, hy, lab)
        hx += col_widths[i]

    # Draw rows. Start just under header
    row_y = table_top_y - header_h
    c.setFont("Helvetica", 8)
    # compute totals
    subtotal = 0.0
    total_cgst = 0.0
    total_sgst = 0.0
    grand_total = 0.0

    # Iterate rows
    for it in items:
        # row height
        row_h = 8 * mm
        # check page break
        if row_y - row_h < bottom_margin + 40 * mm:  # leave space for totals/footer
            c.showPage()
            # redraw border and header lines minimal on new page (optional)
            draw_box(c, left_margin / 2, bottom_margin / 2, width - left_margin, height - bottom_margin, stroke=1)
            # reset position
            row_y = height - top_margin - 40
            # redraw table header on new page
            draw_box(c, table_x, row_y - header_h, table_w, header_h, stroke=1)
            c.setFont("Helvetica-Bold", 8)
            hx = table_x + 4
            hy = row_y - 7 * mm
            for i, lab in enumerate(labels):
                c.drawString(hx, hy, lab)
                hx += col_widths[i]
            row_y = row_y - header_h

        # draw row box
        draw_box(c, table_x, row_y - row_h, table_w, row_h, stroke=1)
        # write values
        cx = table_x + 4
        vals = [
            str(it.get("sr_no", "")),
            str(it.get("description", ""))[:60],
            str(it.get("hsn_sac", "")),
            str(it.get("cur", "")),
            f"{it.get('rate', 0):.2f}",
            f"{it.get('qty', 0):.2f}",
            f"{it.get('amount', 0):.2f}",
            f"{it.get('taxable_amount', 0):.2f}",
            f"{it.get('cgst_rate', 0):.2f}",
            f"{it.get('cgst_amt', 0):.2f}",
            f"{it.get('sgst_rate', 0):.2f}",
            f"{it.get('sgst_amt', 0):.2f}",
            f"{it.get('total_amt', 0):.2f}",
        ]
        for i, v in enumerate(vals):
            c.drawString(cx, row_y - (row_h / 2) + 3, v)
            cx += col_widths[i]

        # totals accum
        subtotal += float(it.get("taxable_amount", 0) or 0)
        total_cgst += float(it.get("cgst_amt", 0) or 0)
        total_sgst += float(it.get("sgst_amt", 0) or 0)
        grand_total += float(it.get("total_amt", 0) or 0)

        row_y -= row_h

    # Totals box on right side of table area
    totals_box_w = 60 * mm
    totals_box_h = 28 * mm
    totals_x = table_x + table_w - totals_box_w
    totals_y = row_y - 10 * mm
    draw_box(c, totals_x, totals_y - totals_box_h, totals_box_w, totals_box_h, stroke=1)
    c.setFont("Helvetica", 9)
    tx = totals_x + 6
    ty = totals_y - 12
    c.drawString(tx, ty + 12, f"Subtotal: {subtotal:.2f}")
    c.drawString(tx, ty, f"CGST Total: {total_cgst:.2f}")
    c.drawString(tx, ty - 12, f"SGST Total: {total_sgst:.2f}")
    c.setFont("Helvetica-Bold", 10)
    c.drawString(tx, ty - 28, f"GRAND TOTAL: {grand_total:.2f}")

    # Footer area: notes + signature
    footer_h = 30 * mm
    footer_x = left_margin
    footer_y = bottom_margin + 10 * mm
    draw_box(c, footer_x, footer_y, page_width, footer_h, stroke=1)
    c.setFont("Helvetica", 9)
    c.drawString(footer_x + 6, footer_y + footer_h - 12, "This is a computer-generated invoice.")
    c.drawString(footer_x + 6, footer_y + footer_h - 26, "For any disputes, refer to the terms and conditions agreed.")

    # Signature placeholder on the bottom-right
    sig_w = 50 * mm
    sig_h = 18 * mm
    sig_x = footer_x + page_width - sig_w - 12
    sig_y = footer_y + 6
    draw_box(c, sig_x, sig_y, sig_w, sig_h, stroke=1)
    c.setFont("Helvetica", 9)
    c.drawCentredString(sig_x + sig_w / 2, sig_y + sig_h / 2 - 4, "Authorised Signatory")

    # Save PDF
    c.save()
    return path

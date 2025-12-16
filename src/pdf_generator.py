# pdf_generator.py
# Professional PDF generator for SANSHIP invoices & debit notes
# - Logo left
# - Tally-style section borders
# - Table with specified column widths
# - Multi-page safe rendering
# - Footer with certification & signatory
#
# Falls back to TXT export if reportlab isn't installed.

import os
from datetime import datetime
from math import floor

BASE_DIR = os.path.dirname(os.path.dirname(__file__))
OUT_DIR = os.path.join(BASE_DIR, "exports")
os.makedirs(OUT_DIR, exist_ok=True)

DEFAULT_LOGO = os.path.join(BASE_DIR, "assets", "logo.png")

PAGE_MARGIN = 36

# Table column widths (points)
COL_WIDTHS = [
    30,   # Sr No
    220,  # Charges Details
    55,   # HSN/SAC
    40,   # CUR
    60,   # Rate
    50,   # Qty
    70,   # Amount (Cur)
    80,   # Taxable Amount
    40,   # CGST %
    65,   # CGST Amt
    40,   # SGST %
    65,   # SGST Amt
    85    # Total Amount
]

def fmt_money(v):
    try:
        return f"{float(v):,.2f}"
    except:
        return safe_text(v)

def safe_text(s):
    return "" if s is None else str(s)


# -------------------------------------------------------------
# MAIN FUNCTION
# -------------------------------------------------------------
def generate_invoice_pdf(header, items, title="TAX INVOICE"):
    """
    Generates PDF invoice using reportlab.
    Falls back to TXT if reportlab unavailable.
    """
    try:
        # Attempt to import reportlab
        from reportlab.lib.pagesizes import A4
        from reportlab.lib import colors
        from reportlab.pdfgen import canvas
        from reportlab.pdfbase import pdfmetrics
        from reportlab.pdfbase.ttfonts import TTFont

        # Load base font
        try:
            font_path = os.path.join(BASE_DIR, "assets", "Roboto-Regular.ttf")
            if os.path.exists(font_path):
                pdfmetrics.registerFont(TTFont("Roboto", font_path))
                base_font = "Roboto"
            else:
                base_font = "Helvetica"
        except:
            base_font = "Helvetica"

        PAGE_W, PAGE_H = A4
        ctime = int(datetime.now().timestamp())

        invoice_num = header.get("invoice_number", f"INV-{ctime}")

        # 🔥 FIX: sanitize invoice number for filename
        safe_inv = invoice_num.replace("/", "_")
        filename = f"{safe_inv}_{ctime}.pdf"

        path = os.path.join(OUT_DIR, filename)
        c = canvas.Canvas(path, pagesize=A4)

        left_x = PAGE_MARGIN
        right_x = PAGE_W - PAGE_MARGIN
        content_width = PAGE_W - 2 * PAGE_MARGIN

        y = PAGE_H - PAGE_MARGIN

        # -------------------------------------------------------------
        # HEADER WITH LOGO + COMPANY DETAILS
        # -------------------------------------------------------------
        def draw_header():
            nonlocal y
            logo_h = 60
            logo_w = 120

            if os.path.exists(DEFAULT_LOGO):
                try:
                    c.drawImage(DEFAULT_LOGO, left_x, y - logo_h,
                                width=logo_w, height=logo_h,
                                preserveAspectRatio=True, mask='auto')
                except:
                    pass

            tx = left_x + logo_w + 10
            ty = y - 6

            c.setFont(base_font, 14)
            c.drawString(tx, ty, "SAN SHIPPING AND LOGISTICS (INDIA) PVT LTD")

            ty -= 16
            c.setFont(base_font, 9)
            address_lines = [
                "International Freight Forwarding Company - World Wide Logistics Provider",
                "123, Harbor Road, Panvel, MAHARASHTRA - 421302, India",
                "Phone: +91-22-XXXX-XXXX | Email: info@sanship.example"
            ]

            for ln in address_lines:
                c.drawString(tx, ty, ln)
                ty -= 12

            y_header_bottom = y - max(logo_h, 60) - 10
            c.setLineWidth(0.6)
            c.line(left_x, y_header_bottom, right_x, y_header_bottom)
            y = y_header_bottom - 12


        # -------------------------------------------------------------
        # BILL TO / SHIPMENT / CONSIGNMENT BOXES (TALLY-STYLE)
        # -------------------------------------------------------------
        def draw_boxes():
            nonlocal y
            box_top = y
            total_h = 120
            gap = 12

            cons_w = content_width * 0.45
            left_w = content_width - cons_w - gap

            left_xb = left_x
            right_xb = left_x + left_w + gap

            bottom_y = box_top - total_h

            # Rectangle outlines
            c.setStrokeColor(colors.black)
            c.setLineWidth(1)

            # BILL-TO (upper-left)
            c.rect(left_xb, bottom_y + (total_h / 2), left_w, total_h / 2, stroke=True)

            # SHIPMENT DETAILS (lower-left)
            c.rect(left_xb, bottom_y, left_w, total_h / 2, stroke=True)

            # CONSIGNMENT (right)
            c.rect(right_xb, bottom_y, cons_w, total_h, stroke=True)

            # BILL TO CONTENT
            bt_x, bt_y = left_xb + 8, box_top - 18
            c.setFont(base_font, 10)
            c.drawString(bt_x, bt_y, "BILL TO")
            bt_y -= 12

            bill_to_block = header.get("bill_to") or (
                "SAN SHIPPING AND LOGISTICS (INDIA) PVT LTD\n"
                "123 Harbor Road\nPanvel, MAHARASHTRA - 421302\nPAN/IT No: -----\n"
                "State Name: Maharashtra, Code: 27\nGSTIN/UIN: -----------"
            )
            c.setFont(base_font, 9)
            for ln in bill_to_block.split("\n"):
                c.drawString(bt_x, bt_y, ln)
                bt_y -= 11

            # SHIPMENT DETAILS
            sh_x, sh_y = left_xb + 8, box_top - (total_h / 2) - 10
            c.setFont(base_font, 10)
            c.drawString(sh_x, sh_y, "SHIPMENT DETAILS")
            sh_y -= 12

            ship_fields = [
                ("Shipper", header.get("shipper")),
                ("Consignee", header.get("consignee_preview")),
                ("POL", header.get("pol")),
                ("POD", header.get("pod")),
                ("Vessel/Flight", header.get("vessel_flight")),
                ("ETD", header.get("etd")),
                ("ETA", header.get("eta")),
            ]

            c.setFont(base_font, 9)
            for label, val in ship_fields:
                c.drawString(sh_x, sh_y, f"{label}: {safe_text(val)}")
                sh_y -= 11

            # CONSIGNMENT DETAILS
            cs_x, cs_y = right_xb + 8, box_top - 18
            c.setFont(base_font, 10)
            c.drawString(cs_x, cs_y, "CONSIGNMENT DETAILS")
            cs_y -= 12

            cons_fields = [
                ("Date", header.get("c_date")),
                ("Invoice No", header.get("c_invoice_no")),
                ("MBL No", header.get("mbl_no")),
                ("Job No", header.get("job_no")),
                ("Gross Weight", header.get("gross_weight")),
                ("Net Weight", header.get("net_weight")),
                ("Volume (CBM)", header.get("volume_cbm")),
                ("Packages", header.get("packages")),
                ("BE No / Date", (header.get("be_no") or "") + " / " + safe_text(header.get("be_date"))),
                ("IGM No / Date", (header.get("igm_no") or "") + " / " + safe_text(header.get("igm_date"))),
                ("Item No", header.get("item_no")),
                ("Exchange Rate", header.get("exchange_rate")),
                ("Reference No", header.get("ref_no")),
            ]

            c.setFont(base_font, 9)
            for label, val in cons_fields:
                c.drawString(cs_x, cs_y, f"{label}: {safe_text(val)}")
                cs_y -= 11

            return bottom_y - 20


        # -------------------------------------------------------------
        # TABLE DRAWING + PAGINATION
        # -------------------------------------------------------------
        def draw_table(y_pos):
            nonlocal y
            y_cursor = y_pos
            left_tx = left_x
            total_width = sum(COL_WIDTHS)
            row_h = 18
            header_h = 20

            # Column x-coordinates
            col_x = [left_tx]
            for w in COL_WIDTHS:
                col_x.append(col_x[-1] + w)

            # Title above table
            c.setFont(base_font, 11)
            c.drawString(left_tx, y_cursor, title)
            c.drawRightString(left_tx + total_width, y_cursor, f"Invoice No: {safe_text(header.get('invoice_number'))}")
            y_cursor -= 18

            # Table header background
            c.setFillColor(colors.lightgrey)
            c.rect(left_tx, y_cursor - header_h, total_width, header_h, fill=True, stroke=False)
            c.setFillColor(colors.black)
            c.setFont(base_font, 8)

            headers = [
                "Sr", "Charges Details", "HSN", "CUR", "Rate", "Qty", "Amt", "Taxable",
                "CGST%", "CGST Amt", "SGST%", "SGST Amt", "Total"
            ]

            for i, htext in enumerate(headers):
                c.drawString(col_x[i] + 3, y_cursor - header_h + 7, htext)

            # Border for header
            c.setLineWidth(0.6)
            c.rect(left_tx, y_cursor - header_h, total_width, header_h, stroke=True)

            for x in col_x[1:-1]:
                c.line(x, y_cursor, x, y_cursor - header_h)

            y_cursor -= header_h + 4

            # Draw rows
            for index, it in enumerate(items):

                # PAGE BREAK
                if y_cursor - row_h < PAGE_MARGIN + 100:
                    c.showPage()
                    redraw_page_header()
                    # reprint table header
                    y_cursor = PAGE_H - PAGE_MARGIN - 80
                    c.setFillColor(colors.lightgrey)
                    c.rect(left_tx, y_cursor - header_h, total_width, header_h, fill=True)
                    c.setFillColor(colors.black)
                    c.setFont(base_font, 8)
                    for i, htext in enumerate(headers):
                        c.drawString(col_x[i] + 3, y_cursor - header_h + 7, htext)
                    c.rect(left_tx, y_cursor - header_h, total_width, header_h)
                    for x in col_x[1:-1]:
                        c.line(x, y_cursor, x, y_cursor - header_h)
                    y_cursor -= header_h + 4

                # Row shading
                if index % 2 == 0:
                    c.setFillColorRGB(1, 1, 1)
                else:
                    c.setFillColorRGB(0.97, 0.97, 0.99)

                c.rect(left_tx, y_cursor - row_h, total_width, row_h, fill=True, stroke=False)
                c.setFillColor(colors.black)
                c.setFont(base_font, 8)

                # Values
                vals = [
                    safe_text(it.get("sr_no")),
                    safe_text(it.get("description")),
                    safe_text(it.get("hsn_sac")),
                    safe_text(it.get("cur")),
                    fmt_money(it.get("rate")),
                    fmt_money(it.get("qty")),
                    fmt_money(it.get("amount")),
                    fmt_money(it.get("taxable_amount")),
                    safe_text(it.get("cgst_rate")),
                    fmt_money(it.get("cgst_amt")),
                    safe_text(it.get("sgst_rate")),
                    fmt_money(it.get("sgst_amt")),
                    fmt_money(it.get("total_amt")),
                ]

                for col_i, text in enumerate(vals):
                    x0 = col_x[col_i] + 3
                    y0 = y_cursor - row_h + 5

                    if col_i == 1:  # wrap description
                        max_w = COL_WIDTHS[1] - 6
                        words = text.split()
                        line = ""
                        yy = y0
                        for w in words:
                            tmp = (line + " " + w).strip()
                            if c.stringWidth(tmp, base_font, 8) < max_w:
                                line = tmp
                            else:
                                c.drawString(x0, yy, line)
                                line = w
                                yy -= 9
                        c.drawString(x0, yy, line)
                    else:
                        # right align numeric columns
                        if col_i in (4, 5, 6, 7, 9, 11, 12):
                            c.drawRightString(col_x[col_i+1] - 4, y0, text)
                        else:
                            c.drawString(x0, y0, text)

                # draw borders
                for x in col_x:
                    c.line(x, y_cursor, x, y_cursor - row_h)

                c.line(left_tx, y_cursor - row_h, left_tx + total_width, y_cursor - row_h)

                y_cursor -= row_h

            # totals box
            totals_h = 80
            totals_w = 320
            totals_x = left_tx + total_width - totals_w
            totals_y = y_cursor - 5

            total_taxable = sum(float(it.get("taxable_amount") or 0) for it in items)
            total_cgst = sum(float(it.get("cgst_amt") or 0) for it in items)
            total_sgst = sum(float(it.get("sgst_amt") or 0) for it in items)
            grand_total = sum(float(it.get("total_amt") or 0) for it in items)

            c.rect(totals_x, totals_y - totals_h, totals_w, totals_h)

            tx = totals_x + 8
            ty = totals_y - 20

            c.setFont(base_font, 9)
            c.drawString(tx, ty, "Taxable Value:")
            c.drawRightString(totals_x + totals_w - 8, ty, fmt_money(total_taxable))
            ty -= 14

            c.drawString(tx, ty, "Total CGST:")
            c.drawRightString(totals_x + totals_w - 8, ty, fmt_money(total_cgst))
            ty -= 14

            c.drawString(tx, ty, "Total SGST:")
            c.drawRightString(totals_x + totals_w - 8, ty, fmt_money(total_sgst))
            ty -= 14

            c.setFont(base_font, 10)
            c.drawString(tx, ty, "Grand Total:")
            c.drawRightString(totals_x + totals_w - 8, ty, fmt_money(grand_total))

            return totals_y - totals_h - 30


        # -------------------------------------------------------------
        # HEADER ON NEW PAGE
        # -------------------------------------------------------------
        def redraw_page_header():
            c.setFont(base_font, 9)
            c.drawString(left_x, PAGE_H - PAGE_MARGIN - 10, "SAN SHIPPING AND LOGISTICS (INDIA) PVT LTD")


        # -------------------------------------------------------------
        # FOOTER
        # -------------------------------------------------------------
        def draw_footer(y_pos):
            c.setFont(base_font, 9)
            fy = PAGE_MARGIN + 40
            fx = left_x
            footer = [
                "Certified that the particulars given above are true and correct.",
                "For SAN SHIPPING AND LOGISTICS (INDIA) PVT LTD",
                "",
                "Authorised Signatory"
            ]
            for ln in footer:
                c.drawString(fx, fy, ln)
                fy += 12

            c.setFont(base_font, 7)
            c.drawRightString(right_x, PAGE_MARGIN + 10,
                              f"Generated on {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")


        # -------------------------------------------------------------
        # EXECUTION
        # -------------------------------------------------------------
        draw_header()
        y = draw_boxes()
        y = draw_table(y)
        draw_footer(y)

        c.showPage()
        c.save()
        return path

    except Exception as e:
        # -------------------------------------------------------------
        # FALLBACK: Generate TXT
        # -------------------------------------------------------------
        try:
            ctime = int(datetime.now().timestamp())
            invoice_num = header.get("invoice_number", f"INV-{ctime}")

            safe_inv = invoice_num.replace("/", "_")
            filename = f"{safe_inv}_{ctime}.txt"
            path = os.path.join(OUT_DIR, filename)

            with open(path, "w", encoding="utf-8") as f:
                f.write(f"{title}\n")
                f.write("SAN SHIPPING AND LOGISTICS (INDIA) PVT LTD\n")
                f.write("123 Harbor Road, Panvel, MAHARASHTRA - 421302\n\n")
                f.write(f"Invoice No: {invoice_num}\n")
                f.write(f"Date: {safe_text(header.get('date'))}\n\n")
                f.write("BILL TO:\n")
                f.write(safe_text(header.get("bill_to")) + "\n\n")

                f.write("SHIPMENT DETAILS:\n")
                for lbl, val in [
                    ("Shipper", header.get("shipper")),
                    ("Consignee", header.get("consignee_preview")),
                    ("POL", header.get("pol")),
                    ("POD", header.get("pod")),
                    ("Vessel/Flight", header.get("vessel_flight")),
                    ("ETD", header.get("etd")),
                    ("ETA", header.get("eta")),
                ]:
                    f.write(f"{lbl}: {safe_text(val)}\n")

                f.write("\nCONSIGNMENT DETAILS:\n")
                for lbl, val in [
                    ("Date", header.get("c_date")),
                    ("Invoice No", header.get("c_invoice_no")),
                    ("MBL No", header.get("mbl_no")),
                    ("Job No", header.get("job_no")),
                    ("Gross Weight", header.get("gross_weight")),
                    ("Net Weight", header.get("net_weight")),
                    ("Volume (CBM)", header.get("volume_cbm")),
                    ("Packages", header.get("packages")),
                    ("BE No", header.get("be_no")),
                    ("BE Date", header.get("be_date")),
                    ("IGM No", header.get("igm_no")),
                    ("IGM Date", header.get("igm_date")),
                    ("Item No", header.get("item_no")),
                    ("Exchange Rate", header.get("exchange_rate")),
                    ("Reference No", header.get("ref_no")),
                ]:
                    f.write(f"{lbl}: {safe_text(val)}\n")

                f.write("\nITEMS:\n")
                for it in items:
                    f.write(
                        f"{it.get('sr_no')}. {it.get('description')} | Qty: {it.get('qty')} | Total: {fmt_money(it.get('total_amt'))}\n"
                    )

                total_taxable = sum(float(it.get("taxable_amount") or 0) for it in items)
                total_cgst = sum(float(it.get("cgst_amt") or 0) for it in items)
                total_sgst = sum(float(it.get("sgst_amt") or 0) for it in items)
                grand_total = sum(float(it.get("total_amt") or 0) for it in items)

                f.write("\n")
                f.write(f"Taxable Value: {fmt_money(total_taxable)}\n")
                f.write(f"Total CGST: {fmt_money(total_cgst)}\n")
                f.write(f"Total SGST: {fmt_money(total_sgst)}\n")
                f.write(f"Grand Total: {fmt_money(grand_total)}\n\n")

                f.write("Certified that the particulars given above are true and correct.\n")
                f.write("For SAN SHIPPING AND LOGISTICS (INDIA) PVT LTD\n")
                f.write("Authorised Signatory\n")

            return path

        except Exception as ee:
            raise ee

import os
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4

BASE_DIR = os.path.dirname(os.path.dirname(__file__))


def generate_invoice_pdf(header, items):
    out_dir = os.path.join(BASE_DIR, "output")
    os.makedirs(out_dir, exist_ok=True)

    filename = f"{header['invoice_number']}.pdf"
    path = os.path.join(out_dir, filename)

    c = canvas.Canvas(path, pagesize=A4)
    width, height = A4

    y = height - 40
    c.setFont("Helvetica-Bold", 16)
    c.drawString(40, y, "TAX INVOICE")
    y -= 30

    c.setFont("Helvetica", 10)
    c.drawString(40, y, f"Invoice No: {header['invoice_number']}")
    y -= 15
    c.drawString(40, y, f"Date: {header['date']}")
    y -= 25

    c.drawString(40, y, "Bill To:")
    y -= 15
    for line in header["bill_to"].splitlines():
        c.drawString(60, y, line)
        y -= 15

    y -= 15
    c.drawString(40, y, "Consignee:")
    y -= 15
    for line in header["consignee"].splitlines():
        c.drawString(60, y, line)
        y -= 15

    y -= 20
    c.setFont("Helvetica-Bold", 9)
    c.drawString(40, y, "Sr  Description                 Rate   Qty   Total")
    y -= 15
    c.setFont("Helvetica", 9)

    for it in items:
        line = f"{it['sr_no']:>2}  {it['description'][:24]:<24} {it['rate']:>6.2f} {it['qty']:>5.2f} {it['total_amt']:>8.2f}"
        c.drawString(40, y, line)
        y -= 14
        if y < 40:
            c.showPage()
            y = height - 40

    c.save()
    return path

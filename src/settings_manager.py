# src/settings_manager.py
from datetime import datetime
from database import get_conn, get_setting, set_setting

INVOICE_PREFIX = "SAN/INV"
DEBIT_PREFIX = "SAN/DN"


def current_fin_year():
    now = datetime.now()
    year = now.year
    if now.month >= 4:
        start = year
        end = year + 1
    else:
        start = year - 1
        end = year
    return f"{str(start)[-2:]}-{str(end)[-2:]}"


def get_next_invoice_number():
    fin = current_fin_year()
    stored = get_setting("inv_year")
    counter = get_setting("inv_counter")
    counter = int(counter) if counter and str(counter).isdigit() else 0

    if stored != fin:
        counter = 1
        set_setting("inv_year", fin)
    else:
        counter += 1

    set_setting("inv_counter", counter)
    return f"{INVOICE_PREFIX}/{fin}/{counter:04d}"


def get_next_debit_number():
    fin = current_fin_year()
    stored = get_setting("dn_year")
    counter = get_setting("dn_counter")
    counter = int(counter) if counter and str(counter).isdigit() else 0

    if stored != fin:
        counter = 1
        set_setting("dn_year", fin)
    else:
        counter += 1

    set_setting("dn_counter", counter)
    return f"{DEBIT_PREFIX}/{fin}/{counter:04d}"

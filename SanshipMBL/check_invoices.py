import sqlite3

with open('db_schema_output.txt', 'w') as f:
    db = sqlite3.connect('sanship.db')
    
    cols = [r[1] for r in db.execute('PRAGMA table_info(invoices)')]
    f.write('INVOICES COLUMNS:\n' + str(cols) + '\n\n')
    
    count = db.execute('SELECT COUNT(*) FROM invoices').fetchone()[0]
    f.write('INVOICES ROWS: ' + str(count) + '\n\n')
    
    ddl = db.execute("SELECT sql FROM sqlite_master WHERE name='invoices'").fetchone()
    f.write('INVOICES DDL:\n' + (ddl[0] if ddl else 'NONE') + '\n\n')
    
    cols2 = [r[1] for r in db.execute('PRAGMA table_info(invoice_items)')]
    f.write('INVOICE_ITEMS COLUMNS:\n' + str(cols2) + '\n\n')
    
    count2 = db.execute('SELECT COUNT(*) FROM invoice_items').fetchone()[0]
    f.write('INVOICE_ITEMS ROWS: ' + str(count2) + '\n\n')
    
    db.close()

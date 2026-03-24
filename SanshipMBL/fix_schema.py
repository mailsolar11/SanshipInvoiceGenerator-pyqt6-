import sqlite3

db = sqlite3.connect('sanship.db')

for table in ['ledgers', 'ledger_groups', 'ledger_entries']:
    try:
        db.execute(f"ALTER TABLE {table} ADD COLUMN created_at TEXT")
        print(f"Added created_at to {table}")
    except Exception as e:
        print(f"{table}: {e}")

db.commit()

# Verify
for table in ['ledger_groups', 'ledgers', 'ledger_entries']:
    cols = [r[1] for r in db.execute(f"PRAGMA table_info({table})")]
    has_it = 'created_at' in cols
    print(f"  Verify {table}.created_at = {'PASS' if has_it else 'FAIL'}")

db.close()

import sqlite3
import os
import codecs

def dump_db(name, f):
    f.write(f"\n======================================\n")
    f.write(f"=== {name} SCHEMA & ROW COUNTS ===\n")
    f.write(f"======================================\n")
    
    if not os.path.exists(name):
        f.write(f"File {name} not found!\n")
        return
        
    try:
        db = sqlite3.connect(name)
        
        f.write("\n--- SCHEMA ---\n")
        for row in db.execute("SELECT name, sql FROM sqlite_master WHERE type='table'"):
            f.write(f"\nTable: {row[0]}\n")
            f.write(f"{row[1]}\n")
            
        f.write("\n--- ROW COUNTS ---\n")
        for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'"):
            table = row[0]
            if table != 'sqlite_sequence':
                count = list(db.execute(f"SELECT COUNT(*) FROM {table}"))[0][0]
                f.write(f"{table}: {count} rows\n")
    except Exception as e:
        f.write(f"Error reading {name}: {e}\n")

with codecs.open('schema_dump.txt', 'w', 'utf-8') as file:
    dump_db('sanship.db', file)
    dump_db('data.db', file)

"""
Database Unification Migration Script v2
Migrates all accounting data from data.db INTO sanship.db
Handles deduplication and foreign key remapping.
"""
import sqlite3
import os
import codecs
import traceback

LOG = []

def log(msg):
    LOG.append(msg)
    print(msg)

def get_columns(conn, table):
    """Get list of column names for a table."""
    cursor = conn.execute(f"PRAGMA table_info({table})")
    return [row[1] for row in cursor.fetchall()]

def migrate():
    if not os.path.exists('data.db'):
        log("ERROR: data.db not found!")
        return
    if not os.path.exists('sanship.db'):
        log("ERROR: sanship.db not found!")
        return

    src = sqlite3.connect('data.db')
    dst = sqlite3.connect('sanship.db')
    dst.execute("PRAGMA foreign_keys = OFF")

    try:
        # =============================================
        # STEP 1: Schema alignment on sanship.db
        # =============================================
        log("--- STEP 1: SCHEMA ALIGNMENT ---")
        
        alter_cols = [
            ("vouchers", "voucher_type_id", "INTEGER"),
            ("ledger_groups", "created_at", "TEXT DEFAULT CURRENT_TIMESTAMP"),
            ("ledgers", "created_at", "TEXT DEFAULT CURRENT_TIMESTAMP"),
            ("ledger_entries", "created_at", "TEXT DEFAULT CURRENT_TIMESTAMP"),
        ]
        for table, col, coltype in alter_cols:
            try:
                dst.execute(f"ALTER TABLE {table} ADD COLUMN {col} {coltype}")
                log(f"  Added {col} to {table}")
            except Exception as e:
                log(f"  {col} already exists in {table}")
        
        dst.commit()

        # =============================================
        # STEP 2: MIGRATE LEDGER GROUPS
        # =============================================
        log("\n--- STEP 2: MIGRATE LEDGER_GROUPS ---")
        
        src_cols = get_columns(src, 'ledger_groups')
        log(f"  Source columns: {src_cols}")
        
        src_groups = list(src.execute("SELECT * FROM ledger_groups"))
        log(f"  Source data.db ledger_groups: {len(src_groups)} rows")
        
        group_id_map = {}
        
        for sg in src_groups:
            src_id = sg[0]
            name = sg[src_cols.index('name')]
            nature = sg[src_cols.index('nature')]
            parent_id = sg[src_cols.index('parent_id')] if 'parent_id' in src_cols else None
            
            existing = list(dst.execute("SELECT id FROM ledger_groups WHERE name = ?", (name,)))
            if existing:
                group_id_map[src_id] = existing[0][0]
                log(f"  Group '{name}' exists (dst_id={existing[0][0]})")
            else:
                cursor = dst.execute(
                    "INSERT INTO ledger_groups (name, nature, parent_id) VALUES (?, ?, ?)",
                    (name, nature, parent_id)
                )
                new_id = cursor.lastrowid
                group_id_map[src_id] = new_id
                log(f"  Group '{name}' INSERTED (dst_id={new_id})")
        
        dst.commit()

        # =============================================
        # STEP 3: MIGRATE LEDGERS
        # =============================================
        log("\n--- STEP 3: MIGRATE LEDGERS ---")
        
        src_cols = get_columns(src, 'ledgers')
        log(f"  Source columns: {src_cols}")
        
        src_ledgers = list(src.execute("SELECT * FROM ledgers"))
        log(f"  Source data.db ledgers: {len(src_ledgers)} rows")
        
        ledger_id_map = {}
        
        for sl in src_ledgers:
            src_id = sl[0]
            name = sl[src_cols.index('name')]
            group_id = sl[src_cols.index('group_id')]
            opening_balance = sl[src_cols.index('opening_balance')]
            opening_type = sl[src_cols.index('opening_type')]
            gstin = sl[src_cols.index('gstin')] if 'gstin' in src_cols else None
            is_system = sl[src_cols.index('is_system')]
            
            new_group_id = group_id_map.get(group_id, group_id)
            
            existing = list(dst.execute("SELECT id FROM ledgers WHERE name = ?", (name,)))
            if existing:
                ledger_id_map[src_id] = existing[0][0]
                dst.execute("UPDATE ledgers SET group_id = ? WHERE id = ?", (new_group_id, existing[0][0]))
                log(f"  Ledger '{name}' exists (dst_id={existing[0][0]}), synced group")
            else:
                cursor = dst.execute(
                    "INSERT INTO ledgers (name, group_id, opening_balance, opening_type, gstin, is_system) VALUES (?, ?, ?, ?, ?, ?)",
                    (name, new_group_id, opening_balance, opening_type, gstin, is_system)
                )
                new_id = cursor.lastrowid
                ledger_id_map[src_id] = new_id
                log(f"  Ledger '{name}' INSERTED (dst_id={new_id})")
        
        dst.commit()

        # =============================================
        # STEP 4: MIGRATE VOUCHER_TYPES  
        # =============================================
        log("\n--- STEP 4: MIGRATE VOUCHER_TYPES ---")
        
        src_vtypes = list(src.execute("SELECT id, name, affects_inventory, is_system FROM voucher_types"))
        log(f"  Source data.db voucher_types: {len(src_vtypes)} rows")
        
        vtype_id_map = {}
        
        for sv in src_vtypes:
            src_id, name, affects_inv, is_sys = sv
            existing = list(dst.execute("SELECT id FROM voucher_types WHERE name = ?", (name,)))
            if existing:
                vtype_id_map[src_id] = existing[0][0]
                log(f"  VoucherType '{name}' exists (dst_id={existing[0][0]})")
            else:
                cursor = dst.execute(
                    "INSERT INTO voucher_types (name, affects_inventory, is_system) VALUES (?, ?, ?)",
                    (name, affects_inv, is_sys)
                )
                new_id = cursor.lastrowid
                vtype_id_map[src_id] = new_id
                log(f"  VoucherType '{name}' INSERTED (dst_id={new_id})")
        
        dst.commit()

        # =============================================
        # STEP 5: MIGRATE VOUCHERS
        # =============================================
        log("\n--- STEP 5: MIGRATE VOUCHERS ---")
        
        src_cols = get_columns(src, 'vouchers')
        log(f"  Source columns: {src_cols}")
        
        src_vouchers = list(src.execute("SELECT * FROM vouchers"))
        log(f"  Source data.db vouchers: {len(src_vouchers)} rows")
        
        voucher_id_map = {}
        
        for sv in src_vouchers:
            src_id = sv[0]
            voucher_no = sv[src_cols.index('voucher_no')]
            vtype_id = sv[src_cols.index('voucher_type_id')]
            vdate = sv[src_cols.index('voucher_date')]
            narration = sv[src_cols.index('narration')] if 'narration' in src_cols else ''
            vtype = sv[src_cols.index('voucher_type')] if 'voucher_type' in src_cols else 'JOURNAL'
            job_id = sv[src_cols.index('job_id')] if 'job_id' in src_cols else None
            
            new_vtype_id = vtype_id_map.get(vtype_id, vtype_id)
            
            existing = list(dst.execute("SELECT id FROM vouchers WHERE voucher_no = ?", (voucher_no,)))
            if existing:
                voucher_id_map[src_id] = existing[0][0]
                dst.execute("UPDATE vouchers SET voucher_type_id = ? WHERE id = ?", (new_vtype_id, existing[0][0]))
                log(f"  Voucher '{voucher_no}' exists (dst_id={existing[0][0]}), updated")
            else:
                cursor = dst.execute(
                    "INSERT INTO vouchers (voucher_no, voucher_type_id, voucher_type, voucher_date, narration, job_id) VALUES (?, ?, ?, ?, ?, ?)",
                    (voucher_no, new_vtype_id, vtype or 'JOURNAL', vdate, narration, job_id)
                )
                new_id = cursor.lastrowid
                voucher_id_map[src_id] = new_id
                log(f"  Voucher '{voucher_no}' INSERTED (dst_id={new_id})")
        
        dst.commit()

        # =============================================
        # STEP 6: MIGRATE LEDGER_ENTRIES
        # =============================================
        log("\n--- STEP 6: MIGRATE LEDGER_ENTRIES ---")
        
        src_cols = get_columns(src, 'ledger_entries')
        log(f"  Source columns: {src_cols}")
        
        src_entries = list(src.execute("SELECT * FROM ledger_entries"))
        log(f"  Source data.db ledger_entries: {len(src_entries)} rows")
        
        migrated = 0
        skipped = 0
        
        for se in src_entries:
            src_id = se[0]
            voucher_id = se[src_cols.index('voucher_id')]
            ledger_id = se[src_cols.index('ledger_id')]
            dr_amt = se[src_cols.index('dr_amount')]
            cr_amt = se[src_cols.index('cr_amount')]
            bank_date = se[src_cols.index('bank_date')] if 'bank_date' in src_cols else None
            
            new_voucher_id = voucher_id_map.get(voucher_id)
            new_ledger_id = ledger_id_map.get(ledger_id)
            
            if new_voucher_id is None or new_ledger_id is None:
                log(f"  SKIP entry src_id={src_id}: unmapped voucher({voucher_id}) or ledger({ledger_id})")
                skipped += 1
                continue
            
            existing = list(dst.execute(
                "SELECT id FROM ledger_entries WHERE voucher_id = ? AND ledger_id = ? AND dr_amount = ? AND cr_amount = ?",
                (new_voucher_id, new_ledger_id, dr_amt, cr_amt)
            ))
            if existing:
                skipped += 1
                continue
            
            dst.execute(
                "INSERT INTO ledger_entries (voucher_id, ledger_id, dr_amount, cr_amount, bank_date) VALUES (?, ?, ?, ?, ?)",
                (new_voucher_id, new_ledger_id, dr_amt, cr_amt, bank_date)
            )
            migrated += 1
        
        dst.commit()
        log(f"  Migrated: {migrated}, Skipped: {skipped}")

        # =============================================
        # VALIDATION
        # =============================================
        log("\n--- VALIDATION ---")
        
        for table in ['ledger_groups', 'ledgers', 'voucher_types', 'vouchers', 'ledger_entries']:
            src_count = list(src.execute(f"SELECT COUNT(*) FROM {table}"))[0][0]
            dst_count = list(dst.execute(f"SELECT COUNT(*) FROM {table}"))[0][0]
            status = "OK" if dst_count >= src_count else "WARNING"
            log(f"  {table}: data.db={src_count}, sanship.db={dst_count} [{status}]")
        
        log("\n=== MIGRATION COMPLETE ===")

    except Exception as e:
        log(f"\nERROR: {e}")
        log(traceback.format_exc())
    finally:
        src.close()
        dst.close()
        with codecs.open('migration_log.txt', 'w', 'utf-8') as f:
            f.write('\n'.join(LOG))

if __name__ == '__main__':
    migrate()

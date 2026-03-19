# Chapter 2: Understanding Transactions and Locking

Understanding locking and transactions is crucial for both performance and correctness. Improper locking can lead to slow applications, timeouts, and unpredictable results.

## 2.1 Working with PostgreSQL Transactions

In PostgreSQL, **everything is a transaction**. Even a single statement is wrapped in a transaction implicitly.

### Basic Commands
*   **`BEGIN`**: Starts a transaction block. Connects multiple commands into one atomic operation.
*   **`COMMIT`**: Ends the transaction and saves changes.
    *   *Synonyms*: `COMMIT WORK`, `COMMIT TRANSACTION`. All three commands have the exact same meaning.
*   **`ROLLBACK`**: Aborts the transaction and discards changes.
*   **`END`**: Identical to `COMMIT` and can be used interchangeably from a feature point of view.

**Time in Transactions**
*   `now()`: Returns the **transaction start time**. It remains constant throughout the transaction.
*   `clock_timestamp()`: Returns the **current system time** (changes during execution).

```sql
BEGIN;
SELECT now(); -- Returns T1
SELECT now(); -- Returns T1 (Same timestamp)
COMMIT;
```

### Transaction Chains
Useful for reducing network round-trips in high-latency systems.
*   **`COMMIT AND CHAIN`**: Commits the current transaction and immediately starts a new one with the **same properties** (e.g., isolation level, read-only status) without needing a new `BEGIN` command.

```sql
BEGIN TRANSACTION READ ONLY;
SELECT 1;
COMMIT AND CHAIN; -- Starts new READ ONLY transaction immediately
SELECT 1;
COMMIT;
```

---

## 2.2 Handling Errors inside a Transaction

In PostgreSQL, **only error-free transactions can be committed**.
If an error occurs (e.g., division by zero), the transaction enters an **aborted state**.
*   All subsequent commands are ignored until `ROLLBACK` or `COMMIT` (which performs a rollback in this case) is issued.

### Savepoints
`SAVEPOINT` allows partial rollbacks within a transaction. You can recover from an error without killing the entire transaction.

```sql
BEGIN;
SELECT 1;
SAVEPOINT a; -- Define safety net
SELECT 2 / 0; -- Error!
ROLLBACK TO SAVEPOINT a; -- Revert to safety net
SELECT 3; -- Success
COMMIT;
```

*   **`RELEASE SAVEPOINT <name>`**: Destroys a savepoint (frees resources).
*   **Note**: Savepoints cease to exist when the transaction ends.

---

## 2.3 Transactional DDLs
PostgreSQL supports **Transactional DDL** (Data Definition Language). You can create, alter, or drop tables inside a transaction and roll them back if needed.
*   **Exception**: A few commands like `DROP DATABASE`, `CREATE TABLESPACE` are not transactional.
*   **Use Case**: Safe software deployments (upgrades). You can run all upgrade scripts in one transaction; if anything fails, everything rolls back.

```sql
BEGIN;
CREATE TABLE t_test (id int);
ALTER TABLE t_test ALTER COLUMN id TYPE int8;
ROLLBACK; -- Table t_test will not exist
```

---

## 2.4 Understanding Basic Locking

### MVCC (Multi-Version Concurrency Control)
*   **Readers do not block Writers.**
*   **Writers do not block Readers.**
*   A transaction only sees data committed *before* it started (depending on isolation level).

### Concurrent Updates
PostgreSQL guarantees internal consistency for updates.
*   If two transactions update the same row, the second one **waits** for the first to commit or rollback.
*   **Lock Scope**: Locks are on **rows affected by UPDATE**. (e.g., 1,000 concurrent updates on *different* rows are fine).

| Transaction 1 | Transaction 2 | Outcome |
| :--- | :--- | :--- |
| `UPDATE t SET x=x+1` | | locks row |
| | `UPDATE t SET x=x+1` | **WAITS** for Tx1 |
| `COMMIT` | | Row updated. Lock released. |
| | | Tx2 wakes up, re-reads new value, updates. |

---

## 2.5 Explicit Locking
Sometimes implicit row locking isn't enough to prevent logical errors (e.g., "select max(id)" then "insert max+1" race condition).

### Avoiding Typical Mistakes
Implicit locking works great for updates, but not for "Read-Modify-Write" cycles.

**Example: The `max(id)` Race Condition**

| Transaction 1 | Transaction 2 |
| :--- | :--- |
| `BEGIN;` | `BEGIN;` |
| `SELECT max(id) FROM product;` (Sees 17) | `SELECT max(id) FROM product;` (Sees 17) |
| *User decides next ID is 18* | *User decides next ID is 18* |
| `INSERT ... VALUES (18);` | `INSERT ... VALUES (18);` |
| `COMMIT;` | `COMMIT;` |

*   **Outcome**: Either a **Duplicate Key Violation** (error) or **Two Identical Entries** (data corruption).
*   **Solution**: Use explicit locking or `SERIAL` / `GENERATED ALWAYS AS IDENTITY` columns.

### lock Modes
Available via `LOCK TABLE name IN mode MODE`.

### Understanding Lock Conflicts
A **Conflict** occurs when process A holds a lock on an object, and process B requests a lock of a type that is **incompatible** with the existing lock.

*   **The Wait Rule**: If a conflict exists, the requesting transaction (Process B) will **WAIT** (sleep) until Process A releases the lock.
*   **Release Moment**: Locks are generally released only when the transaction ends (`COMMIT` or `ROLLBACK`).
*   **Impact**:
    *   **Blocking**: High concurrency conflicts lead to queuing, making the application feel unresponsive.
    *   **Deadlocks**: If Process A waits for B, and B waits for A, PostgreSQL detects this cycle and kills one transaction.

### Detailed Lock Modes
PostgreSQL provides 8 levels of table locks. They form a hierarchy of restrictiveness.
*   **Conflicting** means: "If Tx A holds Lock X, Tx B cannot acquire Lock Y".

**1. ACCESS SHARE**
*   **Acquired By**: `SELECT` commands.
*   **Conflicts With**: `ACCESS EXCLUSIVE` only.
*   **Meaning**: "I am reading the table. Don't drop or alter its structure, but you can write to it."

**2. ROW SHARE**
*   **Acquired By**: `SELECT FOR UPDATE`, `SELECT FOR SHARE`.
*   **Conflicts With**: `EXCLUSIVE`, `ACCESS EXCLUSIVE`.
*   **Meaning**: "I intend to modify specific rows. You can still read or write other rows, but don't lock the whole table exclusively."

**3. ROW EXCLUSIVE**
*   **Acquired By**: `UPDATE`, `DELETE`, `INSERT`.
*   **Conflicts With**: `SHARE`, `SHARE ROW EXCLUSIVE`, `EXCLUSIVE`, `ACCESS EXCLUSIVE`.
*   **Meaning**: "I am modifying data. Nobody can freeze the data snapshot (`SHARE` lock) or lock the whole table."

**4. SHARE UPDATE EXCLUSIVE**
*   **Acquired By**: `VACUUM` (without FULL), `ANALYZE`, `CREATE INDEX CONCURRENTLY`.
*   **Conflicts With**: `SHARE UPDATE EXCLUSIVE`, `SHARE`, `SHARE ROW EXCLUSIVE`, `EXCLUSIVE`, `ACCESS EXCLUSIVE`.
*   **Meaning**: "I am doing maintenance. I don't block reads or writes, but only one maintenance task can run at a time."

**5. SHARE**
*   **Acquired By**: `CREATE INDEX` (without CONCURRENTLY).
*   **Conflicts With**: `ROW EXCLUSIVE` (Writes), `SHARE UPDATE EXCLUSIVE`, and stronger locks.
*   **Meaning**: "I need a stable snapshot of the data to build an index. You can read, but you **cannot write** (INSERT/UPDATE/DELETE)."

**6. SHARE ROW EXCLUSIVE**
*   **Acquired By**: `CREATE TRIGGER`, some `ALTER TABLE` commands.
*   **Conflicts With**: Everything except `ACCESS SHARE` and `ROW SHARE`.
*   **Meaning**: "I am changing definitions. I allow readers and row-lockers, but no actual writers."

**7. EXCLUSIVE**
*   **Acquired By**: `REFRESH MATERIALIZED VIEW CONCURRENTLY`.
*   **Conflicts With**: `ROW SHARE`, `ROW EXCLUSIVE`, `SHARE`, `SHARE ROW EXCLUSIVE`, `EXCLUSIVE`, `ACCESS EXCLUSIVE`.
*   **Meaning**: "I block almost everything (Reads + Writes), except for `ACCESS SHARE` (pure reads)."
*   *Note*: Rarely used explicitly.

**8. ACCESS EXCLUSIVE**
*   **Acquired By**: `DROP TABLE`, `TRUNCATE`, `VACUUM FULL`, `LOCK TABLE` (default).
*   **Conflicts With**: **ALL** lock modes (including `ACCESS SHARE`).
*   **Meaning**: "I need total control. No readers, no writers, nothing."

**Improper Solution Example (Using ACCESS EXCLUSIVE):**
Using a heavy lock to fix the `max(id)` problem works but kills concurrency.
```sql
BEGIN;
LOCK TABLE product IN ACCESS EXCLUSIVE MODE;
-- No other transaction can read or write 'product' now!
INSERT INTO product SELECT max(id) + 1, ... FROM product;
COMMIT;
```
*   **Result**: Safe from race conditions, but **terrible for performance** as it serializes all access to the table.

> **Warning**: Avoid `ACCESS EXCLUSIVE` unless absolutely necessary (e.g., DDL changes).

### Checking Locks
You can see who is locking what by querying `pg_stat_activity`:
```sql
SELECT pid, wait_event_type, wait_event, query
FROM pg_stat_activity
WHERE wait_event_type = 'Lock';
```

### Alternative to Table Locking: CTE Watermark
Instead of locking a whole table to generate a sequence ID, use a small "watermark" table and a CTE (Common Table Expression).
```sql
WITH x AS (
    UPDATE t_watermark
    SET id = id + 1
    RETURNING * -- Returns the new unique, locked value
)
INSERT INTO t_invoice
SELECT * FROM x RETURNING *;
```
*   Only locks the single row in `t_watermark`.
*   Highly scalable compared to `LOCK TABLE`.

---

## 2.6 FOR SHARE and FOR UPDATE
Used when you read data, process it in the app, and then update it. Prevents **Race Conditions**.

### `SELECT FOR UPDATE`
*   Locks the selected rows as if they were updated.
*   Concurrent transactions trying to lock the same rows will **wait**.

### Handling Waits
1.  **`NOWAIT`**: Errors out immediately if row is locked.
    ```sql
    SELECT * FROM tab FOR UPDATE NOWAIT;
    -- ERROR: could not obtain lock on row
    ```
2.  **`lock_timeout`**: Sets logic to wait for X milliseconds before failing.
    ```sql
    SET lock_timeout TO 5000; -- Wait 5 seconds
    ```
3.  **`SKIP LOCKED`**: Skips rows that are currently locked by others.
    *   **Use Case**: Queue processing, booking systems (User A gets Seat 1, User B gets Seat 2).
    ```sql
    SELECT * FROM queue LIMIT 2 FOR UPDATE SKIP LOCKED;
    ```

### Lock Strengths
1.  **`FOR UPDATE`**:
    *   **Behavior**: Strongest. Blocks everything.
    *   **Use Case**: Deleting rows or updating Unique Keys.
2.  **`FOR NO KEY UPDATE`**:
    *   **Behavior**: Weaker. Allows `FOR KEY SHARE` to exist concurrently.
    *   **Use Case**: Updating non-key columns (e.g., changing a user's `email` but not `id`).
    ```sql
    BEGIN;
    SELECT * FROM users WHERE id=1 FOR NO KEY UPDATE;
    -- Other Tx can still take FOR KEY SHARE (e.g., foreign key checks from 'orders' table)
    UPDATE users SET email='new@example.com' WHERE id=1;
    COMMIT;
    ```
3.  **`FOR SHARE`**:
    *   **Behavior**: Shared lock. Multiple transactions can hold it.
    *   **Use Case**: Ensuring a row doesn't change while reading it (e.g., verifying inventory).
    ```sql
    BEGIN;
    SELECT * FROM inventory WHERE item_id=100 FOR SHARE;
    -- Nobody can update/delete this item until I commit.
    -- Other readers can also take FOR SHARE.
    COMMIT;
    ```
4.  **`FOR KEY SHARE`**:
    *   **Behavior**: Weakest. Blocks `FOR UPDATE` but allows `FOR NO KEY UPDATE`.
    *   **Use Case**: Used automatically by PostgreSQL for Referential Integrity (Foreign Key) checks.
    ```sql
    -- Implicitly taken on Parent table when inserting into Child table
    INSERT INTO orders (user_id) VALUES (1);
    -- Takes FOR KEY SHARE on 'users' row id=1.
    -- Allows concurrent 'email' updates on user 1, but blocks 'id' updates/deletes.
    ```

> **Side Effect**: `FOR UPDATE` on a child table might block updates on the parent table (Foreign Keys), or vice versa, to ensure referential integrity.

---

## 2.7 Transaction Isolation Levels

Isolation defines what a transaction can *see* regarding changes made by others.

### 0. READ UNCOMMITTED (Not Supported)
*   **PostgreSQL Behavior**: If you request `READ UNCOMMITTED`, PostgreSQL implicitly maps it to **READ COMMITTED**.
*   **Reason**: PostgreSQL's MVCC architecture physically cannot show dirty (uncommitted) reads. It never allows reading data that hasn't been committed (or is your own).

### 1. READ COMMITTED (Default)
*   **Behavior**: Each **statement** sees a fresh snapshot of data committed at the moment the statement starts.
*   **Visibility**: If Tx1 connects, Tx2 updates & commits, Tx1 queries again -> Tx1 **SEES** the change.
*   **Pros**: Good for OLTP, minimizes errors.

### 2. REPEATABLE READ
*   **Behavior**: The transaction sees a snapshot frozen at the **start of the first query** in the transaction.
*   **Visibility**: Data remains distinct and constant throughout the transaction, regardless of other commits.
*   **Pros**: Essential for Reporting (data consistency between first and last page).
*   **Cons**: Update conflicts results in serialization errors if data changed since snapshot.

| Action | Read Committed | Repeatable Read |
| :--- | :--- | :--- |
| Tx1 Selects | 300 | 300 |
| Tx2 Inserts 100 & Commits | (Invisible locally) | (Invisible locally) |
| Tx1 Selects Again | **400** (Sees change) | **300** (Frozen view) |

### 3. SERIALIZABLE
*   **Behavior**: Emulates serial execution (as if transactions ran one after another).
*   **Mechanism**: Uses SSI (Serializable Snapshot Isolation).
*   **Prerequisite**: Applications must be prepared to handle serialization failures (retries). Best for complex integrity constraints.

---

## 2.8 Deadlocks
Happens when two transactions wait for each other. PostgreSQL attempts to resolve this after `deadlock_timeout` (default 1s) by killing one transaction.

**Scenario**:
1.  Tx1 locks Row A.
2.  Tx2 locks Row B.
3.  Tx1 tries to lock Row B (Waits).
4.  Tx2 tries to lock Row A (Waits). -> **DEADLOCK**

**Error Message**:
```
ERROR: deadlock detected
DETAIL: Process X waits for ShareLock on transaction Y; blocked by process Z...
```
*   **Debugging**: The error usually hints at the `ctid` (physical tuple location) causing the conflict.

### Serialization Failures (Concurrent Update Errors)
Even without a classic deadlock, transactions can fail in `REPEATABLE READ` or `SERIALIZABLE` modes if distinct transactions modify the same data.

**Example: Concurrent Deletes on REPEATABLE READ**
| Transaction 1 | Transaction 2 |
| :--- | :--- |
| `BEGIN ISOLATION LEVEL REPEATABLE READ;` | `BEGIN ... REPEATABLE READ;` |
| `SELECT * FROM t;` (Sees rows) | `SELECT * FROM t;` (Sees rows) |
| `DELETE FROM t;` (Waits for Tx1) | `DELETE FROM t;` (Starts deleting) |
| **ERROR**: could not serialize access | |

*   **Logic**: Tx1 cannot delete a row that Tx2 has *already* deleted and committed *after* Tx1's snapshot started.
*   **Result**: The transaction errors out with `could not serialize access due to concurrent update`.
*   **Fix**: Applications must catch these errors and **retry** the transaction.

---

## 2.9 Advisory Locks
Locks on **abstract numbers**, not database rows.
*   Use Case: Application-level synchronization (e.g., "Only one worker allows to calculate Payroll for Tenant #15").
*   **Persistence**: Do NOT vanish on `COMMIT`. Must be explicitly unlocked (or session end).

```sql
SELECT pg_advisory_lock(15);   -- Lock number 15
SELECT pg_advisory_unlock(15); -- Unlock
```
*   **`pg_advisory_unlock_all()`**: Safety release valve.

---

## 2.10 Optimizing Storage and VACUUM

Transaction visibility (MVCC) means `UPDATE` and `DELETE` do not immediately remove data.
*   `DELETE`: Marks row as "dead".
*   `UPDATE`: Marks old row as "dead", inserts new version.
*   **Dead Rows**: Occupy space but are invisible. Must be cleaned up.

### VACUUM: The Garbage Collector
Because of MVCC, data is never overwritten in place.
*   **The Problem**: Every `UPDATE` creates a new row version. Every `DELETE` leaves a "ghost" row. These "Dead Tuples" accumulate and bloat the table size.
*   **The Solution**: `VACUUM` is the maintenance process that cleans this up.

**How VACUUM Works**:
1.  **Scanning**: It scans the table pages to find dead tuples (rows no longer visible to any active transaction).
2.  **Marking**: It marks the space occupied by dead tuples as "available for reuse" in the **Free Space Map (FSM)**.
3.  **Reuse**: Future `INSERT`s or `UPDATE`s will look at the FSM and reuse these "holes" instead of growing the file.
4.  **Freezing**: It updates Transaction ID watermarks to prevent ID wraparound (failure).

**Crucial Misconception**:
*   **Does VACUUM shrink the physical file size (OS disk space)?**
    *   **NO** (Usually). It creates "internal swiss cheese holes" for reuse.
    *   **Exception**: If the dead rows are exactly at the *end* of the table file, it can truncate the file to release space back to the OS.

### Autovacuum Settings (postgresql.conf)
Automatic daemon that runs VACUUM.
*   `autovacuum_naptime = 1m`: Wakes up every minute to check if work is needed.
*   `autovacuum_max_workers = 3`: Maximum number of parallel worker processes (across all databases) that can run cleaning tasks.
*   **Triggers** (When does it start?):
    *   `autovacuum_vacuum_scale_factor = 0.2`: Trigger vacuum if 20% of table changed.
    *   `autovacuum_vacuum_threshold = 50`: ...AND at least 50 rows modified (prevents constant vacuum on tiny tables).
    *   `autovacuum_vacuum_insert_threshold`: Triggers vacuum for **INSERT-only** workloads (Postgres 13+).

### Transaction Wraparound
Transaction IDs (XID) are 32-bit integers. They eventually overrun.
*   **Freezing**: VACUUM "freezes" old rows so they stay visible in the future.
*   **Safety Settings**: `autovacuum_freeze_max_age` (Forces vacuum to prevent wraparound).
*   **CLOG Cleanup**: Also cleans up the Commit Log (status of transactions).

### VACUUM FULL
*   **Behavior**: Rewrites the entire table to a new file.
*   **Pros**: Returns disk space to OS (shrinks file).
*   **Cons**: **ACCESS EXCLUSIVE LOCK**. Blocks reads/writes. Stop-the-world operation for that table.
*   *Alternative*: Use `pg_squeeze` extension for non-locking rewrite.

### Monitoring VACUUM Efficiency
Example showing file size vs "holes":
1.  Create table `autovacuum_enabled=off`.
2.  Fill 100k rows (~3.5MB).
3.  Update all rows -> Size doubles (7MB) because of internal copying.
4.  `VACUUM` -> Size stays 7MB (Space marked reusable).
5.  Update all rows again -> Size stays 7MB (Used the holes).

### VACUUM and DELETE: When does size shrink?
Common myth: "Deleting 50% of the table and running VACUUM will reduce disk usage by 50%."
*   **Reality**: This usually results in a file with 50% "holes" (internal fragmentation), keeping the **same size** on disk.
*   **The Exception (Truncation)**: VACUUM *can* return space to the OS only if the empty pages are at the **physical end** of the table file.

**Scenario**:
1.  Table has 10,000 blocks.
2.  You `DELETE` rows sitting in blocks 9,000 to 10,000.
3.  `VACUUM` runs -> It truncates the file size because the tail is empty.
4.  **Contrast**: If you delete rows in blocks 1 to 1,000, `VACUUM` marks them reusable, but cannot "cut off" the beginning of the file. Size remains unchanged.

### Advanced VACUUM Options
*   `SKIP_LOCKED`: Skip tables that are locked (don't block). Useful for high-concurrency environments.
*   `INDEX_CLEANUP`: Turn off to speed up vacuum in emergency (wraparound) situations.
*   `PARALLEL integer`: Use multiple CPUs for vacuuming large tables (automatically determined by table size).
*   `DISABLE_PAGE_SKIPPING`: Forces VACUUM to visit all pages, even those that look "clean" (useful if Visibility Map is corrupted).
*   `TRUNCATE`: Can be set to `false` to prevent VACUUM from attempting to truncate the file end (avoids brief exclusive lock).


---

## 2.11 Internals: Pages and Commit Log

### Page Skipping & Visibility Map
PostgreSQL stores data in "Pages" (usually 8KB blocks).
*   **Visibility Map (VM)**: A sidecar file that tracks which pages contain *only* visible (frozen) data.
*   **Optimization**: `VACUUM` looks at the VM. If a page is marked "all visible", VACUUM reads the VM and **skips** reading the actual table page on disk. This saves massive amounts of I/O.
*   **`DISABLE_PAGE_SKIPPING`**: Forces VACUUM to ignore the VM and check every page physically. Used if the VM is suspected to be corrupt.

### CLOG (Commit Log)
The **Commit Log** (stored in `pg_xact`) is a critical status tracking system.
*   **Structure**: A bitmap storing **2 bits per Transaction ID**.
*   **States**: Tracks if a transaction is:
    1.  **In Progress**
    2.  **Committed**
    3.  **Aborted**
    4.  **Sub-committed**
*   **Usage**: When checking if a row is visible, Postgres looks at the row's XMIN (creator Transaction ID) and checks the CLOG to see if that XID Committed.
*   **Cleanup**: Old CLOG files are removed during VACUUM's freeze cycles (`autovacuum_freeze_max_age`) to prevent unlimited growth.

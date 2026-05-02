# Mastering PostgreSQL: Chapter 5 - Log Files and System Statistics

## Introduction to Monitoring
Database administration extends far beyond writing advanced SQL queries. To maintain a professional, reliable, and performant database environment, administrators must continuously monitor the system.

In PostgreSQL, the two foundational pillars of monitoring are:
1.  **Gathering Runtime Statistics:** Understanding what the database is doing internally.
2.  **Creating and Managing Log Files:** Keeping a persistent, configurable record of events, errors, and queries.

---

## 1. Gathering Runtime Statistics
To improve performance and reliability, you must collect data to make informed decisions. PostgreSQL provides a vast array of **system views** designed to give administrators deep visibility into the system's current state.

**The Golden Rule of Monitoring:** 
> *There is no point in drawing a graph for a metric you don't understand.* 

It is crucial not just to collect statistics, but to understand what those statistics actually represent. The following sections will demystify PostgreSQL's onboard system views and demonstrate how to extract actionable intelligence from them.

### `pg_stat_activity` - Checking Live Traffic

When troubleshooting a system, `pg_stat_activity` is generally the very first view you should inspect. It provides a real-time snapshot of what is happening in the database right now, displaying one row for every active connection.

**Example Output:**
```sql
test=# \d pg_stat_activity
View "pg_catalog.pg_stat_activity"
Column | Type ...
------------------+-------------------- ...
datid | oid ...
datname | name ...
pid | integer ...
leader_pid | integer ...
usesysid | oid ...
usename | name ...
application_name | text ...
client_addr | inet ...
client_hostname | text ...
client_port | integer ...
backend_start | timestamp with time zone ...
xact_start | timestamp with time zone ...
query_start | timestamp with time zone ...
state_change | timestamp with time zone ...
wait_event_type | text ...
wait_event | text ...
state | text ...
backend_xid | xid ...
backend_xmin | xid ...
query_id | bigint ...
query | text ...
backend_type | text ...
```


#### Key Columns Explained
*   **Connection Identity:**
    *   `datid` / `datname`: The internal Object ID and name of the database.
    *   `pid`: The operating system Process ID serving this connection.
    *   `leader_pid`: If a query is executed in parallel, PostgreSQL launches worker processes. This indicates the parent coordinator process.
    *   `usesysid` / `usename`: The user's internal ID and name. *(Note: In PostgreSQL, everything internally maps to a numeric ID).*
*   **Source Identification:**
    *   `client_addr`, `client_port`, `client_hostname`: Shows the IP and port of the client. (Hostnames are visible if `log_hostname` is enabled in `postgresql.conf`).
*   **The `application_name` Parameter:**
    *   Clients can (and should) freely set this parameter upon connecting (e.g., `SET application_name TO 'www.cybertec-postgresql.com';`).
    *   *Why it matters:* If thousands of connections are coming from a single IP, identifying the purpose of a specific connection is nearly impossible. Enforcing that clients pass a descriptive `application_name` allows administrators to easily group and diagnose misbehaving connections.
*   **Timestamps & State:**
    *   `backend_start`: When the connection was established.
    *   `xact_start`: When the current transaction started.
    *   `query_start`: When the active query started.
    *   `state_change`: When the connection last changed states (e.g., going from `active` to `idle`). *Tip: Subtracting `query_start` from `state_change` reveals the total execution time of the query.*
    *   `state`: Current status (e.g., `active` or `idle`).
    *   `query`: The text of the currently executing query (or the most recent query if idle).
*   **Deep Internals:**
    *   `wait_event_type` / `wait_event`: Indicates what the process is currently busy doing (e.g., waiting on disk I/O or a row lock).
    *   `backend_xmin`: The transaction ID of the oldest visible transaction to this process. This is critically important for `VACUUM` operations, as it determines which dead rows can be safely recycled.

#### Handling Bad Queries
If you use `pg_stat_activity` to identify an expensive, long-running query, PostgreSQL provides two administrative functions to stop it:

1.  **`pg_cancel_backend(pid)`:** This is the graceful approach. It interrupts and terminates the currently running query, but **keeps the connection alive** so the client can submit new queries.
2.  **`pg_terminate_backend(pid)`:** This is the radical approach. It abruptly kills the entire database connection alongside the query. The client will receive a `FATAL` error and must re-establish the connection.

*Warning: You can mass-terminate all other connections with the following query, but executing this in production will cause extreme downtime:*
```sql
SELECT pg_terminate_backend(pid) 
FROM pg_stat_activity 
WHERE pid <> pg_backend_pid() 
AND backend_type = 'client backend';
```

### `pg_stat_database` - Inspecting Database-Level Statistics

Once you have investigated active connections, you can zoom out to the database level. `pg_stat_database` returns exactly one row for every database within your PostgreSQL cluster, providing aggregated, historical statistics.

```sql
test=# \d pg_stat_database
                     View "pg_catalog.pg_stat_database"
          Column          |           Type           
--------------------------+--------------------------
 datid                    | oid                      
 datname                  | name                     
 numbackends              | integer                  
 xact_commit              | bigint                   
 xact_rollback            | bigint                   
 blks_read                | bigint                   
 blks_hit                 | bigint                   
 tup_returned             | bigint                   
 tup_fetched              | bigint                   
 tup_inserted             | bigint                   
 tup_updated              | bigint                   
 tup_deleted              | bigint                   
 conflicts                | bigint                   
 temp_files               | bigint                   
 temp_bytes               | bigint                   
 deadlocks                | bigint                   
 checksum_failures        | bigint                   
 checksum_last_failure    | timestamp with time zone 
 blk_read_time            | double precision         
 blk_write_time           | double precision         
 session_time             | double precision         
 active_time              | double precision         
 idle_in_transaction_time | double precision         
 sessions                 | bigint                   
 sessions_abandoned       | bigint                   
 sessions_fatal           | bigint                   
 sessions_killed          | bigint                   
 stats_reset              | timestamp with time zone 
```

#### Key Columns Explained
*   **Connection & Transaction Health:**
    *   `numbackends`: The number of connections currently open against this specific database.
    *   `xact_commit` / `xact_rollback`: Tracks total commits vs. rollbacks. A high ratio of rollbacks usually indicates application-level errors.
*   **Cache Performance:**
    *   `blks_hit` / `blks_read`: Tracks cache hits vs. cache misses. *Note: This exclusively measures PostgreSQL's internal `shared_buffers`. A "miss" here means Postgres asked the OS for the block—the OS might still have had it in its own filesystem cache, so a Postgres miss doesn't always equal a physical disk read.*
*   **Workload Profiling:**
    *   `tup_returned`, `tup_inserted`, `tup_updated`, `tup_deleted`: These tuple (row) counters reveal the fundamental nature of your database. Is it overwhelmingly read-heavy (OLAP) or write-heavy (OLTP)?
*   **The Danger of Temp Files:**
    *   `temp_files` / `temp_bytes`: **These are incredibly important columns.** If PostgreSQL runs out of allocated RAM while executing a sort, hash, or aggregation, it is forced to write temporary files to the physical disk. This destroys performance. 
    *   *Causes of high temp files:*
        1.  `work_mem` is configured too low in `postgresql.conf`.
        2.  Unoptimized, massively expensive queries executed in OLTP environments.
        3.  Normal administrative tasks like creating large indexes.

#### Measuring I/O Timing (`track_io_timing`)
By default, the `blk_read_time` and `blk_write_time` columns are completely empty. PostgreSQL disables I/O timing by default (`track_io_timing = off`) because making millions of system clock calls to time every block read can introduce severe CPU overhead.

```bash
[postgres@linux ~]$ pg_test_timing
Testing timing overhead for 3 seconds.
Per loop time including overhead: 23.16 nsec
Histogram of timing durations:
  < usec   % of total      count
       1     97.70300  126549189
       2      2.29506    2972668
       4      0.00024        317
       8      0.00008        101
      16      0.00160       2072
      32      0.00000          5
      64      0.00000          6
     128      0.00000          0
     256      0.00000          0
     512      0.00000          4
    1024      0.00000          0
    2048      0.00000          2
```

Before turning this on, you must test your server's clock overhead using the command-line tool `pg_test_timing`:
*   **Bare-metal Servers:** Usually ~14 to 25 nanoseconds of overhead (Safe to turn on).
*   **Cloud/Virtual Servers:** Usually ~100 to 120 nanoseconds (Proceed with caution).
*   **Poor Virtualization:** Up to 1,900 nanoseconds. (Do **not** turn on; it will cripple your database).

#### Tracking Table Bloat
*   `idle_in_transaction_time`: This tracks how much time the system has spent sitting in an "Idle in Transaction" state. This is critical because **idle transactions are the single biggest cause of table bloat**. While a transaction sits open, `VACUUM` is forbidden from cleaning up any dead rows across the entire database.
*   `session_*` fields: Introduced in PostgreSQL 14, these fields (`sessions`, `sessions_abandoned`, `sessions_fatal`) finally allow administrators to track the total historical volume and lifecycle of all connections made to the database.

### `pg_stat_user_tables` - Inspecting Individual Tables

After analyzing the cluster and database levels, you must drill down into individual tables. `pg_stat_user_tables` is arguably the most important, yet frequently ignored, system view. It holds the key to diagnosing the most common cause of poor performance: missing indexes.

```sql
test=# \d pg_stat_user_tables
                      View "pg_catalog.pg_stat_user_tables"
       Column        |           Type           | Collation | Nullable | Default 
---------------------+--------------------------+-----------+----------+---------
 relid               | oid                      |           |          | 
 schemaname          | name                     |           |          | 
 relname             | name                     |           |          | 
 seq_scan            | bigint                   |           |          | 
 last_seq_scan       | timestamp with time zone |           |          | 
 seq_tup_read        | bigint                   |           |          | 
 idx_scan            | bigint                   |           |          | 
 last_idx_scan       | timestamp with time zone |           |          | 
 idx_tup_fetch       | bigint                   |           |          | 
 n_tup_ins           | bigint                   |           |          | 
 n_tup_upd           | bigint                   |           |          | 
 n_tup_del           | bigint                   |           |          | 
 n_tup_hot_upd       | bigint                   |           |          | 
 n_tup_newpage_upd   | bigint                   |           |          | 
 n_live_tup          | bigint                   |           |          | 
 n_dead_tup          | bigint                   |           |          | 
 n_mod_since_analyze | bigint                   |           |          | 
 n_ins_since_vacuum  | bigint                   |           |          | 
 last_vacuum         | timestamp with time zone |           |          | 
 last_autovacuum     | timestamp with time zone |           |          | 
 last_analyze        | timestamp with time zone |           |          | 
 last_autoanalyze    | timestamp with time zone |           |          | 
 vacuum_count        | bigint                   |           |          | 
 autovacuum_count    | bigint                   |           |          | 
 analyze_count       | bigint                   |           |          | 
 autoanalyze_count   | bigint                   |           |          | 
```

#### Key Columns Explained
*   **Scan Statistics:**
    *   `seq_scan`: The total number of sequential scans executed against this table.
    *   `seq_tup_read`: **Vital Metric.** This tells you exactly how many tuples (rows) the database engine was forced to physically read from disk/memory during those sequential scans. 
    *   `idx_scan`: The number of times an index was successfully used to fetch data from this table.
*   **DML and HOT Updates:**
    *   `n_tup_ins`, `n_tup_upd`, `n_tup_del`: Standard counters for inserts, updates, and deletes.
    *   `n_tup_hot_upd`: **Heap-Only Tuple (HOT) Updates.** When a row is updated, PostgreSQL usually creates a brand new copy of the row to ensure MVCC (Multi-Version Concurrency Control) rollbacks work correctly. A "HOT update" occurs when the new copy of the row fits inside the exact same physical block as the old row. This bypasses the need to update indexes and is immensely beneficial for performance. A high ratio of HOT updates indicates a healthy, highly-optimized, UPDATE-intense workload.

#### The Golden Query: Finding Missing Indexes
Nearly two decades of database consulting reveal that **missing indexes are the single biggest cause of bad performance**. You can proactively find missing indexes by looking for tables with massive `seq_tup_read` values.

Execute this "Golden Query":
```sql
SELECT schemaname, relname, seq_scan, seq_tup_read,
       seq_tup_read / seq_scan AS avg, idx_scan
FROM pg_stat_user_tables
WHERE seq_scan > 0
ORDER BY seq_tup_read DESC
LIMIT 25;
```
*Note: Sequential scans are not inherently bad (they are used naturally for pg_dump backups or OLAP analytic queries). However, if this query surfaces a massive operational table in an OLTP environment with billions of `seq_tup_read`, it desperately needs an index.*

### `pg_statio_user_tables` - Table-Level Caching Behavior

```sql
test=# \d pg_statio_user_tables
                     View "pg_catalog.pg_statio_user_tables"
     Column      |  Type  | Collation | Nullable | Default 
-----------------+--------+-----------+----------+---------
 relid           | oid    |           |          | 
 schemaname      | name   |           |          | 
 relname         | name   |           |          | 
 heap_blks_read  | bigint |           |          | 
 heap_blks_hit   | bigint |           |          | 
 idx_blks_read   | bigint |           |          | 
 idx_blks_hit    | bigint |           |          | 
 toast_blks_read | bigint |           |          | 
 toast_blks_hit  | bigint |           |          | 
 tidx_blks_read  | bigint |           |          | 
 tidx_blks_hit   | bigint |           |          | 
```

While `pg_stat_user_tables` is your primary tool for finding missing indexes, `pg_statio_user_tables` allows you to inspect the exact caching behavior at the physical block level.

*   `heap_blks_read` / `heap_blks_hit`: How often table data was read from disk vs. found in `shared_buffers`.
*   `idx_blks_read` / `idx_blks_hit`: How often index data was read from disk vs. found in `shared_buffers`.
*   `toast_blks_read` / `toast_blks_hit`: How often oversized attributes (like massive JSON documents or long text fields stored in TOAST tables) were read from disk vs. cache.

### `pg_stat_user_indexes` - Digging Into Indexes

While `pg_stat_user_tables` helps find missing indexes, `pg_stat_user_indexes` is essential for finding indexes that **should not exist**. Redundant or unused indexes can consume massive amounts of storage (sometimes over 70% of total disk usage) and significantly slow down write operations.

```sql
test=# \d pg_stat_user_indexes
                      View "pg_catalog.pg_stat_user_indexes"
    Column     |           Type           | Collation | Nullable | Default 
---------------+--------------------------+-----------+----------+---------
 relid         | oid                      |           |          | 
 indexrelid    | oid                      |           |          | 
 schemaname    | name                     |           |          | 
 relname       | name                     |           |          | 
 indexrelname  | name                     |           |          | 
 idx_scan      | bigint                   |           |          | 
 last_idx_scan | timestamp with time zone |           |          | 
 idx_tup_read  | bigint                   |           |          | 
 idx_tup_fetch | bigint                   |           |          | 
```

#### Finding Redundant Indexes
Use the following query to identify indexes that are rarely used but consume significant space. It also calculates a running total of the wasted space:

```sql
SELECT schemaname, relname, indexrelname, idx_scan,
       pg_size_pretty(pg_relation_size(indexrelid)) AS idx_size,
       pg_size_pretty(sum(pg_relation_size(indexrelid)) 
                      OVER (ORDER BY idx_scan, indexrelid)) AS total_running_size
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC;
```

**Tip:** Do not drop indexes blindly. An index might be unused currently because users are accessing the application in a specific way, but could become vital if user behavior changes. Always verify before dropping.

---

### `pg_stat_bgwriter` - Tracking the Background Writer

PostgreSQL processes typically do not write data blocks to disk directly. Instead, they write to `shared_buffers`, and the data is eventually flushed to disk by the **Background Writer** or the **Checkpointer**.

In PostgreSQL 17, this view has been drastically shortened as many metrics were moved to separate views:

```sql
test=# \d pg_stat_bgwriter
                   View "pg_catalog.pg_stat_bgwriter"
      Column      |           Type           | Collation | Nullable | Default 
------------------+--------------------------+-----------+----------+---------
 buffers_clean    | bigint                   |           |          | 
 maxwritten_clean | bigint                   |           |          | 
 buffers_alloc    | bigint                   |           |          | 
 stats_reset      | timestamp with time zone |           |          | 
```

*   **`buffers_clean`**: How many buffers were written by the background writer.
*   **`maxwritten_clean`**: The number of times the background writer stopped a cleaning scan because it hit its maximum write limit (an indicator that it might be under-configured).
*   **`buffers_alloc`**: The total number of buffers that have been allocated.

---

### `pg_stat_io` - Inspecting I/O Statistics

As mentioned earlier, a significant amount of I/O tracking was removed from `pg_stat_bgwriter` in PostgreSQL 17. All of that information was relocated and unified into a powerful new system view: `pg_stat_io`.

The beauty of `pg_stat_io` is that it acts as a single pane of glass for all I/O activity occurring across the entire database system.

```sql
test=# \d pg_stat_io
                        View "pg_catalog.pg_stat_io"
     Column     |           Type           | Collation | Nullable | Default 
----------------+--------------------------+-----------+----------+---------
 backend_type   | text                     |           |          | 
 object         | text                     |           |          | 
 context        | text                     |           |          | 
 reads          | bigint                   |           |          | 
 read_time      | double precision         |           |          | 
 writes         | bigint                   |           |          | 
 write_time     | double precision         |           |          | 
 writebacks     | bigint                   |           |          | 
 writeback_time | double precision         |           |          | 
 extends        | bigint                   |           |          | 
 extend_time    | double precision         |           |          | 
 op_bytes       | bigint                   |           |          | 
 hits           | bigint                   |           |          | 
 evictions      | bigint                   |           |          | 
 reuses         | bigint                   |           |          | 
 fsyncs         | bigint                   |           |          | 
 fsync_time     | double precision         |           |          | 
 stats_reset    | timestamp with time zone |           |          | 
```

#### What `pg_stat_io` Tracks

1.  **Objects:** It tracks I/O for both permanent data and temporary operations.
    ```sql
    test=# SELECT DISTINCT object FROM pg_stat_io;
         object     
    ---------------
     temp relation
     relation
    ```

2.  **Contexts:** PostgreSQL 17 categorizes I/O into four distinct contexts, allowing you to instantly diagnose *why* disk activity is happening (e.g., is an I/O spike caused by normal traffic or an aggressive vacuum?).
    ```sql
    test=# SELECT DISTINCT context FROM pg_stat_io;
      context  
    -----------
     vacuum
     normal
     bulkread
     bulkwrite
    ```

3.  **Extends:** When a table or index fills its allocated space and needs to grow, PostgreSQL performs an "extend" operation. This view tracks exactly how many extends occur and how long they take.
4.  **Cache & Fsyncs:** The view also displays caching behavior (hits vs. evictions) and physical disk flush operations (`fsyncs`).

*Important Reminder: Just like with `pg_stat_database`, the `*_time` columns (like `read_time` and `fsync_time`) will remain empty unless you have explicitly enabled `track_io_timing` in your configuration.*

---

### Tracking, Archiving, and Streaming

This section covers the vital system views related to transaction log (WAL) archiving and replication streaming. These views are essential for guaranteeing High Availability (HA) and disaster recovery.

#### `pg_stat_archiver` - Monitoring Backup Health
The archiver process is responsible for moving transaction log files (WAL) from the main server to your backup storage.

```sql
test=# \d pg_stat_archiver
                       View "pg_catalog.pg_stat_archiver"
       Column       |           Type           | Collation | Nullable | Default 
--------------------+--------------------------+-----------+----------+---------
 archived_count     | bigint                   |           |          | 
 last_archived_wal  | text                     |           |          | 
 last_archived_time | timestamp with time zone |           |          | 
 failed_count       | bigint                   |           |          | 
 last_failed_wal    | text                     |           |          | 
 last_failed_time   | timestamp with time zone |           |          | 
 stats_reset        | timestamp with time zone |           |          | 
```
*   **The Danger of Silent Failures:** While `archived_count` is nice to look at, the most critical fields here are **`failed_count`** and **`last_failed_wal`**. If your transaction log archiving fails silently, you will lose point-in-time recoverability without noticing. You must actively monitor the `failed_count`.

#### `pg_stat_replication` - Monitoring the Sending Server (Primary)
If you are running streaming replication, this view provides one entry per WAL sender process. *If this view is empty, no transaction log streaming is happening!*

```sql
test=# \d pg_stat_replication
                      View "pg_catalog.pg_stat_replication"
      Column      |           Type           | Collation | Nullable | Default 
------------------+--------------------------+-----------+----------+---------
 pid              | integer                  |           |          | 
 usesysid         | oid                      |           |          | 
 usename          | name                     |           |          | 
 application_name | text                     |           |          | 
 client_addr      | inet                     |           |          | 
 client_hostname  | text                     |           |          | 
 client_port      | integer                  |           |          | 
 backend_start    | timestamp with time zone |           |          | 
 backend_xmin     | xid                      |           |          | 
 state            | text                     |           |          | 
 sent_lsn         | pg_lsn                   |           |          | 
 write_lsn        | pg_lsn                   |           |          | 
 flush_lsn        | pg_lsn                   |           |          | 
 replay_lsn       | pg_lsn                   |           |          | 
 write_lag        | interval                 |           |          | 
 flush_lag        | interval                 |           |          | 
 replay_lag       | interval                 |           |          | 
 sync_priority    | integer                  |           |          | 
 sync_state       | text                     |           |          | 
 reply_time       | timestamp with time zone |           |          | 
```
*   **Connection Data:** Shows the standard identity columns (`application_name`, `client_addr`). A very young `backend_start` in production can indicate a network flapping issue where replicas constantly disconnect.
*   **Log Sequence Numbers (LSN):** Shows precisely where the replica is in the WAL lifecycle (`sent_lsn`, `write_lsn`, `flush_lsn`, `replay_lsn`).
*   **Lag Tracking:** Introduced in PG 10, the `*_lag` fields provide the actual time difference (as an `interval`) between the primary server and the replica.
*   *(Note: PG 13 added `pg_stat_replication_slots` containing `spill_*` fields to track when logical decoding is forced to spill to disk, which causes performance issues).*

#### `pg_stat_wal_receiver` - Monitoring the Receiving Server (Replica)
While the previous view is queried on the Primary, this view is the counterpart queried directly on the Replica server.

```sql
test=# \d pg_stat_wal_receiver
                      View "pg_catalog.pg_stat_wal_receiver"
        Column         |           Type           | Collation | Nullable | Default 
-----------------------+--------------------------+-----------+----------+---------
 pid                   | integer                  |           |          | 
 status                | text                     |           |          | 
 receive_start_lsn     | pg_lsn                   |           |          | 
 receive_start_tli     | integer                  |           |          | 
 written_lsn           | pg_lsn                   |           |          | 
 flushed_lsn           | pg_lsn                   |           |          | 
 received_tli          | integer                  |           |          | 
 last_msg_send_time    | timestamp with time zone |           |          | 
 last_msg_receipt_time | timestamp with time zone |           |          | 
 latest_end_lsn        | pg_lsn                   |           |          | 
 latest_end_time       | timestamp with time zone |           |          | 
 slot_name             | text                     |           |          | 
 sender_host           | text                     |           |          | 
 sender_port           | integer                  |           |          | 
 conninfo              | text                     |           |          | 
```
*   **Status & Initialization:** Tracks the connection `status` and what LSN/Timeline (TLI) the receiver started on (`receive_start_lsn`, `receive_start_tli`).
*   **Timestamps:** Compares `last_msg_send_time` with `last_msg_receipt_time` to identify network latency.
*   **Network Info:** `sender_host` and `sender_port` (added in PG 11) clarify exactly which upstream Primary the replica is pulling data from.

---

### `pg_stat_ssl` - Checking SSL Connections

Many modern database deployments rely on SSL to encrypt client-server communication. `pg_stat_ssl` provides an overview of these encrypted connections.

```sql
test=# \d pg_stat_ssl
                     View "pg_catalog.pg_stat_ssl"
    Column     |  Type   | Collation | Nullable | Default 
---------------+---------+-----------+----------+---------
 pid           | integer |           |          | 
 ssl           | boolean |           |          | 
 version       | text    |           |          | 
 cipher        | text    |           |          | 
 bits          | integer |           |          | 
 client_dn     | text    |           |          | 
 client_serial | numeric |           |          | 
 issuer_dn     | text    |           |          | 
```

*   **`ssl`:** A boolean indicating if the connection mapped to `pid` is using SSL.
*   **Encryption Details:** Shows the exact SSL `version`, `cipher`, and `bits` (encryption strength/compression) being utilized.
*   **Client Information:** Extracts the Distinguished Name (`client_dn`) from the client certificate.

---

### `pg_stat_xact_user_tables` - Inspecting Transactions in Real Time

All the statistical views discussed previously aggregate system-wide data. But what if you are an application developer who wants to isolate and inspect the performance impact of a *single, specific transaction*? 

Enter `pg_stat_xact_user_tables`.

```sql
test=# \d pg_stat_xact_user_tables
                  View "pg_catalog.pg_stat_xact_user_tables"
      Column       |  Type   | Collation | Nullable | Default 
-------------------+---------+-----------+----------+---------
 relid             | oid     |           |          | 
 schemaname        | name    |           |          | 
 relname           | name    |           |          | 
 seq_scan          | bigint  |           |          | 
 seq_tup_read      | bigint  |           |          | 
 idx_scan          | bigint  |           |          | 
 idx_tup_fetch     | bigint  |           |          | 
 n_tup_ins         | bigint  |           |          | 
 n_tup_upd         | bigint  |           |          | 
 n_tup_del         | bigint  |           |          | 
 n_tup_hot_upd     | bigint  |           |          | 
 n_tup_newpage_upd | bigint  |           |          | 
```

**Developer Best Practice:**
The columns here are identical to `pg_stat_user_tables`, but the context is scoped entirely to the **current transaction**. 

To use this effectively:
1.  Begin your transaction.
2.  Execute all the complex `INSERT`, `UPDATE`, or `SELECT` statements required by the application logic.
3.  **Just before calling `COMMIT`**, query `pg_stat_xact_user_tables`. 

This will output the exact number of sequential scans, index fetches, and HOT updates caused *exclusively* by that specific transaction, completely isolated from the overall system workload noise.

---

### Tracking Progress of Administrative Tasks

When executing heavy administrative operations on large databases, you want to know how long it will take. PostgreSQL provides views to track the real-time progress of `VACUUM` and `CREATE INDEX` operations.

#### `pg_stat_progress_vacuum`
Introduced in PostgreSQL 9.6, this view reveals what a long-running vacuum process is currently doing.

```sql
test=# \d pg_stat_progress_vacuum
                   View "pg_catalog.pg_stat_progress_vacuum"
        Column        |  Type  | Collation | Nullable | Default 
----------------------+--------+-----------+----------+---------
 pid                  | integer|           |          | 
 datid                | oid    |           |          | 
 datname              | name   |           |          | 
 relid                | oid    |           |          | 
 phase                | text   |           |          | 
 heap_blks_total      | bigint |           |          | 
 heap_blks_scanned    | bigint |           |          | 
 heap_blks_vacuumed   | bigint |           |          | 
 index_vacuum_count   | bigint |           |          | 
 max_dead_tuple_bytes | bigint |           |          | 
 dead_tuple_bytes     | bigint |           |          | 
 num_dead_item_ids    | bigint |           |          | 
 indexes_total        | bigint |           |          | 
 indexes_processed    | bigint |           |          | 
```
*   **Behavioral Note:** The progress reported here is not strictly linear and numbers can jump around. Because vacuums are usually fast, it can be hard to catch them in this view unless you are vacuuming a massive table.

#### `pg_stat_progress_create_index`
Introduced in PostgreSQL 12, this view acts as the counterpart for tracking index creation.

```sql
test=# \d pg_stat_progress_create_index
                View "pg_catalog.pg_stat_progress_create_index"
       Column       |  Type  | Collation | Nullable | Default 
--------------------+--------+-----------+----------+---------
 pid                | integer|           |          | 
 datname            | name   |           |          | 
 relid              | oid    |           |          | 
 index_relid        | oid    |           |          | 
 command            | text   |           |          | 
 phase              | text   |           |          | 
 lockers_total      | bigint |           |          | 
 lockers_done       | bigint |           |          | 
 current_locker_pid | bigint |           |          | 
 blocks_total       | bigint |           |          | 
 blocks_done        | bigint |           |          | 
 tuples_total       | bigint |           |          | 
 tuples_done        | bigint |           |          | 
 partitions_total   | bigint |           |          | 
 partitions_done    | bigint |           |          | 
```

**Understanding the Phases of Index Creation**
If you run `CREATE INDEX` on a table with 50 million rows, you will see the `phase` column transition through several steps:

1.  **Phase 1: `building index: scanning table`**
    *   PostgreSQL is performing a sequential scan of the table to read the data.
    *   Monitor the `blocks_done` vs. `blocks_total` columns to track completion of this phase.
2.  **Phase 2: `building index: loading tuples in tree`**
    *   PostgreSQL is actively constructing the B-Tree index structure.
    *   Monitor the `tuples_done` vs. `tuples_total` columns (e.g., `4,289,774` / `50,000,000` means it is ~8.5% done building the tree).

---

### `pg_stat_statements` - The Ultimate Query Analyzer

If you want to spot performance problems, `pg_stat_statements` is arguably the most important view in PostgreSQL. It is vastly superior to a standard "slow query log." A slow query log only catches individual slow queries, missing the "death by a thousand cuts" caused by millions of medium-speed queries. `pg_stat_statements` tracks everything.

#### Setup Instructions
Because it tracks everything, it requires being loaded into shared memory at startup.
1. Add it to your configuration: `shared_preload_libraries = 'pg_stat_statements'` in `postgresql.conf`.
2. Restart the PostgreSQL server.
3. Run `CREATE EXTENSION pg_stat_statements;` inside the databases you want to monitor.

#### How It Works
PostgreSQL aggregates identical queries by stripping out hardcoded parameters and replacing them with placeholders (e.g., `SELECT * FROM x WHERE y = 10` becomes `SELECT * FROM x WHERE y = ?`).

```sql
test=# \d pg_stat_statements
                      View "public.pg_stat_statements"
        Column         |           Type           | Collation | Nullable | Default 
-----------------------+--------------------------+-----------+----------+---------
 userid                | oid                      |           |          | 
 dbid                  | oid                      |           |          | 
 toplevel              | boolean                  |           |          | 
 queryid               | bigint                   |           |          | 
 query                 | text                     |           |          | 
 plans                 | bigint                   |           |          | 
 total_plan_time       | double precision         |           |          | 
 min_plan_time         | double precision         |           |          | 
 max_plan_time         | double precision         |           |          | 
 mean_plan_time        | double precision         |           |          | 
 stddev_plan_time      | double precision         |           |          | 
 calls                 | bigint                   |           |          | 
 total_exec_time       | double precision         |           |          | 
 min_exec_time         | double precision         |           |          | 
 max_exec_time         | double precision         |           |          | 
 mean_exec_time        | double precision         |           |          | 
 stddev_exec_time      | double precision         |           |          | 
 rows                  | bigint                   |           |          | 
 shared_blks_hit       | bigint                   |           |          | 
 shared_blks_read      | bigint                   |           |          | 
 shared_blks_dirtied   | bigint                   |           |          | 
 shared_blks_written   | bigint                   |           |          | 
 local_blks_hit        | bigint                   |           |          | 
 local_blks_read       | bigint                   |           |          | 
 local_blks_dirtied    | bigint                   |           |          | 
 local_blks_written    | bigint                   |           |          | 
 temp_blks_read        | bigint                   |           |          | 
 temp_blks_written     | bigint                   |           |          | 
 shared_blk_read_time  | double precision         |           |          | 
 shared_blk_write_time | double precision         |           |          | 
 local_blk_read_time   | double precision         |           |          | 
 local_blk_write_time  | double precision         |           |          | 
 temp_blk_read_time    | double precision         |           |          | 
 temp_blk_write_time   | double precision         |           |          | 
 wal_records           | bigint                   |           |          | 
 wal_fpi               | bigint                   |           |          | 
 wal_bytes             | numeric                  |           |          | 
 jit_functions         | bigint                   |           |          | 
 jit_generation_time   | double precision         |           |          | 
 jit_inlining_count    | bigint                   |           |          | 
 jit_inlining_time     | double precision         |           |          | 
 jit_optimization_count| bigint                   |           |          | 
 jit_optimization_time | double precision         |           |          | 
 jit_emission_count    | bigint                   |           |          | 
 jit_emission_time     | double precision         |           |          | 
 jit_deform_count      | bigint                   |           |          | 
 jit_deform_time       | double precision         |           |          | 
 stats_since           | timestamp with time zone |           |          | 
 minmax_stats_since    | timestamp with time zone |           |          | 
```

#### Key Diagnostic Columns

1.  **`stddev_exec_time` (Standard Deviation):** 
    *   If this number is high, the query has an unstable runtime. This could be due to:
        *   Caching: The query is sometimes served from RAM (fast) and sometimes from disk (slow). Check the `shared_blks_hit` vs `shared_blks_read` columns to confirm.
        *   Parameters: Different parameters lead to completely different execution plans.
        *   Locking: The query is occasionally getting stuck behind other transactions.
2.  **`temp_blks_read` / `temp_blks_written`:**
    *   Temporary file I/O is normal during large maintenance operations. However, in an OLTP workload, high temp block usage is a massive red flag. It usually points to:
        *   Queries that shouldn't be running.
        *   A severely misconfigured (too small) `work_mem` setting forcing sorting/hashing to spill to disk.

#### Best Practice: The "Top 10" Query
Instead of guessing what is slow, run this query to identify the top 10 queries consuming the most total execution time across your entire system:

```sql
SELECT
    round((100 * total_exec_time / sum(total_exec_time) OVER ())::numeric, 2) AS percent,
    round(total_exec_time::numeric, 2) AS total,
    calls,
    round(mean_exec_time::numeric, 2) AS mean,
    substring(query, 1, 40) AS query_preview
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 10;
```
*(Tip: Always ORDER BY when querying this view, otherwise you will be overwhelmed by the default 5,000 statements tracked).*

**Resetting the Stats:**
To clear out historical data and establish a fresh baseline for debugging:
```sql
SELECT pg_stat_statements_reset();
```

---

### Server Logging Configuration

While system views show what is happening right now, log files provide the historical context needed to diagnose crashes, errors, and long-term trends. Logging is controlled entirely within `postgresql.conf`.

By default on Unix systems, PostgreSQL sends log information to `stderr`. However, this is usually undesirable for production environments because you need to persist and rotate logs properly.

#### 1. Defining Log Destinations
First, you must instruct PostgreSQL on *how* to process logs.

```ini
# - Where to Log -
log_destination = 'stderr'  # Options: stderr, csvlog, jsonlog, syslog, eventlog (Windows)
logging_collector = on      # MUST BE 'on' to capture stderr/csvlog into physical files
```
*Note: Changing `logging_collector` requires a full server restart.*

#### 2. Log File Location and Naming
Once the collector is on, tell it where to store the files and how to name them.

```ini
log_directory = 'pg_log'                             # Relative to PGDATA, or use absolute path
log_filename = 'postgresql-%Y-%m-%d_%H%M%S.log'      # Supports strftime() placeholders
```
Using a relative path keeps your data directory self-contained, which makes server migrations much easier. The filename pattern is extremely flexible, supporting over 40 `strftime` placeholders.

#### 3. Log Rotation Strategies
You do not want a single log file to grow infinitely and consume all disk space. PostgreSQL provides several triggers for auto-rotation:

```ini
log_rotation_age = 1d       # Rotate every day
log_rotation_size = 10MB    # Rotate when file hits 10MB
log_truncate_on_rotation = off  # Append to existing files instead of overwriting
```

**Best Practice: The 7-Day Rolling Log**
A highly effective setup for maintaining a week of logs without infinite disk growth is to use the `%a` (day of week) placeholder combined with truncation:

```ini
log_filename = 'postgresql_%a.log'
log_truncate_on_rotation = on
log_rotation_age = 1d
log_rotation_size = 0       # Disable size-based rotation
```
This guarantees you will always have exactly 7 log files (e.g., `postgresql_Mon.log`, `postgresql_Tue.log`). On Monday, it will overwrite last Monday's file.

#### 4. Configuring Syslog
Many sysadmins prefer shipping logs to `syslog` for centralized management. To do this, simply set `log_destination = 'syslog'` and configure the identifiers:

```ini
syslog_facility = 'LOCAL0'
syslog_ident = 'postgres'
```

#### 5. Logging Slow Queries
Historically, the "slow query log" was the only way to find bad performance. While `pg_stat_statements` is now superior for aggregate analysis, the slow query log is still useful for catching specific, anomalous spikes.

```ini
log_min_duration_statement = 500  # Logs any query taking longer than 500 milliseconds
```
*   **Word of Caution:** Do not treat the slow query log as absolute truth. A few expected, slow queries (like data exports) are often less harmful to your system than thousands of highly concurrent, fast queries that overwhelm the CPU.

#### 6. Defining What to Log
By default, PostgreSQL only logs errors. To gain deeper visibility, adjust these parameters:

*   **Connections:**
    ```ini
    log_connections = on
    log_disconnections = on
    ```
    *Warning: While great for security audits or analytical databases, turning this on for OLTP systems with high connection churn will severely degrade performance.*

*   **Checkpoints & Locks:**
    ```ini
    log_checkpoints = on
    log_lock_waits = on
    ```
    `log_lock_waits` is incredibly valuable. If a transaction waits for a lock longer than `deadlock_timeout` (usually 1 second), it gets logged. This is the fastest way to detect blocking issues.

*   **Statement Logging (`log_statement`):**
    ```ini
    log_statement = 'ddl'  # Options: 'none', 'ddl', 'mod', 'all'
    ```
    *   `none`: Only errors.
    *   `ddl`: Logs structure changes (`CREATE`, `ALTER`, `DROP`). (Highly recommended).
    *   `mod`: Logs DDLs + Data modifications (`INSERT`, `UPDATE`, `DELETE`).
    *   `all`: Logs everything. Will destroy OLTP performance due to massive disk I/O.

*   **Temporary File Spikes (`log_temp_files`):**
    ```ini
    log_temp_files = 4096  # Logs queries producing temp files larger than 4MB
    ```
    While `pg_stat_statements` shows aggregate temporary I/O, `log_temp_files` points to the exact, specific query that caused the disk spill. This is essential for tuning `work_mem` or catching unoptimized analytic queries.

#### 7. Formatting the Log Output
Before PostgreSQL 10, log lines were very barebones. You must ensure your logs contain enough metadata to be useful. This is controlled by `log_line_prefix`.

```ini
log_line_prefix = '%m [%p] %u@%d '  
```
*   `%m`: Timestamp with milliseconds (Crucial for knowing *when* something broke).
*   `%p`: Process ID.
*   `%u`: Username.
*   `%d`: Database name.
*   `%x`: Transaction ID.

#### 8. Controlling Log Volume via Sampling
If your database is massive and logging everything causes severe disk I/O, you can use sampling rates to capture only a percentage of events.

```ini
log_statement_sample_rate = 1.0     # 1.0 = 100%, 0.1 = 10%
log_transaction_sample_rate = 0.0
log_min_duration_sample = -1
```

*Summary: Configure logging wisely. Extract exactly as much information as you need to diagnose issues, but avoid excessive logging that creates a performance bottleneck of its own.*

#### 9. Monitoring Replication Conflicts
In high-availability setups, a Replica server might be forced to suddenly terminate a long-running read query because of a "replication conflict" (i.e., the Primary server deleted or updated the row the Replica was trying to read).

```ini
log_recovery_conflict_waits = on
```
If enabled, PostgreSQL will send a message to the log if a replication conflict persists longer than `deadlock_timeout`. This is crucial for detecting when your read-heavy analytics queries on replicas are being violently cancelled or blocked.

*Tip: If your logs show extensive replication conflicts, consider researching and enabling `hot_standby_feedback = on`. This tells the Primary server not to vacuum away dead rows that the Standby server is still actively querying.*

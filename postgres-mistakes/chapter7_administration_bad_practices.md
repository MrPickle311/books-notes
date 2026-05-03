# Chapter 7: Administration Bad Practices

Even the most optimized PostgreSQL database will fail if its daily administration is neglected. This chapter covers the most common and dangerous administrative mistakes that can lead to performance degradation, security breaches, or catastrophic data loss.

## 1. Not Tracking Disk Usage
Running out of disk space is one of the most common causes of database outages. If the disk containing your Write-Ahead Log (WAL) reaches 100% capacity, PostgreSQL cannot journal transactions and will immediately panic and shut down.

### The Dangers of Panic Reactions
When disk space hits 100%, administrators often attempt "quick fixes" that make the situation worse:
*   **Attempting to `DELETE` data:** In PostgreSQL's MVCC architecture, deleting a row requires writing a new WAL record. If the disk is completely full, the `DELETE` command will fail due to lack of space. Even if successful, it only marks rows as "dead" and does not return space to the OS without a blocking `VACUUM FULL`.
*   **Resizing Cloud Volumes:** Resizing a live volume on many cloud providers pauses all I/O, causing unexpected application downtime.

### The Ultimate Mistake: Deleting WAL Files
In older versions, the WAL was stored in `pg_xlog` and the Commit Log in `pg_clog`. Inexperienced administrators would see the word "log" and delete these directories to free up space. **Deleting anything inside the WAL directory instantly corrupts and nukes the database.** 
*(To prevent this, modern versions renamed these to `pg_wal` and `pg_xact`, and the actual text logs to `log`).*

### What Consumes Disk Space?
*   **Table Bloat:** Accumulation of dead rows due to inadequate Autovacuuming.
*   **Temporary Files:** Queries with massive sorts or hashes that exceed `work_mem` spill to disk.
*   **Abandoned Replication Slots:** If a standby goes offline, the primary hoards WAL files indefinitely.
*   **Failing `archive_command`:** WAL files accumulate because they cannot be shipped to the backup repository.

**Mitigation:** Place `PGDATA` (data files) and `pg_wal` (transaction logs) on completely separate physical disks or partitions. Monitor both aggressively.

---

## 2. Logging to PGDATA
By default, PostgreSQL writes its text logs to the same filesystem as the database itself. This introduces a vulnerability: **Log-induced Disk Exhaustion.**

If a developer deploys a bug—such as an infinite loop querying a table with a typo in the name—PostgreSQL will log a high-frequency stream of `ERROR: relation does not exist` messages. This can generate gigabytes of text logs in minutes. If the logs share the `PGDATA` disk, this infinite loop will rapidly consume 100% of the disk and crash the entire database.

**Mitigation:** Always store text logs on a separate partition. Configure log rotation using `log_rotation_size` and `log_rotation_age`.

---

## 3. Ignoring the Logs
Many administrators only check the logs *after* an outage. PostgreSQL logs act as a near real-time diagnostic feed that can warn you of impending doom:

*   **Bad Configurations:** `FATAL: requested standby connections exceeds max_wal_senders` (Replication is broken).
*   **Memory Issues:** Frequent `LOG: temporary file: path...` messages indicate your `work_mem` is too low for your queries.
*   **Performance Bottlenecks:** `LOG: duration: 157 ms statement: ...` (Caught by setting `log_min_duration_statement`).
*   **I/O Overload:** `LOG: checkpoints are occurring too frequently` (Increase `max_wal_size` to reduce disk thrashing).
*   **Application Logic Bugs:** `ERROR: deadlock detected` (Requires fixing the order of operations in the application code).
*   **Hardware Failure:** `ERROR: could not read block... invalid page` (Early warning of disk corruption).
*   **Security Breaches:** Rapid, repeated `FATAL: password authentication failed` (Brute-force attack).

**Mitigation:** Use tools like `pgBadger` to parse the logs and generate HTML reports summarizing slow queries and errors.

---

## 4. Failing to Monitor the Database
"A tested system is a stable system" is a dangerous myth. Workloads change, and hardware degrades. Without active monitoring, you are flying blind.

**Crucial Metrics to Track:**
*   Disk and Temporary File Usage.
*   Replication Lag (`pg_stat_replication`).
*   Active, Waiting, and Blocked Connections (`pg_stat_activity`).
*   Transaction ID Age (to prevent the catastrophic Transaction ID Wraparound).

Use standard tools (Prometheus, Zabbix, PGWatch) combined with the built-in `pg_monitor` role to achieve full observability.

---

## 5. Not Tracking Statistics Over Time
Monitoring tools excel at telling you what is happening *right now*, but they often drop historical data to save space. Without historical trends, you cannot perform capacity planning.

To predict when you need to upgrade server hardware, you must track cache hit ratios, I/O rates, and database growth over months or years.
*(Consider lightweight, in-database extensions like `pg_statviz` to snapshot and plot performance metrics over time using `pg_cron`).*

---

## 6. Refusing to Upgrade PostgreSQL
Staying on an old major version because "it works fine now" exposes your infrastructure to severe risks:
1.  **Security Vulnerabilities:** You miss out on critical security patches.
2.  **Unpatched Bugs:** You will likely hit a data corruption bug that was already fixed in a newer version.
3.  **Missing Features:** Refusing to upgrade means missing out on massive leaps forward (e.g., declarative partitioning, logical replication, pipelining).

Upgrading major versions is safe and well-documented using `pg_upgrade`. Always read the release notes, even for minor versions!

---

## 7. Refusing to Upgrade the Operating System
Even if PostgreSQL is up to date, running it on an ancient Linux distribution or using outdated system libraries can cause obscure, impossible-to-debug issues.

For example, an outdated `glibc` library might contain an unoptimized math function. This can cause geospatial queries (like PostGIS `ST_DistanceSphere`) to run 50x slower for specific coordinates due to trigonometric bugs deep inside the OS. 

**Never lock down system dependencies out of fear of breakage; the risk of missing critical fixes is always higher.**

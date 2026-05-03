# Chapter 10: Making Sense of Backups and Replication

While logical backups (like `pg_dump`) are useful, they struggle to scale in large environments because they take an increasing amount of time as data volume grows. For large-scale, enterprise systems, we must rely on **binary backups**, the **transaction log**, and **replication**.

This chapter covers the foundational concepts required to implement high availability, Point-in-Time Recovery (PITR), and both synchronous and asynchronous replication.

---

## Understanding the Transaction Log (WAL)

Every modern database must guarantee that it can survive a sudden crash or power loss without corrupting its data. PostgreSQL achieves this through **Write-Ahead Logging (WAL)**.

### The Problem with Direct Writes
Imagine you execute `INSERT INTO data VALUES ('12345678');`. 
If PostgreSQL wrote this data directly into the underlying table's data file, and the server lost power mid-write, the file would be permanently corrupted. It might contain half-written rows, broken index pointers, or missing commit information because underlying hardware cannot guarantee atomic writes for large data chunks.

### The WAL Solution
To prevent corruption, PostgreSQL writes the change to a sequential log *first*—the Write-Ahead Log. 
*   The WAL consists of records chained together, protected by a header and a checksum.
*   A single SQL transaction might generate dozens of WAL records (modifying the B-tree, updating the storage manager, writing the commit record).
*   If a crash occurs, PostgreSQL simply reads the WAL upon reboot and replays the chained records to safely repair and reconstruct the data files.

---

## Looking at the Transaction Log

By default, the transaction log files live inside the `pg_wal` directory, which is located inside your main data directory (`$PGDATA`).

### File Structure
When you look inside `pg_wal`, you will see files with 24-digit hexadecimal names (e.g., `000000010000002F00000030`).

*   **Fixed Size:** By default, every WAL file is exactly **16 MB** in size.
*   **Transaction Size vs. File Count:** The number of WAL files currently sitting in your directory is *not* related to the size of your active transactions. PostgreSQL recycles and rotates these files. You can execute a multi-terabyte transaction while only maintaining a small, fixed number of WAL files on disk.

### Customizing WAL Segment Size
Historically, WAL files were strictly 16 MB. However, in modern PostgreSQL, you can change the size of a WAL segment during the initial creation of the database cluster.

In some high-throughput scenarios, increasing the segment size can yield performance benefits. You configure this using `initdb`:

```bash
initdb -D /pgdata --wal-segsize=32
```
This command initializes a new database cluster where every WAL file is 32 MB instead of the default 16 MB.

---

## Understanding Checkpoints

Because every change is logged sequentially, the transaction log would eventually consume all available disk space if left unchecked. To prevent this, PostgreSQL must periodically "recycle" old log files. This process is triggered by a **checkpoint**.

1.  When a transaction occurs, the changes are written to the WAL on disk, and the actual data pages in memory (RAM) become "dirty buffers."
2.  During a checkpoint, the background writer forcefully flushes all of these dirty buffers from memory down to the actual data files on disk.
3.  Once the data is safely written to the permanent data files, the older WAL files that recorded those changes are no longer needed for crash recovery. PostgreSQL will either delete or recycle them.

> [!CAUTION]
> **Never manually delete files from `pg_wal`.** 
> PostgreSQL manages WAL space automatically. If you delete these files by hand to clear disk space, and the database crashes, it will be unable to start back up, permanently destroying your database.

### Optimizing the Transaction Log

Checkpoints happen automatically, triggered by either reaching a specific **time limit** or a **size limit**. You can configure these in `postgresql.conf`:

```ini
checkpoint_timeout = 5min
min_wal_size = 80MB
max_wal_size = 1GB
```
*(Note: `max_wal_size` is a "soft limit". Under heavy load, PostgreSQL might temporarily overshoot this size. Ensure your WAL partition always has a buffer of free space).*

#### The "Larger Distance" Paradox
You might assume that frequent checkpoints save disk space. In reality, **larger checkpoint distances lead to LESS overall WAL being created.**

When a block of data is touched for the very first time *after* a checkpoint, PostgreSQL writes the entire block (a "full-page write") to the WAL to ensure safety. If that block is changed 100 more times before the next checkpoint, only the tiny byte-level changes are logged. 
If you checkpoint constantly, PostgreSQL is forced to write expensive full-page writes over and over again, massively inflating WAL size. Stretching out your checkpoints significantly reduces write amplification.

#### Smoothing I/O Spikes
Checkpoints cause massive disk activity. Instead of flushing all dirty buffers to disk as fast as possible (which stalls the database), PostgreSQL can "stretch out" the write load:

```ini
checkpoint_completion_target = 0.9
```
A value of `0.9` (the default in modern PostgreSQL) tells the system to spread the disk writes out so that they finish when the next checkpoint is 90% due. This flattens I/O spikes and prevents performance degradation during checkpoints.

#### Filesystem-Specific Optimizations
By default, PostgreSQL renames and recycles old WAL files instead of creating new ones from scratch:
```ini
wal_recycle = on
```
This is excellent for traditional filesystems (ext4, xfs). However, if you are running PostgreSQL on a **Copy-on-Write (COW)** filesystem like `btrfs` or `ZFS`, recycling files creates severe fragmentation. On COW filesystems, you should set `wal_recycle = off` for better performance.

---

## Transaction Log Archiving and PITR

Because the transaction log contains a complete sequential history of every binary change made to the database, we can archive it. Archiving the WAL is the foundation of **Point-in-Time Recovery (PITR)**.

### Advantages of PITR over Logical Backups
Standard logical dumps (like `pg_dump`) have significant limitations compared to PITR:
1.  **Less Data Loss:** A dump only restores data up to the exact moment the dump was taken. PITR allows you to roll forward and restore data up to a specific second, right before a disaster occurred.
2.  **Faster Restoration:** Logical dumps require PostgreSQL to slowly rebuild indexes from scratch upon restore. PITR restores physical files, meaning indexes are already built and ready to use immediately.

### Configuring for Archiving
To enable archiving and PITR, you must adjust the `postgresql.conf` file:

```ini
wal_level = replica
max_wal_senders = 10
```
*   `wal_level = replica`: Instructs PostgreSQL to write enough detailed information into the WAL to support replication and PITR. (If left at `minimal`, the WAL cannot be used for recovery).
*   `max_wal_senders`: Limits the number of active streams allowed to pull WAL from the server. This is required for streaming replicas and tools like `pg_basebackup`.

#### Archiving via the Filesystem
During normal operation, PostgreSQL constantly generates 16MB WAL files. When a file is full, we need to move it to a safe, external storage location. 

Assuming you have a secure directory (`/archive`) owned by the `postgres` user, you configure archiving like this:

```ini
archive_mode = on
archive_command = 'cp %p /archive/%f'
```
*   `%p`: The full path to the WAL file PostgreSQL wants to archive.
*   `%f`: Only the file name.

> [!IMPORTANT]
> **The Exit Code is Critical:** You can put any shell command in `archive_command` (e.g., `rsync`, `scp`, or an AWS S3 CLI command). 
> PostgreSQL relies strictly on the script's exit code. If your script returns `0`, PostgreSQL assumes the file is safely archived and recycles the local WAL file. If the script returns *anything else*, PostgreSQL assumes failure and will retry indefinitely. It does this to guarantee that absolutely no WAL files go missing, which would break the unbroken chain needed for recovery.

### Using Archiving Libraries
Executing a shell script (`archive_command`) every time a 16MB file fills up introduces overhead. To improve efficiency, modern PostgreSQL introduced **archiving libraries**:

```ini
archive_library = ''
```
Instead of spawning external shell processes, backup software vendors can write compiled libraries that plug directly into the PostgreSQL engine to handle archiving natively. If `archive_library` is left empty, the system falls back to using the traditional `archive_command`.

---

## Configuring `pg_hba.conf` for Replication

While we have configured `postgresql.conf` to generate and send WAL, we must also configure network authentication in `pg_hba.conf` to allow external tools (like `pg_basebackup`) or replicas to connect and stream the WAL.

Standard database connection rules are not enough. Replication connections require a specialized entry where the database name is explicitly set to **`replication`**.

### Example Configuration

```ini
# TYPE  DATABASE        USER            ADDRESS                 METHOD

# Allow local replication connections
local   replication     postgres                                trust

# Allow remote replication connections
host    replication     replicator      192.168.1.50/32         scram-sha-256
```

### Best Practices for Replication Authentication
1.  **Use a Dedicated Role:** While the default examples often use the `postgres` superuser, it is highly recommended to create a dedicated user strictly for replication to minimize security exposure.
    ```sql
    CREATE ROLE replicator WITH REPLICATION LOGIN ENCRYPTED PASSWORD 'secret';
    ```
2.  **Add Remote IPs:** Modern PostgreSQL versions typically include `local` replication rules out of the box, but if you are pulling backups or streaming to a standby server across the network, you must add explicit `host` entries for those remote IPs.

Once your `pg_hba.conf` file is updated with the correct `replication` rules, restart the PostgreSQL service to apply the networking changes.

---

## Creating Base Backups

With the server configured to archive WAL, we now need to establish an initial foundation—a **base backup**. This base backup, combined with the archived WAL files, allows us to reach any point in time.

The standard tool for this is the `pg_basebackup` command-line utility. 

### Basic Usage
```bash
pg_basebackup -D /some_target_dir -h localhost --checkpoint=fast --wal-method=stream
```

Let's break down these critical parameters:
*   **`-D` (Destination):** Specifies the directory where the backup should be written. This directory must be completely empty.
*   **`-h` (Host):** The IP address or hostname of the primary database you are backing up.
*   **`--checkpoint=fast`:** By default, `pg_basebackup` politely waits for the primary server to execute a naturally occurring checkpoint before it begins copying files. Because checkpoints can be up to an hour apart, this can delay your backup indefinitely. `--checkpoint=fast` forces the primary to checkpoint immediately so the backup can start instantly.
*   **`--wal-method=stream`:** The data files on the primary are constantly changing while `pg_basebackup` is copying them, meaning the raw copied files will be internally inconsistent. By using `stream`, the tool streams the WAL files generated *during* the backup process alongside the data. This guarantees that the resulting backup directory is entirely self-contained, consistent, and ready to start. *(Note: This is the default behavior in PostgreSQL 17+).*

### Advanced `pg_basebackup` Options

**1. Reducing Bandwidth (`-r`)**
`pg_basebackup` is designed to run as fast as possible. If your server has a weak I/O system, the backup process might consume all available disk bandwidth, causing massive performance spikes for your end-users. You can throttle the transfer rate:
```bash
pg_basebackup ... -r 50M
```

**2. Formatting (`-F`)**
By default, the tool copies raw files as plain text (`-F p`). However, if you are writing the backup to tape storage (like Tivoli), thousands of small files are inefficient. You can force the tool to wrap the entire backup into a single tarball using `-F t`.

**3. Tablespace Mapping (`-T`)**
If you are restoring a backup to a server with a different disk layout than the primary, you can remap tablespaces on the fly:
```bash
pg_basebackup ... -T /old/disk/path=/new/disk/path
```

**4. Defining Targets (`-t`)**
By default, the backup is sent to the `client` executing the command. However, you can instruct the database to store the backup locally on its own disk (`server`), or throw it away entirely for testing purposes (`blackhole`).

### Tracking Backup Progress
Taking a base backup of a multi-terabyte database takes time. You can monitor its exact progress by querying a built-in system view on the primary server:

```sql
test=# \d pg_stat_progress_basebackup
```
This view provides real-time data on `backup_total`, `backup_streamed`, and `phase`, allowing administrators to accurately estimate completion times.

---

## Testing Transaction Log Archiving

Before relying on PITR, you must verify that your archiving is actually working.

1.  **Check the Directory:** Simply use `ls -l /archive` to ensure 16MB WAL files are appearing.
2.  **Monitor the Archiver View:** PostgreSQL provides a system view specifically for tracking the archiver's health:
    ```sql
    test=# \d pg_stat_archiver
    ```
    This view tracks `archived_count`, `last_archived_wal`, and most importantly, `failed_count`. If archiving stalls due to a bad script or full disk, you will see it here.

### The `.backup` File Marker
When you run `pg_basebackup`, it drops a small marker file into your WAL archive stream:
`000000010000000000000015.00000028.backup`

This file is purely informative, but it is incredibly valuable for maintenance. The filename tells you that this specific base backup began at WAL segment `...0015`. **Therefore, any WAL files older than `...0015` are absolutely useless for restoring this specific base backup.** You can safely delete the older WAL files to reclaim disk space (assuming you don't intend to keep older base backups around).

---

## Replaying the Transaction Log (Recovery)

Once a disaster strikes, you must use your base backup and archived WAL files to perform a Point-in-Time Recovery. 

> [!TIP]
> Before deleting your broken database cluster to make room for the restore, move or copy the broken data directory to a safe place. PostgreSQL support engineers often need the corrupted files to perform forensic analysis and determine *why* the crash happened.

### Step 1: Prepare the Data Directory
The recovery target directory must be completely empty. Once cleared, copy the contents of your base backup into the data directory:
```bash
cp -Rv /path/to/base_backup/* /var/lib/postgresql/data/
```

### Step 2: Configure Recovery Settings
Next, you must tell PostgreSQL how to retrieve the archived WAL files. Open the `postgresql.conf` inside your restored data directory and add:

```ini
restore_command = 'cp /archive/%f %p'
recovery_target_action = 'promote'
```
*   `restore_command`: The exact opposite of the `archive_command`. It tells PostgreSQL how to fetch a missing WAL file (`%f`) from the archive and copy it into the active cluster's WAL directory (`%p`) for replay.
*   `recovery_target_action = 'promote'`: Instructs PostgreSQL that once it has consumed all available WAL files and reached the end of the archive stream, it should automatically promote itself out of recovery mode and become a fully operational primary server.

### Step 3: Create the Recovery Signal
This is the most critical and frequently forgotten step. You must create an empty marker file named `recovery.signal` inside the root of your data directory:

```bash
touch /var/lib/postgresql/data/recovery.signal
```
If this file is missing, PostgreSQL will simply start up normally using only the data inside the base backup. It will **not** attempt to replay the transaction log archive. The `recovery.signal` file is the trigger that forces PostgreSQL into archive recovery mode.

### Step 4: Start and Monitor
Start the PostgreSQL service. Watch the server logs carefully to ensure recovery is working:

1.  `starting archive recovery`: Confirming the `.signal` file was recognized.
2.  `restored log file "..." from archive`: Confirming the `restore_command` is successfully pulling files.
3.  `consistent recovery state reached at ...`: The base backup and the WAL stream have successfully aligned; the database is now structurally sound and uncorrupted.
4.  `archive recovery complete`
5.  `database system is ready to accept connections`

Because of the `promote` action, the database is now fully online, strictly recovered up to the last available WAL file before the crash.

---

## Running Point-in-Time Recovery (PITR)

Consuming the entire WAL until the end of the archive is perfect for recovering from a hardware crash. But what if a developer accidentally drops a production table at 10:15 AM? If you replay the entire WAL, you will simply replay the `DROP TABLE` command, defeating the purpose of the recovery.

To rescue data from human error, you must stop the replay process *just before* the disaster occurred.

### Recovering to a Timestamp
You can define a hard stop using a timestamp in `postgresql.conf`:
```ini
recovery_target_time = '2024-12-29 10:14:59'
```
*(Warning: If you provide a timestamp that is in the future, or otherwise unreachable within the available WAL files, PostgreSQL will fail with a `FATAL: recovery ended before configured recovery target was reached` error).*

### The "When Did It Happen?" Problem (Pausing Recovery)
In the real world, users rarely know the exact second they deleted data. Guessing the correct `recovery_target_time` is extremely difficult. If you guess wrong and go too far, the data is gone and you have to start the restore over from scratch.

To solve this, PostgreSQL allows you to **pause** recovery instead of promoting automatically.

```ini
hot_standby = on
recovery_target_action = 'pause'
```
With `hot_standby = on`, you can actually connect to the database *while* it is in recovery mode. When PostgreSQL hits your guessed timestamp, it will pause. You can log in, run a `SELECT` query, and see if the deleted data is there. If you haven't gone far enough, you can manually advance the replay.

#### Manual Pausing Functions
While logged into a recovering standby, you can manually control the WAL replay using built-in SQL functions:
*   `SELECT pg_wal_replay_pause();` (Halts the replay)
*   `SELECT pg_wal_replay_resume();` (Continues the replay)
*   `SELECT pg_get_wal_replay_pause_state();` (Returns `not paused`, `pause requested`, or `paused`)

### Recovering Using Named Markers (Restore Points)
Guessing timestamps is stressful. If you have predictable, high-risk operations (like a massive nightly batch job), you can explicitly inject named markers into the transaction log to act as safe restore points.

When the batch job successfully finishes, execute:
```sql
test=# SELECT pg_create_restore_point('my_daily_process_ended');
```

If disaster strikes the following day, you don't need to guess timestamps. You just instruct PostgreSQL to recover up to the exact named marker:
```ini
recovery_target_name = 'my_daily_process_ended'
```
The database will seamlessly replay the WAL and halt exactly at the moment the batch job succeeded.

---

## Cleaning Up the Transaction Log Archive

While PostgreSQL automatically recycles WAL files inside its local `pg_wal` directory, **it does absolutely nothing to manage the external `/archive` directory.** 

PostgreSQL has no idea what your company's data retention policy is, so it will blindly push 16MB files into the archive directory until the disk is 100% full. It is the administrator's (or the backup software's) responsibility to periodically purge old transaction logs.

### Using `pg_archivecleanup`
Manually calculating which hexadecimal WAL files are older than a specific backup is tedious. PostgreSQL provides a standalone command-line tool called `pg_archivecleanup` to do this math for you.

You provide the tool with the archive directory and the `.backup` marker file of the *oldest base backup you intend to keep*. The tool will safely delete every WAL file older than that marker.

**Standalone Example:**
```bash
pg_archivecleanup /archive 000000010000000000000010.00000020.backup
```

**Helpful Options:**
*   `-n` (`--dry-run`): Highly recommended. It prints the names of the files it *would* delete, without actually deleting anything.
*   `-d` (`--debug`): Prints verbose output.
*   `-b`: Also cleans up old backup history files.

### Automating Cleanup on Standby Servers
If you are running a continuous standby server that is pulling files from a shared archive directory, you can instruct PostgreSQL to run this cleanup tool automatically as it successfully consumes files.

In `postgresql.conf`:
```ini
archive_cleanup_command = 'pg_archivecleanup /archive %r'
```
The `%r` variable automatically passes the name of the oldest file required by the current restore process, allowing the standby to prune the archive folder behind itself.

---

## Incremental Backups (PostgreSQL 17+)

If you have a 10 TB database and you want to keep 7 daily base backups, you need 70 TB of backup storage. However, the daily delta of changed data might only be a few gigabytes. Taking full base backups every day for massive databases is incredibly expensive and slow.

PostgreSQL 17 introduced native **incremental backups** to solve this. Instead of copying the entire database, an incremental backup only copies the 8k blocks that have changed since the previous backup.

### 1. Enabling the Summarizer
To enable incremental backups, you must activate the WAL summarizer worker process in `postgresql.conf` (requires a restart):
```ini
summarize_wal = on
wal_summary_keep_time = '10d'
```
The summarizer monitors the WAL stream and keeps an efficient record of exactly which data blocks were modified. `wal_summary_keep_time` dictates how many days the system should hold onto these summary files before discarding them.

### 2. Taking Incremental Backups
You take your initial full backup exactly as you normally would:
```bash
pg_basebackup -h localhost -D /path/base_1 --checkpoint=fast
```

When you take your *next* backup, you pass the `-i` flag pointing to the `backup_manifest` of the previous backup. This tells `pg_basebackup` to only copy the differences:
```bash
# Day 1: Incremental against the Base
pg_basebackup -h localhost -D /path/incremental_1 --checkpoint=fast -i /path/base_1/backup_manifest

# Day 2: Incremental against Day 1's Incremental
pg_basebackup -h localhost -D /path/incremental_2 --checkpoint=fast -i /path/incremental_1/backup_manifest
```
In a typical scenario, `/path/base_1` might be 300MB, while `/path/incremental_1` is only 23MB—a massive saving in disk I/O and storage space.

### 3. Reconstructing the Database (`pg_combinebackup`)
If a disaster happens on Day 2, you **cannot** just start PostgreSQL using the `/path/incremental_2` directory. If you try, the server will crash instantly with: `FATAL: this is an incremental backup, not a data directory`.

Before you can restore, you must explicitly stitch the chain of backups back together using the `pg_combinebackup` utility:

```bash
pg_combinebackup -o /path/new_base /path/base_1 /path/incremental_1 /path/incremental_2
```
This command takes the output destination (`-o /path/new_base`), the original base backup, and the chronological list of all subsequent incrementals. It applies the partial block changes on top of the base backup.

The resulting `/path/new_base` directory is structurally identical to a standard, full base backup. You can now start the database directly from it, or use it as the starting point for Point-in-Time Recovery.

---

## Setting Up Asynchronous Streaming Replication

While archiving and PITR are great for disaster recovery, they do not provide high availability. To keep a secondary database continuously synchronized with the primary, we use **Asynchronous Streaming Replication**. Instead of waiting for 16MB WAL files to fill up and ship, the replica streams the WAL directly from the primary in real-time.

### Step 1: Configure the Primary Server
On the primary server, configure `postgresql.conf`:

```ini
wal_level = replica
max_wal_senders = 10
hot_standby = on
```
*   `max_wal_senders`: Ensure this is high enough. A streaming base backup requires two senders. If you are taking a backup while a replica is running, you need at least three. Give yourself plenty of headroom.
*   `hot_standby`: The primary server completely ignores this parameter. However, because `pg_basebackup` clones the configuration file verbatim, setting it on the primary ensures the replica will boot up in a readable state automatically.

After configuring `postgresql.conf`, update `pg_hba.conf` to allow the replica's IP to connect to the `replication` database, and restart the primary server.

### Step 2: Clone the Primary
On the secondary server (replica), ensure the target data directory is completely empty. Then, run `pg_basebackup`:

```bash
pg_basebackup -D /var/lib/pgsql/data -h primary.example.com --checkpoint=fast --wal-method=stream -R
```
The critical parameter here is **`-R` (`--write-recovery-conf`)**. 
This magic flag instructs `pg_basebackup` to automatically generate the necessary configuration files on the replica. It tells the cloned server, "You are a replica, and here are the connection strings you need to stream from the primary."

### Step 3: Verify the Replication
Start the PostgreSQL service on the secondary server. 

To verify that the two servers are talking, check the active processes on the operating system:

**On the Primary:**
```bash
ps ax | grep sender
# Look for: postgres: wal sender process ... streaming
```

**On the Replica:**
```bash
ps ax | grep receiver
# Look for: postgres: wal receiver process ... streaming
```

If both the sender and receiver processes are active and report `streaming`, your asynchronous replication is successfully configured and running.

---

## Improving Security (Dedicated Replication User)

By default, tutorials often show streaming replication running as the `postgres` superuser. Exposing the superuser account over a network connection is a massive security risk.

You should always create a dedicated user that only has permission to consume the WAL stream and nothing else:

```sql
test=# CREATE USER repl LOGIN REPLICATION ENCRYPTED PASSWORD 'strong_password';
```
By giving this user only the `REPLICATION` attribute, it cannot read application tables, drop databases, or modify data. Update your `pg_hba.conf` and the replica's connection string to use this `repl` user instead.

---

## Halting and Resuming Replication

Once configured, streaming replication runs silently and continuously in the background. However, there are scenarios where a database administrator must deliberately halt the replication process.

### Why Halt Replication?
Imagine you are upgrading a complex, risky piece of application software (like a legacy CMS). If the upgrade script has a bug and corrupts the database by dropping the wrong tables, **streaming replication will instantly replicate that catastrophic error to the replica**, destroying your backup data before you even realize a mistake was made.

To protect against this:
1.  Halt replication on the standby server.
2.  Run the application upgrade on the primary server.
3.  Test the primary. If the upgrade succeeded, resume replication.
4.  If the upgrade corrupted the primary, failover to the standby server (which still holds the safe, pre-upgrade data).

### How to Halt Replay
To pause the replication, execute the following command **on the replica**:

```sql
test=# SELECT pg_wal_replay_pause();
```

> [!NOTE]
> Halting replication *only pauses the replay* of the WAL. The primary server will still actively stream the WAL files over the network to the replica's disk. This guarantees your data is still protected against a primary server hardware crash, it just hasn't been applied to the replica's database engine yet.

If you mistakenly try to run this command on the primary server, PostgreSQL will throw an error: `ERROR: recovery is not in progress`.

### How to Resume Replay
Once you have confirmed the primary server is healthy, execute the following command **on the replica** to catch back up:

```sql
test=# SELECT pg_wal_replay_resume();
```
PostgreSQL will immediately begin processing the backlog of WAL files and synchronize with the primary.

---

## Checking Replication Health (Monitoring)

If replication silently fails and the primary server crashes, data will be permanently lost. Monitoring the health, state, and lag of your replication streams is a core responsibility of a PostgreSQL administrator.

### Monitoring from the Primary
The primary tool for monitoring replication is a system view located on the primary server:

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
This view lists one row for every active WAL sender process currently streaming to a replica. Key fields include:

*   **`client_addr`**: The IP address of the connected replica.
*   **`state`**: If the system is healthy, this should read `streaming`.
*   **The LSN Fields (Log Sequence Numbers)**: These four fields are crucial. They track the exact byte-position of the WAL as it moves across the network to the replica:
    1.  `sent_lsn`: How much WAL the primary has transmitted over the network.
    2.  `write_lsn`: How much WAL the replica has received and passed to its operating system cache.
    3.  `flush_lsn`: How much WAL the replica has successfully synced to its physical hard disk.
    4.  `replay_lsn`: How much WAL the replica has actively applied to the database engine. *(If you halted replication using `pg_wal_replay_pause()`, this number will stop increasing, even though `flush_lsn` continues to rise).*

> [!TIP]
> **Calculating Replication Lag:** You can calculate exactly how far behind a replica is (in bytes) by subtracting the replica's `replay_lsn` from the primary server's current WAL position, which is retrieved using the `pg_current_wal_lsn()` function.

### Monitoring from the Replica
You can also monitor the stream from the receiving side using the standby's system view:

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
This view provides the `status` of the receiver process, the `flushed_lsn`, and the `conninfo` used to connect to the primary. However, in practice, most third-party monitoring tools and administrators rely on the primary's `pg_stat_replication` view, as it provides a centralized overview of all connected replicas.

---

## Performing Failovers and Understanding Timelines

If a primary server suffers a catastrophic failure, you must promote a standby server to take its place.

### Promoting a Replica
To promote a standby server to a primary, run the following command on the replica:

```bash
pg_ctl -D /var/lib/pgsql/data promote
```
The server will instantly disconnect from the failed primary stream and transition into a primary itself. 

> [!TIP]
> **Zero-Downtime Transition for Read Queries:** If your replica is currently serving thousands of read-only queries, promotion does not drop them. PostgreSQL seamlessly turns all existing read-only connections into read/write connections without requiring the client applications to reconnect.

Alternatively, since PostgreSQL 12, you can promote the database using plain SQL:
```sql
test=# SELECT pg_promote();
```

### Understanding Timelines
Whenever a PostgreSQL server is promoted, it increments its **Timeline ID**.

*   A brand-new database cluster starts on Timeline `1`.
*   A replica cloned from that primary is also on Timeline `1`.
*   If that replica is promoted, it diverges into its own alternate history and becomes Timeline `2`.

Timelines are crucial for Point-in-Time Recovery. If a failover occurred at midnight, and later that day you need to recover data from 2:00 PM, PostgreSQL uses the timeline to know which branch of history (the old primary's or the new primary's) it should follow during the WAL replay.

#### Timelines in WAL Filenames
The timeline ID is physically embedded as the first 8 characters of every WAL filename.

*   Timeline 1 WAL: `0000000100000000000000F5`
*   Timeline 2 WAL: `0000000200000000000000F5`

Because the timeline dictates the prefix, WAL files from different timelines (different branches of history) can safely coexist in the same `/archive` directory without ever overwriting each other.

---

## Managing Replication Conflicts

Because a primary server does not wait for a replica to finish reading data before writing new WAL files, **replication conflicts** can occur. 

A conflict happens when the primary executes a command that destroys data currently being read by a long-running query on the replica.

### Common Conflict Scenarios
1.  **`DROP TABLE`:** If the primary drops a table, the underlying files are physically deleted. When the replica receives the WAL instructing it to delete the files, it has a problem if a user is currently running a `SELECT` against that table on the replica.
2.  **`VACUUM`:** If a row is deleted on the primary, it remains on disk until `VACUUM` physically removes it. If `VACUUM` removes the row on the primary, the replica is instructed to remove it as well. If a long-running transaction on the replica still needs to see that old row (due to MVCC), a conflict occurs.

### Resolving Conflicts: Timeout vs. Feedback
PostgreSQL gives you two ways to handle these conflicts via `postgresql.conf`:

**1. The Timeout Approach (`max_standby_streaming_delay`)**
```ini
max_standby_streaming_delay = 30s
```
When a conflict occurs, the replica will patiently pause WAL replay and wait for the conflicting `SELECT` query to finish. If the query takes longer than 30 seconds, the replica aggressively **cancels the user's query** so it can apply the WAL and avoid falling behind. 

**2. The Feedback Approach (`hot_standby_feedback`)**
```ini
hot_standby_feedback = on
```
If this is enabled, the replica continuously tells the primary, *"I have a query running that started at Transaction ID X."* The primary server's `VACUUM` process will politely defer cleaning up any dead rows that the replica might still need.

> [!WARNING]
> `hot_standby_feedback` is `off` by default for a very good reason. If a user runs a forgotten 24-hour query on your replica, the primary server will be completely forbidden from vacuuming dead rows for 24 hours, causing massive **table bloat** on the primary. Enable this only if you tightly control query durations.

---

## Making Replication More Reliable (Log Retention)

One of the most common operational failures in streaming replication is **WAL starvation**.

If a replica goes offline for an hour (due to network failure, maintenance, or waiting for a massive index creation to replicate), the primary server continues processing transactions. By default, the primary recycles old WAL files aggressively to save disk space.

When the replica reconnects, it asks for the WAL file it needs. If the primary has already deleted that file, the replication stream is permanently broken, and the administrator is forced to take a completely new base backup from scratch.

### The Solution: `wal_keep_size`
To prevent this, configure the primary to artificially retain a buffer of old WAL files:

```ini
wal_keep_size = 10GB
```
This guarantees that the primary will retain at least 10 GB of old WAL files on disk, even if they are no longer needed for crash recovery. This gives slow or temporarily disconnected replicas a massive safety window to reconnect and catch up before the files are recycled. 
*(Note: Ensure your `pg_wal` disk partition has enough free space to accommodate this setting).*

---

## Upgrading to Synchronous Replication

Asynchronous replication is fast, but it carries a risk: if the primary crashes immediately after a user issues a `COMMIT`, the WAL might not have reached the replica yet. That transaction is permanently lost.

**Synchronous Replication** eliminates this risk. When enabled, a `COMMIT` will not return a success message to the client application until at least one replica confirms that it has securely flushed the transaction to its physical disk. 

### Configuration
Configuring synchronous replication requires two coordinated steps:

**1. On the Primary (`postgresql.conf`):**
You must define which standby servers are allowed to act as synchronous partners by listing their application names:
```ini
synchronous_standby_names = 'replica1, replica2, replica3'
```

**2. On the Replica (`primary_conninfo`):**
The replica must identify itself when it connects to the primary so the primary can match it against the list. Add `application_name` to the replica's connection string:
```ini
primary_conninfo = '... application_name=replica2'
```

### The Danger of the Infinite Wait
Synchronous replication provides absolute data safety, but it trades away availability. 

> [!CAUTION]
> If a transaction is flagged as synchronous, and **none** of the replicas listed in `synchronous_standby_names` are online or reachable, the primary server will completely freeze. It will wait *forever* for a replica to return. All write operations on your database will halt until a replica is restored.

For this reason, **you should never run synchronous replication with just two nodes** (1 primary, 1 replica). You must have at least three nodes (1 primary, 2 replicas). If one replica dies, the primary instantly fails over its synchronous requirement to the surviving replica, and the end users never notice the hardware failure.

### Advanced Syntax (`FIRST` and `ANY`)
You can configure exactly how PostgreSQL chooses which nodes to synchronize with using `FIRST` (Priority) and `ANY` (Quorum).

**Priority-based (Strict Ordering):**
```ini
synchronous_standby_names = 'FIRST 2 (replica1, replica2, replica3)'
```
PostgreSQL will attempt to synchronize with the first two available servers in exact order.

**Quorum-based (Latency Optimized):**
```ini
synchronous_standby_names = 'ANY 2 (replica1, replica2, replica3)'
```
PostgreSQL broadcasts the WAL and waits for *any* two servers to reply. This is generally preferred in high-performance environments because the primary doesn't have to wait for the slowest specific server; it just takes the first two that finish.

---

## Adjusting Durability (`synchronous_commit`)

Replication does not have to be an all-or-nothing global configuration. Because synchronous replication incurs a performance penalty (network latency and disk I/O waits), PostgreSQL allows you to fine-tune the balance between speed and safety using the `synchronous_commit` parameter.

### The 5 Levels of Durability
Assuming you have configured `synchronous_standby_names`, you can set `synchronous_commit` to one of five distinct levels:

1.  **`off` (Max Performance, Max Risk):** The primary server does not even wait to flush the WAL to its own local disk before returning `COMMIT` success. If the primary crashes, you may lose data.
2.  **`local` (Standard Asynchronous):** The primary waits to safely flush the WAL to its own local disk, but it does *not* wait for any replica.
3.  **`remote_write` (Optimized Synchronous):** The primary waits for the replica to receive the WAL and pass it to the operating system's cache. It *does not* wait for the replica to flush it to physical disk. This is very fast, and data loss only occurs if both the primary and replica experience total hardware failure simultaneously.
4.  **`on` (Standard Synchronous):** The primary waits until the replica confirms that the WAL has been successfully flushed to the replica's physical disk.
5.  **`remote_apply` (Max Safety & Consistency):** The primary waits until the replica has received, flushed, *and fully replayed* the WAL. This is the slowest option, but it provides **read-your-writes consistency**. If an application load-balances the next read query to the replica, it is 100% guaranteed to see the data that was just committed.

### Granular Configuration (Per-Transaction Tuning)
The true power of `synchronous_commit` is that it is not restricted to a global `postgresql.conf` setting. You can configure it dynamically at multiple levels:

*   **Per Database:** `ALTER DATABASE reporting SET synchronous_commit = off;`
*   **Per User:** `ALTER USER fast_writer SET synchronous_commit = local;`
*   **Per Transaction:** `SET LOCAL synchronous_commit = ...`

This extreme granularity allows you to build highly optimized applications. For example, within the exact same database connection, you can execute a low-priority logging insert asynchronously for maximum speed (`SET LOCAL synchronous_commit = off;`), and then immediately execute a critical credit card payment synchronously (`SET LOCAL synchronous_commit = on;`) to guarantee it is backed up to a secondary node before acknowledging the payment to the user.

---

## Making Use of Replication Slots

Earlier, we discussed using `wal_keep_size` to prevent a primary server from deleting WAL files before a disconnected replica could fetch them. However, guessing the correct size for `wal_keep_size` is risky.

**Replication Slots** are the definitive solution to WAL starvation. 

When a replica connects using a replication slot, the primary server precisely tracks the replica's progress. The primary will **never** recycle or delete a WAL file until the slot explicitly confirms that the replica has consumed it. This guarantees that a replica can never fall so far behind that it requires a full resync from a base backup.

### The Danger of Abandoned Slots
Replication slots are incredibly powerful, but they introduce a severe new risk. 

If a replica server is permanently destroyed or permanently disconnected without telling the primary, the primary still thinks the replica is just "running late." The primary will hoard WAL files forever, waiting for the dead replica to return. **Eventually, the primary's disk will hit 100% capacity, causing a total database crash and outage.**

#### The Safety Valve: `max_slot_wal_keep_size`
To prevent an abandoned slot from taking down the primary server, PostgreSQL 14 introduced a safety valve:
```ini
max_slot_wal_keep_size = 50GB
```
By default, this is `-1` (unlimited). You should always set this to a value lower than your total free disk space. If a dead replica causes the slot to hoard more WAL than this limit, the primary will sacrifice the slot (permanently breaking the replica's stream) to save itself from running out of disk space.

*Note: Regardless of this setting, you must actively monitor your replication slots and manually drop them if they are no longer in use.*

### Two Types of Slots
There are two flavors of replication slots in PostgreSQL:

1.  **Physical Replication Slots:** Used for standard streaming replication (what we have configured in this chapter). It passes raw, binary blocks.
2.  **Logical Replication Slots:** Used for logical decoding. It acts like a `tail -f` for the database, allowing plugins to translate binary WAL changes into human/application-readable formats (like JSON or SQL statements). This allows you to stream specific row-level changes to external applications or separate PostgreSQL clusters.

---

## Configuring and Managing Physical Replication Slots

To utilize physical replication slots, you must adjust the primary server's configuration and manage the slots via SQL.

### 1. Configure the Primary Server
In `postgresql.conf`:
```ini
wal_level = replica 
max_replication_slots = 10
```
*Note: If you plan to use logical slots, `wal_level` must be set to `logical`. Leave spare capacity in `max_replication_slots` so you can add new consumers in the future without requiring a database restart.*

### 2. Create the Slot
Connect to the primary server and execute the slot creation function:

```sql
test=# SELECT * FROM pg_create_physical_replication_slot('standby_slot_1', true);
```
The second parameter (`true` for `immediately_reserve`) is highly recommended. If set to `true`, the primary immediately begins protecting and hoarding WAL files right now. If omitted or `false`, the slot will not protect WAL files until a replica actually connects for the first time.

### 3. Connect the Replica
On the replica server, instruct it to bind to the specific slot by adding it to its configuration:

```ini
primary_slot_name = 'standby_slot_1'
```
*(Alternatively, you can pass `-S standby_slot_1` when running `pg_basebackup` to configure this automatically).*

Once the replica is restarted, it will actively consume WAL from that slot.

### 4. Monitoring Slots
You can view the status of all slots on the primary:

```sql
test=# \d pg_replication_slots
test=# SELECT slot_name, slot_type, active, restart_lsn FROM pg_replication_slots;
```
*   `active`: If `t` (true), a replica is currently connected and streaming. If `f` (false), the replica is offline, and the primary is actively hoarding WAL files for it.
*   `restart_lsn`: The exact WAL byte position this slot is holding onto. The primary cannot delete any WAL files newer than this position.

### 5. Dropping a Slot
If you permanently decommission a replica, you **must** drop its slot to prevent the primary from running out of disk space:

```sql
test=# SELECT pg_drop_replication_slot('standby_slot_1');
```

> [!IMPORTANT]
> You cannot drop a replication slot while a replica is actively connected to it. PostgreSQL will intentionally throw an error. You must shut down the replica (or reconfigure it to detach from the slot) before executing the `DROP` command.

---

## Handling Logical Replication Slots

While physical slots pass raw binary data to a standby server, **logical replication slots** act as a real-time `tail -f` for your database. They allow plugins to decode binary WAL changes into human/application-readable formats (like JSON) so you can stream specific row changes to external systems (e.g., Apache Kafka, auditing tools, or other databases).

*Prerequisite: `wal_level` must be set to `logical` in `postgresql.conf`.*

### 1. Creating a Logical Slot
When creating a logical slot, you must specify the name of the **output plugin** you want to use to translate the data (e.g., `test_decoding`, `wal2json`).

```sql
test=# SELECT * FROM pg_create_logical_replication_slot('logical_slot', 'test_decoding');
```

### 2. Consuming the Stream
Once the slot is created, any `INSERT`, `UPDATE`, or `DELETE` executed in the database is queued in the slot. You can fetch and decode these changes using SQL:

```sql
test=# SELECT pg_logical_slot_get_changes('logical_slot', NULL, NULL);
```
**Output Example:**
```text
(0/EF8CCCD8,606547,"table public.t_demo: INSERT: id[integer]:1 name[text]:'hans'")
```
*Note: `pg_logical_slot_get_changes` actively consumes the queue. If you run it a second time, it returns 0 rows because the data has been drained. If you just want to preview the stream without draining it, use `pg_logical_slot_peek_changes()`.*

You can also consume the stream continuously from the operating system using the `pg_recvlogical` command-line tool, piping the stream directly to standard output or a file.

### 3. The `REPLICA IDENTITY` Problem (Deletes and Updates)
By default, when you execute a `DELETE` or an `UPDATE`, PostgreSQL's WAL only records the physical location of the tuple that was removed, not the actual data that used to be inside the tuple. 

If you delete a row, your logical stream will output something useless:
```text
table public.t_demo: DELETE: (no-tuple-data)
```
If an external application is relying on this stream to keep a secondary system in sync, `(no-tuple-data)` is impossible to process.

To fix this, you must configure the table's `REPLICA IDENTITY` to log the full "before-image" of the row:
```sql
test=# ALTER TABLE t_demo REPLICA IDENTITY FULL;
```
Now, when you run a `DELETE`, the transaction log stream will explicitly contain the data that was removed:
```text
table public.t_demo: DELETE: id[integer]:1 name[text]:'hans'
```

---

## Native Logical Replication (Publications and Subscriptions)

Physical streaming replication clones the *entire database instance*. If you only want to replicate a specific database, or even just a specific table, you need **Native Logical Replication**. 

Because logical replication operates on decoded SQL-like data rather than raw binary blocks, it can replicate data across completely different major versions of PostgreSQL, making it the primary tool for **near-zero downtime major version upgrades**.

*Prerequisite: The primary server must have `wal_level = logical`.*

### 1. Manual Schema Creation
Logical replication replicates *data*, not DDL (schema changes). Before you begin, you must manually create the empty target table with the exact same structure on the subscriber database:
```sql
repl=# CREATE TABLE t_test (a int, b int);
```

### 2. The Publisher
On the primary database, define exactly what data you want to broadcast using a **Publication**. You can publish specific tables or all tables:

```sql
test=# CREATE PUBLICATION pub1 FOR TABLE t_test;
-- Or: CREATE PUBLICATION pub1 FOR ALL TABLES;
```

### 3. The Subscriber
On the secondary database, subscribe to the publication using a connection string pointing to the primary:

```sql
repl=# CREATE SUBSCRIPTION sub1
       CONNECTION 'host=primary.example.com dbname=test user=repl_user'
       PUBLICATION pub1;
```
When you execute this command, PostgreSQL automatically:
1. Reaches out to the primary and creates a logical replication slot.
2. Performs an initial copy of the existing data in `t_test` to get the replica in sync.
3. Begins continuously streaming incoming logical changes.

### Caveat: Testing on Localhost
If you are learning logical replication by creating both the publisher and the subscriber within the *exact same PostgreSQL instance* (e.g., streaming from `db1` to `db2` on the same machine), the `CREATE SUBSCRIPTION` command will hang forever.

To bypass this local-testing bug, you must manually create the slot and instruct the subscription not to try creating it:

**On Publisher:**
```sql
test=# SELECT pg_create_logical_replication_slot('sub1', 'pgoutput');
```
**On Subscriber:**
```sql
repl=# CREATE SUBSCRIPTION sub1
       CONNECTION 'host=localhost dbname=test user=postgres'
       PUBLICATION pub1
       WITH (create_slot = false);
```
*(You only need to do this workaround when testing on a single instance; in standard multi-server deployments, `CREATE SUBSCRIPTION` handles slot creation perfectly on its own).*

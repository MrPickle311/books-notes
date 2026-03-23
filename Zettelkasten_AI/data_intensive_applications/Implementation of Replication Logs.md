Under the hood, how does the leader actually send a stream of changes to the followers? There are a few different methodologies used:

**1. Statement-based Replication**
The leader physically logs every SQL statement (`INSERT`, `UPDATE`, `DELETE`) it executes and sends the exact SQL string to the followers to execute.
*   *The Problem:* This breaks down completely if the SQL relies on **nondeterminism**. For example, `UPDATE users SET last_login = NOW()`. When the follower parses and runs `NOW()`, it will insert a different hardware timestamp than the leader did! Similar issues occur with `RAND()`, auto-incrementing columns, or triggers.
*   MySQL used this before v5.1, but due to determinism nightmares, it is largely abandoned in favor of row-based replication.

**2. Write-Ahead Log (WAL) Shipping**
As discussed in Chapter 4, every modification first hits a low-level append-only WAL before touching the B-Tree. The leader simply copies this exact WAL bytes over the network to the followers.
*   *The Problem:* The WAL is an extremely low-level mapping of physical bytes to specific disk blocks. Because of this, it is **tightly coupled** to the specific storage engine and database version.
*   This makes zero-downtime upgrades nearly impossible. You cannot have a follower running Postgres v13 while the leader runs Postgres v12, because the physical disk WAL format likely changed between versions.

**3. Logical (Row-based) Log Replication**
To solve the tight-coupling of WAL shipping, databases create a separate log exclusively for replication, known as a **Logical Log** (e.g. MySQL's `binlog`).
*   Instead of writing "Modify disk block 583", it writes "Row ID 52 was updated over in the Users table. Column X changed to Y".
*   Because it strictly defines *logical rows* rather than *physical disk bytes*, it is completely decoupled from the storage engine. This seamlessly allows leaders and followers to run entirely different database versions, enabling zero-downtime rolling upgrades.
*   *(Bonus: Logical logs are incredibly easy for external systems to read, enabling Change Data Capture (CDC) to stream live database edits into a Data Warehouse).*
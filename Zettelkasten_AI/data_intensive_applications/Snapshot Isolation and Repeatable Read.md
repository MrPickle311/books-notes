---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Read Committed is great, but it still allows a dangerous concurrency bug called **Read Skew** (also known as a Nonrepeatable Read).

**The Read Skew Anomaly:**
Imagine Aaliyah has $1,000 split across two bank accounts ($500 each). A transaction begins transferring $100 from Account 1 to Account 2. 
If Aaliyah opens her banking app at the exact wrong millisecond, her app might query Account 1 *before* the transfer hits it (saying $500), but then query Account 2 *after* the transfer has left it (saying $400). 
To Aaliyah, it appears she only has $900. $100 has vanished into thin air.
![Figure 8-6: Read skew: Aaliyah observes the database in an inconsistent state.](figure-8-6.png)

This is completely legal under "Read Committed" isolation! Both the $500 and the $400 were officially committed values at the exact millisecond her app queried them. If she refreshes the page, it will fix itself, so for a banking app, Read Skew is usually an acceptable temporary side effect.

**When Read Skew is Unacceptable:**
However, temporary Read Skew is completely catastrophic for:
1.  **Backups:** A backup can take 10 hours. If a backup copies Account 1 at hour 1, and Account 2 at hour 9 (after the transfer), the backup is permanently corrupted with vanished money. 
2.  **Analytics:** If an analytical query takes 20 minutes to aggregate global revenue, and active transactions are actively shifting money around while the query runs, the final revenue report will be mathematically nonsensical.

#### The Solution: Snapshot Isolation
To fix Read Skew, databases use **Snapshot Isolation**. 
When a transaction begins, it takes a "Snapshot" of the database. For the entire duration of that transaction, it will *only* see the data exactly as it existed at that specific moment in time. Even if 10,000 other transactions actively change data underneath it, the query is completely isolated in its frozen snapshot in the past. 
*(This is a massively popular feature used by Postgres, MySQL InnoDB, Oracle, and Data Warehouses like BigQuery).*

#### Multi-Version Concurrency Control (MVCC)
To guarantee Snapshot Isolation, a database can't just keep 2 versions of a row (Old and New) like Read Committed does. Because an analytical query might run for 2 hours, the database might need to keep dozens of historical versions of the exact same row alive simultaneously.

This mechanism is called **MVCC (Multi-Version Concurrency Control)**.
*   **The Golden Rule of MVCC:** Readers never block writers, and writers never block readers.
*   **How it Works (PostgreSQL Example):** Every single transaction is given a permanent, unique Auto-Incrementing ID (`txid`). 
    *   Every row on disk has an `inserted_by` field and a `deleted_by` field.
    *   When you `UPDATE` a row, Postgres doesn't overwrite it! It actually marks the old row's `deleted_by` field with your `txid`, and inserts a brand new row with your `txid` in the `inserted_by` field.
    *   Both the old and new rows physically exist on disk side-by-side as a linked list.
    *   Any other transaction reading the database simply looks at their own `txid`, compares it to the rows, and mathematically ignores any row that was inserted "after" they started their snapshot.
![Figure 8-7: Implementing snapshot isolation using multi-version concurrency control.](figure-8-7.png)

*(Eventually, when the database knows for a fact that the oldest ongoing analytical query has finally finished, a garbage collection process—like Postgres's `VACUUM`—will sweep through the disk and physically delete all the obsolete historical rows to free up space).*

#### Visibility Rules for MVCC Snapshots
How does the database actually decide which historical row a transaction legally gets to see? It relies on the `txid` math:
1.  **Ignore Active Contemporaries:** The exact millisecond your transaction starts, the database writes down a list of every other transaction currently in progress globally. Anything written by those transactions is completely ignored (even if they happen to commit 5 seconds later).
2.  **Ignore the Future:** Any transaction that started *after* you is mathematically from "the future." Every row stamped with their `txid` is completely invisible to you.
3.  **Ignore the Aborted:** Any writes made by an aborted transaction are obviously ignored (which is brilliantly efficient, because Abort no longer requires actively deleting the bad rows; the database just flags the `txid` as aborted and everyone mathematically ignores it).
4.  **Accept the Rest:** The only rows you see are those that were successfully committed *before* your specific `txid` began.

#### Indexes and Snapshot Isolation
How do Indexes work if there are 5 different versions of the exact same row on the disk simultaneously?
*   **The Postgres Approach:** The Index simply points to *every* version of the row. When the query uses the index to jump to the row, it must quickly glance at the `txid` linked-list to figure out which specific version it is legally allowed to see.
*   **The Append-Only B-Tree (CouchDB, Datomic):** Instead of pointing to multiple rows, the database uses an *Immutable Copy-on-Write* B-Tree. When a row changes, the database creates a brand new copy of that leaf node, and a new copy of its parent node, all the way up to creating a brand new root node. To query a snapshot, you just hold a pointer to the "Root Node" that existed at your exact point in time, and those pointers naturally ignore all future writes (which spin off into new tree branches).

#### Snapshot Isolation vs. "Repeatable Read" (Naming Confusion)
Because Snapshot Isolation is so incredibly useful, you would think the SQL standard would clearly define it. 
Unfortunately, the official SQL standard was written in 1992, based on IBM's 1975 papers, *before Snapshot Isolation was fully invented*.

Therefore, the SQL standard doesn't mention Snapshot Isolation; instead, it defines a flawed, extremely vague isolation level called **Repeatable Read**. 

Because of this, database marketing is completely chaotic and non-standardized:
*   **PostgreSQL** offers true Snapshot Isolation, but explicitly calls it `Repeatable Read` just so they can legally claim they comply with the SQL standard.
*   **MySQL (InnoDB)** also calls it `Repeatable Read`, but implements it very differently with weaker consistency than Postgres.
*   **Oracle** offers true Snapshot Isolation, but falsely calls it `Serializable` (the highest possible isolation level).
*   **IBM Db2** uses the phrase `Repeatable Read`, but actually maps it to true `Serializable`.

*Conclusion: Nobody actually knows what "Repeatable Read" means anymore. You must read the specific documentation for your database to understand what anomalies it actually protects you against.*
---
## Related Concepts
* [[Data Intensive Applications]]

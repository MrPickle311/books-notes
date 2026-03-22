---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
To recap, Atomicity and Isolation are deeply tied to the concept of a **Multi-Object Transaction**. 

A Multi-Object Transaction is required when your application needs to modify several pieces of data that *must* be kept in sync. 
Example: An email application where you insert a new Email record into the `emails` table, but also must update a denormalized `unread_count` integer on the `users` table.

**Why Isolation is Needed:**
If you don't use a transaction, User 2 might refresh their screen at the exact millisecond after the email was inserted, but *before* the counter was incremented. They see the email in their inbox, but their notification badge incorrectly says 0. The database is in an inconsistent halfway state.
![Figure 8-2: Violating isolation: one transaction reads another transaction’s uncommitted writes (a “dirty read”).](data_intensive_applications/figure-8-2.png)

**Why Atomicity is Needed:**
If a system crash or network error occurs the instant after the email is inserted but *before* the counter is incremented, the write fails. In a non-ACID database, you are permanently left with phantom emails and a permanently corrupt notification counter. With an Atomic transaction, the database safely rolls back the inserted email.
![Figure 8-3: Atomicity ensures that if an error occurs any prior writes from that transaction are undone, to avoid an inconsistent state.](data_intensive_applications/figure-8-3.png)

#### Grouping Operations Together
To perform a multi-object transaction, the database needs a way to know exactly which writes belong together.
*   **Relational Databases:** The grouping is bound to the client's physical TCP connection. Everything sent between a `BEGIN TRANSACTION` and a `COMMIT` statement is tracked as a single unit. If the TCP connection unexpectedly drops mid-way, the DB automatically aborts the transaction.
*   **Non-Relational (NoSQL):** Finding a `BEGIN TRANSACTION` command in NoSQL is rare. Even if a Key-Value store provides a "multi-put" API to update several keys at once, you must read the documentation carefully. Often, these APIs lack true transaction semantics; if it fails, some keys may have successfully updated while others failed, abandoning you in a corrupted state.
---

### Single-Object Writes
Atomicity and Isolation don't just apply to updating *multiple* records; they are absolutely critical when modifying a *single* object.

Imagine writing a 20 KB JSON document to a database, and the power fails after exactly 10 KB is written. If the database didn't have Atomicity at the single-record level, a subsequent read would return half a document (an unparseable corrupted mess). 
To prevent this, storage engines go to great lengths to ensure single objects are written automatically (e.g., using a Write-Ahead Log) and isolated (e.g., placing a temporary lock on the row so nobody reads it while it is actively being overwritten).

**Advanced Single-Object Operations:**
Many databases provide advanced operations that act atomically on a single record to prevent race conditions:
1.  **Atomic Increments:** Removes the need for the dangerous "read-modify-write" logic shown in Figure 8-1. The database natively handles the math atomically.
2.  **Compare-and-Set (CAS):** A conditional write. "Update this JSON document, but *only* if nobody else has touched it since the last time I read it."

*(Note: These single-object guarantees are sometimes marketed heavily by NoSQL databases—like Aerospike's "strong consistency" or Cassandra's "lightweight transactions"—but these are NOT true multi-object transactions).*

### The Need for Multi-Object Transactions
Could we build an application completely without true multi-object transactions, relying solely on single-record updates? Technically yes, but it is incredibly difficult because most data paradigms require coordinating multiple objects:
1.  **Relational:** `Foreign Keys`. If inserting a child, the parent must exist.
2.  **Document:** `Denormalization`. If you lack `JOINs`, you denormalize your data (like the unread counter in Figure 8-2). Denormalized data inherently requires updating several separate documents in sync.
3.  **Indexes:** `Secondary Indexes`. Every time you change a value, the underlying data *and* the secondary index must both be updated. Without transactions, the index can go out of sync with the raw data.
---
## Related Concepts
* [[Data Intensive Applications]]

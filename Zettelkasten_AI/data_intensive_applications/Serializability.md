---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
After encountering Dirty Writes, Dirty Reads, Lost Updates, Read Skew, and Write Skew caused by Phantoms, the harsh reality of weak isolation is clear: it places a massive burden on the application developer to manually reason through every possible timing issue and explicitly add `FOR UPDATE` locks everywhere.

This has been the situation since the 1970s. The academic answer to all of this has always been simple: use **Serializable Isolation**.
It is the gold standard, strongest possible isolation level. It guarantees that even if 1,000 transactions fire simultaneously, the database mathematically guarantees the outcome will be *identical* as if they had run in a strict, single-file serial order (one at a time). It perfectly prevents every single race condition we've discussed.

So why doesn't everyone use it? *Performance.*
Historically, true serializability was far too slow for production. However, modern databases actually achieve it using one of three techniques:
1.  **Actual Serial Execution:** Literally running everything single-file on one CPU thread.
2.  **Two-Phase Locking (2PL):** The classic, brutal locking mechanism.
3.  **Serializable Snapshot Isolation (SSI):** A modern, optimistic approach.

#### 1. Actual Serial Execution
The simplest way to fix concurrency bugs is to completely remove concurrency. Literally execute only one transaction at a time, entirely on a single CPU thread. No locks needed, no conflict detection needed. It is serializable by definition.

For 30 years, database engineers considered a single-threaded database to be absurdly slow. What changed in the 2000s?
1.  **Cheap RAM:** Datasets can now fit entirely in RAM. A single CPU thread can instantly execute a transaction in memory without agonizingly waiting 10 milliseconds for a spinning Hard Drive disk.
2.  **Splitting Analytics:** Engineers realized OLTP writes are incredibly tiny (inserting a medical record). If you move your massive 2-hour analytics queries out of the serial loop (using Snapshot Isolation), that single thread can burn through tens of thousands of tiny OLTP writes per second.
*(Used by: Redis, VoltDB, Datomic).*

#### Encapsulating Transactions in Stored Procedures
A single-threaded loop has one fatal weakness: network latency. 
In traditional interactive SQL, the application code sends a `SELECT` statement, waits 2ms for the network, thinks about it, and sends an `UPDATE` statement. 
If the database only has one CPU thread, it CANNOT afford to sit completely idle for 2ms waiting on the network for the application code. 

To fix this, single-threaded databases completely ban interactive multi-statement network transactions. 
Instead, you must write your entire application logic as a **Stored Procedure** inside the database itself. You hit the database once: "Execute Procedure X". Because the code and the data are running locally on the exact same server, the single thread never waits on the network and can rip through thousands of procedures blisteringly fast.
![Figure 8-9: The difference between an interactive transaction and a stored procedure (using the example transaction of Figure 8-8).](data_intensive_applications/figure-8-9.png)

**Pros and Cons of Stored Procedures**
Stored procedures originally gained a terrible reputation in the 1990s:
*   *Ugly Languages:* Oracle's `PL/SQL` and Postgres's `PL/pgSQL` felt incredibly archaic compared to modern programming languages. 
*   *Hard to Manage:* Code inside the database is notoriously hard to version control, impossible to test cleanly, and difficult to monitor.
*   *Performance Traps:* A badly written memory-leak inside an application server just kills that one server. A badly written stored procedure directly inside the database kernel can melt the entire company's database instance.

However, modern Serializable databases have fixed the language issue: VoltDB uses Java/Groovy, Redis uses Lua, and Datomic uses Clojure/Java. Provided the code is clean and the entire dataset fits in RAM, stored procedures run furiously fast on a single thread. 
*(Note: VoltDB literally uses Stored Procedures for replication. Instead of copying gigabytes of raw data over the network, it just sends the tiny stored procedure's name to the other nodes, and they all run it locally. This requires every procedure to be strictly deterministic).*

#### Sharding within Serial Execution
Single-threaded serial execution is amazingly fast, but it mathematically caps out at the maximum speed of a single CPU core. 

To break past this hardware limit, databases use **Sharding**. 
The database gives *each individual CPU core* its own specific shard of data and its own personal, isolated Serial Thread.
*   *The Good:* If you can properly shard your data so that a transaction only ever reads/writes to data within a single shard, throughput scales linearly. (Throw 32 CPU cores at it, get 32x the speed).
*   *The Bad:* If your data model requires a transaction to touch multiple shards (e.g., using Global Secondary Indexes), performance completely craters. The database has to orchestrate a distributed lock-step coordination across multiple isolated threads, slowing down cross-shard writes by orders of magnitude. 

#### Summary of Actual Serial Execution
Serial execution is a completely viable way to get perfect Serializable Isolation, but it comes with intense architectural constraints:
1.  Your active dataset must physically fit entirely inside the server's RAM. 
2.  Every transaction must cleanly execute as a pre-written Stored Procedure. Interactive network SQL is banned. 
3.  Transactions must be extremely small and fast. One massive, slow 1-second write will completely freeze the single thread and grind the entire database to a halt.
4.  To scale past one CPU core, you must strictly partition your data to cleanly avoid cross-shard transactions.

---

#### 2. Two-Phase Locking (2PL)
For about 30 years, if you wanted true Serialization, **Two-Phase Locking (2PL)** was the *only* algorithm that existed.
*(Clarification: 2PL is NOT Two-Phase Commit [2PC]. They are completely different concepts. 2PL is for isolation; 2PC is for distributed atomic commits).*

We previously saw locks used to prevent Dirty Writes (where writers block other writers). 2PL requires much stronger locking rules:
*   **Writers block Writers:** If someone is writing to a row, nobody else can write to it.
*   **Writers block Readers:** If someone is writing to a row, *nobody else can even read it*. The reader must wait until the writer commits.
*   **Readers block Writers:** If someone is reading a row, *nobody else can write to it*. The writer must wait until the reader finishes.

This fundamentally breaks the golden rule of MVCC/Snapshot Isolation ("Readers never block writers, and writers never block readers"). In 2PL, *everything blocks everything*. This brutal locking mechanism guarantees perfect serializability, preventing Lost Updates and Write Skew unconditionally.

#### Implementation of Two-Phase Locking
How do databases (like MySQL InnoDB, SQL Server) actually pull this off? They use multiple types of locks.
*   **Shared Lock:** If a transaction wants to `SELECT` a row, it must acquire a Shared Lock. Multiple concurrent readers can hold a shared lock simultaneously.
*   **Exclusive Lock:** If a transaction wants to `UPDATE/DELETE` a row, it must acquire an Exclusive Lock. No other transaction (neither reader nor writer) is allowed to legally touch the row while this lock is held. (If the transaction originally held a Shared Lock, it must "upgrade" it to an Exclusive Lock).

*Why is it called "Two-Phase"?*
Every transaction has two distinct phases:
1.  **Phase 1 (Growing):** As the transaction executes queries, it actively acquires locks.
2.  **Phase 2 (Shrinking):** When the transaction officially Commits or Aborts, it releases *all* of its locks simultaneously. (You cannot release a lock early and then acquire a new one).

Because practically everything places a lock on something, transactions constantly block each other. It is extremely common for Transaction A to wait for a lock held by Transaction B, while Transaction B is simultaneously waiting on a lock held by Transaction A. This infinite loop is called a **Deadlock**. The Database automatically detects deadlocks, aggressively kills (Aborts) one of the transactions, and forces the client application to retry.

#### Performance of Two-Phase Locking
This algorithm is why true Serializability had a terrible reputation for decades.
*   **Reduced Concurrency:** The design of 2PL mathematically destroys concurrency. If an analytical query needs to back up a large table, it places a Shared Lock on the *entire table*. For the 10 minutes that backup runs, the *entire database is effectively read-only*. Every single write transaction globally is frozen in a queue waiting for the backup to finish.
*   **Unstable Latency:** Performance at high percentiles (p99 latency) is incredibly unpredictable. A single poorly designed transaction that accidentally acquires too many locks can instantly cause a massive cascading traffic jam, grinding the entire system's throughput to a halt. 
*   **High Deadlock Rate:** Because everyone is locking everything, Deadlocks happen constantly. The database wastes massive amounts of CPU detecting deadlocks, killing transactions, and forcing your application servers to aggressively retry failed requests (which wastes even more CPU).

#### Predicate Locks (How 2PL Fixes Phantoms)
We established earlier that **Phantoms** are when parallel transactions *insert* new rows that retroactively change the result of a `SELECT` query, causing Write Skew (e.g., booking a meeting room). 
If 2PL is truly Serializable, it must stop Phantoms. But how do you place a lock on a row that doesn't exist yet?

The conceptual answer is a **Predicate Lock**.
Instead of locking a specific existing row (like `id = 123`), a Predicate Lock literally locks the *search condition itself*.
For example: `SELECT * FROM bookings WHERE room_id = 123 AND start_time > '12:00'`
*   When a transaction reads this query, it acquires a Shared Predicate Lock on the mathematical concept of "Room 123 between 12:00 and 1:00".
*   If *another* transaction attempts to `INSERT` a brand new booking, the database mathematically checks if the new row falls inside any active Predicate Locks. If it does, the second transaction is completely blocked from inserting the row.
This perfectly prevents Phantoms and Write Skew without forcing you to use ugly "Materialized Conflicts."

The key idea here is that a predicate lock applies even to objects that do not yet exist in the database, but which might be added in the future (phantoms).

#### Index-Range Locks (Next-Key Locking)
While conceptually perfect, true Predicate Locks perform dreadfully. Checking every single `INSERT` against a massive list of complex mathematical predicates consumes too much CPU.

To solve this, most databases using 2PL (like MySQL InnoDB) actually use an approximation called **Index-Range Locking** (or Next-Key Locking).
Instead of locking an abstract mathematical condition, the database just places a lock directly on an **Index**. 

*   *Example 1 (Room Index):* If the database uses an index on `room_id` to run the query, it places a Shared Lock on the exact index entry for `Room 123`. Now, nobody is allowed to insert *any* meeting into Room 123 until the lock releases.
*   *Example 2 (Time Index):* If it uses a time-based index, it locks all index entries between 12:00 and 1:00. Now, nobody is allowed to insert *any* meeting (for any room globally) during that hour.

*Trade-off:* Index-Range Locks lock a much larger area of the database than mathematically necessary (over-locking). However, because placing a lock on an existing Index B-Tree node is extremely cheap and fast compared to calculating predicates, it is wildly successful as a performance compromise. If no suitable index exists, the database falls back to placing a lock on the entire table.

---

#### 3. Serializable Snapshot Isolation (SSI)
Throughout this chapter, we faced a bleak trade-off: use weak isolation (fast but dangerous) or serializable isolation (safe but dreadfully slow). 
**Serializable Snapshot Isolation (SSI)** breaks this trade-off. Introduced in 2008, it provides full, mathematically perfect Serializability with only a tiny performance penalty over standard Snapshot Isolation. (It is used heavily by PostgreSQL, CockroachDB, and FoundationDB).

**Pessimistic vs. Optimistic Concurrency Control**
To understand SSI, we must understand the philosophical difference in concurrency control:
*   **Pessimistic (2PL, Serial Execution):** Assumes the worst. If anything *might* go wrong, it aggressively blocks and forces everyone to wait in line. It performs horribly when millions of users are trying to access the same system.
*   **Optimistic (SSI):** Assumes the best. Instead of blocking, transactions just boldly act as if they are the only user on the server. There are zero locks; everyone runs at maximum speed. However, right before a transaction commits, the database rapidly audits the math. If it detects that a dangerous race condition actually occurred (isolation was violated), it Aborts the transaction and forces the app to retry.

Optimism performs incredibly well under normal conditions (because locking overhead is zero). It only suffers if there is hyper-contention (thousands of users editing the exact same row simultaneously), causing massive amounts of aborted retries. 

**Decisions Based on an Outdated Premise**
How does an Optimistic database detect a Write Skew bug without using locks?
Remember the Write Skew pattern: The application queried a premise ("Are there 2 doctors on call?"), made a business decision ("Yes, I can take leave"), and executed a write.
By the time the transaction attempts to Commit, the database must ask: *Is the original premise still true?*
If another transaction changed the number of doctors while you were deciding, your write was based on an outdated premise. To guarantee Serializability, the database *must* detect this and Abort your transaction.

The database tracks this by looking for two specific mathematical patterns:
1.  **Reads of a stale MVCC object:** The DB notices you read an old version of a row because an uncommitted write occurred before your read.
2.  **Writes that affect prior reads:** The DB notices that someone else literally overwrote the exact row you previously read, after you read it.

#### 1. Detecting Stale MVCC Reads
Because SSI builds perfectly on top of MVCC Snapshot Isolation, it naturally ignores uncommitted writes. 
*   Transaction A reads the Doctors count and sees "Aaliyah is on call." It ignores Transaction B (which is currently deleting Aaliyah) because B hasn't committed yet.
*   However, when Transaction A tries to finally Commit, the database checks on Transaction B. 
*   If Transaction B has now committed, the database realizes Transaction A's original read was fundamentally "Stale." Transaction A's premise is no longer mathematically sound, so the database Aborts Transaction A.
![Figure 8-10: Detecting when a transaction reads outdated values from an MVCC snapshot.](data_intensive_applications/figure-8-10.png)
*(Why wait until commit? If Transaction A was a read-only analytics query, it doesn't matter if it's stale, so the database lets it finish perfectly safely. It only aborts if Transaction A subsequently tries to WRITE based on that stale read).*

#### 2. Detecting Writes That Affect Prior Reads
What if you search for something that doesn't exist yet (a Phantom)? 
In SSI, we repurpose the idea of an **Index-Range Lock**. 
*   When a transaction searches the index for `shift_id = 1234`, the database drops a "Tripwire" onto that index entry.
*   Unlike 2PL, this Tripwire *does not block anyone*. Another transaction is completely free to swoop in and insert a new Doctor into `shift = 1234`.
*   However, as the second transaction modifies the index, it trips the wire. It leaves a mathematical note saying, "Hey, whoever dropped this tripwire just had their premise invalidated."
*   When the first transaction finally tries to Commit, it checks its tripwires. Seeing the notification, it realizes its original search was compromised by a Phantom, and it Aborts.
![Figure 8-11: In serializable snapshot isolation, detecting when one transaction modifies another transaction’s reads.](data_intensive_applications/figure-8-11.png)

#### Performance of Serializable Snapshot Isolation (SSI)
SSI is a massive breakthrough for database performance:
*   **Zero Blocking:** The biggest advantage over 2PL is that nobody ever waits for locks. Readers don't block writers, and writers don't block readers. This makes database latency smooth, incredibly predictable, and entirely immune to catastrophic locking pileups. 
*   **Highly Scalable:** Unlike "Actual Serial Execution" which caps out at a single CPU core, SSI scales beautifully across multiple cores and even distributed architectures (like FoundationDB), because the conflict-detection math can be distributed globally. 
*   **The Only Weakness - Abort Rates:** The fundamental performance limit of SSI is the retry rate. If your application has extremely long-read-write transactions that constantly trip each other's wires, the database will spend all its time aborting and retrying them. (To solve this, keep Read-Write transactions extremely short; long-running Read-Only analytics transactions are perfectly safe from aborts).


---
## Related Concepts
* [[Data Intensive Applications]]

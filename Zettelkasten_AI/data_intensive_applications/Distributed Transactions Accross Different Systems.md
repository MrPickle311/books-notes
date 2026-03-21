---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Distributed transactions and 2PC have a very mixed reputation. They are technically essential for safety, but are heavily criticized for murdering performance (forcing extra `fsync` disk flushes) and causing catastrophic operational outages. Cloud providers often refuse to implement them entirely due to the operational headaches.

However, before dismissing them, we must realize that the phrase "Distributed Transaction" actually refers to two vastly different concepts:

**1. Database-Internal Distributed Transactions**
This is a transaction among servers that are all running the *exact same software* (e.g., all computers are running CockroachDB, or all are running VoltDB). Because the vendor entirely controls the ecosystem, they can design highly optimized, proprietary protocols for internal 2PC. These work incredibly well and perform exceptionally.

**2. Heterogeneous Distributed Transactions**
This is the nightmare scenario. A transaction that spans *two completely different vendor technologies*. For example: making sure an update to an Oracle database commits at the exact same time as a message sent via a RabbitMQ message broker.
Because the technologies are fundamentally different under the hood, they are forced to use agonizingly slow, standardized atomic commit protocols. 

#### Exactly-Once Message Processing
Why would anyone want to bind a database and a message broker together in a Heterogeneous Transaction? 
To achieve **"Exactly-Once" Semantics**.

*Imagine a streaming system:* A worker reads a task from a message queue, processes it, and writes the result to a database. 
*   If the database write fails, you want the message broker to cleanly redeliver the message later. 
*   If the database write succeeds but acknowledging the message fails, the broker will redeliver it, causing you to accidentally process it and write to the database *twice*.

By using a Distributed Transaction to bind the message broker and the database together, you guarantee that *both* happen, or *neither* happens. The message acknowledgment perfectly coalesces with the database write. If anything fails, the entire side effect is perfectly rolled back. Therefore, the message is effectively processed **exactly once**, no matter how many retries occurred in the background.

*(Limitation: This magic only works if every single system involved speaks the same atomic commit protocol. If the side-effect was sending an email—and your SMTP server doesn't support 2PC—you'll still end up sending the email twice during a retry loop!).*

#### XA Transactions
How do an Oracle database and an activeMQ message broker talk to each other to coordinate 2PC? They use **X/Open XA (eXtended Architecture)**. 

Introduced in 1991, XA is the industry standard for Heterogeneous 2PC. 
*   XA is *not* a network protocol. It is simply a C API (also implemented in Java as JTA/JDBC/JMS) for interfacing with a Transaction Coordinator.
*   The Coordinator is usually implemented as a library loaded directly inside the application process making the request. 
*   If the application crashes, the Coordinator crashes with it. The Participants (databases/message brokers) are left hanging "in doubt," completely unable to contact the Coordinator directly because all communication was routed through the application's client libraries.

#### Holding Locks While in Doubt
Why is a database getting stuck "in doubt" so catastrophic? Can't the rest of the users just ignore the frozen transaction and go about their business?
**No, because of locks.**

When a transaction modifies a row, it takes an exclusive lock. If it uses 2PL, it also takes shared locks on everything it reads. 
A database is mathematically prohibited from releasing those locks until the transaction is definitively committed or aborted. While a Participant is stuck "in doubt" waiting for a crashed Coordinator to reboot, **it must hold onto every single lock indefinitely**. 
*   If the Coordinator takes 20 minutes to reboot, those rows are completely frozen for 20 minutes.
*   If the Coordinator's log is corrupted, those rows are frozen *forever*.

Other transactions trying to access that same data will immediately block and wedge. Very quickly, a single "in doubt" transaction cascadingly blocks huge portions of your application, grinding the entire system to a complete halt.

#### Recovering from Coordinator Failure
If the Coordinator's transaction log is lost, the "in doubt" transactions literally cannot be processed. They become **Orphaned**. 
Even rebooting the database server won't fix it! A correct 2PC implementation must durably persist "in doubt" locks to disk so they survive server reboots. 

The only escape is a DB Admin manually SSHing into the server, investigating every transaction, and deciding by hand whether to kill it or commit it. 
*   **Heuristic Decisions:** Many XA implementations offer an emergency escape hatch called a "Heuristic Decision", which allows a database to unilaterally guess whether it should abort or commit an stuck transaction without the Coordinator. 
*   *Warning:* "Heuristic" is just an academic euphemism for "probably breaking atomicity." Unilaterally deciding to commit/abort breaks the entire 2PC system of promises and will almost certainly cause a torn global transaction. It is a desperate last-resort only to be used during a massive production outage to stop the bleeding of deadlocked rows.

#### Problems with XA Transactions
Ultimately, Heterogeneous XA Transactions are deeply flawed:
1.  **Single Point of Failure:** The Coordinator becomes a SPOF. Because it's embedded in the application server, the application server itself becomes a durable, stateful SPOF (which breaks the modern stateless microservice philosophy).
2.  **Lowest Common Denominator:** Because XA has to be compatible with every vendor from 1991 to today, it is incredibly basic. It cannot perform global distributed deadlock detection (because there's no standardized API for vendors to share what locks they are waiting on), and it is completely incompatible with optimistic Serializable Snapshot Isolation (SSI).

Because keeping heterogeneous systems natively consistent via XA is so fragile and dangerous, the industry has largely begun looking for totally different architectural solutions to this problem (which the book covers later in Chapters 11 and 12).

#### Database-Internal Distributed Transactions (The Good Kind)
It's important to remember that all the horror stories about XA do *not* apply to **Database-Internal Distributed Transactions**. 
Modern "NewSQL" databases (CockroachDB, Spanner, FoundationDB) route transactions across their own internal shards using highly optimized, proprietary 2PC protocols. 

Because the designers control the entire ecosystem, they can fix almost all of XA's fatal flaws:
*   **Replicated Coordinators:** They replace the single-node Coordinator with a highly available, replicated state machine using Consensus Algorithms (Chapter 10). If the Coordinator crashes, another node takes over instantly.
*   **Direct Communication:** The Coordinator and the database shards talk directly to each other, completely bypassing the application server.
*   **Smart Concurrency:** Because all shards run the same software, the distributed transaction integrates perfectly with Serializable Snapshot Isolation (SSI) or distributed deadlock detectors. 

#### Exactly-Once Message Processing (Without 2PC)
Earlier, we used a heavy Heterogeneous XA Transaction to achieve "Exactly-Once" semantics between a message broker and a database. Are we forced to use XA if we want exactly-once processing?
No. You can achieve the exact same mathematical guarantee using *only* internal database transactions, and making the operation **Idempotent**.

If the database is the only system participating in the transaction, it looks like this:
1.  **Assign Message IDs:** Guarantee every message from the broker has a globally unique ID.
2.  **Create a Tracker Table:** Create a table in your database called `processed_messages`. 
3.  **The Transaction:** When a worker receives a message, it opens a *single-node database transaction*. 
    *   It checks `processed_messages` for the ID. If it exists, it aborts the transaction (and drops the message).
    *   If it doesn't exist, it processes the message, performs its business logic writes, *and* inserts the `Message ID` into the `processed_messages` table, all in the same atomic transaction. 
4.  **Confirm to Broker:** Only after the database successfully commits do you acknowledge the message to the broker. 

**Why this works without XA:**
*   If the worker crashes *before committing*, the broker retries. The database rolls back the attempt, and the second attempt succeeds cleanly. 
*   If the worker crashes *after committing but before confirming to the broker*, the broker retries sending the duplicate. When the worker opens the new transaction, it sees the `Message ID` is already deeply persisted in the `processed_messages` table. The worker safely aborts, ignores the duplicate, and confirms to the broker. 

By using unique IDs and forcing the database operation to become Idempotent, we entirely bypassed the need for catastrophic XA Heterogeneous Transactions!
---
## Related Concepts
* [[Data Intensive Applications]]

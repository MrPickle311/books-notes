---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---

---The safety guarantees provided by transactions are universally described by the acronym **ACID** (Atomicity, Consistency, Isolation, Durability), coined in 1983. 
*(Note: Today, "ACID compliant" has sadly become a vague marketing term. Different databases implement "Isolation" entirely differently. Systems that explicitly abandon ACID are sometimes called BASE—Basically Available, Soft state, Eventual consistency—which essentially just means "Not ACID").*

#### A: Atomicity (Abortability)
In multi-threaded programming, "atomic" means a thread cannot see half-finished work. In the context of ACID databases, **Atomicity has nothing to do with concurrency**.

ACID Atomicity describes what happens if a client executes a sequence of writes, and a fault occurs halfway through (e.g., the network drops, or a disk fills up). 
*   If grouped in a transaction, the database guarantees it will **Abort / Rollback** the entire transaction, flawlessly discarding every partial write that occurred before the crash. 
*   Because of this, the application natively knows that if a transaction returns an error, *absolutely nothing* was changed in the database, meaning it is 100% safe to blindly retry the transaction without fear of accidentally duplicating data.
*(A far better word for this would have been "Abortability").*

#### C: Consistency
The word "Consistency" is the most terribly overloaded term in computer science (Replica Consistency, Consistent Hashing, CAP Theorem Consistency). 

In the context of ACID, **Consistency refers to application-specific Invariants** (statements about your data that must always be true).
*   *Example:* In an accounting system, credits and debits across all accounts must always balance to zero.
*   If a transaction starts in a valid state, and completes in a valid state, the database has maintained "Consistency."
*   *The Catch:* The database has no idea what your business logic is. While it can enforce basic rules (like Foreign Keys or Uniqueness constraints), the C in ACID largely relies on the application developer writing correct transactions. It is not a property of the database alone.

#### I: Isolation
Most databases are accessed concurrently by dozens of clients. If two clients try to access the exact same record simultaneously, you get a "Race Condition."
*   *Example:* Two users simultaneously read a counter at `42`, add 1 in their app, and write back `43`. The counter should be `44`. 
![Figure 8-1: A race condition between two clients concurrently incrementing a counter.](data_intensive_applications/figure-8-1.png)

**Isolation** guarantees that concurrently executing transactions cannot step on each other's toes. 
*   In classic database theory, perfect isolation is called **Serializability**. It guarantees that even if 100 transactions are running perfectly in parallel, the final result will be identical as if they had run *serially* (queued up one at a time).
*   *The Catch:* Perfect serializability is incredibly slow. Because of the performance cost, almost all databases (even Oracle) use weaker isolation levels (like "Snapshot Isolation") by default, intentionally allowing some obscure race conditions to occur in exchange for blazing speed.

#### D: Durability
**Durability** is the promise that once a transaction successfully reports "Committed," the data will never be lost, even if someone immediately unplugs the database from the wall.
*   *Single-Node DBs:* It means the data has been physically written to non-volatile disk/SSD, usually by forcing an `fsync()` system call, and appending it to a Write-Ahead Log to repair corrupted files upon reboot.
*   *Replicated DBs:* It means the data has been successfully copied across the network to multiple nodes.

**The Reality of Perfect Durability:**
"Perfect" durability does not exist; there are only risk-reduction techniques:
1.  If you write strictly to one disk, and that physical disk's controller dies, the data is unrecoverable until the hardware is manually fixed.
2.  If you replicate across the network to memory but don't force it to disk, a datacenter-wide power outage wipes everything instantly.
3.  SSDs have notorious firmware bugs where `fsync` silently fails, or where disconnected SSDs bleed data away after a few months.
4.  Data on disks can experience silent "Bit Rot", slowly corrupting active replicas and backups over years.
5. Subtle interactions between the storage engine and the filesystem implementation can lead to bugs that are hard to track down, and may cause files on disk to be corrupted after a crash. Filesystem errors on one replica can sometimes spread to other replicas as well.
6. Data on disk can gradually become corrupted without this being detected. If data has been corrupted for some time, replicas and recent backups may also be corrupted. In this case, you will need to try to restore the data from a historical backup.

True durability requires a layered defense: `fsync` to disks + network replication + offsite historical backups.
## Related Concepts
* [[Data Intensive Applications]]

---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
Beyond suppressing duplicates, databases must also enforce **Constraints**. 
*   **Uniqueness:** Two users cannot claim the exact same username, or book the exact same airline seat.
*   **Boundaries:** An account balance cannot dip below zero, or an inventory count below zero.

#### Why Uniqueness Requires Consensus
In a distributed system, enforcing a uniqueness constraint formally requires **Consensus**. If two users on opposite sides of the earth click "Claim Username 'Batman'" at the exact same millisecond, the distributed system must somehow mathematically agree on who won and who lost.

Historically, this meant funneling all writes through a Single Leader Node. Multi-Leader replication cannot solve this (both leaders would accept the username, sync an hour later, and realize they violated the uniqueness constraint).

#### Uniqueness in Log-Based Messaging
If you adopt the "Unbundled" approach, you can actually solve the Consensus/Uniqueness problem flawlessly using a shared event log and sharding!

Because a Stream Processor consumes from a single Kafka partition sequentially, single-threaded, it is mathematically guaranteed to process conflicting events in a perfectly unambiguous chronological order. 

Here is how you build a massively scalable, distributed username-claimer *without* legacy Distributed Transactions:
1. **Sharded Logging:** Both users click "Claim 'Batman'". The web server hashes the word 'Batman' and guarantees both requests are pushed into the exact same log shard (e.g., Partition 4).
2. **Sequential Processing:** The Stream Processor reads Partition 4. It sees User A's claim arrived 10 milliseconds before User B's. 
3. **Deterministic Choice:** It records 'Batman' as owned by User A, emits a "Success" event for User A, and explicitly emits a "Rejected: Already Taken" event for User B.

This architecture fundamentally relies on exactly one principle: **Any writes that might conflict must be routed to the exact same log shard and processed sequentially.** 
By doing this, you can infinitely scale out by simply adding more shards, achieving total correctness without relying on expensive synchronous Two-Phase Commit locks!

#### Multi-Shard Request Processing
Ensuring correctness on a single shard is easy. But what happens when an operation span across multiple shards?
Imagine a Bank Transfer:
1. Deduct $11 from Payer (Shard A)
2. Add $11 to Payee (Shard B)
3. Deduct $1 from Payer for Fees (Shard C)

A traditional SQL database would achieve this via atomic cross-shard committing. But because cross-shard locks absolutely destroy throughput, we can achieve identical correctness using **Event Logs and Stream Processors** instead.

![Figure 13-2: Atomically transferring money across shards using event logs.](figure-13-2.png)
*Figure 13-2: A complex multi-shard atomic transfer implemented entirely with asynchronous, deterministic stream processors and End-to-End Request IDs.*

**The Stream Workflow:**
1. The user's phone generates a UUID and submits the Transfer Request. It is routed to the Payer's log shard.
2. The Stream Processor for the Payer shard reads the request, verifies the balance, reserves the $12, and emits three brand new events to the network: An Outgoing Event, an Incoming Event (routed to Payee shard), and a Fee Event (routed to Fee shard). *All three events proudly carry the exact same original UUID.*
3. Independent Stream Processors read the Payee and Fee shards. They receive the Incoming/Fee events, safely update their balances, and record the UUID to ensure they never process it twice.

This is a profoundly different way to build systems. Atomicity does not come from locking the three shards together! 
Atomicity comes solely from **the very first atomic write of the Request Event to the Payer Log**. 
Because the downstream processors are strictly deterministic, once that first parent event is successfully logged, it is mathematically guaranteed that the three child events will eventually be emitted and sequentially processed by the Payee and Fee shards, no matter how many times the servers crash in between!

---
## Related Concepts
* [[Data Intensive Applications]]

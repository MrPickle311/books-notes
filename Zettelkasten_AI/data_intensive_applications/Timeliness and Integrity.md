In traditional database transactions, as soon as a `COMMIT` finishes, the new data is instantly visible to the next `SELECT` query. This is called *Strict Serializability*. 
However, in asynchronous stream processing, this is lost. The sender does not wait for the downstream views to update. The user might submit a payment, quickly hit refresh on their account balance page, and successfully see the *old* un-deducted balance because the read happened before the stream updated the cache.

To understand why this is acceptable, we must split "Consistency" into two completely separate concepts:
1.  **Timeliness:** Ensuring the user observes an up-to-date state. If timeliness is violated (the user sees an old balance), it is just *annoying*. It fixes itself if they wait 3 seconds and hit refresh again. This is "Eventual Consistency".
2.  **Integrity:** Ensuring there is no data loss or contradictory corruption. If integrity is violated (a $10 payment vanishes entirely), it is *catastrophic*. Waiting 3 seconds will not magically bring the money back. This is "Perpetual Inconsistency".

For 99% of business applications, **Integrity is vastly more important than Timeliness**. It is perfectly acceptable for a Credit Card charge to take 24 hours to appear on a statement (slow timeliness) as long as it mathematically adds up perfectly at the end of the month (strict integrity).

#### Correctness of Dataflow Systems
Traditional ACID transactions violently forced both Timeliness and Integrity together via synchronous locks. 
Event-driven Dataflow proudly *decouples* them:
*   We completely abandon Timeliness (Stream processing is inherently asynchronous).
*   We absolutely double down on Integrity! By utilizing Exactly-Once semantics, Idempotence, and End-to-End UUIDs, streaming systems achieve flawless mathematical integrity without needing Distributed Transactions.

### Loosely Interpreted Constraints (Apology Workflows)
We established earlier that enforcing hard uniqueness constraints (like "You cannot sell more items than you have in stock") requires sharding all requests sequentially through a single stream-processor node. 

However, in the real world, strict hard constraints are often bad for business!
*   **Airlines overbook flights** fully expecting someone to cancel. 
*   **Warehouses accept back-orders** knowing a forklift might accidentally run over some stock tomorrow anyway. 
*   **Banks allow you to overdraft** into the negative so they can explicitly penalize you with a lucrative $35 overdraft fee!

In business, constraints are often *loosely interpreted*. It is highly profitable to optimistically accept a write that technically violates a constraint, and then just issue an **Apology** (a "Compensating Transaction") later!
If an airline accidentally double-books a seat, the business doesn't crash like a C++ program with a Segmentation Fault. The airline simply prints out an apology letter, gives one passenger a $500 flight voucher, and puts them in a hotel. 

If your business is perfectly comfortable running an "Apology Workflow", then insisting on blocking a customer checkout behind a massive synchronous Two-Phase Commit transaction just to prevent a temporary negative inventory count is incredibly foolish. We can achieve massive scale by embracing Optimistic Asynchronous Writes, and relying on compensating events to patch up any rare conflicts after the fact.

### Coordination-Avoiding Data Systems
By piecing together everything we've learned, we arrive at two powerful conclusions:
1. **Integrity is Possible Without Atomicity:** Dataflow systems can provably ensure permanent, mathematical integrity (no lost data, no duplicate charges) *without* needing Strict Serializability, Linearizability, or expensive cross-shard Two-Phase Commits.
2. **Constraints Can Be Loose:** While perfect strict Unique Constraints structurally *require* a single node to establish Consensus, most businesses actually prefer to break constraints and execute "apology workflows" later anyway.

Combining these two facts unlocks the ultimate holy grail of distributed databases: **Coordination-Avoiding Systems**.
By entirely removing the need for servers to synchronously pause and talk to each other (Coordinate) before accepting a write, you can achieve unparalleled performance and infinite scale.

You can have a Multi-Leader database stretched across 10 regions around the entire globe. Each Datacenter can independently accept writes on its own without *ever* needing to synchronously check with the other 9 datacenters first. The system will inherently suffer from terrible *Timeliness* (because they only asynchronously sync logs later), but will flawlessly uphold *Integrity*. 

We cannot reduce the number of apologies to zero. But by avoiding coordination, we massively increase Availability and Performance. Building modern data systems means finding the sweet spot: giving up just enough traditional transaction features to avoid outages, while establishing enough event-driven integrity to ensure you don't ruin the customer experience!

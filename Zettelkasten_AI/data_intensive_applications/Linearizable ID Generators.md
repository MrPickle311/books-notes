---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
As brilliant as Lamport and Hybrid Logical Clocks are, they are fundamentally **not linearizable**. 
A Lamport clock dictates that if Node A reads a timestamp from Node B, it will mathematically guarantee its *next* timestamp is larger. But it says absolutely nothing about nodes that have never spoken to each other.

To see why this destroys linearizability, look at this scenario:
1.  **User A (Laptop):** Changes their social media account profile to "Private". This write hits the *Accounts Database*. The Accounts database uses a Lamport Clock and assigns this write `Timestamp 100`.
2.  **User A (Phone):** Five seconds later, the user uploads an embarrassing photo. This write hits the entirely separate *Photos Database*. Because the Photos Database hasn't synced with the Accounts database recently, its local Lamport clock is still artificially slow. It assigns the photo upload `Timestamp 50`.
3.  **The Flaw:** Even though the user performed the actions entirely sequentially in the real world, the logical clocks are inverted.
4.  **The Result:** A stranger queries the database. Because the photo `(50)` appears to have happened *before* the account was made Private `(100)`, the database serves the embarrassing photo to the stranger! 

![Figure 10-10: User A first sets their account to private, then shares a photo. With a non-linearizable ID generator, an unauthorized viewer may see the photo.](data_intensive_applications/figure-10-10.png)

This is why Logical Clocks are not enough for total system correctness. You need **Linearizable ID Generators**.

#### Implementing Linearizable IDs
If we cannot trust logical timestamps, how do we build an ID generator that is fully linearizable across a massive distributed database?

1.  **The Timestamp Oracle (Single Node):**
    The simplest solution is to go back to the beginning: use **one single server** whose entire job in life is to spit out sequential integers. To survive crashes, it is backed by Single-Leader replication.
    *   *Optimization:* Instead of making a network trip for every single ID, the Oracle can hand out a "batch" (e.g., IDs 1,000 to 2,000) to a worker node. If the worker crashes, those IDs are permanently skipped, but no duplicate or mathematically backwards IDs are ever created.
    *   (This is the exact architecture used by TiDB/TiKV, inspired by Google's Percolator). 
2.  **TrueTime / Synchronized Clocks (Spanner):**
    If you refuse to use a Single-Node bottleneck, you must do what Google Spanner does. Spanner uses atomic clocks and GPS receivers to calculate the exact physical time, but returns an **Uncertainty Interval** (e.g., "It is currently 1:04 PM, give or take 3 milliseconds").
    *   To guarantee linearizability across the globe, the database node simply **pauses execution and waits** for those 3 milliseconds of uncertainty to completely pass before returning the `OK` to the user. This guarantees that any subsequent write anywhere on earth will mathematically receive a larger timestamp.

#### Enforcing Constraints using Logical Clocks
If you have a strict constraint ("two people cannot claim the same username"), could you use a Logical Clock to solve it? (i.e. Just let everyone submit their request, use a Lamport clock to order them, and whoever gets the smallest timestamp wins the username?)

**No.**
To know that your timestamp `(10, NodeA)` is truly the smallest timestamp in the entire system, Node A must hear from *every single other node in the cluster* to verify they didn't generate a `(9, NodeB)`. If even one node is offline due to a network partition, the entire registration system grinds to a halt because it cannot confirm victory.

To build distributed uniqueness constraints, locks, and leader elections that actually survive datacenter outages, logical clocks and linearizable IDs are simply not enough.

**We need Consensus.**

---
## Related Concepts
* [[Data Intensive Applications]]

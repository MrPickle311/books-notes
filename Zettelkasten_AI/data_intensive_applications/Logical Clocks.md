---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Physical Clocks (Wall-Clock and Monotonic) measure the actual passage of time in seconds. 
A **Logical Clock** is an algorithm that completely ignores true physics, and instead just counts the *relative order of events*. 

A Logical Clock Timestamp doesn't tell you "What time of day it is". It only exists so you can compare it to another Logical Timestamp and definitively say "Event A happened before Event B."

A true logical clock must guarantee:
1.  It is compact and unique.
2.  Any two timestamps can be compared (they are totally ordered).
3.  **Causal Consistency:** If operation A caused or happened before B, then A's timestamp is mathematically guaranteed to be smaller than B's.

A single-node autoincrementing ID achieves this perfectly. But how do we achieve this in a distributed, fault-tolerant cluster? We need specialized algorithms.

### Lamport Timestamps
The most famous logical clock was invented by Leslie Lamport in 1978. A **Lamport Timestamp** is a brilliantly simple concept that provides a total chronological ordering consistent with causality.

Instead of a single integer, a Lamport Timestamp is a pair of two values: `(counter, node_id)`.
1.  **Node ID:** Every node in the cluster has a unique identifier (e.g., Aaliyah, Bryce, Caleb, or a Random UUID).
2.  **Counter:** Every node keeps a local integer counter that starts at 0.

**The Algorithm:**
*   Every time a node generates a new ID, it increments its local counter and returns the pair (e.g., `(1, Aaliyah)`).
*   **Crucially:** Every time a node *receives* a message from another node, it inspects the attached Lamport Timestamp. If the sender's counter is larger than the receiver's local counter, the receiver immediately jumps its local counter forward to match the sender. 

![Figure 10-9: Lamport timestamps provide a total ordering consistent with causality.](data_intensive_applications/figure-10-9.png)

This simple rule guarantees causality! If Aaliyah sends Bryce message `(1, A)`, Bryce's counter jumps to `1`. If Bryce replies, he increments his counter to `2`, generating `(2, B)`. Because `2` is greater than `1`, the reply mathematically proves it occurred *after* the original message.

If two nodes generate a message with the exact same counter, we break the tie using the `node_id` lexicographically (e.g., `(1, Aaliyah)` happens "before" `(1, Caleb)`). 

*Note: Lamport Clocks provide total causal ordering, but they **do not** provide Linearizability. The timestamp only dictates what happened before what; it cannot mathematically guarantee that you are reading the absolutely newest data in existence right now.*

### Hybrid Logical Clocks (HLC)
Lamport clocks are historically brilliant but deeply flawed in practice:
1.  They have absolutely zero relation to the physical sun. You cannot use a Lamport clock to query "All records created on Tuesday."
2.  If two nodes in a cluster never talk to each other, one node's counter might race to `10,000` while the other sits at `0`.

Modern databases (like CockroachDB) solve this using a **Hybrid Logical Clock (HLC)**. 
An HLC is the ultimate compromise. It reads the local machine's Wall-Clock (microseconds), but it enforces the Lamport algorithm on top of it:
*   If a node receives a message from a node with a timestamp of `1:04:05 PM`, but its local physical clock still says `1:04:00 PM`, the local HLC will artificially jump itself forward to `1:04:05 PM`.
*   If an NTP sync artificially forces a server clock backwards, the HLC ignores the jump and monotonically increments forward anyway.

HLCs give you the best of both worlds: a timestamp that looks and acts like understandable human physical time, but is mathematically fortified to guarantee causal ordering and survive drifting clocks.

### Logical Clocks vs. Vector Clocks
Lamport Clocks and HLCs are perfect for generating distributed Transaction IDs for Snapshot Isolation databases. 
However, they have one final blind spot: **Detecting Concurrency**. 

If you look at two Lamport timestamps—`(10, NodeA)` and `(15, NodeB)`—you can easily sort them. But you have absolutely no way to know if Node B acted causally *after* Node A, or if they both happened to generate the timestamps *at the exact same time* and Node B just had a larger integer. A Lamport clock forces a total order, destroying the nuance of concurrent events.

If it is critical for your database to definitively detect concurrent writes (so it can prompt the user to resolve the conflict), you must use a **Vector Clock**. 

Instead of passing a single integer, a Vector Clock passes an array containing the counters for *every single node in the entire cluster* (e.g., `[A: 10, B: 15, C: 2]`). By comparing these massive arrays, you can mathematically prove that two users edited the same document at the exact same time. If write A has a higher counter value than B for one node, and write B has a higher counter value than A for another node, then A and B must be concurrent. The brutal downside is storage: Vector Clocks require an enormous amount of space attached to every single row in the database, while Lamport/HLCs only require a few bytes.
---
## Related Concepts
* [[Data Intensive Applications]]

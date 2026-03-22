---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
If your app doesn't require Range Scans, the absolute best way to eliminate Skew and uniformly distribute data is to run the Partition Key through a Hash Function.

A good 32-bit hash function takes incredibly similar input strings and outputs wildly different, perfectly uniform random numbers. (It doesn't need to be cryptographically secure; MongoDB uses MD5, Cassandra uses Murmur3). For example, in Java’s `Object.hashCode()` and Ruby’s `Object#hash`, the same key may have a different hash value in different processes, making them unsuitable for sharding

**The Modulo N Problem**
Once you have the hash, how do you map it to a node?
The amateur attempt is to take `hash(key) % N` (where N is the total number of nodes). If you have 10 nodes, this neatly spits out a node ID from `0-9`.
*   **The Disadvantage:** Modulo N is a catastrophic design flaw if the cluster ever changes size. If you add an 11th node, `hash % 10` evaluates entirely differently than `hash % 11`. Almost every single piece of data in the entire database will suddenly belong to the wrong node, triggering a massive, unnecessary reshuffling of terabytes of data across the network just to rebalance.
![Figure 7-3: Assigning keys to nodes by hashing the key and taking it modulo the number of nodes. Changing the number of nodes results in many keys moving from one node to another.](data_intensive_applications/figure-7-3.png)
---
## Related Concepts
* [[Data Intensive Applications]]

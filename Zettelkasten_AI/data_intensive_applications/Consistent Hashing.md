---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
**Consistent Hashing** is an algorithm designed to fix the Modulo N problem. It distributes data in a way that satisfies two rules:
1.  Keys are distributed roughly equally across shards.
2.  When the number of shards changes (a node crashes or is added), *only the absolute minimum number of keys are moved*. 

*(Note: "Consistent" here has absolutely nothing to do with Replica Consistency from Chapter 6, or ACID Consistency from Chapter 8. It simply implies the key consistently stays in its assigned shard unless absolutely forced to move).*

There are several competing algorithms to achieve this, including Rendezvous Hashing, Jump Consistent Hash, and Cassandra's token-ring variant. 
---
## Related Concepts
* [[Data Intensive Applications]]

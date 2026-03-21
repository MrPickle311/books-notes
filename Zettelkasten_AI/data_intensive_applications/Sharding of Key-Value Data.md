---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
How do you actually decide which record goes to which shard? 
The goal is to spread the load perfectly evenly. If 10 nodes share the load equally, you can handle 10x the traffic.
*   **Skew & Hot Spots:** If the sharding algorithm is unfair, one shard might receive 90% of the traffic while the other 9 nodes sit idle. This unbalanced scenario is called **Skew**. The overloaded node is called a **Hot Spot** (and if it's caused by a single heavily-accessed record like a celebrity's social media profile, it's called a **Hot Key**).
*   **The Partition Key:** To achieve balance, the database passes a specific column/field (the Partition Key) into an algorithm to determine its destination shard.
---
## Related Concepts
* [[Data Intensive Applications]]

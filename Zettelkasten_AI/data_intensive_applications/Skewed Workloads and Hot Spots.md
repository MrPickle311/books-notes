---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Having a perfect Consistent Hashing algorithm guarantees that your *Keys* are uniformly distributed across servers. However, this does NOT guarantee your *Load* will be uniformly distributed. 

**The Celebrity Problem:**
If there are 1,000,000 normal users on Shard A, and exactly 1 Celebrity with 50 million active followers on Shard B, Shard B will melt down under extreme read/write load despite holding only 1 "key". This is a severe Hot Spot caused by highly skewed application behavior.

**Application-Level Mitigations:**
If a specific key is known to be astronomically hot, you can't rely on the database's automatic hashing. You must intervene at the application code level via **Key Splitting**:
1.  *Salt the Key:* Append a random 2-digit number (00-99) to the end of the celebrity's ID before writing. `User_402_99`.
2.  *The Result:* This splits the single celebrity's writes perfectly evenly across 100 different physical keys, which hash to dozens of different independent physical shards. The brutal write bottleneck is eliminated.
3.  *The Downside:* To read the celebrity's profile, the application must run 100 simultaneous parallel reads across the global cluster, combine all 100 responses, and serve them to the user. This severely degrades read performance.

This technique is messy. You must maintain bookkeeping logic to know *which* keys are currently hot enough to require splitting, and hot keys change dynamically over time. Cloud providers like Amazon offer automated "Heat Management" algorithms to dynamically scale and partition these unpredictable spikes behind the scenes.
---
## Related Concepts
* [[Data Intensive Applications]]

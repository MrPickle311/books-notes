---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Replication means keeping a copy of the same data on multiple machines that are connected via a network. We assume the dataset is small enough to fit on a single machine (datasets too big require sharding, covered in Chapter 7).

**Why do we replicate data?**
1.  **Latency:** Keep data geographically close to users.
2.  **Availability & Durability:** Allow the system to continue working even if parts of it fail.
3.  **Read Throughput:** Scale out the number of machines that can serve read queries.
---
## Related Concepts
* [[Data Intensive Applications]]

---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
In many applications, we need to generate unique IDs for new database records (e.g., primary keys). 

In a single-node database, the easiest solution is an **Autoincrementing Integer** (1, 2, 3...). 
This has two massive advantages:
1.  **Compactness:** It easily fits into a 32-bit or 64-bit integer.
2.  **Orderability:** Because the generator is linearizable, the numbers dictate the absolute timeline of creation. If Message `3` exists, we mathematically know it was created *after* Message `1`.

![Figure 10-8: An ID generator that assigns auto-incrementing integer IDs to messages in a chat application.](figure-10-8.png)

However, a single-node generator is a dangerous Single Point of Failure and a massive global bottleneck. If a user in Tokyo has to query a database in New York just to allocate the integer `42`, the latency will be terrible.
---
## Related Concepts
* [[Data Intensive Applications]]

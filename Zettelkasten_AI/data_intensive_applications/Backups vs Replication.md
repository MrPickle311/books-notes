---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
#### Backups vs. Replication
Replication is *not* a substitute for backups. 
*   **Replication** instantly propagates changes across nodes. If you accidentally execute `DELETE FROM users`, that deletion is instantly replicated everywhere, and you lose the data.
*   **Backups** store immutable snapshots of past states that allow you to roll back in time to recover from human error.
They are complementary: replication archives can even be stored in cold object storage to act as backups.
---
## Related Concepts
* [[Data Intensive Applications]]

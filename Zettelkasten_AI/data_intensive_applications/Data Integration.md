---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
Throughout the book, we've seen that there is no single "silver bullet" database. 
*   B-Trees are great for OLTP queries, but Column-stores are vastly superior for Analytics.
*   Single-leader replication guarantees consistency, but Multi-leader replication is required for global availability.

Because no single tool can perfectly satisfy every access pattern, a complex application inevitably requires cobbling together multiple specialized systems (e.g., Postgres as the source of truth, Elasticsearch for the full-text search bar, and Redis for caching). The hardest architectural challenge is integrating these deeply diverse data systems.

### Combining Specialized Tools by Deriving Data
When you have a piece of data that must physically exist in three different databases, you must be incredibly strict about your dataflows. 
*   **The Anti-Pattern (Dual Writes):** Allowing the backend application code to directly connect and write to both Postgres and Elasticsearch simultaneously invites catastrophic race conditions (as seen in Figure 12-4). Neither system is "in charge" of the sorting order, inevitably leading to permanent divergence.
*   **The Solution (Deriving Data):** You must establish a single "System of Record" that dictates the total order of writes. By funneling all user input into a single append-only log, you can use **Change Data Capture (CDC)** or **Event Sourcing** to mathematically *derive* the caches and search indexes by processing the writes in a strictly deterministic sequence.
---
## Related Concepts
* [[Data Intensive Applications]]

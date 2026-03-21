---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 1: Foundations"
status: pending
---
This distinction clarifies the flow of data through a complex system.

*   **System of Record (Source of Truth):** Holds the authoritative, canonical version of data. Each fact is typically represented exactly once (**normalized**). If there is a discrepancy, the value in the system of record is considered correct.
*   **Derived Data System:** Data in this system is the result of transforming or processing data from another system. It is redundant but essential for performance. If lost, it can be recreated from the source.
    *   **Examples:** Caches, search indexes, materialized views, and models trained on a dataset.
---
## Related Concepts
* [[Data Intensive Applications]]

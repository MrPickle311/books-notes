---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 1: Foundations"
status: pending
---
*   **Declarative (SQL):** You specify *what* you want (e.g., "users named 'Alice' sorted by age"), not *how* to get it. The database optimizer decides the best algorithm (indexes, join methods).
*   **Imperative (Code):** You specify the exact steps (loops, variables). Harder to optimize and parallelize.

**MapReduce** (used in MongoDB) is a hybrid: a declarative framework that accepts imperative code snippets (JavaScript functions).
---
## Related Concepts
* [[Data Intensive Applications]]

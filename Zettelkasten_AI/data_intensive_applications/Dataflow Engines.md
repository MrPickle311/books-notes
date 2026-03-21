---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
To solve MapReduce's problems, the industry created **Dataflow Engines** (like **Apache Spark** and **Apache Flink**).
Instead of breaking a massive workflow into 50 disjointed MapReduce jobs, Dataflow Engines model the entire workflow as a single continuous DAG (Directed Acyclic Graph) of data flowing through various processing stages. 

These engines offer massive advantages over MapReduce:
1.  **High-Level Operators:** Instead of writing raw java `map()` functions, you can natively call relational operations like `Join`, `GroupBy`, `Filter`, or `Count`.
2.  **Pipelining & Optimization:** Because the scheduler can see the entire DAG at once, it can combine several operations (like map + filter) into a single task. It can also begin executing downstream operators the second the first byte of input is ready, rather than waiting for the entire preceding stage to finish saving to disk.
3.  **In-Memory Intermediate State:** The biggest performance jump. Dataflow engines avoid writing intermediate data to the Distributed File System (which forces network replication overhead). Instead, intermediate data is passed strictly in RAM, or spilled out to cheap local disks, making the jobs run significantly faster than MapReduce.
---
## Related Concepts
* [[Data Intensive Applications]]

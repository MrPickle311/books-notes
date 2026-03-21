---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
### Sysops Squad Saga: Data Access For Ticket Assignment

*   **Problem:** The `Ticket Assignment Service` needs fast, frequent read access to the `expert profile` table, which is owned by the `User Management Service`.
*   **Analysis:**
    *   Service consolidation and shared data domain are ruled out because the services belong to completely different business domains.
    *   Inter-service communication is rejected by the development team ("We can't do that!") due to the severe performance impact on the assignment algorithms.
    *   This leaves **replicated caching**. The team analyzes the data volume (1.2MB total) and determines it is small enough for an in-memory cache. The data is also relatively static.
*   **Decision:** The team decides to use the **replicated caching** pattern. They accept the trade-offs of a startup dependency and the risk of using a new technology, which will be mitigated with research and a proof-of-concept.
*   **ADR: Use of in-memory replicated caching for expert profile data**
    *   **Context:** The Ticket Assignment Service needs continuous, high-performance access to expert profile data owned by the User Management Service.
    *   **Decision:** We will use replicated caching. The User Management Service will own the cache (writes), and the Ticket Assignment Service will have a read-only replica.
    *   **Justification:** This pattern resolves the performance and fault tolerance issues of inter-service communication. The data volume is low and the data is relatively static, making it a good fit.
    *   **Consequences:** The User Management Service must be running to start the first Ticket Assignment Service instance. Licensing costs for a caching product may be required.
---
## Related Concepts
* [[Architecture]]

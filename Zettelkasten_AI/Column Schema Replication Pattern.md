---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
### 2. Column Schema Replication Pattern

With this pattern, specific columns are replicated from the owning service's table to the requesting service's table. The `item_desc` column is added to the `Wishlist` table.

![Figure 10-3: The `item_desc` column has been replicated and added to the Wishlist table, giving the Wishlist service local access.](figure-10-3.png)

This pattern solves the performance and availability issues of the previous pattern but introduces new challenges.
*   **Data Synchronization:** A mechanism (usually asynchronous messaging) is required to propagate changes from the `Catalog` service to the `Wishlist` service to keep the replicated data consistent.
*   **Data Ownership Governance:** It can be difficult to prevent the `Wishlist` service from incorrectly updating the replicated `item_desc` data it does not own.
*   **Use Cases:** Best suited for data aggregation, reporting, or scenarios with high performance/fault tolerance requirements where other patterns are not a good fit.

| Advantages                        | Disadvantages                  |
| --------------------------------- | ------------------------------ |
| Good data access performance      | Data consistency issues        |
| No scalability and throughput issues | Data ownership issues          |
| No fault tolerance issues         | Data synchronization is required |
| No service dependencies           |                                |
---
## Related Concepts
* [[Architecture]]

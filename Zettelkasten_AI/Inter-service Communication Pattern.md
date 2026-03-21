---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

### The Data Access Challenge

In a distributed architecture, services need to access data owned by other services. The `Wishlist` service, for example, needs item descriptions from the `Product` table, which is owned by the `Catalog` service. This chapter explores patterns to solve this problem.

![Figure 10-1: A diagram illustrating the problem. The Wishlist service needs `item_desc` from the Product table, but it only has access to its own Wishlist table.](figure-10-1.png)

---

### 1. Inter-service Communication Pattern

This is the most common and straightforward pattern. The service needing data simply makes a remote call (e.g., REST) to the service that owns the data.

![Figure 10-2: The Wishlist service makes a synchronous remote call to the Catalog service to get item descriptions.](figure-10-2.png)

While simple, this pattern is fraught with issues.
*   **Performance:** The call suffers from network, security, and data latency, which can add up to a significant delay for the end user.
*   **Service Coupling:** The `Wishlist` service is now statically coupled to the `Catalog` service. If the `Catalog` service is down, the `Wishlist` service is also non-operational, reducing fault tolerance.
*   **Scalability:** As the `Wishlist` service scales, so must the `Catalog` service to handle the increased request load.

| Advantages                  | Disadvantages                                 |
| --------------------------- | --------------------------------------------- |
| Simplicity                  | Network, data, and security latency (performance) |
| No data volume issues       | Scalability and throughput issues             |
|                             | No fault tolerance (availability issues)      |
|                             | Requires contracts between services           |
---
## Related Concepts
* [[Architecture]]

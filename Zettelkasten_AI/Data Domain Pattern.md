---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
This pattern is an extension of the one used to solve joint ownership in Chapter 9. The tables needed by multiple services are placed in a shared schema or database, forming a broader bounded context that both services can access directly.

![Figure 10-8: The Wishlist and Product tables are placed in a shared data domain, allowing both services to access them directly via SQL.](figure-10-8.png)

This pattern should be used when other patterns are not feasible (e.g., due to data volume or consistency requirements).
*   **Advantages:** Excellent performance (simple SQL join), services are completely decoupled, and data remains highly consistent and integral (foreign keys can be used).
*   **Disadvantages:**
    *   **Broader Bounded Context:** It breaks the tight bounded context principle. Changes to the shared schema may now impact multiple services, increasing coordination effort, testing scope, and deployment risk.
    *   **Security:** The `Wishlist` service now has access to the entire data domain, which may be a security concern if it contains sensitive data not relevant to the wishlist function.

| Advantages                        | Disadvantages                                |
| --------------------------------- | -------------------------------------------- |
| Good data access performance      | Broader bounded context to manage data changes |
| No scalability and throughput issues | Data ownership governance                      |
| No fault tolerance issues         | Data access security                         |
| No service dependency             |                                              |
| Data remains consistent           |                                              |

---
## Related Concepts
* [[Architecture]]

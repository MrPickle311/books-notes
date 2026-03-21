---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
#### 1. Single Table Ownership
*   **Problem:** How to assign ownership when one service writes to a table (e.g., `User Maintenance Service` updates the `Expert Profile` table) and another needs frequent read access (e.g., `Ticket Assignment Service`).
*   **Decision:** The service that **writes** to the table is the owner.
*   **ADR: Single table ownership for bounded contexts**
    *   **Decision:** When only one service writes to a table, that table is owned by that service. Other services needing read-only access cannot connect directly to the database but must go through the owning service's API.
    *   **Consequences:** Services requiring read-only access may incur performance and fault tolerance issues.

#### 2. Joint Table Ownership
*   **Problem:** Both the `Ticket Completion Service` and the `Survey Service` need to write to the `Survey` table. This is a joint ownership scenario.
*   **Analysis:** A shared data domain won't work because a service cannot connect to two different schemas. A table split is not feasible. This leaves the delegate technique.
*   **Decision:** Use the **delegate technique**. The `Survey Service` will be the single owner of the `Survey` table. The `Ticket Completion Service` already sends a message to the Survey Service to start the survey process; it will now pass the necessary data in that message payload, delegating the database write to the Survey Service.
*   **ADR: Survey service owns the survey table**
    *   **Decision:** The Survey Service will be the single owner of the Survey table. The Ticket Completion Service will delegate its write operation by passing the data in the event it already sends.
    *   **Consequences:** The event payload is now larger. The creation of the survey record is now decoupled from the ticket completion process.

![Figure 9-21: The final architecture. The Survey service owns the data, and the Ticket Completion Service delegates its write by sending data in a message.](figure-9-21.png)

---
## Related Concepts
* [[Architecture]]

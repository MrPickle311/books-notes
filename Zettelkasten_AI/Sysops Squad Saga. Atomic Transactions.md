---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
*   **Problem:** The team models the "mark ticket complete" workflow using an **Epic Saga(sao)**, requiring an atomic update across the `Ticket Service` and the `Survey Service`.
    ![Figure 12-22: The Epic Saga workflow for completing a ticket.](figure-12-22.png)
*   **Analysis of Failures:**
    1.  **Side Effects:** If the `Survey Service` fails, a compensating update is sent to the `Ticket Service`. However, the `Ticket Service` had already published an event that was consumed by the `Analytics Service`. The analytics data is now inconsistent, and reversing it is extremely difficult. This demonstrates the lack of isolation.
        ![Figure 12-23: The side-effect problem. The Analytics Service has already processed the "completed" status before the transaction is compensated.](figure-12-23.png)
    2.  **Compensation Failure:** What if the compensating update itself fails? The system is now left in a completely inconsistent state, and the user receives a confusing error message.
        ![Figure 12-24: The compensation failure problem, leading to data inconsistency and a confusing user experience.](figure-12-24.png)
*   **Decision:** The team realizes the Epic Saga is too complex and brittle. They decide to investigate patterns that use **eventual consistency and state management** (like the Fairy Tale or Parallel sagas) as a more robust and responsive alternative.

---
## Related Concepts
* [[Architecture]]

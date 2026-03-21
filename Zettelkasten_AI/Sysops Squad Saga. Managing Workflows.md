---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
*   **Problem:** The team needs to choose a coordination pattern for the primary ticket workflow, which involves four services and six steps. Austen favors choreography for decoupling; Addison favors orchestration for control.
*   **Models:** They model the workflow using both patterns.
    *   **Choreography Model:**
        ![Figure 11-15: The Sysops Squad ticket workflow modeled using choreography.](figure-11-15.png)
    *   **Orchestration Model:**
        ![Figure 11-16: The Sysops Squad ticket workflow modeled using an orchestrator.](figure-11-16.png)
*   **Analysis:** They perform a trade-off analysis based on key business requirements:
    1.  **Workflow Control (Lost Tickets):** Orchestration is better for controlling the flow and preventing lost tickets.
    2.  **State Query (Ticket Status):** Both patterns can support this, so it's a tie.
    3.  **Error Handling (Cancellations/Reassignments):** Orchestration is clearly superior for managing complex error conditions centrally.
*   **Decision:** Based on the analysis, **orchestration** is the clear winner for this specific workflow.

| Orchestration      | Choreography |
| ------------------ | ------------ |
| workflow control   |              |
| state query        | state query  |
| error handling     |              |

*   **ADR: Use orchestration for primary ticket workflow**
    *   **Context:** The primary ticket workflow requires strong control, error handling, and status tracking.
    *   **Decision:** We will use orchestration.
    *   **Justification:** The trade-off analysis showed that orchestration better handles the key requirements for workflow control and complex error handling.
    *   **Consequences:** The orchestrator could become a scalability bottleneck if requirements change significantly in the future.
---
## Related Concepts
* [[Architecture]]

---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
### Workflow State Management in Choreography

Without a central orchestrator, managing the state of a workflow becomes a challenge.
1.  **Front Controller Pattern:** The first service in the chain (e.g., `Order Placement Service`) is designated as the state owner. Other services must call back to it to update the workflow status. This creates a "pseudo-mediator."
    ![Figure 11-13: The Order Placement Service acts as a Front Controller, and other services must communicate back to it to update the workflow state.](figure-11-13.png)
2.  **Stateless Choreography:** No service holds the transient workflow state. To find the status of an order, a query must be sent to *all* participating services to build a real-time snapshot. This is highly scalable but complex.
3.  **Stamp Coupling:** The workflow state is added to the message contract itself. Each service updates its part of the state and passes the entire state object to the next service.

#### Trade-offs for Choreography

| Advantage        | Disadvantage          |
| ---------------- | -------------------- |
| Responsiveness   | Distributed Workflow |
| Scalability      | State Management     |
| Fault tolerance  | Error handling       |
| Service decoupling | Recoverability       |

---
## Related Concepts
* [[Architecture]]

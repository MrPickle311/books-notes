---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
Instead of relying on complex compensating transactions, a better approach for sagas with eventual consistency (like the Fairy Tale or Parallel sagas) is to manage the workflow using a **finite state machine**.

An orchestrator tracks the current state of the saga (e.g., `CREATED`, `ASSIGNED`, `COMPLETED`). If a step fails (e.g., the `Survey Service` is down), the orchestrator simply moves the saga to an error state (e.g., `NO_SURVEY`) and responds successfully to the user. A background process can then handle retries or manual escalation to eventually correct the issue. This decouples the user from transient system failures and improves responsiveness.

![Figure 12-20: A Fairy Tale Saga using state management. The failure of the Survey Service doesn't fail the whole transaction for the user; it's handled asynchronously.](figure-12-20.png)
![Figure 12-21: The state machine diagram for a new problem ticket in the Sysops Squad system, showing all possible states and transitions.](figure-12-21.png)

| Initiating State | Transition State | Transaction Action           |
| ---------------- | ---------------- | ---------------------------- |
| START            | CREATED          | Assign ticket to expert      |
| CREATED          | ASSIGNED         | Route ticket to assigned expert |
| ...              | ...              | ...                          |

---
## Related Concepts
* [[Architecture]]

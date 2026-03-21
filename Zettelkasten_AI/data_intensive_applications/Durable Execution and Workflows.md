---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 2: Internals"
status: pending
---
In service-based architectures, complex processes (like processing a payment) often require coordinating multiple services (e.g. Fraud Detection -> Credit Card API -> Bank API). 
*   **Workflows:** This sequence of service calls is called a *workflow* (a graph of tasks). Depending on the framework, tasks are also called "activities" or "durable functions".
*   **Workflow Engines:** Systems that execute these workflows. They consist of an **Orchestrator** (which triggers the workflow on a schedule or via web request and delegates tasks) and an **Executor** (which physically runs the tasks).
    *   *Types:* Some focus on data ETLs (Airflow, Dagster), some allow non-engineers to build visual Business Process Models (Camunda), and others focus heavily on code-based durable execution (Temporal, Restate).

*   **Description:** This figure shows an example workflow for payment processing utilizing Business Process Model and Notation (BPMN) to visually map out task sequences and conditionals.
![Figure 5-7: Example of a workflow expressed using Business Process Model and Notation (BPMN), a graphical notation.](figure-5-7.png)

#### Durable Execution (Exactly-Once Semantics)
When dealing with distributed systems (like payments), you want your workflow to run **exactly once**. However, you cannot wrap three separate microservices and a third-party gateway into a single database transaction. If the server crashes right after charging the credit card but before depositing the funds, you are left with a broken state.

**How Durable Execution Solves This:**
Frameworks like **Temporal** provide transaction-like safety across distributed services.
*   **The WAL (Write-Ahead Log):** The framework rigorously logs every single RPC call, state change, and returned result to durable backend storage as it happens.
*   **Replay on Crash:** If the server running the workflow crashes halfway through, a new machine picks up the workflow and completely re-executes the code from the very top.
*   **Skipping the Logged Steps:** However, when the code reaches an RPC call that it ALREADY completed before the crash, the framework *intercepts the call*. Instead of actually hitting the credit card API a second time, it just reads the result from its durable log and hands it back to the code instantly, allowing the code to proceed to the incomplete steps.

**The Challenges of Durable Execution:**
While powerful, these frameworks require strict discipline:
1.  **Idempotence is still required:** You still need to pass unique IDs to external APIs so they don't double-charge if a network timeout occurs mid-call.
2.  **Code changes are extremely brittle:** Because the framework replays the code and expects it to match the historical log in exact sequence, you generally cannot re-order or modify function calls in an existing workflow script. If an in-flight workflow wakes up and the script looks different, it will fail. You must deploy massive changes as entirely separate, newly-versioned workflows.
3.  **Strict Determinism:** Replay systems break if your code is non-deterministic (meaning the same inputs produce different outputs). You cannot use standard random number generators or system clocks in a durable workflow. You must use the framework's custom, deterministic implementations of those limits, and rely on their static analysis tools to ensure you haven't broken the determinism rules.

---
## Related Concepts
* [[Data Intensive Applications]]

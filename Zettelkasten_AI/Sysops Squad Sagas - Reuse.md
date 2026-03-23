
#### 1. Common Infrastructure Logic
*   **Problem:** Different services and libraries are producing inconsistent, duplicated log messages. How should operational concerns like logging be handled consistently?
*   **Decision:** Use a **sidecar and service mesh**.
*   **ADR: Using a Sidecar for Operational Coupling**
    *   **Context:** Services require consistent operational behavior (monitoring, logging, security).
    *   **Decision:** We will use a sidecar component with a service mesh to consolidate shared operational coupling. A central infrastructure team will own the sidecar.
    *   **Consequences:** Domain classes must not be added to the sidecar to avoid inappropriate coupling.

#### 2. Shared Domain Functionality
*   **Problem:** Three new ticketing services (Creation, Assignment, Completion) all share common database access logic. Should this logic be in a **shared service** or a **shared library**?
    ![Figure 8-18: Diagram showing the shared service option, with three services calling a central "Ticket Data Service."](figure-8-18.png)
    ![Figure 8-19: Diagram showing the shared library option, with three services each including a "Shared Data DLL."](figure-8-19.png)
*   **Analysis (Trade-offs):**
    *   **Shared Service:** Would introduce significant performance and fault tolerance issues (especially for the customer-facing Ticket Creation service) and increased risk from runtime changes. The shared code was found to be stable, so the main benefit of a shared service (agility for frequent changes) did not apply.
    *   **Shared Library:** Avoids the performance and reliability issues. Since the code is stable, the downside (re-deploying services for a change) is minimal.
*   **Decision:** Use a **shared library (DLL)**.
*   **ADR: Use of a shared library for common ticketing database logic**
    *   **Context:** Deciding how to share common database logic for the three ticketing services.
    *   **Decision:** We will use a shared library.
    *   **Justification:** This approach improves performance, scalability, and fault tolerance. The shared code is stable, minimizing the impact of change control.
    *   **Consequences:** Changes to the shared library will require the ticketing services to be re-tested and re-deployed.
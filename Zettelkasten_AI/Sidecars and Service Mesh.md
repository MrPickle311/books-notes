---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

This pattern provides a solution for sharing cross-cutting **operational** concerns (logging, monitoring, security, circuit breakers) without coupling them to the domain logic.

*   **The Problem:** How do you enforce consistent logging or security across all services without forcing every team to manage the same dependencies?
*   **The Solution (Sidecar Pattern):** The operational logic is placed in a separate component (the "sidecar") that is deployed alongside every service instance. This is inspired by the Hexagonal (or Ports and Adaptors) Architecture.
    ![Figure 8-12: A diagram of the Hexagonal Architecture, showing domain logic in the center, isolated from external technical concerns by ports and adaptors.](figure-8-12.png)
*   **Service Mesh:** When every service has a sidecar, the sidecars can communicate with each other over a dedicated control plane, forming a **service mesh**. This allows for centralized operational control and observability.
    ![Figure 8-15: A diagram showing multiple services, each with a sidecar. The sidecars are all interconnected, forming a "mesh" for operational traffic.](figure-8-15.png)

The key distinction is that a sidecar is for **operational coupling**, not domain coupling. You would put logging and authentication in a sidecar, but you would *not* put a shared `Customer` class in it.

#### Trade-offs for Sidecars / Service Mesh

| Advantages                                       | Disadvantages                                |
| ------------------------------------------------ | -------------------------------------------- |
| Offers a consistent way to create isolated coupling | Must implement a sidecar per platform        |
| Allows consistent infrastructure coordination    | Sidecar component may grow large/complex     |
| Ownership can be centralized or shared           |                                              |

*   **When to Use:** The ideal pattern for managing cross-cutting operational concerns in a distributed architecture, especially microservices.
---
## Related Concepts
* [[Architecture]]

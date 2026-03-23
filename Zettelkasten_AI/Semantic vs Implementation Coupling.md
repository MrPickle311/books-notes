*   **Semantic Coupling:** The inherent complexity and number of steps required by the business domain's workflow. An architect cannot reduce this.
*   **Implementation Coupling:** The complexity added by the architectural design. An architect can make this worse.

> **Semantic Coupling:** An architect can never reduce semantic coupling via implementation, but they can make it worse.

The goal is to choose an implementation (e.g., orchestration) that closely matches the semantic coupling of the workflow without adding unnecessary complexity. Trying to force a complex, error-prone workflow into a simple choreographed implementation often leads to a tangled mess.

![Figure 11-12: In a technically partitioned layered architecture, a single domain concept like "Catalog Checkout" is smeared across all layers, increasing implementation complexity. In a domain-partitioned architecture, it is cleanly encapsulated.](figure-11-12.png)

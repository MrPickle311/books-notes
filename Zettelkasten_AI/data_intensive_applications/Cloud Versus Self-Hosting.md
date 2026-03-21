---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 1: Foundations"
status: pending
---
This section discusses the "build vs. buy" decision for software and infrastructure.

*   **Description**: This diagram shows a spectrum of choices for software development and operations. At one end is fully bespoke software, written and run in-house. At the other end are SaaS products, where both development and operations are handled by a vendor. In the middle lies off-the-shelf software (like open-source databases) that a company chooses to self-host.
![Figure 1-2: A spectrum of types of software and its operations.](figure-1-2.png)

#### Pros and Cons of Cloud Services
*   **Pros:**
    *   Can be faster and easier to get started.
    *   Outsources operational expertise.
    *   **Elasticity:** Valuable for variable workloads, allowing resources to scale up and down.
*   **Cons:**
    *   **Lack of control:** Can't add missing features or easily diagnose deep performance issues.
    *   **Vendor Lock-in:** High cost of switching if the service's API is not standard.
    *   **Security & Compliance:** Requires trusting the provider with your data.
---
## Related Concepts
* [[Data Intensive Applications]]

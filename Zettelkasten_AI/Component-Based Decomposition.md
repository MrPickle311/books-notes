---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

### Component-Based Decomposition

This is an extraction-based approach best suited for codebases with well-defined (or at least loosely-defined) components. It involves refactoring to refine component boundaries, group them into domains, and then extract them as services.

![Figure 4-5: A diagram showing a directory path mapping to a component namespace, e.g., `penultimate/ss/ticket/assign` becomes `penultimate.ss.ticket.assign`.](figure-4-5.png)

> **Tip:** When breaking apart monolithic applications into distributed architectures, build services from components, not individual components.

This approach often leads to a **Service-Based Architecture** first, which is a pragmatic stepping stone to microservices because:
*   It allows architects to focus on domain partitioning before tackling database decomposition.
*   It doesn't require a full leap to containerization and complex operational automation.
*   It's primarily a technical move that doesn't immediately require reorganizing teams.

> **Tip:** When migrating monolithic applications to Microservices, move to a Service-based architecture first as a stepping stone to Microservices.
---
## Related Concepts
* [[Architecture]]

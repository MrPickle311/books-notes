---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: processed
---
> **1. Use the "Water Glass" Analogy.** This is a simple but effective way to explain the scalability limitations of a monolith and the benefits of modularity to business stakeholders.
>
> **2. Frame Refactoring as a Business Enabler.** Don't talk about technical purity. Build a business case by mapping the system's specific, painful problems to the concrete benefits of the five modularity drivers (e.g., "By improving fault tolerance, we will solve the availability issue that is causing customer dissatisfaction.").
>
> **3. Formalize Decisions with an ADR.** Use an Architecture Decision Record to clearly document the context, decision, justification, and consequences of a major architectural change. This creates alignment and provides a historical record.
>
> **4. Beware of Distributed Monoliths.** True modularity requires more than just breaking code into separate services. If your services have high inter-service communication (especially synchronous calls), you will lose the benefits of testability, deployability, and fault tolerance.
>
> **5. Differentiate Scalability from Elasticity.** Understand that scalability (handling gradual growth) is improved by modularity, while elasticity (handling load spikes) is improved by fine-grained services with a low start-up time (MTTS).
>
> **6. Emphasize Asynchronous Communication for Resilience.** To achieve true fault tolerance where the failure of one service doesn't cascade to others, you must favor asynchronous communication.
---
## Related Concepts
* [[Architecture]]

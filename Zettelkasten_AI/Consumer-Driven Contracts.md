---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
This pattern resolves the classic microservices dilemma: how to have loosely coupled services while still guaranteeing contract fidelity. It inverts the traditional "push" model into a "pull" model.

*   **How it works:** Each **consumer** service defines a contract specifying exactly the data it needs from a **provider** service. This contract is given to the provider team, who integrates it into their build pipeline as a set of automated tests. The provider must keep these consumer contract tests "green" at all times.
*   **Benefit:** The provider can evolve its internal model freely, but the build will break if they make a change that violates a contract a consumer depends on. This allows for loose coupling (name/value pairs) with automated governance.

![Figure 13-5: A diagram showing one provider service running contract tests for three different consumer services.](figure-13-5.png)

| Advantages                               | Disadvantages                      |
| ---------------------------------------- | ---------------------------------- |
| Allows loose contract coupling between services | Requires engineering maturity      |
| Allows variability in strictness         | Two interlocking mechanisms rather than one |
| Evolvable                                |                                    |
---
## Related Concepts
* [[Architecture]]

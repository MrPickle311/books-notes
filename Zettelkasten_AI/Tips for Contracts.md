---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
> **1. Treat Contracts as a First-Class Architectural Concern.** Contracts are the "glue" of your system. The choice between strict and loose coupling has profound impacts on evolvability, brittleness, and team autonomy.

> **2. Default to Loose Contracts, Justify Strict Ones.** In distributed architectures like microservices, loose coupling is a primary goal. Start with the assumption of a loose contract (e.g., name/value pairs) and only introduce stricter contracts when there is a clear justification, such as tightly coupled domain logic.

> **3. Use Consumer-Driven Contracts to Govern Loose Contracts.** Don't let "loose contract" mean "unreliable contract." Implement consumer-driven contracts as an architectural fitness function to get the best of both worlds: the flexibility of loose coupling with the safety of automated verification.

> **4. Design Contracts Based on Need-to-Know.** Avoid the stamp coupling anti-pattern. A consumer's contract should only specify the fields it actually needs, not the provider's entire data model. This prevents brittle changes and reduces unnecessary coupling.

> **5. Consider Real-World Constraints in Contract Design.** As the mobile app saga shows, deployment constraints can be a powerful driver for contract design. A technically "perfect" strict contract is useless if it can't be deployed effectively.

> **6. Leverage Stamp Coupling for Complex Choreography.** When you need the scalability of choreography for a complex workflow, consider using stamp coupling as a deliberate pattern to pass workflow state between services. This is a valid trade-off of data coupling for coordination.

> **7. Don't Confuse Implementation with Contract Style.** gRPC, REST, and Messaging are implementations, not contract styles in themselves. You can implement a relatively loose contract with gRPC (by using flexible message types) or a very strict one with REST (by enforcing a rigid schema). Focus on the desired level of coupling first, then choose the tool.
---
## Related Concepts
* [[Architecture]]

---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
> **1. Use the Eight Saga Patterns as a Design Palette.** Don't assume "saga" just means one thing. Use the matrix of eight patterns to consciously choose the right set of trade-offs for your specific workflow. Start by deciding on your required consistency level (atomic vs. eventual).

> **2. Avoid Atomic Sagas (Epic, Phone Tag, etc.) if Possible.** Attempting to achieve perfect atomic consistency across distributed services is fraught with complexity, side effects, and failure modes. Challenge business requirements that demand it and favor eventual consistency unless absolutely necessary.

> **3. Prefer Eventual Consistency with State Management.** For most distributed workflows, a saga pattern based on eventual consistency (like Fairy Tale or Parallel) combined with a state machine in an orchestrator provides the best balance of responsiveness, scalability, and manageable complexity.

> **4. Decouple the User from System Failures.** A key benefit of eventual consistency and state management is that transient downstream failures don't have to fail the entire operation for the user. The saga can enter an error state and be corrected asynchronously, leading to a much better user experience.

> **5. Understand the Horror of the "Horror Story(aac)" Pattern.** Be aware that combining atomic consistency with asynchronous, choreographed communication is a recipe for disaster. If you find yourself designing this, it's a sign that you need to re-evaluate your core architectural choices, likely by relaxing the atomicity constraint.

> **6. Use Orchestration for Complex, State-Dependent Workflows.** If your workflow has multiple steps, error conditions, and branching logic, an orchestrator (as in the Fairy Tale or Parallel sagas) is invaluable for centralizing that logic and managing state.

> **7. Use Choreography for High-Throughput, Simple Workflows.** If your workflow is a simple, linear chain and your primary driver is maximum scale and decoupling, a choreographed pattern (like Time Travel or Anthology) is a good fit.

> **8. Document Your Sagas Programmatically.** Use techniques like custom annotations or attributes to embed saga participation information directly in your service code. This creates a living document that helps developers understand the testing scope and impact of changes.

---
## Related Concepts
* [[Architecture]]

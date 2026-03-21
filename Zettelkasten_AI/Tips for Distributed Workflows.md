---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

> **1. Choose Your Pattern Based on Workflow Complexity.** Don't default to one style. Use choreography for simple, high-throughput workflows. Use orchestration for complex workflows with significant error handling, branching, or compensation logic.

> **2. Don't Let Implementation Add Complexity.** Understand the inherent (semantic) complexity of your business workflow. Your architecture should model this complexity as directly as possible, not add new layers of implementation complexity on top of it.

> **3. Make State Management a Deliberate Decision.** In choreographed systems, deciding where workflow state lives is a critical decision. Choose explicitly between a Front Controller, a stateless approach, or stamp coupling based on your needs for queryability and performance.

> **4. Recognize That Error Handling Is the Litmus Test.** The "happy path" often looks simple in any pattern. The true test of your design is how it handles error conditions, retries, and compensating transactions. This is where the cost of choreography's "simplicity" becomes apparent.

> **5. Keep Orchestrators Scoped to a Single Workflow.** Avoid creating a monolithic Enterprise Service Bus (ESB). In a microservices architecture, each complex workflow should have its own dedicated, single-purpose orchestrator.

> **6. Perform a Formal Trade-Off Analysis.** Don't let the decision be guided by dogma ("always use choreography for decoupling"). Use a structured process like the one in the Sysops Squad saga to compare the patterns against your specific business drivers and architectural characteristics.

> **7. Document Your Workflow Decision in an ADR.** The choice between orchestration and choreography has a major impact on the system's coupling, scalability, and maintainability. Record why you chose a particular pattern and the trade-offs you accepted.

---
## Related Concepts
* [[Architecture]]

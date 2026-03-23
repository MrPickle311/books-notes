---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

### When Does Reuse Add Value?

The chapter concludes with a critical insight: the value of reuse depends on the rate of change.

> **Reuse is derived via abstraction but operationalized by slow rate of change.**

Architects in the early SOA era mistakenly tried to reuse everything, including highly volatile domain concepts like "Customer," leading to brittle, complex systems.

![Figure 8-17: A diagram showing a disastrous architecture where multiple business domains are all tightly coupled to a single, monstrous "CustomerService."](figure-8-17.png)

We benefit from reusing things that are stable (operating systems, frameworks, static utilities). Coupling to things that change frequently creates brittleness. The goal is to identify stable components for reuse and find other patterns for volatile ones.

---
## Related Concepts
* [[Architecture]]

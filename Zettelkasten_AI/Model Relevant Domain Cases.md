---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
Abstract analysis only goes so far. Architects should model specific, relevant domain scenarios to uncover the real trade-offs hidden in complex workflows. For example, when deciding between a single payment service and multiple services, modeling a complex workflow that uses multiple payment types reveals the trade-off between performance (single service) and extensibility (separate services).

![Figure 15-5: Choosing between a single payment service or one per payment type](figure-15-5.png)
![Figure 15-6: Scenario 1: update credit card processing service](figure-15-6.png)
![Figure 15-7: Scenario 2: adding a payment type](figure-15-7.png)
![Figure 15-8: Scenario 3: using multiple types for payment](figure-15-8.png)

---
## Related Concepts
* [[Architecture]]

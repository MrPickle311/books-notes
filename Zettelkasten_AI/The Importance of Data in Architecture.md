---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: processed
---
Data is a company's most durable asset, often outliving the systems and architectures that create it. Many of the "hard parts" of modern distributed architecture arise from the tension between architectural goals (like service independence) and data concerns (like consistency and integrity).

*   **Operational Data (OLTP):** Online Transactional Processing data is what the company *runs on*. It includes sales, inventory, and customer transactions. It's typically relational and must be highly available and consistent. If this data is interrupted, the business stops.
*   **Analytical Data:** Data used by business analysts and data scientists for trending, predictions, and business intelligence. It is not critical for moment-to-moment operations but for long-term strategic decisions. It's often non-relational and can exist in different formats (e.g., graph databases, data lakes).
---
## Related Concepts
* [[Architecture]]

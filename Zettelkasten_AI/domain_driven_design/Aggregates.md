---
aliases:
tags:
  - domaindrivendesign
source_book: "Domain-Driven Design"
status: pending
---
An aggregate is a cluster of domain objects (entities and value objects) that can be treated as a single unit for data changes.

#### Core Responsibilities

1.  **Consistency Enforcement:** Acts as a consistency boundary, ensuring all business rules and invariants are protected
2.  **Transaction Boundary:** All changes within an aggregate must be committed atomically
3.  **Public Interface:** External access only through the aggregate root's commands

![Figure 6-3: A diagram showing an aggregate as a hierarchy of entities within a single consistency boundary.](figure-6-3.png)
---
## Related Concepts
* [[Domain Driven Design]]

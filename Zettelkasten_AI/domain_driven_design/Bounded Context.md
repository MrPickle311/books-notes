---
aliases:
tags:
  - domaindrivendesign
source_book: "Domain-Driven Design"
status: pending
---
The DDD solution is to stop trying to create one model. Instead, we divide the system into multiple models and explicitly define the boundary where each model applies. This boundary is called a **Bounded Context**.

*   **Core Idea:** A Bounded Context is a boundary (a line drawn in the sand) within which a particular model and its Ubiquitous Language are consistent and have a precise meaning.
*   **Consistency:** The term "Lead" can exist in both the `Marketing Context` and the `Sales Context`. As long as it has one, unambiguous meaning *inside* each context, the model is sound.
*   **Refined Ubiquitous Language:** A Ubiquitous Language is not universal across the company; it is only ubiquitous *within its Bounded Context*.

![Figure 3-3: A diagram showing the "Lead" model split into Marketing and Sales bounded contexts.](figure-3-3.png)
---
## Related Concepts
* [[Domain Driven Design]]

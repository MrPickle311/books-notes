---
aliases:
tags:
  - domaindrivendesign
source_book: "Domain-Driven Design"
status: pending
---
In any large organization, different departments or experts often use the same term to mean different things. This creates ambiguity that is difficult to represent in a single, unified software model.

*   **Example:** The term **"Lead"** can have conflicting meanings.
    *   To the **Marketing** department, a lead is a simple notification of interest.
    *   To the **Sales** department, a lead is a complex entity representing the entire sales lifecycle.

![Figure 3-1: A diagram showing a telemarketing company with marketing and sales departments.](figure-3-1.png)

Trying to force a single model for "Lead" for the entire company results in a solution that is either over-engineered for Marketing or under-engineered for Sales. The traditional approach of creating a massive, enterprise-wide model often fails, becoming a "jack of all trades, master of none."

![Figure 3-2: An image of a massive, complex entity-relationship diagram covering a wall.](figure-3-2.png)
---
## Related Concepts
* [[Domain Driven Design]]

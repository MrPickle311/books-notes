---
aliases:
tags:
  - domaindrivendesign
source_book: "Domain-Driven Design"
status: pending
---
This is one of the most crucial concepts in DDD. They are not the same thing.

| Aspect | Subdomains | Bounded Contexts |
| :--- | :--- | :--- |
| **Space** | **Problem Space** | **Solution Space** |
| **How to find** | You **discover** them | You **design** them |
| **Purpose** | To analyze the business and its strategy (Core, Supporting, Generic). | To define the boundaries of your software models and teams. |

The relationship is not always one-to-one. You might design a single Bounded Context that contains multiple subdomains, or you might design multiple Bounded Contexts to model different aspects of a single, complex subdomain.

![Figure 3-7: A diagram showing Bounded Contexts aligned one-to-one with subdomains.](figure-3-7.png)
---
## Related Concepts
* [[Domain Driven Design]]

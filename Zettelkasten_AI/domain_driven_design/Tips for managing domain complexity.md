---
aliases:
tags:
  - domaindrivendesign
source_book: "Domain-Driven Design"
status: pending
---
Here are some key takeaways and guiding principles from this chapter that you can apply in your day-to-day work as a software architect and developer.

> **1. Avoid the "One-Model-to-Rule-Them-All" Trap.**
> Resist the urge to create a single, massive, enterprise-wide model that tries to satisfy every department. Such models inevitably become overly complex, hard to maintain, and a "master of none."

> **2. Don't Just Prefix Conflicting Terms; Define Their Context.**
> When you find terms like "Lead" or "Customer" meaning different things to different teams, don't just rename them in code (e.g., `SalesLead`, `MarketingLead`). This is a code smell indicating a hidden boundary. Instead, make that boundary explicit by designing a Bounded Context for each meaning.

> **3. A Bounded Context is a Design Choice You Make.**
> Remember this crucial distinction: You **discover** subdomains (the problem space), but you **design** Bounded Contexts (the solution space). You have the power to draw the lines of your system to best fit your technical and organizational needs. They don't have to be a 1-to-1 match with subdomains.

> **4. Use Bounded Contexts to Define Team Ownership.**
> Enforce a strict rule: **"One team per Bounded Context."** Never have multiple teams working on the same context. This creates clear ownership, reduces communication overhead, and allows teams to work more autonomously. A single team *can*, however, own multiple contexts.

> **5. A Bounded Context is a Physical, Deployable Unit.**
> Each Bounded Context should be its own project, service, or independent module. This allows it to be developed, deployed, and versioned independently, which is the foundation of a microservices architecture.

> **6. Use Bounded Contexts to Enable Technical Freedom.**
> Because contexts are physically separate, they don't have to share the same technology stack. One team can use Java, another can use Python, and a third can use a specialized database. Use this to pick the right tool for the job within each context.

> **7. Find the "Goldilocks" Size for Your Contexts.**
> There is no perfect size, but there are two extremes to avoid:
> *   **Too Big:** The language becomes inconsistent and the model is hard to maintain.
> *   **Too Small:** You create excessive integration overhead between dozens of tiny services.
> A good rule of thumb is to **keep coherent use cases together.** Don't split a feature that naturally belongs together across multiple Bounded Contexts.

> **8. A "Ubiquitous Language" is Only Ubiquitous Within Its Boundary.**
> Don't get hung up on the word "ubiquitous." The language is only shared and consistent *inside* its Bounded Context. You should expect—and design for—the language to be different across different contexts. 
---
## Related Concepts
* [[Domain Driven Design]]

---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

#### Ticket Assignment Granularity

*   **Problem:** Should ticket *assignment* (complex algorithms) and ticket *routing* (sending the ticket to an expert's device) be one service or two?
    ![Figure 7-18: A diagram showing two options: a single "Ticket Assignment Service" or two separate "Ticket Assignment" and "Ticket Routing" services.](figure-7-18.png)
*   **Analysis:**
    *   **Disintegrator (Code Volatility):** The assignment algorithms change frequently, while routing logic is stable. This favors splitting.
    *   **Integrator (Workflow):** Assignment and routing are tightly bound. A ticket is assigned *then immediately routed*. If routing fails, a new assignment must be made. This creates a "chatty," synchronous workflow between the two potential services.
*   **Decision:** Combine them into a **single consolidated service**. The performance and reliability problems from the tight workflow were deemed more important than isolating the volatile code. The team agreed to use internal components (namespaces) to keep the code logically separate within the single service.
*   **ADR: Consolidated service for ticket assignment and routing**
    *   **Context:** Deciding between a single service or two separate services for ticket assignment and routing.
    *   **Decision:** We will create a single consolidated ticket assignment service.
    *   **Justification:** The two operations are tightly bound and synchronous. Performance, fault tolerance, and workflow control favor a single service. Scalability needs are identical for both functions.
    *   **Consequences:** Changes to either assignment or routing logic will require testing and deployment of the entire service, increasing scope and risk.

#### Customer Registration Granularity

*   **Problem:** Should customer registration (Profile, Credit Card, Password, Products) be one service, four separate services, or two services (secure vs. non-secure data)?
    ![Figure 7-19: A diagram showing three options for customer services: one large service, four small services, or two medium services.](figure-7-19.png)
*   **Analysis:**
    *   **Disintegrator (Security):** Credit card and password data are highly sensitive. Separating them provides better security access control. This favors splitting.
    *   **Integrator (Database Transactions):** The business stakeholder (Parker) declared that customer registration *must* be an all-or-nothing, atomic operation. This is a hard requirement for an ACID transaction, which is impossible across separate services with separate databases.
*   **Decision:** Combine them into a **single consolidated service**. The business requirement for transactional integrity outweighed the architectural preference for separating services for security. The security risk was mitigated through other means (using the "Tortoise" security library at the API and service mesh layers).
*   **ADR: Consolidated service for customer-related functionality**
    *   **Context:** Deciding the granularity for services handling customer profile, credit card, password, and product data.
    *   **Decision:** We will create a single consolidated customer service for all four functions.
    *   **Justification:** The requirement for a single, atomic (ACID) transaction for customer registration and unsubscription is paramount. Security risks are acceptably mitigated by using the Tortoise security library.
    *   **Consequences:** Security access must be managed carefully within the single service. Testing scope and deployment risk are increased. The combined functionality must scale as a single unit.
---
## Related Concepts
* [[Architecture]]

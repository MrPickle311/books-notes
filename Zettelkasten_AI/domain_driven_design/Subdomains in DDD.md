---
aliases:
tags:
  - domaindrivendesign
source_book: "Domain-Driven Design"
status: pending
---
### The Three Types of Subdomains

#### 1. Core Subdomains
- **What they are:** The unique, strategic parts of the business that provide a **competitive advantage**. This is what the company does better than its competitors.
- **Complexity:** High. They involve complex business logic, proprietary algorithms, or unique processes.
- **Volatility:** High. They change frequently as the business innovates and responds to the market.
- **Strategy:** Build **in-house** with the most skilled team members. These are critical investments that shouldn't be outsourced.
- **Example:** For Uber, the ride-matching and dynamic pricing algorithms are core subdomains.

#### 2. Generic Subdomains
- **What they are:** Complex problems that are common to many businesses and have already been solved. They offer **no competitive advantage**.
- **Complexity:** High. The problems themselves are difficult (e.g., authentication, payment processing).
- **Volatility:** Low. The solutions are stable and well-established.
- **Strategy:** **Buy or adopt** an off-the-shelf or open-source solution. It's more cost-effective than building one from scratch.
- **Example:** An authentication service (like OAuth) or a credit card payment gateway.

#### 3. Supporting Subdomains
- **What they are:** Activities necessary for the business to function but which do not provide a competitive advantage and are not complex enough to have generic solutions.
- **Complexity:** Low. The business logic is typically simple, often involving basic data entry and management (CRUD operations).
- **Volatility:** Low. They do not change often.
- **Strategy:** Can be built **in-house or outsourced**. Since they are not strategic, they don't require the best developers or advanced engineering techniques.
- **Example:** A simple content management system for an internal knowledge base or a tool for managing discount codes.

---

### Comparison Summary

| Aspect | Core | Generic | Supporting |
| :--- | :--- | :--- | :--- |
| **Competitive Advantage** | **Yes** | No | No |
| **Complexity** | High | High | Low |
| **Volatility (Rate of Change)** | High | Low | Low |
| **Solution Strategy** | Build In-house | Buy / Adopt | In-house / Outsource |
---
## Related Concepts
* [[Domain Driven Design]]

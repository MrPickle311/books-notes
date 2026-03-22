---
aliases:
tags:
  - domaindrivendesign
source_book: "Domain-Driven Design"
status: pending
---
The cornerstone of DDD for solving this communication problem is the **Ubiquitous Language**.

*   **Core Idea:** A single, shared language that is developed collaboratively by all project stakeholders—domain experts, engineers, product owners, designers, etc.
*   **Purpose:** To eliminate ambiguity and ensure that when someone says "campaign" or "account," everyone has the exact same concept in mind.
*   **Application:** This language should be used *everywhere*: in conversations, diagrams, documentation, requirements, tests, and most importantly, in the source code itself.

### Characteristics of a Good Ubiquous Language

A Ubiquitous Language is a **model of the business domain**. It is a simplified representation intended to solve a problem, much like a map is a model of the world that omits unnecessary details.

![Figure 2-3: An image showing various types of maps (terrain, subway, nautical, etc.) as different models of the earth.](figure-2-3.png)

To be effective, it must be:

1.  **The Language of the Business, Not Technology:**
    *   It must consist of business terms that domain experts use and understand.
    *   **Avoid technical jargon.**
    *   **Good Example (Business Language):** "A campaign can be published only if at least one of its placements is active."
    *   **Bad Example (Technical Language):** "A campaign can be published only if it has at least one associated record in the active-placements table."

2.  **Precise and Consistent:**
    *   **Eliminate Ambiguity:** If a term like "Policy" can mean two different things (e.g., an insurance contract vs. a regulatory rule), the language must be refined to use two distinct terms: `Insurance Contract` and `Regulatory Rule`.
    *   **Eliminate Synonyms:** If the business uses "user," "visitor," and "customer" interchangeably, dig deeper. These often represent different concepts with different behaviors and should be named explicitly in the language (e.g., an `Unregistered Visitor` vs. a `Registered Customer`).
---
## Related Concepts
* [[Domain Driven Design]]

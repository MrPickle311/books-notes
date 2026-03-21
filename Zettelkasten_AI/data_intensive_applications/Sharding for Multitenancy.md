---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
In modern SaaS applications, you often have hundreds of completely independent corporate customers (Tenants). You can utilize sharding by assigning each Tenant its own dedicated logical shard (or grouping small tenants into shared shards).

**Advantages of Tenant-based Sharding:**
*   **Resource Isolation:** A heavy algorithmic query run by Tenant A won't consume the CPU of Tenant B (preventing the "Noisy Neighbor" problem).
*   **Permission Isolation:** A severe security bug in the application logic is much less likely to accidentally leak Tenant A's private data to Tenant B if they are physically isolated in different databases.
*   **Cell-based Architecture:** You can group tenants into isolated "cells." If an infrastructure layer crashes, the blast radius is contained. Only one cell goes down, leaving the rest of your customers perfectly online.
*   **Per-Tenant Administration:** You can take a snapshot backup of a single customer, or instantly export/delete a customer's entire dataset to perfectly comply with GDPR "Right to be Forgotten" regulations.
*   **Data Residency:** You can strictly bind a European tenant's shard to a German datacenter to easily comply with local data sovereignty laws.
*   **Gradual Rollouts:** Schema migrations can be rolled out to exactly 1% of tenants to test for bugs before affecting the entire company.

**Disadvantages of Tenant-based Sharding:**
*   **The "Whale" Tenant:** If one massive enterprise customer grows so large that their data exceeds the capacity of a single machine, the entire tenant-sharding model breaks down. You must now painfully shard data *within* that specific tenant's shard.
*   **Overhead:** Giving millions of tiny "freemium" user-tenants their own dedicated shards creates massive, unjustifiable administrative overhead.
*   **Cross-Tenant Analytics:** Running a global ML model across all customers becomes excruciatingly difficult if the data is hard-siloed across hundreds of separate physical shards.
---
## Related Concepts
* [[Data Intensive Applications]]

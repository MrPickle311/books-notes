
> **1. Use the Disintegrator/Integrator Framework for Objective Analysis.** Replace gut feelings and subjective opinions ("micro means small") with a structured analysis of the opposing forces. This leads to better, more justifiable decisions.

> **2. Find the Equilibrium; Don't Just Maximize Disintegration.** The goal is not to create the smallest possible services. The goal is to find the right balance for your specific context by weighing the trade-offs between the disintegrators and integrators.

> **3. Prioritize Business Requirements for Transactions.** The need for a true ACID transaction is one of the strongest integrators. If a business process absolutely requires an atomic, all-or-nothing operation, you must keep that functionality within a single service boundary.

> **4. Beware of "Chatty" Services.** High-frequency, synchronous communication between services is a strong indicator that they are too granular. This workflow creates performance bottlenecks and brittle, cascading failures. When you see it, consider combining the services.

> **5. Isolate Volatility and Different Scalability Needs.** Code that changes frequently or has vastly different performance requirements from the rest of the service are prime candidates for being split into their own services. These are powerful and common disintegration drivers.

> **6. Treat Shared Domain Code as a Red Flag.** Shared libraries containing common *domain* logic (not utilities) create a distributed monolith. If multiple services rely heavily on the same shared domain code, it's a strong sign they should be integrated into a single service.

> **7. Formalize Granularity Decisions with ADRs.** Service granularity decisions have long-lasting consequences. Document the options considered, the final decision, and most importantly, the trade-offs that were accepted (e.g., "We accepted lower agility to guarantee transactional integrity").
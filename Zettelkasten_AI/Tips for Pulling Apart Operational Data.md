
> **1. Justify Data Decomposition with Clear Drivers.** Don't break apart a database just because it's a "best practice." Build a solid business case using the data disintegrators (change control, scalability, fault tolerance, etc.) to justify the effort and risk.

> **2. Balance Disintegrators with Integrators.** Acknowledge the forces keeping data together. The need for strong transactional consistency and the complexity of breaking foreign key relationships are powerful integrators. The right answer is a trade-off, not an absolute.

> **3. Use the Five-Step Process for a Safe Migration.** Follow the iterative process of identifying domains, logically separating them with schemas, refactoring service connections, and only then physically moving them to new servers. This controls risk at each stage.

> **4. Think in Data Domains.** Use data domains as your primary unit of analysis for database decomposition. A data domain defines the bounded context for your data and is the precursor to a data-sovereign service.

> **5. Don't Reach Into Other Databases.** Once you've established data domains, enforce the rule that a service can only access the data it owns. If it needs other data, it must make an API call to the service that owns that data.

> **6. Embrace Polyglot Persistence.** Don't assume a relational database is the answer to everything. Analyze the specific needs of each data domain and choose the database type (Document, Graph, Key-Value, etc.) that provides the optimal set of trade-offs for that domain.

> **7. Document Data Architecture Decisions in an ADR.** Decisions about data are among the most critical and long-lasting. Use an ADR to capture the context, the decision (e.g., "Use a single aggregate document model for surveys"), the justification, and the consequences (e.g., "We accept data duplication to improve UI performance").
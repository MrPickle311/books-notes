While both CDC and Event Sourcing revolve around streaming an immutable log of events, they operate at completely different levels of abstraction.

*   **Change Data Capture (Low-Level):** CDC intercepts physical database updates. The application code still acts completely normally, writing `UPDATE` and `DELETE` queries to a mutable table. The CDC stream simply extracts the low-level row diffs (e.g., "Row 42's 'status' column changed from 'active' to 'inactive'").
*   **Event Sourcing (High-Level):** Event sourcing forces the application to be entirely rebuilt around immutable events. `UPDATES` and `DELETES` are explicitly banned in the application code. Instead, the application writes business-level intents (e.g., "User clicked Cancel Subscription Button") to a permanent event log. The current state is purely derived by summing up those specific high-level events.

**Compaction Differences:** Because CDC logs contain physical row states, they are perfectly suited for Kafka Log Compaction (you only need the newest version of Row 42). 
Event Sourcing logs *cannot* be simply compacted like this. Because the events represent high-level business intents that act incrementally, dropping an older event permanently destroys the history required to reconstruct the final aggregate state. Event sourcing requires keeping the entire raw history forever, periodically taking "Snapshots" for operational read-speed optimizations.

### The Downside of CDC: Public Data Schemas
While CDC is much easier to adopt than Event Sourcing (because the core application doesn't have to rewrite its `UPDATE` logic), it introduces a massive API maintenance nightmare.

In a normal Microservice architecture, the underlying database schema is considered purely internal. An engineer can rename columns or delete tables without breaking the rest of the company, as long as the service's HTTP JSON API stays the same.
However, turning on a CDC stream instantly transforms the underlying Postgres schema into a Public API. Every downstream Cache, Search Index, and Analytics Dashboard becomes physically hardcoded to the internal column names of your database table. If a developer innocently refactors a column name, it immediately breaks every consumer listening to the Kafka stream, causing a catastrophic company-wide outage.

#### The Outbox Pattern
To solve the schema-coupling nightmare, engineers combine the CDC stream with the **Outbox Pattern**.

Instead of attaching the CDC tool to the internal `users` table, the domain service creates a dedicated, entirely separate `outbox` table. 
1. When a user updates their profile, the application writes the final data to the internal `users` table, and constructs a standardized JSON event.
2. The application explicitly inserts that JSON event into the `outbox` table.
3. Crucially, **both inserts occur inside a single local Database Transaction** (guaranteeing atomic failure/success).
4. The CDC tool (like Debezium) is attached *only* to the `outbox` table.

This cleanly decouples the architectures. The application developer can freely manipulate their internal `users` table schema without breaking any downstream consumers, because the CDC stream is exclusively monitoring the standardized, strictly contracted JSON payloads placed into the `outbox` table. The obvious tradeoff is the sheer performance overhead of doubling the physical write volume on the primary database!
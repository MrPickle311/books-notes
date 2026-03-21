---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 1: Foundations"
status: pending
---
#### Event Sourcing
*   **Definition:** Using an **append-only log** of immutable events as the primary **source of truth**. Every state change is recorded as an event (named in the past tense, e.g., `seatsBooked`).
*   **CQRS (Command Query Responsibility Segregation):** Separates the write model (event log) from the read model (materialized views). The read models are derived from the event log.
*   **Pros:**
    *   **Intent:** Events capture *why* something happened (e.g., "booking cancelled" vs. just a deleted row).
    *   **Reproducibility:** You can delete and rebuild materialized views from the log to fix bugs or change logic.
    *   **Flexibility:** Multiple read views can be optimized for different queries. New features can process the old event history.
    *   **Auditability:** The log serves as a perfect audit trail.
    *   **Performance:** High write throughput due to sequential appending.
    *   **Reversibility:** Errors are fixed by appending a correction event, rather than destructively updating data.
*   **Cons:**
    *   **External Info:** Processing must be deterministic. If an event relies on external data (e.g., exchange rates), that data must be stored with the event or queried historically.
    *   **Deletion (GDPR):** Immutability makes "right to be forgotten" hard. Solutions include deleting the user's specific log or **crypto-shredding** (encrypting data and deleting the key).
    *   **Side Effects:** Reprocessing events shouldn't trigger external actions (like sending emails) again.
    *   **Ordering:** The system must guarantee that views process events in the exact order they appear in the log.

*   **Description**: This diagram shows the Event Sourcing pattern. User requests are validated and written as immutable "Events" to an "Event Log". This log is the source of truth. "Materialized Views" (like a Booking Status or Dashboard) are derived by processing the event log.
![Figure 3-8: Using a log of immutable events as source of truth.](data_intensive_applications/figure-3-8.png)
---
## Related Concepts
* [[Data Intensive Applications]]

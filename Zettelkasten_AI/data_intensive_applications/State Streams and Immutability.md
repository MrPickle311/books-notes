---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
A profound mathematical relationship exists between "State" (what is currently in your database) and "Streams" (the log of all events). They are simply two sides of the exact same coin.

*   **The Stream (The Derivative):** The append-only log of immutable changes over time (e.g., "+$50", "-$10").
*   **The State (The Integral):** The current viewable database (e.g., "$40"). You arrive at this State by mathematically *integrating* the event stream over time.

![Figure 12-7: Integrating an event stream mathematically produces the current state.](data_intensive_applications/figure-12-7.png)
*Figure 12-7: State is just the mathematical integration of a stream of events. A stream is just the physical differentiation of state over time.*

As Jim Gray noted in 1992, you don't actually *need* a database. The log of events contains 100% of the information. The only reason we use a Database is for read-performance (so we don't have to sequentially sum up billions of log entries every time a user opens their banking app).

### Advantages of Immutable Events
Replacing mutable destructive writes (updating a row in place) with an immutable append-only log provides massive operational advantages:

1.  **Auditing and Recovery (The Accountant Method):** If an accountant makes a mistake, they never use an eraser to physically alter the ledger. They append a *compensating transaction* at the end of the log. If you deploy a bug to production that incorrectly debits user accounts, recovering an immutable log is trivial: you just append a refund event. If you were using a mutable database and destructively overwrote the balances, you would have no idea what the original balances were!
2.  **Capturing Unseen Value:** Mutable databases hide user intent. If a user adds an item to a cart and then removes it, a mutable database performs an `INSERT` and then a `DELETE`. The database ends up empty. The company loses the insight that the user was interested in the item! An immutable event log permanently captures data ("Added Item", "Removed Item"), which is incredibly valuable for Analytics.

### Deriving Multiple Views from a Single Log (CQRS)
By separating the Event Log from the Application State, you unlock the ability to derive multiple different views simultaneously. 
One event log can feed a Postgres Database (for the web app), an Elasticsearch cluster (for the search bar), and a Snowflake data warehouse (for analytics).

This physical separation of Write-Optimized storage (the Kafka Event Log) and Read-Optimized storage (the Materialized View in Postgres) is known as **Command Query Responsibility Segregation (CQRS)**.

*   **Denormalization Becomes Safe:** We learned in the database chapters that denormalizing data (duplicating it) is dangerous because keeping the duplicates in sync is a nightmare. CQRS makes denormalization safe! Because the CDC stream guarantees perfect sequential synchronization, you can heavily duplicate and denormalize data inside your read-optimized views without fear.

### Concurrency Control
Immutability also magically solves some of the hardest problems in Concurrency Control:

*   **Atomic Single Writes:** In a traditional database, completing a user action might require starting an expensive Multi-Object Transaction to update 5 different tables simultaneously. In an Event Sourced system, the user action requires exactly one physical write: atomically appending a single JSON event to the end of the log in Kafka.
*   **Sequential Processing:** Because Kafka enforces total ordering inside a Partition, the downstream consumer applying those events to the database operates strictly as a single thread. As we saw in Chapter 7, running data through a single thread completely eliminates all concurrency bugs, lock contention, and race conditions by definition!

### Limitations of Immutability
Despite the incredible power of immutable event logs, keeping a permanent history of exactly everything that ever happened has massive scaling limits.

1.  **Prohibitive Storage Costs:** If your dataset involves tiny rows that are updated a million times a second (e.g. tracking the X/Y coordinates of a mouse cursor), keeping the entire immutable history is physically impossible. You *must* rely on frequent Log Compaction or destructive mutability simply to survive.
2.  **The Nightmare of Deletion (GDPR):** Sometimes, data *must* be physically destroyed. If a European citizen enacts their "Right to Be Forgotten" under GDPR, you cannot simply append a new "User Deleted" event to the log. You are legally required to actually erase the historical data.
    *   **The Immutability Paradox:** Deleting data from an immutable log is brutally difficult. It requires rewriting history (like Git `filter-branch`), or using concepts like Datomic's *Excision*. 
    *   **Crypto-Shredding:** A common workaround is storing the immutable data AES-encrypted. When the user requests deletion, you physically destroy the AES decryption key. While the impossible-to-delete immutable log remains, it is permanently transformed into cryptographic garbage.
---
## Related Concepts
* [[Data Intensive Applications]]

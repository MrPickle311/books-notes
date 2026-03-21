---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
These patterns are used to resolve the inconsistencies inherent in distributed transactions. Consider a customer unsubscribing, which requires data to be removed from `Profile`, `Contract`, and `Billing` tables, owned by separate services.

![Figure 9-14: The "before" state for eventual consistency. A customer has unsubscribed via the Profile Service, but their data still exists in the Contract and Billing tables.](figure-9-14.png)

#### 1. Background Synchronization Pattern

A separate process (e.g., a nightly batch job) periodically scans the data sources and synchronizes them.
*   **Pros:** Good responsiveness for the user; services are decoupled.
*   **Cons:** Very slow to reach consistency. The background process is tightly coupled to all data sources, **breaking their bounded contexts** and duplicating business logic. This is generally a poor choice for modern distributed architectures.

![Figure 9-15: How background sync works. The user gets a fast response. Much later, a batch process runs and cleans up the remaining data.](figure-9-15.png)
![Figure 9-16: The problem with background sync. The batch process requires direct write access to all tables, breaking ownership and bounded contexts.](figure-9-16.png)

| Advantages                | Disadvantages                         |
| ------------------------- | ------------------------------------- |
| Services are decoupled    | Data source coupling                  |
| Good responsiveness       | Complex implementation                |
|                           | Breaks bounded contexts               |
|                           | Business logic may be duplicated      |
|                           | Slow eventual consistency             |

#### 2. Orchestrated Request-Based Pattern

An orchestrator service manages the entire transaction during the business request, calling each participating service in sequence or parallel.
*   **Pros:** Data consistency is achieved quickly; services remain decoupled.
*   **Cons:** Slower responsiveness for the user. Error handling is extremely complex (requires compensating transactions).

![Figure 9-18: An orchestrator service manages the unsubscribe process, calling each of the three services to remove the customer data before responding to the user.](figure-9-18.png)
![Figure 9-19: The error handling problem. If the Billing service fails, the orchestrator must now issue compensating transactions to re-add the data to the Profile and Contract services, which is complex and can also fail.](figure-9-19.png)

| Advantages                     | Disadvantages                         |
| ------------------------------ | ------------------------------------- |
| Services are decoupled         | Slower responsiveness                 |
| Timeliness of data consistency | Complex error handling                |
| Atomic business request        | Usually requires compensating transactions |

#### 3. Event-Based Pattern

The initiating service performs its action and then publishes an event (e.g., `CustomerUnsubscribed`). Other interested services subscribe to this event and process it asynchronously. This is a very popular and reliable pattern.
*   **Pros:** Very fast responsiveness; services are highly decoupled; consistency is achieved quickly.
*   **Cons:** Error handling is still complex and often relies on a dead-letter queue (DLQ) for failed events, which may require manual intervention.

![Figure 9-20: The event-based pattern. The Profile service removes the user, publishes an event, and immediately responds. The other two services receive the event asynchronously and update their data.](figure-9-20.png)

| Advantages                     | Disadvantages          |
| ------------------------------ | ---------------------- |
| Services are decoupled         | Complex error handling |
| Timeliness of data consistency |                        |
| Fast responsiveness            |                        |

---
## Related Concepts
* [[Architecture]]

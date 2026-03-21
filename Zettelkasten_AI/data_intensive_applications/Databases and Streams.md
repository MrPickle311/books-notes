---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
Historically, "Databases" and "Message Brokers" were viewed as completely separate worlds. However, as we just saw, Log-based brokers (Kafka) achieved massive success by stealing ideas from Databases (Write-Ahead Logs and Sharding). 

Now, the industry is reversing the trend: taking ideas from streams and bringing them *into* databases.

If you think deeply about it, every single write to a database is essentially an event.
*   **Database Replication:** In Chapter 5, we saw that Leader databases stream their Write-Ahead Log to Follower databases over the network to keep them in sync. That Replication Log is structurally just a massive stream of events!
*   **State Machine Replication:** If you have an empty database on Day 1, and you sequentially stream every single `INSERT`, `UPDATE`, and `DELETE` event into it in the exact same mathematical order, that database will end up in the exact same final state. 

This leads to a powerful architectural paradigm: **Event Sourcing**. Instead of storing the mutable final state of an object (e.g., "User's Balance is $50" and then overwriting it to "$40"), you permanently store every state change as an *immutable event* in an append-only log ("Deposited $50", "Withdrew $10"). Your actual viewable databases (read-optimized Materialized Views) simply derive their values by streaming the mathematical sum of that log.

### Keeping Systems in Sync (The Heterogeneous Data Problem)
Modern applications cannot run on a single database. A typical architecture requires massive heterogeneity:
*   An OLTP Database (Postgres) as the source of truth for user purchases.
*   A Cache (Redis) to speed up common read requests.
*   A Full-Text Search Index (Elasticsearch) to power the website's search bar.
*   A Data Warehouse (Snowflake) for business analytics.

Because the exact same piece of data (e.g., an "Item Listing") physically exists in all four systems in completely different file formats, how do you keep them perfectly mathematically synchronized?

#### The Dual-Write Problem
The historical (and deeply flawed) solution was for the application backend to perform **Dual Writes**. When a user updates an Item Listing, the backend application code explicitly connects to Postgres, then connects to Elasticsearch, then connects to Redis, and writes the updated data to all three.

Dual-writing introduces two massive, system-breaking problems:
1.  **Partial Failures:** If the Postgres write succeeds, but the Elasticsearch write crashes because of a momentary network blip, the systems are now entirely out of sync. Solving this requires incredibly complex, slow, and expensive "Two-Phase Commit" distributed transactions.
2.  **Concurrency Race Conditions:** If two users update the same item at the exact same millisecond, the network might interleave the requests, devastating your data integrity.

![Figure 12-4: The devastating race condition caused by dual writes to multiple databases.](figure-12-4.png)
*Figure 12-4: Client 1 writes 'A'. Client 2 writes 'B'. If the network arbitrarily delays the packets, Postgres correctly ends up with 'B', but Elasticsearch permanently ends up with 'A'.*

This race condition is disastrous because no errors are thrown; one value simply silently overwrites the other, and the two databases diverge forever. 

**The the only way to solve this is to establish a Single Leader.** If Postgres is the absolute leader, can we somehow make Elasticsearch a "Follower" of Postgres, so that it receives every write event from Postgres in a strictly coordinated sequence? We will explore this next.
---
## Related Concepts
* [[Data Intensive Applications]]

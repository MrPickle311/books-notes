---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Nodes fail constantly (due to crashes or routine patch reboots). The goal of replication is to keep the system running despite individual node deaths.

#### Follower Failure: Catch-up Recovery
If a follower crashes or loses its network connection, recovering is easy:
1.  On its local disk, the follower keeps a log of data changes it has received.
2.  Upon reboot, it checks its log to see the very last transaction it successfully processed before dying.
3.  It connects to the leader and requests all transactions that occurred *after* that specific point.
4.  Once it finishes applying this backlogged stream of writes, it is "caught up" and resumes normal operation.
*(Performance Note: This recovery places high load on both the follower and the leader. If a follower is dead for a long time, the leader must choose to either keep the giant backlog of logs (wasting disk space) or delete them (forcing the follower to rebuild from a backup when it returns).)*

#### Leader Failure: Failover
Handling a dead leader is vastly more complex. One of the followers needs to be promoted, clients need to be reconfigured to send writes to the new leader, and remaining followers need to consume from the new leader. This process is called **Failover**.

An automatic failover process typically involves:
1.  **Determining the leader failed:** Since there is no foolproof way to detect a crash, systems rely on a **timeout**. Nodes constantly ping each other; if a node doesn't respond for 'X' seconds, it's presumed dead.
2.  **Choosing a new leader:** This is a *consensus* problem. The remaining replicas hold an election, usually picking the follower with the most up-to-date data (e.g., the greatest log sequence number in async replication) to minimize data loss.
3.  **Reconfiguring the system:** Clients are routed to the new leader. Crucially, the system must ensure the old leader recognizes it has been demoted and becomes a follower if it ever wakes back up.

**Why Failover is Dangerous:**
Automatic failover is fraught with terrifying edge cases:
*   **Discarded Data (Asynchronous Replication):** If the old leader died before replicating its most recent writes, the newly promoted leader won't have them. If the old leader wakes up, those old writes are usually just discarded to prevent conflicts. *This means writes you confirmed to the user as successful were actually permanently lost.*
*   **External System Corruption:** Discarding those writes is catastrophic if other external systems coordinate with them. For example: GitHub had a MySQL DB using auto-increment primary keys and a Redis cache looking at those keys. An out-of-date follower was promoted and reused discarded primary keys for new data. Redis became entirely out of sync with MySQL, exposing private data to the wrong users.
*   **Split Brain:** If a network partition occurs, you could end up with *two* nodes actively believing they are the leader simultaneously. Both accept writes, guaranteeing massive data corruption. Systems try to prevent this by forcing old leaders offline (a mechanism known as **STONITH:** *Shoot The Other Node In The Head*). But a buggy STONITH configuration could accidentally shoot *both* nodes, bringing down the entire database.
*   **Timeout Tuning:** If your timeout is too long, failover takes forever and the system is down for writes. If it's too short, a temporary load spike will trigger unnecessary failovers, making the system's performance *substantially worse* while it struggles to elect a new leader and catch up.

Because of these sheer complexities and catastrophic risks, many operations teams disable automatic failover entirely and mandate that a human administrator performs the failover manually.
---
## Related Concepts
* [[Data Intensive Applications]]

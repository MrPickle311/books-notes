---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
The most common solution for database replication is **Leader-based replication** (also called primary-backup, active/passive).
*   **The Leader (Primary/Source):** One replica is designated as the leader. Whenever a client writes to the database, it *must* send its request to the leader.
*   **The Followers (Read Replicas/Secondaries):** The other replicas are followers. When the leader writes data to its local storage, it streams the changes to all followers via a replication log. The followers apply the writes in the exact same order.
*   **Reads:** Clients can query the leader OR any follower for read-only queries. **Writes** only go to the leader.

*Note:* If the database is sharded, each shard has its own leader. Leader-based replication is the standard in almost every major system: PostgreSQL, MySQL, SQL Server, MongoDB, Kafka, and consensus algorithms like Raft.

![Figure 6-1: Single-leader replication directs all writes to a designated leader, which sends a stream of changes to the follower replicas.](figure-6-1.png)
---
## Related Concepts
* [[Data Intensive Applications]]

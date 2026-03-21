---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
While replication adds fault tolerance, many massive online architectures use it purely for **Read Scaling**. 
If your app is 95% reads, you can simply spin up 50 asynchronous followers, distribute the read-load across them, and leave the Leader to only handle the 5% write traffic.

However, this only works if the replication is *asynchronous* (making 50 servers completely synchronous would violently crash the database). The inherent flaw of asynchronous read-heavy systems is **Replication Lag**:
*   If a user writes something to the leader, and then immediately reads from an async follower, the follower might not have received that write log yet. The user will see outdated data.
*   This creates a terrifying logical anomaly: if you query the Leader and the Follower at the exact same physical millisecond, you get two different results back!
*   This state is called **Eventual Consistency**. It just means that if you stop writing and wait a few seconds, eventually the followers will catch up and the system will achieve consistent harmony.

Usually, replication lag is fractions of a millisecond and completely unnoticeable. But during peak load or network congestion, replication lag can stretch into SECONDS or MINUTES, creating massive real-world problems for applications.

#### 1. Reading Your Own Writes (Read-After-Write Consistency)
Imagine a user updates their profile picture, hits 'Save', and is immediately redirected to their profile page. 
*   The 'Save' (Write) was routed to the Leader.
*   The 'View Profile' (Read) was routed to an async Follower.
*   Because of replication lag, the Follower hasn't received the new picture yet. To the user, it looks like their save action was completely lost, causing extreme frustration.
![Figure 6-3: A user makes a write, followed by a read from a stale replica. To prevent this anomaly, we need read-after-write consistency.](figure-6-3.png)

To prevent this, databases implement **Read-Your-Writes Consistency** (guaranteeing a user always sees their own updates, making no promises about seeing *other* users' updates).
Solutions include:
*   **Routing by editability:** If something can only be edited by the owner (like a profile), route all requests for the user's *own* profile to the Leader, and let them read *other* users' profiles from the Followers.
*   **Time-based routing:** The client remembers the timestamp of its last write. For the next 1 minute, all read requests from that specific client are forced to go to the Leader.
*   *Cross-device challenges:* This gets exponentially harder if the user edits their profile on their desktop, but immediately checks it on their mobile phone. Since the phone doesn't know the desktop's "last write timestamp", the database needs centralized metadata and region-aware routing to solve this.

#### Regions and Availability Zones (Sidebar)
*   **Region:** A physical geographic location (e.g., "US-East"). 
*   **Availability Zone (Zone):** A specific datacenter within that Region, with its own dedicated power and cooling. Regions consist of multiple Zones connected by ultra-fast networks.
Spreading nodes across Zones protects against a single datacenter burning down. Spreading across Regions protects against an earthquake destroying the entire US-East coast, but introduces extreme latency to the system.

#### 2. Monotonic Reads (Time Traveling Backward)
The second massive anomaly of replication lag occurs if a user makes multiple reads in sequence and hits *different* followers.
1.  User views a comment thread. Their read hits **Follower A** (which has 0 lag). They see a brand new comment by John.
2.  User refreshes the page. The load balancer routes this new read to **Follower B** (which has 10 seconds of lag).
3.  Because Follower B hasn't received John's comment yet, the comment suddenly vanishes from the screen!
To the user, time literally moved backward. They saw the future, and then reverted to the past.
![Figure 6-4: A user first reads from a fresh replica, then from a stale replica. Time appears to go backward. To prevent this anomaly, we need monotonic reads.](figure-6-4.png)

**Monotonic Reads** is a guarantee that this never happens. It promises that if you read newer data, your subsequent reads will never be served by a replica holding older data. 
*   *Solution:* The simplest way to achieve this is to ensure a specific user is *always* routed to the exact same replica for all their reads (e.g., routing based on a hash of their User ID, rather than random load balancing). If that replica dies, the user is re-routed to a new one.

#### 3. Consistent Prefix Reads (Violation of Causality)
The third anomaly involves causality and the order of operations. Imagine a conversation consisting of a question and an answer:
1.  **Mr. Poons:** "How far into the future can you see?" *(Write 1)*
2.  **Mrs. Cake:** "About ten seconds." *(Write 2)*

If an observer is reading from a sharded, asynchronous database, **Follower A** (holding Mr. Poons's shard) might have a huge replication lag, while **Follower B** (holding Mrs. Cake's shard) might have zero lag. 
The observer would see Mrs. Cake's answer appear *before* Mr. Poons's question is ever asked, making her look psychic!
![Figure 6-5: If some shards are replicated slower than others, an observer may see the answer before they see the question.](figure-6-5.png)

This happens in sharded/partitioned databases because different shards operate completely independently. There is no global ordering of writes across them.

**Consistent Prefix Reads** is a guarantee that prevents this. It ensures that if a sequence of writes happens in a specific order, anyone reading those writes will see them appear in that exact same order.
*   *Solution:* The main workaround is to ensure that any writes that are causally related to each other are written to the exact same partition/shard. (However, in complex applications, this can be extremely inefficient).
---
## Related Concepts
* [[Data Intensive Applications]]

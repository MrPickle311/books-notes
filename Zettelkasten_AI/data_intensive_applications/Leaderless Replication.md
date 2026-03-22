---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Thus far, we've discussed replication utilizing Leaders. A totally different architectural approach is to abandon the concept of a "Leader" entirely and allow *any replica* to directly accept writes from clients.

This **Leaderless Replication** model fell out of fashion during the era of Relational Databases, but was heavily resurrected in 2007 when Amazon published the paper on their internal *Dynamo* system. Today, databases inspired by this model (Riak, Cassandra, ScyllaDB) are known as **Dynamo-style** databases.
*(Note: Amazon's modern cloud offering 'DynamoDB' actually uses strictly single-leader replication, despite the name).*

#### Writing to the Database When a Node is Down
In a Single-Leader architecture, if the Leader reboots, you must perform a complex Failover. In a Leaderless architecture, failover simply does not exist.

If you have 3 replicas, and Replica 3 is offline updating its kernel:
*   The client sends their `write` to all 3 replicas in parallel.
*   Replica 1 and 2 accept the write and respond "OK". Replica 3 misses it entirely.
*   The client receives the two "OKs", considers the write successful, and entirely ignores the fact that Replica 3 missed it.
![Figure 6-12: A quorum write, quorum read, and read repair after a node outage.](data_intensive_applications/figure-6-12.png)

#### Reading & Version Numbers (Solving Stale Data)
When Replica 3 finishes updating and comes back online, it is missing the new data. If a client queries it, they will receive stale, outdated data.
To solve this, clients in a Leaderless system never read from a single node. **Read requests are also sent to several nodes in parallel.**

*   The client receives multiple responses (e.g., getting the new value from Replica 1, and the stale value from Replica 3).
*   *How does it know which is correct?* Every value written must be mathematically tagged with a **Version Number**. The client simply discards the response with the lower version number.

#### Catching up on Missed Writes
Since there is no "Leader", how does Replica 3 ever catch up and receive the data it missed? Dynamo-style databases use several mechanisms:
1.  **Read Repair:** When a client does a parallel read and notices that Replica 3 returned a stale version 6 (while Replicas 1 and 2 returned version 7), the *client itself* turns around and writes version 7 back into Replica 3 to fix it. This works wonderfully for frequently-read data.
2.  **Hinted Handoff:** While Replica 3 was down, Replica 2 accepted the writes on its behalf, storing them in a temporary "hint" file. When Replica 3 comes back online, Replica 2 acts as a good neighbor, hands over the hint file to push the updates, and deletes local hints.
3.  **Anti-Entropy Process:** A continuous background process on the servers that constantly compares the mathematical differences in data between replicas and quietly copies over missing data (this process doesn't care about order, and can have significant delays).

#### Quorums for Reading and Writing
In the example above, we considered the write "successful" because 2 out of 3 replicas accepted it. How far can we push this? Can we get away with only 1 replica accepting it?

This brings us to **Quorums**. If you know that every write is physically guaranteed to live on *at least* 2 out of 3 nodes, you can safely deduce that if you read from *at least* 2 nodes, it's mathematically impossible to miss the latest data. 

**The Quorum Formula:**
If you have **n** total replicas:
*   Let **w** = the number of nodes that must confirm a write for it to be successful.
*   Let **r** = the number of nodes you must query during a read to consider it successful.

As long as **w + r > n**, you are mathematically guaranteed to get an up-to-date value when reading, because the set of nodes you wrote to and the set of nodes you read from MUST overlap. These are called **Quorum Reads and Writes** (effectively, the minimum "votes" required). A common choice is to make `n` an odd number (typically 3 or 5) and to set `w = r = (n + 1) / 2` (rounded up).

*   *Typical configuration:* If `n=3`, a common setup is `w=2` and `r=2`. 
*   *Tolerating Outages:* Because `2 < 3`, you can successfully process writes even if an entire node is dead and unreachable.
*   *Tuning for Speed:* If you have massive read-heavy traffic, you could configure `w=3` and `r=1`. Reading from 1 node is incredibly fast! However, the trade-off is brutal: if a single node dies, your `w=3` requirement can't be met, meaning your entire database is paralyzed and cannot accept any writes until the node is fixed.
![Figure 6-13: If w + r > n, at least one of the r replicas you read from must have seen the most recent successful write.](data_intensive_applications/figure-6-13.png)

#### Limitations of Quorum Consistency
Even when you correctly configure `w + r > n`, it is a mistake to assume quorums provide absolute, perfect consistency guarantees. There are many confusing edge cases:
*   **Sloppy Quorums:** If a massive network partition occurs, Leaderless databases (like Riak or Cassandra) can temporarily allow writes to be safely stored on *any* reachable node in the cluster, even if it's not the designated replica for that data. This allows emergency writes to succeed, but means subsequent reads won't see the new data until the network is fixed and the data is moved back to the correct place.
*   **Concurrent Write Conflicts:** If two writes occur simultaneously, they can reach replicas in different orders, leading to conflicts that must be resolved (e.g., via LWW, which drops data).
*   **Read/Write Overlap:** If a read happens at the exact same millisecond a write is propagating, the read might query the nodes that haven't received the write yet, returning the old value. 
*   **Failed Writes:** If a write succeeds on 1 node but fails on 2 nodes (e.g. disks are full), the overall quorum failed. However, the system does not "roll back" the write on that 1 node. Subsequent reads might still accidentally read that new, unconfirmed data.

Because of this, you should view Quorums as a way to adjust the *probability* of reading stale data, rather than an absolute ironclad guarantee.

**Monitoring Staleness:**
It is critical that operations teams monitor replication lag. 
*   *Single-Leader:* This is easy. The leader knows its exact log position (e.g., commit #1000). A follower knows its position (commit #990). The lag is mathematically exactly 10 commits.
*   *Leaderless:* This is incredibly difficult. Since writes happen on all nodes simultaneously with no fixed global order, it's very hard to calculate exactly how far "behind" a replica is, making "eventual consistency" frustratingly vague for Ops teams to measure.

#### Single-Leader vs Leaderless Performance
Why choose Leaderless if the consistency guarantees are so vague? 

**The Flaws of Single-Leader Performance:**
*   You cannot scale read throughput unless you accept reading stale data from async followers.
*   If the Leader fails, the system is 100% frozen and unavailable for writing until the complex failover protocol finishes electing a new leader.
*   If the Leader is experiencing a slow disk (a "gray failure"), the entire global application slows down for every single user because all writes must go through that one slow disk.

**The Brilliance of Leaderless Resilience:**
*   A leaderless system inherently doesn't care if a node fails. It doesn't even distinguish a "crashed node" from normal operations. If a node is slow, or completely dead, the client simply ignores it and routes the Parallel Read/Write to the other healthy nodes. 
*   **Request Hedging:** Clients can query 3 nodes, wait for the first 2 fast nodes to reply, and immediately render the UI, completely ignoring the slow 3rd node. This makes Leaderless dramatically more resilient to temporary latency spikes and gray failures.
*   *The Trade-off:* Leaderless clusters suffer from "Hinted Handoffstorms." When a dead node boots back up, its neighbors dump massive files of missed writes onto it, spiking CPU and network traffic exactly when the cluster is trying to restabilize.

#### Multi-Region Operation
Leaderless databases are excellent for massive Multi-Region clusters since they naturally tolerate high latency and network partitions.
*   **Cassandra / ScyllaDB:** A client sends a write to their local datacenter. The local node acts as a "Coordinator", writing it to the local replicas, and simultaneously forwarding it across the ocean to exactly *one* node in the remote datacenter (which then distributes it locally there). This prevents sending duplicates over expensive trans-oceanic cables.
*   Clients can choose their Quorum scope dynamically per-request: e.g., requiring a "Local Quorum" (fast, skips oceanic latency, but risks reading stale global data) versus a "Global Quorum" (slowest, but guarantees the freshest global data).

---

### Detecting Concurrent Writes
In Leaderless databases, multiple clients can write to the same key simultaneously. Because there is no single leader ordering these writes, they can arrive at the various replicas in completely different orders due to network delays.

**Example of the Inconsistency Nightmare:**
Imagine Client A writes "A" and Client B writes "B" at the same time.
*   Node 1 receives A. (It never receives B due to a network glitch).
*   Node 2 receives A, and *then* B. (Node 2 thinks "B" is the final value).
*   Node 3 receives B, and *then* A. (Node 3 thinks "A" is the final value).
![Figure 6-14: Concurrent writes in a Dynamo-style datastore: there is no well-defined ordering.](data_intensive_applications/figure-6-14.png)

If the nodes just blindly overwrite their local data based on the order the packets arrived, the database becomes permanently corrupted and inconsistent. The replicas must converge using a conflict resolution strategy (like LWW or CRDTs). But to resolve conflicts, a database must first mathematically detect that a conflict actually occurred (i.e., that the writes were *concurrent*).

#### The "Happens-Before" Relation and Concurrency
How does a database know if two writes are conflicting (concurrent) versus just being sequential updates?

**1. Causally Dependent (Happens-Before)**
If Operation B *knows about*, *depends on*, or *builds upon* Operation A, then Operation A mathematically "happened before" Operation B. 
*   *Example:* User A inserts a row. User B increments a counter on that exact row. Since User B couldn't increment a row that didn't exist, B's action absolutely depended on A. These are *not* concurrent. Operation B safely overwrites Operation A.

**2. Concurrent**
If Operation A does not know about Operation B, and Operation B does not know about Operation A, they are **Concurrent**. Neither happened before the other. This definition is the absolute key to distributed systems. 

**Concurrency, Time, and Relativity (Sidebar)**
In computer science, "concurrent" does *not* mean "happening at the exact same physical millisecond". 
It simply means the two clients were **unaware of each other's operations** when they issued their write.
*   Client 1 could issue a write at 1:00 PM. 
*   Client 2 could issue a write an hour later at 2:00 PM. 
*   If a network partition prevented Client 2 from seeing Client 1's write, these two writes are defined as mathematically **Concurrent**, even though they happened an hour apart. Since neither "happened before" the other, this represents a conflict that the database must resolve.

---

### Capturing the Happens-Before Relationship
To correctly resolve conflicts, the database needs an algorithm that can definitively prove whether a write *happened before* another write, or whether they were *concurrent*.

The algorithm hinges on two strict rules:
1.  **Read before Write:** A client *must* read a key before writing to it.
2.  **Pass the Version:** When writing, the client *must* pass the version number it received from the prior read. 

When a server receives a write containing a version number, it knows exactly which state the client's write is building upon (i.e. what happened before).
*   The server safely overwrites all values with that exact version number (or below).
*   The server preserves all values with *higher* version numbers, as those represent concurrent writes that the client was entirely unaware of!

**The Shopping Cart Example:**
Imagine two clients concurrently adding items to a single shopping cart over an unreliable network.
![Figure 6-15: Capturing causal dependencies between two clients concurrently editing a shopping cart.](data_intensive_applications/figure-6-15.png)

1.  **Client 1** adds `[milk]`. The server assigns it **Version 1**.
2.  **Client 2** (unaware of Client 1) adds `[eggs]`. The server assigns it **Version 2**. Because it was concurrent to Version 1, the server keeps *both* values as siblings.
3.  **Client 1** wants to add flour. Its device only knows about milk (Version 1). It sends `[milk, flour]` back to the server tagged with **Version 1**.
    *   *The Server's Logic:* "Client 1 is building upon Version 1. I can safely overwrite Version 1 because Client 1 has merged it. However, Client 1 is completely unaware of Version 2 `[eggs]`. Therefore, I must keep Version 2 as a sibling."
    *   The server saves `[milk, flour]` as **Version 3** and keeps `[eggs]` as **Version 2**.
4.  **Client 2** wants to add ham. Its device previously read the database and knows about both siblings (`[milk]` and `[eggs]`). It merges them locally, adds ham, and sends `[eggs, milk, ham]` tagged with **Version 2**.
    *   *The Server's Logic:* "Client 2 is building upon Version 2. I can overwrite Version 2. However, Client 2 is completely unaware of Version 3 `[milk, flour]`. I must keep Version 3."
    *   The server saves `[eggs, milk, ham]` as **Version 4** and keeps `[milk, flour]` as **Version 3**.

Because the clients were forced to pass Version Numbers back and forth, the server successfully mapped out a strict causal dependency graph. Even though the clients' network connections were terribly out of sync, the database never lost a single write. 
![Figure 6-16: Graph of causal dependencies in Figure 6-15.](data_intensive_applications/figure-6-16.png)

#### Version Vectors
The shopping cart example used a Single-Leader database (only one replica accepting writes), so it only needed one single Version Number. How does this work in a Leaderless Database where *every* node accepts writes simultaneously?

A single version number is not enough. You must use a **Version Number per Key, per Replica**.
*   Every single replica increments its own local version number when it processes a write.
*   It also stores the exact version numbers it has seen from *every other replica* in the cluster.

The mathematical collection of all these version numbers from all replicas bundled together is called a **Version Vector** (or a "Dotted Version Vector" like Riak uses).

*   *How it works:* Exactly like the previous example, this entire "Version Vector" is sent back down to the client every time they perform a read. When the client performs a write, they must pass the entire vector back up to the database.
*   *Why it's brilliant:* Because the Version Vector inherently tracks the causal history across *every independent node* globally, the database can perfectly distinguish between simple overwrites versus dangerous concurrent writes, guaranteeing that no data is ever silently lost, even if you are reading from one replica and suddenly writing to a totally different replica.

*(Sidebar: "Version Vectors" are frequently, but incorrectly, called "Vector Clocks". While similar, Version Vectors are the correct data structure and mathematical term when dealing with the state of replicas).*

---
## Related Concepts
* [[Data Intensive Applications]]

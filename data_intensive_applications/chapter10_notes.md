# Chapter 10: Consistency and Consensus

*"Never go to sea with two chronometers; take one or three."* — Frederick P. Brooks Jr.

As established in Chapter 9, distributed systems are chaotic and prone to an endless myriad of faults. To survive these faults, our best tool is **Replication** (keeping multiple copies of data on different nodes). However, as we saw in Chapter 6, replication introduces a massive new problem: **Inconsistency** (e.g., stale reads, or concurrent write conflicts).

At a high level, there are two competing philosophies for dealing with replication inconsistencies:

### 1. Eventual Consistency
*   **The Philosophy:** The system makes no attempt to hide the reality of replication. The application developer is fully responsible for handling the chaos, inconsistencies, and write conflicts. 
*   **Where it's used:** Multi-leader and Leaderless replication architectures. 
*   **When to use it:** When high availability is critical, or when applications must work offline (e.g., Local-First software).

### 2. Strong Consistency
*   **The Philosophy:** The database completely hides the messy reality of replication. To the application developer, the database behaves exactly as if it were a single, perfect, fault-free node.
*   **The Cost:** While it makes application development dramatically simpler, ensuring strong consistency incurs heavy performance penalties and can cause total system outages if certain faults occur (faults that an Eventually Consistent system would easily survive).

This chapter dives deep into the **Strongly Consistent** approach, focusing on three core areas:
1.  Defining exactly what "Strong Consistency" mathematically means (**Linearizability**).
2.  The surprising difficulty of generating IDs and Timestamps.
3.  How we actually achieve linearizability while surviving faults (**Consensus Algorithms**).

---

## 1. Linearizability
If an engineer asks for "Strong Consistency," they are usually asking for a specific mathematical guarantee called **Linearizability** (also known as *Atomic Consistency*, *Immediate Consistency*, or *External Consistency*).

The goal of Linearizability is simple: **Make a replicated system appear as if there is only exactly one copy of the data, and all operations on it are atomic.**

If a system is linearizable, it provides a strict **recency guarantee**. The instant that *any* client successfully completes a write to the database, *every other client* reading from the database is mathematically guaranteed to see that exact new value. They will never accidentally read from a stale cache or lagging replica.

### A Violation of Linearizability
To understand linearizability, it is easiest to look at a system that fails to provide it.

Imagine a nonlinearizable sports website running on a replicated database:
1.  The game ends. The master database is updated with the final score.
2.  **Aaliyah** hits refresh on her phone. Her request routes to the Master node. She receives the final score.
3.  Aaliyah excitedly turns to Bryce and announces the winner.
4.  **Bryce** hears this and incredulously hits refresh on his own phone. 
5.  His request happens to route to an asynchronous Read Replica that is currently lagging by two seconds.
6.  Bryce's screen shows the game is still ongoing! 

![Figure 10-1: This system is not linearizable, causing sports fans to be confused.](figure-10-1.png)

This is a **Violation of Linearizability**. 
If Aaliyah and Bryce had refreshed at the exact same millisecond, different results would be excusable. But because Bryce physically hit refresh *after* he heard Aaliyah announce the final score, the timeline established that the data existed in the real world *before* his query began. Because Bryce's query returned a stale result that violated this real-world timeline, the database is considered nonlinearizable.

### What Makes a System Linearizable?
To fully understand how to build a linearizable database, we need to trace the exact timings of reads and writes. 
In distributed systems theory, when multiple clients read/write the same object, that object is called a **Register**.

#### The Rules of Concurrency
Imagine a timeline where a Register `x` initially holds the value `0`. 
Client C begins writing the new value `1` to the database. At the exact same time, Clients A and B are violently polling the database, sending read requests to see the latest value.

![Figure 10-2: If a read request is concurrent with a write request, it may return either the old or the new value.](figure-10-2.png)

Because of unpredictable network delays, Client C's write request takes some time to arrive at the database, process, and return. During this ongoing window:
*   If a read happens *before* the write begins, it mathematically must return `0`.
*   If a read happens *after* the write completes, it mathematically must return `1`.
*   If a read overlaps in time with the write (they are **Concurrent**), the read could return *either* `0` or `1`. Neither answer violates physics, because the write might be sitting in a router queue or half-committed to memory.

However, simply saying "concurrent reads can return either value" is not enough to achieve Linearizability! If we leave it at that, Client A might read `1`, then immediately read `0` again while the write is still resolving. This destroys the illusion of a single, perfect copy of data.

#### The Flipping Point
To achieve Linearizability, we must add a strict mathematical constraint:

![Figure 10-3: After any one read has returned the new value, all following reads must also return the new value.](figure-10-3.png)

We must mathematically guarantee that at exactly one specific microsecond *during* the write operation, the value of the Register instantaneously flips from `0` to `1`. 

**The Golden Rule of Linearizability:** If *any* client successfully reads the new value `1`, then **every single read that begins after that exact millisecond** must also return `1` (or a newer value). The database is never allowed to "forget" the new value and regress back to `0`, even if the write operation itself hasn't technically finished confirming with the client.

#### Visualizing Linearizable Sequences
We can visualize this by drawing a vertical line down every operation bar. This line represents the exact millisecond the database physically executed the command.

![Figure 10-4: Visualizing the points in time at which reads and writes appear to have taken effect.](figure-10-4.png)

In a linearizable system, if you connect these vertical execution lines chronologically, the connecting wire must **always** move forward in time (left to right). It can never loop backwards. Once the wire moves past a new value, the system's state has advanced permanently.

*(In the diagram above, the final read by B violates linearizability because it attempts to read the value `2`, even though Client A chronologically already read the newer value `4`. This pulls the timeline backwards).*

### Linearizability vs. Serializability
These two terms sound incredibly similar but define completely different computer science concepts. It is critical to memorize the distinction:

*   **Serializability (Isolation Level):** A property of *Transactions* containing multiple operations across multiple objects (rows/documents). It guarantees that if two transactions run simultaneously, the final database state will look exactly as if they had been executed one by one in a serial order. However, **Serializability allows stale reads!** A fully serializable database can legally return data from an hour ago, as long as the transactions don't conflict.
*   **Linearizability (Recency Guarantee):** A property of an *individual object* (register). It doesn't group operations into transactions, meaning it cannot stop Write Skew. However, it guarantees pure recency: if Operation A finishes before Operation B starts, Operation B mathematically *must* see the state generated by A.

If a database guarantees both (transactions are isolated AND immediately fresh on all nodes), it is called **Strict Serializability** (or Strong-1SR). Single-node databases do this naturally. Truly distributed databases built for this (like Google Spanner and FoundationDB) achieve it, but at immense engineering and coordination costs.

## 2. Relying on Linearizability
Viewing an outdated sports score is annoying but harmless. However, there are several critical architectural scenarios where a lack of Linearizability will completely destroy your system:

### Locking and Leader Election
In a Single-Leader replication setup, you must guarantee there is exactly one leader to avoid split-brain data corruption. This is often achieved by having nodes race to acquire a **Lease** (a distributed lock) on startup.

The storage system granting this lease **must** be linearizable. If it is not, two nodes could concurrently believe they acquired the exact same lock! This is why databases rely on linearizable coordination services like **Apache ZooKeeper** or **etcd** to manage leader elections. (Note: ZooKeeper only provides linearizable *writes*; etcd v3 provides linearizable reads by default).

### Constraints and Uniqueness Guarantees
If your database enforces a strict uniqueness constraint (e.g., "Two users cannot register the same username" or "Two people cannot book the same airplane seat"), this inherently requires Linearizability.

The operation acts exactly like a Distributed Lock or an Atomic Compare-And-Set (`CAS`). To enforce that a bank balance doesn't drop below zero, every node in the cluster must mathematically agree on exactly what the single, up-to-date balance is. If constraints are treated "loosely" (e.g., airlines intentionally overbooking flights and sorting it out with vouchers later), you can survive without linearizability. But for hard database constraints, it is mandatory. 

### Cross-Channel Timing Dependencies
Linearizability violations often happen when your system has **two different communication channels** racing against each other. 

In the sports score example, the two channels were:
1. The slow Database Replication channel.
2. The fast Audio channel (Aaliyah telling Bryce the score).

Consider a web architecture where a user uploads a video:
1. The web server writes the 5GB video file to a Cloud Storage service.
2. The web server then pushes a tiny message to a highly-optimized Message Queue (e.g., RabbitMQ), telling a Transcoder Worker to compress the new video.
3. The Transcoder reads the queue instantly and attempts to fetch the raw video from the Cloud Storage.

![Figure 10-5: The web server and video transcoder communicate both through file storage and a message queue, opening the potential for race conditions.](figure-10-5.png)

If the Cloud Storage is *not* linearizable, its internal replication might be slower than the Message Queue. The transcoder races to the storage service and either finds a stale, older version of the file, or a 404 Not Found error, crashing the job. 

To fix this, the storage service must provide immediate recency (Linearizability), guaranteeing that if the write finished *before* the message queue was pinged, the data is globally visible.

## 3. Implementing Linearizable Systems
If Linearizability means "behave as though there is only a single copy of the data," the most obvious solution is to literally only host a single copy of the data. 

However, a single node cannot tolerate faults. If it crashes, all data is lost or offline. To survive datacenter outages, we must use Replication. But how do the different replication methods stack up when trying to achieve Linearizability?

1.  **Single-Leader Replication (*Potentially* Linearizable)**
    *   If you route **100%** of your reads and writes exclusively to the Leader node, your system is technically linearizable (since there is only one authoritative copy).
    *   *The Catch:* This assumes you actually know who the Leader is! Due to process pauses and network faults (covered in Chapter 9), a "Zombie" node might incorrectly believe it is still the leader. If clients read from this delusional Zombie, linearizability is instantly violated. Furthermore, asynchronous failover can lose committed writes, destroying the timeline.
2.  **Consensus Algorithms (*Likely* Linearizable)**
    *   Consensus algorithms (like ZooKeeper's *Zab* or etcd's *Raft*) are essentially Single-Leader replication systems that have been mathematically bulletproofed to safely auto-elect leaders and prevent split-brain. 
    *   These are the tools actually used to build linearizable infrastructure. 
    *   *The Catch:* Even here, if the algorithm allows a node to serve a read *without* verifying it is still the current leader, the read could be stale.
3.  **Multi-Leader Replication (*Not* Linearizable)**
    *   Because multiple nodes accept writes concurrently and blindly replicate them asynchronously to the background, conflicts are guaranteed. It is fundamentally impossible for a Multi-Leader architecture to act like a single, atomic copy of data.
4.  **Leaderless Replication (*Probably Not* Linearizable)**
    *   Databases like Dynamo, Cassandra, and ScyllaDB often claim they can achieve "Strong Consistency" if you configure your read and write Quorums heavily ($w + r > n$).
    *   *The Catch:* This is almost always false. Because these systems resolve conflicts using "Last Write Wins" (LWW) based on Wall Clocks, and Wall Clocks are fundamentally broken across servers (Chapter 9), the mathematical timeline is easily corrupted. Even with perfect clocks, strange edge cases in Quorum overlap can still violate linearizability (as we will see next).

### Linearizability and Quorums
In a Leaderless Dynamo-style database, it feels intuitive that requiring strict Quorum reads and writes ($w + r > n$) would guarantee linearizability. Let's look at why that intuition is wrong.

Imagine a cluster of 3 nodes where the initial value of $x$ is 0. 
A writer attempts to update $x$ to 1 with $w=3$. Because of variable network delays, the write message reaches Node 1 instantly, but is heavily delayed reaching Nodes 2 and 3.

At this exact moment, two clients perform a read quorum of $r=2$:
*   **Client A** queries Nodes 1 and 2. It sees the new value `1` on Node 1, and the old value `0` on Node 2. It correctly resolves the conflict and returns the new value `1`.
*   **Client B** begins its query *after Client A finishes*. Client B queries Nodes 2 and 3. The delayed write *still* hasn't reached them. So Client B receives `0` from both nodes. Even though B queried strictly after A successfully read the new value, B returns the old value.

![Figure 10-6: A nonlinearizable execution, despite using a quorum.](figure-10-6.png)

This perfectly fulfills the strict Quorum requirements ($3 + 2 > 3$), but it is a blatant **Violation of Linearizability**. B's read pulled the timeline backward. 
*(Note: You can force a Dynamo-style database to be linearizable by requiring readers to synchronously repair the data before returning the result, and requiring writers to read the latest quorum state before writing. However, the performance penalty is so severe that almost no databases do it).*

## 4. The Cost of Linearizability
If Linearizability is the gold standard for avoiding horrific data bugs, why doesn't every database just use it? The answer is the brutal tradeoff between Linearizability and **Availability**. 

Let's look at a Multi-Region deployment with two datacenters (e.g., East Coast and West Coast). What happens if a backhoe severs the fiber-optic cable between the two datacenters, creating a **Network Partition**?
*   Nodes within the East Coast can talk to each other.
*   Nodes within the West Coast can talk to each other.
*   But the East Coast cannot talk to the West Coast.

![Figure 10-7: A network interruption forcing a choice between linearizability and availability.](figure-10-7.png)

#### Multi-Leader (Available, but Nonlinearizable)
If each datacenter has its own Leader, the localized networks function perfectly. Users on the West Coast write to the local West leader. Users on the East Coast write to the local East leader. The writes are queued up and will asynchronously synchronize whenever the fiber-optic cable is eventually repaired.
*   **Result:** The application is 100% **Available** to all users. However, it is fundamentally **Nonlinearizable**. 

#### Single-Leader (Linearizable, but Unavailable)
For a system to be linearizable, there can only be one true Leader (let's say it lives on the East Coast). All reads and writes *must* route through this single East Coast Leader to guarantee strict recency.
When the cable is severed:
*   Users on the East Coast continue operating perfectly (they have local access to the Leader).
*   Users on the West Coast can no longer contact the Leader. They could execute a local read from a West Coast replica, but it might be stale, violating linearizability. If they attempt a write, it will simply fail. 
*   **Result:** The system remains perfectly **Linearizable**, but the application experiences a total **Outage (Unavailable)** for half the country until the cable is fixed.

### The CAP Theorem
This harsh choice between Linearizability and Availability during a network partition is universally true for any distributed database. It led to a famous rule of thumb called the **CAP Theorem** (Consistency, Availability, Partition Tolerance).

*   **CP (Consistent and Partition Tolerant):** If you require linearizability (Consistency), a network partition forces nodes that cannot reach the majority to stop processing requests and return errors. The system sacrifices Availability to maintain Consistency.
*   **AP (Available and Partition Tolerant):** If you abandon linearizability, nodes can continue independently serving requests during a partition (like a Multi-Leader setup). The system maintains Availability at the cost of Consistency.

#### Why CAP is Unhelpful Today
While CAP was historically important for triggering the NoSQL movement in the 2000s, modern engineers consider it deeply flawed and unhelpful for actual system design:
1.  **"Pick 2 out of 3" is a lie:** You can't "choose" not to have Network Partitions. Partitions are physical faults (fiber cables breaking, switches failing) that *will* happen. The only real choice is: *When* a partition happens, do you pick Consistency or Availability?
2.  **Narrow Definitions:** CAP defines "Consistency" exclusively as Linearizability (ignoring all other useful consistency models) and its definition of "Availability" doesn't actually match how we measure real-world uptime. 
3.  **Missing Reality:** According to Google, network partitions cause less than 8% of incidents. CAP says absolutely nothing about network delays, dead nodes, or slow disks. 

Because of this, CAP is mostly considered a piece of history today and has been replaced by more precise mathematical models (like PACELC).

### Linearizability and Network Delays
It turns out that very few systems in the world are actually linearizable. 
Astonishingly, even the multi-core RAM in your own laptop is *not* linearizable! If Core A writes to memory, and Core B reads that exact address a nanosecond later, Core B might read the old value because the new value is still asynchronously trapped in Core A's L1 cache.

Why do hardware manufacturers intentionally build nonlinearizable CPUs? **Performance.**
They aren't doing it to survive "Network Partitions" between the cores. They do it because forcing every single CPU cache to synchronously coordinate every read/write to main memory would slow the computer down to a crawl. 

This mirrors exactly why distributed databases abandon Linearizability: **Latency.**
If you demand true Linearizability in a network with unpredictable delays (like the internet), computer scientists (Attiya and Welch) have mathematically proven that the database's response time will *always* be heavily bottlenecked by those network delays. There is no magic algorithm that can make Linearizability fast over an unreliable network. 

If your application is sensitive to high latency, you are fundamentally forced to abandon linearizability and embrace weaker consistency models.

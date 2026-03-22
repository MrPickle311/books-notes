---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
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

![Figure 10-1: This system is not linearizable, causing sports fans to be confused.](data_intensive_applications/figure-10-1.png)

This is a **Violation of Linearizability**. 
If Aaliyah and Bryce had refreshed at the exact same millisecond, different results would be excusable. But because Bryce physically hit refresh *after* he heard Aaliyah announce the final score, the timeline established that the data existed in the real world *before* his query began. Because Bryce's query returned a stale result that violated this real-world timeline, the database is considered nonlinearizable.

### What Makes a System Linearizable?
To fully understand how to build a linearizable database, we need to trace the exact timings of reads and writes. 
In distributed systems theory, when multiple clients read/write the same object, that object is called a **Register**.

#### The Rules of Concurrency
Imagine a timeline where a Register `x` initially holds the value `0`. 
Client C begins writing the new value `1` to the database. At the exact same time, Clients A and B are violently polling the database, sending read requests to see the latest value.

![Figure 10-2: If a read request is concurrent with a write request, it may return either the old or the new value.](data_intensive_applications/figure-10-2.png)

Because of unpredictable network delays, Client C's write request takes some time to arrive at the database, process, and return. During this ongoing window:
*   If a read happens *before* the write begins, it mathematically must return `0`.
*   If a read happens *after* the write completes, it mathematically must return `1`.
*   If a read overlaps in time with the write (they are **Concurrent**), the read could return *either* `0` or `1`. Neither answer violates physics, because the write might be sitting in a router queue or half-committed to memory.

However, simply saying "concurrent reads can return either value" is not enough to achieve Linearizability! If we leave it at that, Client A might read `1`, then immediately read `0` again while the write is still resolving. This destroys the illusion of a single, perfect copy of data.

#### The Flipping Point
To achieve Linearizability, we must add a strict mathematical constraint:

![Figure 10-3: After any one read has returned the new value, all following reads must also return the new value.](data_intensive_applications/figure-10-3.png)

We must mathematically guarantee that at exactly one specific microsecond *during* the write operation, the value of the Register instantaneously flips from `0` to `1`. 

**The Golden Rule of Linearizability:** If *any* client successfully reads the new value `1`, then **every single read that begins after that exact millisecond** must also return `1` (or a newer value). The database is never allowed to "forget" the new value and regress back to `0`, even if the write operation itself hasn't technically finished confirming with the client.

#### Visualizing Linearizable Sequences
We can visualize this by drawing a vertical line down every operation bar. This line represents the exact millisecond the database physically executed the command.

![Figure 10-4: Visualizing the points in time at which reads and writes appear to have taken effect.](data_intensive_applications/figure-10-4.png)

In a linearizable system, if you connect these vertical execution lines chronologically, the connecting wire must **always** move forward in time (left to right). It can never loop backwards. Once the wire moves past a new value, the system's state has advanced permanently.

*(In the diagram above, the final read by B violates linearizability because it attempts to read the value `2`, even though Client A chronologically already read the newer value `4`. This pulls the timeline backwards).*

---
## Related Concepts
* [[Data Intensive Applications]]

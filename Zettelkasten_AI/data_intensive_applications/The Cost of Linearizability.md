If Linearizability is the gold standard for avoiding horrific data bugs, why doesn't every database just use it? The answer is the brutal tradeoff between Linearizability and **Availability**. 

Let's look at a Multi-Region deployment with two datacenters (e.g., East Coast and West Coast). What happens if a backhoe severs the fiber-optic cable between the two datacenters, creating a **Network Partition**?
*   Nodes within the East Coast can talk to each other.
*   Nodes within the West Coast can talk to each other.
*   But the East Coast cannot talk to the West Coast.

![Figure 10-7: A network interruption forcing a choice between linearizability and availability.](data_intensive_applications/figure-10-7.png)

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
---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
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

![Figure 10-6: A nonlinearizable execution, despite using a quorum.](data_intensive_applications/figure-10-6.png)

This perfectly fulfills the strict Quorum requirements ($3 + 2 > 3$), but it is a blatant **Violation of Linearizability**. B's read pulled the timeline backward. 
*(Note: You can force a Dynamo-style database to be linearizable by requiring readers to synchronously repair the data before returning the result, and requiring writers to read the latest quorum state before writing. However, the performance penalty is so severe that almost no databases do it).*
---
## Related Concepts
* [[Data Intensive Applications]]

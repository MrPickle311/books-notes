---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Consensus is conceptually simple: **getting multiple nodes to agree on a single value**. 

While simple to define, it is the most infamously difficult problem in all of distributed systems to implement correctly. For decades, computer scientists have tried to reliably elect a Single Leader, implement an Atomic Compare-and-Set, or construct an Append-Only Log across an unreliable network. 

*(Surprise! All three of those problems are mathematically identical. If you invent an algorithm that solves one, it perfectly solves the other two. We lump all of these under the definition of "Consensus").*

The most famous consensus algorithms dominating the modern tech stack are **ZooKeeper (Zab), etcd (Raft), Paxos**, and Viewstamped Replication. These algorithms are designed for "Non-Byzantine" fault models (meaning they assume nodes might crash or the network might fail, but they assume no node is actively trying to hack the algorithm through malicious lies). 

### The Impossibility of Consensus (FLP Result)
In 1985, Fischer, Lynch, and Paterson published the famous **FLP Result**, which mathematically proved that if there is even the slightest risk that a node might crash, there is absolutely **no deterministic algorithm that can guarantee consensus will ever be reached**. 

At a glance, this proof is horrifying—it mathematically states our entire modern internet infrastructure shouldn't be possible. 
However, the FLP proof relied on the pure *Asynchronous System Model* (Chapter 9). It assumed the algorithm was not legally allowed to use a physical clock or a Timeout. 

In engineering reality, we *are* allowed to use Timeouts and random numbers! By simply utilizing timeouts (even if the timeouts are sometimes wildly inaccurate due to network latency), the impossibility shatters and Consensus becomes solvable again in the real world.

### Single-Value Consensus
Let's drill down into the core rules of an algorithm that allows multiple nodes to agree on a single value (e.g., deciding which server officially won the lock on a username).

For a Consensus Algorithm to be legally valid, it must guarantee four absolute properties:
1.  **Uniform Agreement:** No two nodes decide differently. Everyone must eventually embrace the same winning idea.
2.  **Integrity:** Once a node makes a decision, that decision is locked in blood. A node cannot change its mind later.
3.  **Validity:** A node can only decide on a value that was actively proposed. (You can't write a lazy algorithm that bypasses the work by just hardcoding the answer `null` every time).
4.  **Termination:** Every node that hasn't violently crashed *must* eventually reach a decision. The algorithm is not allowed to hang infinitely. 

*Agreement, Integrity, and Validity* are **Safety Properties** ("Nothing bad happens").
*Termination* is a **Liveness Property** ("Something good eventually happens").

#### The Reality of Fault Tolerance
If we didn't care about fault tolerance, we wouldn't need a PhD algorithm. We could just pick one node to be the "Dictator," let them make all the decisions, and we would perfectly satisfy Agreement, Integrity, and Validity. But if the Dictator crashes, Termination physically stops happening.

A true consensus algorithm's actual job is to guarantee **Termination** (Liveness), even when earthquakes are destroying datacenters. Because of physics, termination is mathematically guaranteed *only if* an absolute majority ($> 50\%$) of the cluster is currently online and communicating. 

However, the brilliant design of Raft and Paxos guarantees that the **Safety Properties** (Agreement and Integrity) NEVER break, no matter what happens. Even if $99\%$ of the nodes are destroyed or a network partition severs the country in half, the system will completely cease to process requests rather than risking a single inconsistent or "split-brain" decision.
---
## Related Concepts
* [[Data Intensive Applications]]

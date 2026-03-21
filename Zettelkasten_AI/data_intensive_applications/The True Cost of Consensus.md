---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
### The True Cost of Consensus
Consensus is a miraculous breakthrough in computer science. It is essentially **"Single-Leader replication permanently fixed."** It guarantees automatic failover, no split brain, and no lost committed data. Any distributed system today that promises automatic failover but *does not* use a proven consensus algorithm is almost certainly fundamentally flawed.

However, we do not use Consensus for everything. Why? Because the mathematical rules of Consensus impose brutal taxes on the system:

1.  **Strict Majority Operations:** Every single write requires a synchronous network round-trip to a majority ($>50\%$) of the cluster.
2.  **Hardware Inefficiency:** To survive 1 node failing, you must buy 3 nodes. To survive 2 nodes failing, you must buy 5 nodes. The cost scales terribly.
3.  **Inverse Scaling:** You cannot improve write throughput by adding more nodes. In fact, because every write must be approved by a >50% quorum, **adding more nodes actually slows the database down!**
4.  **Timeout Tuning:** Consensus algorithms are hyper-sensitive to Timeouts. If you set the "Leader Dead" timeout too high, the system will sit completely frozen for minutes waiting for a recovery. If you set the timeout too low, a slight network hiccup will trigger a massive Leader Election, grinding the cluster to a halt unnecessarily. 
5.  **Flaky Networks:** Raft is notoriously susceptible to strange network edge cases. If one specific network cable in the cluster is flaky, it can trick Raft into triggering infinite back-to-back leader elections, completely paralyzing the entire database. (Some derivatives, like Egalitarian Paxos/EPaxos, attempt to fix this by abandoning the concept of a Leader entirely).
---
## Related Concepts
* [[Data Intensive Applications]]

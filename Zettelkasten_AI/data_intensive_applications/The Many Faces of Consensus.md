---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Consensus algorithms can be packaged and used in several different ways. In fact, if you solve one of the following problems, you have mathematically solved all the others:

#### 1. Compare-and-Set (CAS) as Consensus
A Compare-and-Set operation checks if a register holds an expected value, and if so, atomically updates it to a new value.
*   **Consensus to CAS:** If you have a Consensus algorithm, you can build CAS. When multiple nodes want to update `x` from `0` to `1`, they use Consensus to propose their updates. The Consensus algorithm decides the winner, and the winner performs the CAS.
*   **CAS to Consensus:** If you have a fault-tolerant, linearizable CAS operation, you can build Consensus. You set a register to `null`. Every node tries to run `CAS(expected: null, new: their_proposal)`. Whoever successfully flips the register from `null` wins the Consensus decision.

#### 2. Shared Logs as Consensus (Total Order Broadcast)
Instead of just agreeing on *one* value, what if you need to agree on an infinite sequence of values? This is an **Append-Only Shared Log**. 
If multiple clients concurrently shout "Append Event A!" and "Append Event B!", every single node in the cluster must record them in the exact same chronological order.

A fully fault-tolerant Shared Log (also formally known as **Total Order Broadcast** or Atomic Broadcast) must satisfy these properties:
1.  **Eventual append:**  If a node requests for some value to be added the log, and the node does not crash, then that node must eventually read that value in a log entry.
2.  **Reliable Delivery:** No log entries are lost. If one node reads entry $E$, all healthy nodes will eventually read entry $E$.
3.  **Agreement:** If two nodes both read entry $E$, they mathematically must have both read the exact same sequence of entries leading up to $E$. (No branching timelines).
4.  **Validity:** Every entry must have been proposed by a client.
5.  **Append-Only:** Once an entry is committed, it is immutable forever.

**The Equivalence:**
*   **Shared Log to Consensus:** If you have a Shared Log, solving single-value Consensus is trivial. Everyone proposes their idea to the log. You simply read the very first entry inserted into the log, and that is the officially decided value.
*   **Consensus to Shared Log:** If you have a single-value Consensus algorithm, you can build a Shared Log by running an infinite number of separate Consensus instances. You run Consensus to decide "What is Log Entry #1?", then run another Consensus to decide "What is Log Entry #2?", and so on, forever. The details are a bit more complicated, but the basic idea is this [75]:
    1. You have a slot in the log for every future log entry, and you run a separate instance of the consensus algorithm for every such slot to decide what value should go in that entry.
    2. When a node wants to add a value to the log, it proposes that value for one of the slots that has not yet been decided.
    3. When the consensus algorithm decides for one of the slots, and all the previous slots have already been decided, then the decided value is appended as a new log entry, and any consecutive slots that have been decided also have their decided value appended to the log.
    4. If a proposed value was not chosen for some slot, the node that wanted to add it retries by proposing it for a later slot.

The realization that Leader Election, Distributed Locks, and Append-Only Logs are all just different masks worn by the exact same underlying mathematical problem (Consensus) is a profound revelation in distributed systems.

#### 3. Fetch-and-add as Consensus (Partially)
A Fetch-and-add operation atomically increments a counter and returns the old value (like an auto-incrementing ID). 
*   **Consensus to Fetch-and-add:** You can easily implement a Fetch-and-add using CAS (and therefore using Consensus) by reading the old value and running a CAS loop to increment it.
*   **Fetch-and-add to Consensus?** If you have a fault-tolerant Fetch-and-add, can you solve Consensus? Almost. If a register is set to `0`, everyone runs a Fetch-and-add. Whoever receives the `0` technically "wins" the consensus. However, the losers only receive `1`, `2`, `3`... they know they lost, but they *don't know who the winner was!* Because of this flaw, Fetch-and-add can only guarantee consensus if exactly 2 nodes are competing (it has a **"Consensus Number" of 2**). By contrast, CAS and Shared Logs have a Consensus Number of infinity (they work for any number of nodes).

#### 4. Atomic Commitment as Consensus
From Chapter 8, we discussed Distributed Transactions and the concept of Atomic Commitment (like Two-Phase Commit / 2PC), where every shard in a transaction must all Agree to Commit or all Agree to Abort. 

At first glance, this sounds identical to Consensus. However, there is a fundamental difference in the rules:
*   **Consensus:** The algorithm is completely happy deciding on *any* value, as long as everyone agrees on the same value.
*   **Atomic Commitment:** The algorithm is forced to ABORT if even a single participant votes to abort. It can only COMMIT if 100% of participants voted to commit.

The properties of Atomic Commitment are:
1.  **Uniform Agreement:** It is impossible for one node to commit while another aborts.
2.  **Integrity:** Once committed/aborted, a node cannot change its mind.
3.  **Validity:** If it commits, *all* nodes must have previously voted to commit.
4.  **Non-triviality:** If all nodes voted to commit and there are no timeouts, it *must* commit (it can't just be lazy and always abort).
5.  **Termination:** Every healthy node eventually decides.

**The Equivalence:**
*   **Consensus to Atomic Commitment:** Everyone broadcasts their vote (Commit/Abort) to everyone else. Any node that receives 100% "Commit" votes uses the Consensus algorithm to officially propose the value "Commit". If a node receives even one "Abort" or sees a timeout, it uses the Consensus algorithm to propose "Abort". Whatever the consensus algorithm decides is the final answer.
*   **Atomic Commitment to Consensus:** A node creates a transaction and uses a single-node CAS to claim its value. If the CAS succeeds, it votes Commit. If it fails, it votes Abort. If the Atomic Commitment decides "Commit", consensus is reached!

This proves that Atomic Commitment and Consensus are mathematically equivalent.

### Consensus in Practice
While all these problems are mathematically equivalent, in the real world, which one do we actually build? 
The answer is the **Shared Log (Total Order Broadcast)**. 
Modern consensus systems like Raft, Zab (ZooKeeper), and Viewstamped Replication provide a Shared Log out of the box. (Even Paxos, which technically only solves single-value consensus, is almost exclusively used in its "Multi-Paxos" form to build a Shared Log).

#### Using Shared Logs
A Shared Log is the holy grail of distributed databases. 
If you can guarantee that every replica in a cluster processes the exact same sequence of state-changing events in the exact same order, the replicas will always be in perfect sync. 
This powers:
*   **State Machine Replication:** The foundation of Event Sourcing.
*   **Serializable Transactions:** If the log entries are deterministic stored procedures, forcing every node to execute them in the log's exact order guarantees mathematically perfect Serializability.

A Shared Log also gives you an easy cheat code to solve the other consensus problems:
*   *Need a Distributed Lock?* Append a message to the log. Whoever's message gets recorded first wins the lock.
*   *Need a Fencing Token?* The auto-incrementing ID of the log entries themselves can act as the sequential fencing token (e.g., ZooKeeper's `zxid`).

### From Single-Leader Replication to Consensus
In Chapter 5, we discussed Single-Leader replication. It is a primitive form of a Shared Log: one Dictator node writes the log, and the followers read it. The glaring flaw was failover. If the leader crashed, a human administrator had to manually wake up in the middle of the night to choose a new leader. 
We need a consensus algorithm to automatically elect a new leader. But this presents a massive paradox!
*   To solve Consensus (a Shared Log), we need a Leader to dictate the order of writes.
*   To elect a Leader, we need Consensus so we don't accidentally create a Split-Brain.
How do we break the loop?

#### Epochs and Two Rounds of Voting
Consensus algorithms elegantly break the paradox by dropping the strict requirement that there is only one leader *ever*. Instead, they guarantee there is only one leader **per Epoch** (called a *term* in Raft, *ballot* in Paxos, *view* in Viewstamped Replication).

When a node suspects the current leader is dead (via timeout), it increments the Epoch Number and starts an election to become the new leader.
If two different leaders are fighting (because the old leader wasn't actually dead), the leader with the strictly higher Epoch Number mathematically crushes the old leader.

To achieve this, the system performs **Two Rounds of Voting**, requiring a Quorum (majority) of nodes for each:
1.  **Vote to Elect a Leader:** A node campaigns for the highest Epoch. Nodes vote 'Yes' only if they haven't seen a higher Epoch yet.
2.  **Vote to Append a Log Entry:** Before the newly elected Leader is legally allowed to write to the Shared Log, it must propose the write to the cluster. Nodes vote 'Yes' to accept the write *only if* they haven't voted for a newer Epoch in the meantime.

Because both rounds require a Quorum (> 50%), the physics of math dictate that the two quorums **must overlap**. At least one node that voted to accept the new write *must* have also been a node that voted in the latest leader election. If a newer leader had been elected, that overlapping node would immediately reject the write and inform the old leader it has been deposed. 

*(Note: Unlike Two-Phase Commit, which breaks completely if even one node crashes, Consensus Voting only requires a majority. It continues functioning perfectly even if 49% of the servers are physically destroyed).*

#### Subtleties of Consensus
While the basic structure is similar across algorithms, the devil is in the details:
*   **Up-to-date Leaders:** When a leader dies, how do we make sure the new leader doesn't accidentally overwrite good data? 
    *   **Raft**'s rule: You cannot physically win an election unless your personal log is already as up-to-date as the majority of the cluster.
    *   **Paxos**'s rule: Anyone can win an election, but before you are allowed to append *new* entries, you must first forcefully synchronize your log with the rest of the cluster.
*   **Consistency vs. Availability:** Sticking to the strict rules above guarantees Consensus. But sometimes, databases knowingly break the rules to stay online. For example, Kafka allows "Unclean Leader Election" (letting an out-of-date replica become leader). This maximizes Availability but knowingly sacrifices Consistency (guaranteeing some data will be permanently lost if a crash occurs).
*   **Reconfiguration:** Standard algorithms assume a static number of servers. If you want to dynamically add or remove servers (e.g., migrating to a new datacenter) without 100% downtime, the algorithm requires incredibly complex "Reconfiguration" protocols.

---
## Related Concepts
* [[Data Intensive Applications]]

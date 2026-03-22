---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Up until now, we've only discussed isolation within a single node. If a database is replicated with a single-leader, all transaction logic still executes on that single leader node perfectly fine.

But what happens when a single transaction needs to touch *multiple* nodes? For example, writing to multiple different shards simultaneously, or updating a Global Secondary Index that lives on a different node than the primary record. This is a **Distributed Transaction**.

For *isolation* (concurrency control), the rules are mostly the same: 2PL works in distributed environments, and SSI provides distributed serializability checkers (like in FoundationDB). 
However, fundamentally guaranteeing **Atomicity** (all-or-nothing execution) in a distributed environment introduces a massive new problem.

#### Single-Node Atomicity
On a single machine, Atomicity is simple. When you type `COMMIT`, the database's storage engine:
1. Writes the new data to the disk (the Write-Ahead Log).
2. Appends a final `"COMMIT RECORD"` to the disk. 
The entire atomic decision comes down to that single disk controller writing that single record. If power is lost instantly before that commit record hits the disk, the database recovers, sees no commit record, and safely tears down the uncommitted data.

#### The Distributed Atomic Commitment Problem
In a distributed database, simply telling every node independently to "Commit!" creates catastrophic bugs. 
Imagine you send the commit request to Node A and Node B over the network:
*   Node A successfully receives the request, processes it, and writes the commit record to its local disk.
*   Node B detects a constraint violation (or the network cable is cut, or it runs out of disk space, or it crashes before the commit hits the disk) and Aborts.

![Figure 8-12: When a transaction involves multiple database nodes, it may commit on some and fail on others.](data_intensive_applications/figure-8-12.png)

Now you have a torn transaction. Half the database (Node A) permanently committed the write, while the other half (Node B) rejected it. 
Furthermore, you cannot retroactively undo Node A! Because Node A committed, under standard isolation levels (like Read Committed), other users immediately began seeing and reading that data and making business decisions based on it. If you forcibly "undo" Node A, you break the reality of every transaction that followed it. 

To prevent this nightmare, the database must guarantee that **all nodes commit, or all nodes abort.** There cannot be a mixture. This fundamental hurdle is known as the **Atomic Commitment Problem**.

---

#### Two-Phase Commit (2PC)
**Two-Phase Commit (2PC)** is the classic distributed algorithm designed to solve the Atomic Commitment problem. It ensures that a transaction spanning multiple database nodes (Participants) commits or aborts seamlessly as a single unit. 
*(Note: 2PC is completely unrelated to Two-Phase Locking [2PL]. 2PC provides distributed atomicity, while 2PL provides isolation).*

To achieve this, 2PC introduces a brand new component: The **Coordinator** (or Transaction Manager). 

Instead of a single "Commit" request, the Coordinator splits the process into two phases:
1.  **Phase 1 (Prepare):** The Coordinator asks every Participant: "Are you *definitely* able to commit this transaction?"
2.  **Phase 2 (Commit/Abort):** 
    *   If *every single Participant* replies "Yes", the Coordinator says: "Great, everyone Commit!"
    *   If *even one Participant* replies "No" (or times out), the Coordinator says: "Abort!" and every node throws the transaction away.
![Figure 8-13: A successful execution of two-phase commit (2PC).](data_intensive_applications/figure-8-13.png)

*The Marriage Analogy:* This is identical to a Western wedding ceremony. The minister (Coordinator) asks both the bride and groom (Participants) "Do you?". Only if *both* say "I do" (Phase 1) does the minister officially pronounce them married (Phase 2). If anyone says "No," the wedding is aborted.

#### A System of Promises (Why 2PC Actually Works)
Why does this two-phase structure prevent torn transactions? If the network can fail, couldn't the Coordinator's "Phase 2 Commit" message just get lost over the network anyway?
2PC theoretically dodges this by breaking the process into a strict, step-by-step system of rigid promises and two "Points of No Return":

1.  **Request Transaction ID:** The application wants to begin a distributed transaction, so it requests a globally unique Transaction ID from the Coordinator.
2.  **Execute Single-Node Writes:** The application executes standard single-node writes on all the Participants (Node A and Node B), tagging them with the global Transaction ID. (If anything fails right now, it's fine, anyone can safely abort).
3.  **Phase 1 - Prepare Request:** The application tells the Coordinator it is ready to commit. The Coordinator sends a `Prepare` request to all Participants.
4.  **The Participant's Promise (Point of No Return #1):** When a Participant receives the `Prepare` request, it does all the heavy lifting. It writes the data to its disk, checks constraints, and prepares to commit. 
    By replying `"YES"` to the Coordinator, the Participant **surrenders its right to abort.** It mathematically promises: "Even if my power is unplugged right now, or my disk fills up, I guarantee that whenever I wake back up, I am still fully capable of committing this transaction if you ask me to."
5.  **The Coordinator's Decision (Point of No Return #2):** Once the Coordinator receives a "YES" from all participants, it makes the final decision: Commit. 
    Crucially, the Coordinator writes this decision *to its own local disk*. This disk write is the global **Commit Point**. If the Coordinator crashes instantly after this write, the transaction remains committed upon reboot.
6.  **Phase 2 - Commit/Abort:** Once the Coordinator's decision hits the disk, it sends the `Commit` request to all Participants over the network. 
    If the network fails or a Participant crashes, the Coordinator must **retry forever** until it succeeds. There is absolutely no going back. Because the Participants already voted "YES" in Step 4, they cannot refuse to commit when they finally receive the message.

#### Coordinator Failure (The Single Point of Failure)
What happens if the Coordinator itself crashes?
*   If the Coordinator crashes *before* sending "Prepare" requests, the Participants can safely abort.
*   However, if a Participant has already replied "YES" to the Phase 1 Prepare request, it is trapped. It surrendered its right to abort, but it hasn't received the final Commit/Abort decision yet. 
*   If the Coordinator crashes at this exact moment, the Participant is stuck in a state called **"In Doubt" (or Uncertain)**.

![Figure 8-14: The coordinator crashes after participants vote “yes.” Database 1 does not know whether to commit or abort.](data_intensive_applications/figure-8-14.png)

The Participant mathematically *cannot* unilaterally Abort (because the Coordinator might have successfully told Database 2 to Commit right before crashing). It also mathematically *cannot* unilaterally Commit (because the Coordinator might have decided to Abort). The Participant has absolutely no alternative but to freeze and wait.

**Recovery:**
The only way 2PC can break this freeze is if the Coordinator reboots.
When the Coordinator recovers, it reads its own local transaction log on disk.
*   If it finds a "Commit" record, it knows it made the decision before crashing, and re-broadcasts the Phase 2 "Commit" message to the frozen Participants.
*   If it doesn't find a record, it assumes it crashed before deciding, and broadcasts an "Abort" message to the frozen Participants.

*The Ultimate Nightmare:* This means the ultimate global Atomicity of 2PC relies entirely on a **Single-Node Atomic Commit** occurring on the Coordinator's local disk. The system is designed to wait indefinitely for the Coordinator to recover. But if the Coordinator suffers a catastrophic hardware failure and its hard drive is permanently destroyed, the frozen "In Doubt" databases will wait forever. The only way to unstuck the system is for a human database administrator to manually log in and forcefully Commit/Abort the frozen transactions by hand.

#### Three-Phase Commit (3PC)
Because Two-Phase Commit can get permanently stuck waiting on a crashed Coordinator, it is formally known as a **Blocking Atomic Commit Protocol**. 
Can we design a *non-blocking* atomic commit algorithm that never gets stuck? 

Academics proposed an algorithm called **Three-Phase Commit (3PC)**. 
However, 3PC has a fatal flaw: it only works in a theoretical universe where you can mathematically guarantee that the network delay has a maximum allowed limit, and that servers will always respond within a fixed timeout. 
In the real world (as Chapter 9 will show), networks drop packets randomly and garbage-collection pauses can freeze servers for seconds at a time. In the real world of unbounded delays, 3PC falls apart and cannot guarantee atomicity. 

*Conclusion:* 3PC is not practically viable. Instead of trying to fix 2PC by adding a third phase, the modern solution is to replace the single Coordinator node with an entirely different mathematical architecture: **Fault-Tolerant Consensus Algorithms** (which we will finally explore in Chapter 10).
---
## Related Concepts
* [[Data Intensive Applications]]

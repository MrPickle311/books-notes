---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
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

![Figure 10-5: The web server and video transcoder communicate both through file storage and a message queue, opening the potential for race conditions.](data_intensive_applications/figure-10-5.png)

If the Cloud Storage is *not* linearizable, its internal replication might be slower than the Message Queue. The transcoder races to the storage service and either finds a stale, older version of the file, or a 404 Not Found error, crashing the job. 

To fix this, the storage service must provide immediate recency (Linearizability), guaranteeing that if the write finished *before* the message queue was pinged, the data is globally visible.

---
## Related Concepts
* [[Data Intensive Applications]]

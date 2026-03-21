---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
In Batch Processing, fault tolerance is trivial: if a map task fails, the framework simply throws away its transient output files and restarts the task on another machine. Because the input files are immutable, the output is guaranteed to be exactly the same. The job achieves **Exactly-Once Semantics**.

Stream processing is infinitely harder because a stream never explicitly "finishes". If a stream processor crashes, you cannot simply wait until the end to see the results—it has already been incrementally writing out outputs to the outside world!

### Microbatching and Checkpointing
To achieve Exactly-Once semantics in a streaming environment, frameworks heavily borrow from the batch world:
1.  **Microbatching (Spark Streaming):** The stream is artificially chopped into 1-second chunks. Each 1-second chunk is treated as a miniature immutable batch job. If the stream processor crashes, it throws away the failed 1-second output and retries that tiny batch.
2.  **Checkpointing (Apache Flink):** Instead of forcing artificial batches, Flink processes events seamlessly in real time. However, it periodically inserts "Barrier" events into the data stream. When a processor receives a barrier, it takes a massive persistent snapshot of its current internal memory and effectively *saves its game*. If it crashes, it just restarts from the last barrier snapshot.

However, microbatching and checkpointing only protect the *internal* state of the stream framework. Once the stream processor emits an email or sends a physical write to a downstream Postgres database, it cannot "rollback" that write if it crashes a second later!

### Atomic Commits (Stream Transactions)
To solve the side-effect problem, modern frameworks wrap the entire processing pipeline in an atomic Distributed Transaction.
If a Google Cloud Dataflow job consumes a message from Kafka, updates its internal state, and writes an output to a downstream database, all three of those steps either atomically succeed or atomically fail together. It guarantees that the Kafka Offset will not advance internally unless the downstream database physically confirmed the write.

### Idempotence
The most powerful (and easiest) way to handle fault tolerance without building expensive atomic transactions is to design your stream processors to rely on **Idempotence**.
An idempotent operation allows you to execute the exact same command 100 times, but the end result is physically identical to executing it once. 
*   **Non-Idempotent:** `UPDATE visits = visits + 1`
*   **Idempotent:** `UPDATE visits = 42`

If a stream processor crashes, it simply starts up again and blindly replays the last 5 minutes of the Kafka stream. As long as every outgoing database write is designed to be an idempotent upsert based on the unique Kafka message Offset, the downstream database safely ignores the duplicate messages. You cleanly achieve Exactly-Once semantics using only At-Least-Once delivery!

### Rebuilding State After a Failure
If an Flink processor maintaining a massive 500GB Hash Join Window crashes, how does the backup node recover that 500GB state?
1.  **Remote Datastore:** It can keep its state entirely in an external Redis node. *(Downside: Extremely slow network overhead for every message).*
2.  **Local State Checkpointing:** The stream processor keeps state purely in local RAM/Disk, but periodically flushes massive snapshots of that RAM to a distributed filesystem (like HDFS or S3). When a new node boots up, it downloads the 500GB file from S3 to restore its RAM.
3.  **Replaying from the Log:** If the state is just a local Materialized View caching a CDC Stream, the new node boots up entirely empty, connects to the Kafka log, and re-scans the compacted topic top-to-bottom to completely rebuild its state from scratch!

---
## Related Concepts
* [[Data Intensive Applications]]

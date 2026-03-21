---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
For decades, a database's internal Replication Log was completely hidden away. It was considered a proprietary, internal-only implementation detail used purely to keep a Postgres Leader synced with a Postgres Follower.

However, the industry recently realized that exposing this internal log is the ultimate solution to the Dual-Write Synchronization problem. This practice is known as **Change Data Capture (CDC)**. 

CDC is the process of observing all data changes written to a primary database (the Leader), mathematically extracting them into a universal event format, and streaming them out to any other system (the Followers) that wants to listen.

### Solving the Concurrency Race Condition
By capturing the exact log from the primary database, the race condition from Figure 12-4 completely disappears:

![Figure 12-5: CDC solving the dual-write race condition by establishing a Single Leader.](figure-12-5.png)
*Figure 12-5: The Database acts as the Single Leader. It totally orders the incoming writes (A, then B). The search index simply reads the CDC log and applies the changes in that exact same guaranteed order.*

Because Postgres physically writes "A" to its replication log first, and then writes "B", the CDC stream mathematically guarantees that Elasticsearch will receive "A" first and "B" second. The two systems remain perfectly synchronized forever. 

### Implementing Change Data Capture
CDC effectively turns your core OLTP database (e.g. Postgres) into the **System of Record (The Leader)**, and physically demotes everything else (Elasticsearch, Redis, Snowflake) into **Derived Data Systems (Followers)**.

To physically transport these CDC pipelines, engineers overwhelmingly use **Log-Based Message Brokers** (like Kafka). As discussed earlier, Kafka strictly preserves the sequencing of events inside a partition, which is an absolute requirement for replicating a database log without scrambling the order of `INSERTs` and `DELETEs`.

**Open Source Tooling:**
Because building custom parsers for proprietary database binaries is a nightmare, the industry standard open-source framework for CDC is **Debezium**. Debezium acts as a Kafka Connect plugin that directly attaches to the internal replication logs of MySQL, PostgreSQL, Oracle, MongoDB, etc. It translates those raw binary logs into standard JSON/Avro events and magically dumps them directly into a Kafka Topic.

*(Note: Just like standard message brokers, CDC is completely asynchronous. Postgres does not pause and wait for Elasticsearch to process the CDC event before returning success to the user. This means your website will experience **Replication Lag**—a user might update their profile name, refresh the page immediately, and momentarily see their old name if the Redis cache is 100 milliseconds behind the CDC stream).*

### The Initial Snapshot
If you want to spin up a brand new full-text search index today, turning on a CDC stream is not enough. The CDC stream will only pipe over the writes that happen *today*. Your new search index will be missing the 10 years of historical data that already existed in the database!

To build a fresh derived system, you must first construct an **Initial Snapshot**:
1.  **Snapshotting:** You take a massive point-in-time snapshot of the entire database. Crucially, this snapshot must record the *exact Offset/Log Sequence Number* it was taken at.
2.  **Bulk Loading:** You bulk-load this massive snapshot into the new search index (acting like a Batch Process).
3.  **Catching Up:** Once the bulk load finishes, the search index connects to the CDC Kafka stream and tells Kafka: *"Please start playing the stream exactly from Offset X (the moment my snapshot was taken)."*

Modern CDC tools like Debezium have advanced watermark algorithms (like Netflix's DBLog algorithm) explicitly designed to do this incremental snapshotting safely without having to lock the primary production database for hours.

### Log Compaction
Taking massive database snapshots and bulk-loading them is a painful, operational headache. If you are using a Log-based Message Broker (like Apache Kafka), there is a far more elegant solution: **Log Compaction**.

If you configure a Kafka topic to use Log Compaction, the broker fundamentally stops acting like a dumb "Circular Buffer" that blindly deletes the oldest segments to save disk space. 
Instead, the storage engine runs a background Garbage Collection process that periodically reads through the log, groups events by their **Primary Key**, and throws away all older duplicates, keeping *only the most recent event* for each key.

![Figure 12-6: Log compaction retaining only the newest value for each primary key.](figure-12-6.png)
*Figure 12-6: A compacted log tracking video views. Even though millions of 'mew' events were fired into the topic, the background compactor deletes all historical records of 'mew' and only keeps the absolute latest value (19,451).*

*(Note: Deletions are handled via **Tombstones**. If the Primary Database sends an event deleting the row, the CDC writes a special `null` value to Kafka. The compactor sees this Tombstone and eventually deletes the key entirely).*

**The Game-Changing Benefit:** If a CDC stream is configured with Log Compaction, the log's total disk size depends *only on the current data sitting in the database*, not on the billions of historical updates that have happened over the last 10 years. 
Because of this, **you never need to take a snapshot again.** If you want to spin up a brand new search index, you simply point it at Offset 0 of the compacted Kafka topic. By scanning the topic from top to bottom, the search index will naturally reconstruct a perfect, full copy of the current database state!

### API Support for Change Streams
Historically, CDC required massive engineering hacks (like Debezium reverse-engineering raw Postgres binlogs). Today, because CDC is recognized as a fundamental architectural requirement, databases are building "Change Streams" as first-class, native public APIs.

*   **Cloud Databases:** Managed vendors like Google Cloud offer native CDC services (Datastream) out of the box. 
*   **Quorum/Leaderless Databases:** Surprisingly, even leaderless databases like Cassandra now support CDC. However, achieving this is brutally complex. Because there is no "Single Leader" acting as the source of truth, Cassandra exposes the raw log segments of *every individual node*. If a downstream system wants to listen, it must manually read all the disparate logs and merge them together in the exact same mathematical way a quorum-read coordinates data. 

Ultimately, frameworks like **Kafka Connect** serve as the universal glue, bridging these countless database APIs directly into standardized Kafka topics, ready to be consumed by Search Indexes, Caches, or powerful Stream Processing engines.
---
## Related Concepts
* [[Data Intensive Applications]]

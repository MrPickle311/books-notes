---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
Traditional AMQP/JMS message brokers inherited the transient networking mindset: send a packet, receive it, delete it. They treat messages as ephemeral. 

Databases, in contrast, expect data to be permanently recorded. This fundamental difference destroys a primary benefit we saw in Chapter 11's Batch Processing: **Repeatability**.
Because traditional message brokers destructively delete a message once an `ACK` is received, you cannot run a new Consumer against old data. If you attach a brand new analytics Consumer to an active messaging queue today, it will only see messages from today onwards. The historical data is completely gone. 

To gain the low-latency notifications of messaging *and* the durable storage of a database, the industry created a hybrid architecture: **Log-based Message Brokers** (like Apache Kafka and Amazon Kinesis).

### Using Logs for Message Storage
A log is simply an append-only sequence of records on a disk (identical to the Write-Ahead Logs we covered in Database Storage). 

A Log-based Broker operates exactly like appending to a file:
*   The **Producer** simply appends new message strings to the end of the log file.
*   The **Consumer** reads the log sequentially from top to bottom. If the Consumer reaches the end of the file, it simply waits for the operating system to notify it that new bytes have been appended (exactly how the Unix command `tail -f` works).

### Sharding the Log (Partitions)
A single hard drive appending to a single text file cannot physically scale to millions of messages per second. To solve this, Log-based Brokers utilize Database Sharding.

![Figure 12-3: A log-based message broker with multiple partitions.](data_intensive_applications/figure-12-3.png)
*Figure 12-3: Producers append to shards (partitions). Consumers independently read these files sequentially.*

Instead of one massive log, the topic is split across many different shards (which Apache Kafka calls **Partitions**). Each partition is hosted on a completely independent machine and written to independently.

*   **Offsets:** Within a single partition, every appended message is assigned a monotonically increasing, totally ordered sequence number called an **Offset** (e.g., Message 10, Message 11, Message 12).
*   **Ordering Guarantee:** Because a partition is strictly append-only, the messages *inside that specific partition* are totally ordered. However, there is absolutely zero ordering guarantee across *different* partitions.

By scaling these partitioned log-files across thousands of commodity servers and replicating the files for fault tolerance, brokers like Apache Kafka easily achieve throughputs of millions of messages per second while writing every single event durably to physical hard drives.

### Logs vs. Traditional Messaging
Log-based brokers trivially support **Fan-Out** messaging: because reading a message does not delete it from the hard drive, thousands of independent consumers can read the exact same log without affecting each other. 

Achieving **Load Balancing**, however, works differently. Instead of the broker dealing out individual messages round-robin style to consumers, the broker assigns *entire partitions* to specific consumers within a Consumer Group.
*   **The Downside (Concurrency Limit):** If a topic has 10 partitions, you can only ever have a maximum of 10 consumer nodes working in parallel. Adding an 11th node will do nothing, as it cannot be assigned a partition.
*   **The Downside (Head-of-Line Blocking):** Because a consumer reads a partition strictly sequentially, if a single message is very slow to process, it completely halts the processing of all subsequent messages in that partition.

**Rule of Thumb:** 
*   If individual messages are expensive to process, ordering doesn't matter, and you need massive parallelization on a message-by-message basis: use a traditional AMQP broker (RabbitMQ).
*   If you have millions of fast-to-process messages per second and strict ordering is critical: use a Log-based broker (Kafka).

*(Note: To maintain strict ordering for specific entities in a Log-based setup, you must use a **Partition Key**. For example, by hashing the `user_id`, you guarantee every single event for User A is mathematically routed to the exact same partition, ensuring they are always processed sequentially).*

### Consumer Offsets
In traditional messaging, the broker must track an individual `ACK` for every single message. In Log-based messaging, tracking progress is incredibly cheap: the broker simply records the **Consumer Offset**.

Because consumers read partitions strictly top-to-bottom, if a consumer declares its current Offset is `10,000`, the broker mathematically knows that all messages from `0` to `9,999` have inherently been processed. 
This is mathematically identical to the **Log Sequence Number (LSN)** used in Database Replication (where the Kafka broker acts as the Leader database, and the Consumer acts as the Follower).

If a consumer server crashes, a new server simply takes over that partition and resumes reading from the last recorded Offset. 
*(Danger: If the consumer successfully processed messages 10,001 through 10,005, but crashed before officially saving its new Offset to the broker, the new server will resume from 10,000 and process those five messages a second time!)*

### Disk Space Usage
Because Log-based brokers only ever *append*, they will mathematically always run out of hard drive space eventually. 
To reclaim space, the log acts as a massive **Circular/Ring Buffer**: the log is broken down into segments, and the oldest segments are routinely deleted or moved to cold archive storage.

Even though it deletes old data, the scale is massive. With modern 20TB hard drives, a Kafka broker being hammered with 250 MB/s of data will take an entire day to fill a single drive. In practice, production clusters have enough distributed storage to buffer days or weeks' worth of unread messages before the circular buffer finally deletes them.

**Tiered Storage (Object Stores)**
To gain virtually infinite retention, modern brokers (like Redpanda, WarpStream, Confluent) now utilize **Tiered Storage**. Instead of relying purely on expensive local SSDs, they continuously offload older log segments into cheap Cloud Object Storage (like Amazon S3).
Not only is this phenomenally cost-efficient, but storing data transparently in Object Stores (often as Apache Iceberg tables) allows standard Data Warehouse SQL batch jobs to query the historical stream data directly without needing complex ETL pipelines.

### When Consumers Cannot Keep Up
In the previous sections, we saw that systems must either Drop Messages, Apply Backpressure, or Buffer them. A log-based broker acts strictly as a **Massive Fixed-Size Buffer** (limited by the disk size).

If a consumer's codebase is painfully slow (e.g., struggling to do expensive mathematical clustering on each row), what happens?
*   The consumer's tracked Offset simply falls further and further behind the active "Head" of the log where the producers are writing.
*   **The Ultimate Consequence:** If the consumer falls *so far behind* that its Offset points to a log segment that has been permanently deleted by the Circular Ring Buffer, the consumer will literally "drop off the edge of the world" and begin missing messages.

**The Operational Advantage:** Because the disk buffers are generally measured in days or weeks, operational teams have massive leeway. If a dashboard shows a consumer is heavily lagging, a human operator has literal days to fix the consumer's bug and restart it before any data is permanently lost. Furthermore, because reading is entirely independent, a slow/crashing consumer never disrupts live production traffic for any other fast consumer!

### Replaying Old Messages (The Return of Batch Philosophy)
The greatest superpower of the Log-Based approach is that it brings the core philosophy of Batch Processing (Chapter 11) to the world of real-time streaming: **Repeatability by separating derived output from read-only inputs.**

In a traditional AMQP system, receiving a message is a destructive act (the broker deletes it after ACK). You can never run a new piece of logic over yesterday's data.

In a Log-based system, consuming is a 100% read-only operation. The log is physically untouched. The *only* state mutation is the Consumer's Offset integer moving forward.
Because the Offset is completely under the consumer's control, **you can manually rewind time.**

**Replaying:** If you deploy a bug to production that corrupts your analytics database for 24 hours, you do not panic. You simply fix the bug, manually reset the Consumer's Offset back to the integer value from 24 hours ago, and point the consumer to a fresh empty database. The consumer will rapidly "replay" the last 24 hours of logs at maximum disk speed, flawlessly reconstructing the correct derived data exactly as perfectly as a Hadoop MapReduce batch job would!

---
## Related Concepts
* [[Data Intensive Applications]]

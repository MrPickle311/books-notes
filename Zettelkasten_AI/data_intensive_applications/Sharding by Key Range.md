---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
The simplest method is assigning a continuous range of keys to each shard (exactly like how volumes of a physical encyclopedia are broken up: Vol 1 is A-B, Vol 2 is C-D, etc.).
![Figure 7-2: A print encyclopedia is sharded by key range.](data_intensive_applications/figure-7-2.png)

*   *Advantages:* Because keys are kept in sorted order within the shard, **Range Scans** are incredibly fast and easy. If your Partition Key is a timestamp, querying "Get all events from July 1st to July 31st" is blazing fast because they are all physically located next to each other on the exact same shard.
*   *Disadvantages:* Range sharding guarantees **Hot Spots** if your partition key is a timestamp. If you shard by Day, all write traffic for *Today* goes to exactly one shard, perfectly overloading it while yesterday's shards sit completely idle. 
    *   *Workaround:* Prefix the timestamp with another value (like Sensor ID). Write load is spread across shards by Sensor ID, but querying a specific Sensor's historical data remains fast.

**Rebalancing Key Ranges**
As a database grows, ranges must adapt:
*   *Pre-splitting:* If you know your data mathematically, you can manually define the shard boundaries on day 1 (used by HBase/MongoDB).
*   *Dynamic Splitting:* The database monitors the shards. If a shard reaches a hard limit (e.g. 10 GB), the database automatically splits it down the middle into two smaller shards (just like a B-Tree). This elegantly adapts to data volume.
*   *The Catch:* Splitting a shard is a brutally intensive background operation. It forces a massive amount of disk I/O at the exact moment the shard is already cracking under high load. It requires all of its data to be rewritten into new files, similarly to a compaction in a log-structured storage engine
---
## Related Concepts
* [[Data Intensive Applications]]

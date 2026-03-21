---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Since Modulo N is broken, a popular alternative (used by Elasticsearch, Riak, Couchbase) is to create drastically more shards than there are physical nodes right on day 1.
*   *How it works:* You have 10 nodes. You immediately create 1,000 shards. Each node gets exactly 100 shards. You assign keys to shards using `hash(key) % 1000`. 
*   *Rebalancing:* When you add an 11th node, the math (`hash % 1000`) *doesn't change.* The system simply plucks 9 existing shards from each of the old nodes and hands them to the new node so everyone has roughly ~90 shards. You are just moving the physical location of the shards, not changing the mathematical mapping of keys to shards.
![Figure 7-4: Adding a new node to a database cluster with multiple shards per node.](figure-7-4.png)

**The Downside of Fixed Shards:**
You must guess your final massive scale perfectly on Day 1. If you guessed 1,000 shards, you can never scale beyond 1,000 nodes.
*   If your dataset stays tiny, you have 1,000 microscopic shards creating immense administrative overhead.
*   If your dataset grows monstrous, your 1,000 shards become physically too massive, making transferring them to a new node painstakingly slow. 
---
## Related Concepts
* [[Data Intensive Applications]]

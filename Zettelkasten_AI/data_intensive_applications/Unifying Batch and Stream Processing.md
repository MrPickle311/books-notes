---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
The ultimate goal of data integration is ensuring data ends up in all the right places, in the right formats. The dual engines powering this dataflow are Batch Processing and Stream Processing.
As established in Chapters 11 and 12, Batch and Streaming are fundamentally identical paradigms. The only difference is that batch processing operates on finite, bounded datasets, while stream processing operates on infinite, unbounded datasets.

### Maintaining Derived State
Both processing paradigms strongly encourage a functional programming philosophy: 
Data pipelines should be constructed from **Deterministic, Pure Functions** with explicitly defined inputs and outputs.
Inputs are treated as strictly immutable, and outputs are strictly append-only. There should be no hidden side-effects.

If a data pipeline is perfectly deterministic, maintaining derived state becomes incredibly robust and failure-tolerant. If your entire Elasticsearch index crashes and burns, you do not panic. You simply restart the pure function from the beginning of the immutable input log and mathematically rebuild the cache perfectly from scratch.
Furthermore, asynchronous pipelines inherently contain faults. If the pipeline updating the Elasticsearch index crashes, the rest of the company (e.g., the Postgres database and the Redis cache) keeps running flawlessly. Distributed transactions, by contrast, amplify failures by forcing the entire system to abort if a single index node goes down.

### Reprocessing Data for Application Evolution
In the real world, applications evolve. A company might suddenly realize their old data pipeline is generating useless analytics, and they want to completely restructure how the data is modeled. 

**Reprocessing** is the superpower that makes this possible. 
Because you possess an immutable append-only log of all historical events, you can create a *brand new*, completely restructured view of the data by simply writing a new functional pipeline and replaying the entire history into it from the beginning!

#### 🚂 Schema Migrations on Railways
To understand why gradual reprocessing is so powerful, look at railway migrations. In 19th-century England, competing railway companies built tracks with different widths (gauges).
When the government finally standardized a single gauge, companies couldn't just shut down the entire country's economy for two years to rebuild the tracks. 
Instead, they laid down a **Third Rail**. This created a "mixed gauge" track where both old trains and new trains could run simultaneously. Over decades, as old trains were retired and replaced with standard-gauge trains, they finally ripped up the old rail.

This is exactly how you perform zero-downtime database schema migrations! 
1. You keep the old pipeline running and perfectly serving the legacy users.
2. You spin up the new, redesigned pipeline side-by-side, reprocessing historical data to catch up to the present.
3. You slowly route 5% of your users to the new system to test for bugs.
4. Once all 100% of users are safely migrated, you delete the old pipeline.

This process is entirely reversible at any stage, completely removing the terror of migrating a live production database.

### Unifying Batch and Stream Processing
Historically, developers had to write one set of code for Batch (e.g. Hadoop) and a completely separate set of code for Streams (e.g. Storm). 

*   **Lambda Architecture:** An older concept that suggested keeping both systems. Run a fast, approximate stream processor for real-time views, and a slow, accurate batch processor overnight to correct the stream's mistakes. This was notoriously hated because developers had to maintain two completely separate codebases for the exact same logic.
*   **Kappa Architecture:** The modern approach. It argues that *Batch is just a specialized case of Streaming*. You can build a single, unified codebase that handles both. 

With systems like Apache Flink or Google Cloud Dataflow, you write the logic once. 
If you point the engine at a historical log on disk, it acts like a blistering fast Batch processor. If you point that exact same code at a live Kafka topic, it acts like a real-time Stream processor. Achieving this unification requires ensuring the engine natively handles **Event Time** windowing (so historical data isn't stamped with today's execution clock) and provides strict **Exactly-Once Semantics**.
---
## Related Concepts
* [[Data Intensive Applications]]

---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
Whether you are using Unix tools, MapReduce, Spark, Flink, or BigQuery, all distributed batch processing relies on one foundational algorithm to scale past a single server: **The Shuffle**.

*"Shuffling" in computer science does not mean randomizing (like shuffling cards). It means mathematically Sorting and Routing data across a network so that identical keys end up on the exact same server.*

Whenever you call a `Join` or an `Aggregation` (like `GROUP BY user_id`), the dataflow engine must perform a Shuffle.
Using MapReduce as the structural example, a Shuffle works like this:

![Figure 11-1: A MapReduce job with three mappers and three reducers.](data_intensive_applications/figure-11-1.png)
*Figure 11-1 shows the dataflow of a MapReduce job with 3 Mappers (m1, m2, m3) and 3 Reducers (r1, r2, r3).*

1.  **Multiple Input Shards:** The source dataset is broken into chunks (labeled $m_1$, $m_2$, and $m_3$). The framework starts a separate Map task for each input shard. For example, each shard may be a separate file on HDFS or a specific prefix in an S3 bucket.
2.  **Local Sorting & Writing:** The output of each Mapper consists of key-value pairs. To ensure that identical keys end up on the exact same Reducer, each Mapper calculates a Hash of the output Key. Based on this hash, the Mapper creates a *separate* output file on its local disk for *every* Reducer pipeline. 
    *(For example, the smaller intermediate file labelled `$m_1, r_2$` in Figure 11-1 is the file created by Mapper 1 containing specifically the data destined for Reducer 2).* The Mapper locally sorts these records in memory, and writes them out to its local hard drive.
3.  **The Network Shuffle Phase:** Once the mappers finish, the Reducer servers reach across the network, connecting to every single Mapper server to systematically download their designated pieces of the puzzle. 
4.  **Merge-Sort & Reduce:** The Reducer now has a bunch of sorted files from different Mappers. It merges these files together, preserving the strict merge-sort order so that all rows with the identical Key are consecutive. The `reduce()` function is called, and the outputs are sequentially written to a final output file (labeled $r_1$, $r_2$, and $r_3$ in Figure 11-1), which become the shards of the new dataset on the distributed filesystem.

*(Note: Advanced modern cloud warehouses like BigQuery no longer force the Mappers and Reducers to do the Shuffle locally. They actually extract the Shuffle phase into dedicated, independent "Sorting Services" that run entirely in RAM to massively accelerate the network routing).*

---
## Related Concepts
* [[Data Intensive Applications]]

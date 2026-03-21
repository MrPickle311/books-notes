---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
To get the best of both worlds (eliminating hotspots with hashes, but dynamically scaling like key ranges), databases like DynamoDB and MongoDB offer **Hash Range Sharding**.

Instead of assigning a *continuous block of keys* to a shard, you assign a *continuous block of hashes* to a shard.
*   Shard 0 handles hashes `0000` to `1111`
*   Shard 1 handles hashes `1112` to `2222`
![Figure 7-5: Assigning a contiguous range of hash values to each shard.](figure-7-5.png)

Like standard Key-Range sharding, when a shard gets too large, it automatically splits down the middle. This allows the cluster to handle infinite growth without you needing to perfectly guess the shard count on Day 1.

The downside compared to key-range sharding is that range queries over the partition key are not efficient, as keys in the range are now scattered across all the shards. However, if keys consist of two or more columns, and the partition key is only the first of these columns, you can still perform efficient range queries over the second and later columns: as long as all records in the range query have the same partition key, they will be in the same shard.

**The Cassandra / ScyllaDB Variant:**
Cassandra takes this further. Instead of having cleanly split, evenly distributed hash boundaries, Cassandra splits the entire hash circle into random boundaries, resulting in many small and large ranges. It then gives each physical node *dozens* of these random ranges (vNodes). Since each node possesses so many random ranges, the statistical law of averages guarantees that every node holds an effectively equal amount of data, while vastly simplifying the math regarding how to redistribute data when a node crashes or is added.
![Figure 7-6: Cassandra and ScyllaDB split the range of possible hash values (here 0–1023) into contiguous ranges with random boundaries, and assign several ranges to each node.](figure-7-6.png)

*(Sidebar: Remember that using a Hash obliterates Range Scans. If your partition key is hashed, finding "Dates between July 1 and July 3" requires scanning the entire global cluster. Databases mitigate this by using a Compound Key. The first column is hashed to determine the Shard. The second column is used to sort the data strictly within that one Shard, enabling rapid range scans as long as you filter by the exact first column).*
---
## Related Concepts
* [[Data Intensive Applications]]

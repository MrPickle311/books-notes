Thus far, we've discussed looking up records specifically by their Primary Key (Partition Key). This is easy because the partition key determines exactly which shard holds the data.

However, the situation degrades rapidly if your application requires **Secondary Indexes** (e.g., "Find all red cars" or "Find all articles by user 123"). Secondary indexes are the backbone of relational databases and search engines, but they do not map neatly to shards because a secondary index tracks *searchable values*, not primary keys.

There are two main approaches to handling secondary indexes in a sharded database:

#### 1. Local Secondary Indexes (Document-Partitioned)
In this model, every single shard acts completely independently. It maintains its own internal secondary index covering *only* the data stored inside itself. It doesn't know or care what the other shards are doing.
![Figure 7-9: Local secondary indexes: each shard indexes only the records within its own shard.](data_intensive_applications/figure-7-9.png)

*   **Writing (Blazing Fast):** If you add a "Red Car" to Shard 1, Shard 1 instantly updates its own internal `color:red` index. No network coordination with other shards is required.
*   **Reading (Scatter-Gather / Extremely Slow):** Because the query "Find all red cars" doesn't specify a primary key, the routing tier has absolutely no idea which shard holds red cars. Therefore, it fires the query at *every single shard* in the entire global cluster in parallel. It must wait for all shards to respond, gather their local results, merge them, and return them.
    *   *The Problem:* This is called "Scatter-Gather" querying. Because querying requires hitting every shard, this query suffers heavily from **Tail Latency Amplification**. Just one slow shard out of 100 will drastically slow down the entire search. Even though you add more nodes, your overall query throughput does not improve because every node is forced to process every search.
*(Used by: MongoDB, Riak, Cassandra, Elasticsearch).*

#### 2. Global Secondary Indexes (Term-Partitioned)
Rather than each shard keeping a localized index, the database maintains a single **Global Index** covering data from all nodes. However, since a global index would instantly melt a single machine, the global index itself is *also sharded*.

It gets sharded by the Search Term (e.g., Colors A-R go to Shard 0, Colors S-Z go to Shard 1).
![Figure 7-10: A global secondary index reflects data from all shards, and is itself sharded by the indexed value.](data_intensive_applications/figure-7-10.png)

*   **Reading (Blazing Fast):** A query for "Find all red cars" hashes the word "red" and instantly routes the query to exactly one shard (Shard 0) which contains the complete, authoritative list of all red car IDs globally.
*   **Writing (Slow and Error-Prone):** Because the index is global, adding a single new car might involve updating the main data on Shard 0, updating the Make index on Shard 2, and updating the Color index on Shard 4. These distributed writes are complex and very difficult to keep in perfect sync mathematically. Many databases (like DynamoDB) only update global indexes *asynchronously*, meaning a user might add a Red Car but then momentarily be unable to see it in the search results until the global index catches up (Replication Lag).

*Global secondary indexes are incredibly powerful for read-heavy apps, but the complexity of keeping them synchronized during writes forces engineers to explicitly orchestrate distributed transactions or accept eventual consistency.*

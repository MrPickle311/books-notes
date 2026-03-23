At a certain scale, adding more replication doesn't help if your database is fundamentally too large or handles too many writes for a single machine. The solution is **Sharding** (also known as Partitioning).

*   **Replication** = Having a copy of the *same* data on multiple nodes.
*   **Sharding** = Splitting up a large dataset into smaller chunks (shards) and assigning them to *different* nodes. Every piece of data belongs to exactly one shard.

Usually, sharding and replication are combined. A single physical node might act as the "Leader" for Shard A, but simultaneously act as a generic "Follower" for Shard B.
![Figure 7-1: Combining replication and sharding: each node acts as leader for some shards and follower for other shards.](data_intensive_applications/figure-7-1.png)

#### Terminology
What this book calls a "Shard" goes by many different names depending on the database:
*   **Partition:** Kafka
*   **Region:** HBase, TiDB
*   **Tablet:** Bigtable, Cassandra, Spanner
*   **vNode / Token-Range / vBucket:** Riak, Cassandra, Couchbase

*(Note: "Partitioning" data has absolutely nothing to do with "Network Partitions", which are a type of fault where nodes lose connection to each other).*

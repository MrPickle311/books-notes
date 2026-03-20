---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

Once data is separated, architects can choose the best database for each domain. The chapter provides a trade-off analysis for various database types.

| Database Type   | Popular Products                       | Key Characteristics                                                                      |
| --------------- | -------------------------------------- | ---------------------------------------------------------------------------------------- |
| **Relational**  | PostgreSQL, Oracle, SQL Server         | ACID transactions, flexible modeling, SQL. Mature and well-understood. Scales vertically. |
| **Key-Value**   | Redis, DynamoDB, Riak KV               | Simple `get/put/delete` operations. Highly scalable and performant for simple lookups.   |
| **Document**    | MongoDB, Couchbase                     | Stores JSON/XML documents. Flexible schema, good for aggregate-oriented data. Popular.     |
| **Column Family** | Cassandra, Scylla                      | Wide-column store, handles sparse data well. Excellent for high-write-volume scenarios.   |
| **Graph**         | Neo4j, Tiger Graph                     | Models data as nodes and relationships. Optimized for traversing complex connections.      |
| **NewSQL**        | CockroachDB, VoltDB, MemSQL            | Combines the scalability of NoSQL with the ACID guarantees and SQL of relational DBs.    |
| **Cloud Native**  | Snowflake, Datomic, AWS Redshift       | Managed cloud services, reduce operational burden, often specialized for analytics.     |
| **Time Series**   | InfluxDB, TimescaleDB                  | Optimized for append-only, time-stamped data. Used for monitoring, IoT, analytics.       |

*(Star-rating figures for each database type are included below, summarizing trade-offs for learning curve, data modeling, scalability, consistency, etc.)*

![Figure 6-25: Star ratings for Relational databases.](figure-6-25.png)
![Figure 6-26: Star ratings for Key-Value databases.](figure-6-26.png)
![Figure 6-27: Star ratings for Document databases.](figure-6-27.png)
![Figure 6-28: Star ratings for Column family databases.](figure-6-28.png)
![Figure 6-30: Star ratings for Graph databases.](figure-6-30.png)
![Figure 6-31: Star ratings for NewSQL databases.](figure-6-31.png)
![Figure 6-32: Star ratings for Cloud native databases.](figure-6-32.png)
![Figure 6-33: Star ratings for Time series databases.](figure-6-33.png)
---
## Related Concepts
* [[Architecture]]

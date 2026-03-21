---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Once you have split your data into hundreds of shards across dozens of nodes, a final glaring issue remains: When a client wants to read or write a specific key, how do they actually know which physical IP address and Port holds that specific shard?

Unlike generic web application servers (where a Load Balancer can blindly send a request to *any* stateless server), database routing must be highly precise. 

There are three high-level architectures for Routing:
1.  **Node Forwarding (Gossip):** The client sends a request to a completely random node. If that node owns the shard, it handles it. If not, it forwards the request to the correct node, receives the reply, and passes it back to the client. (Used by Cassandra/Riak).
2.  **Routing Tier (Proxy):** The client sends all requests to a dedicated Routing Proxy. The proxy itself doesn't store data; it just acts as a shard-aware load balancer that instantly forwards the request to the correct IP. (Used by MongoDB's `mongos`).
3.  **Client-Aware Routing:** The database driver installed directly inside the client application downloads the routing map. The client connects perfectly directly to the correct node without any intermediary hops. (Used by Redis Cluster).
![Figure 7-7: Three different ways of routing a request to the right node.](figure-7-7.png)

#### Service Discovery and ZooKeeper
Regardless of which of the 3 architectures you use, *something* in the system needs to maintain the authoritative map of exactly which shard currently lives on which physical node. And this map constantly changes as nodes reboot or shards rebalance.

To track this routing map perfectly without encountering Split Brain or fatal desyncs, many databases rely on a separate, dedicated Coordination Service (like **Apache ZooKeeper** or **etcd**).
*   ZooKeeper utilizes incredibly strict Consensus Algorithms (discussed later in Chapter 10) to maintain an uncorruptible, highly available mapping of shards to nodes.
*   Every database node registers itself with ZooKeeper.
*   The Routing Tier (or a Client-Aware driver) simply subscribes to ZooKeeper. Whenever a shard changes ownership or a node reboots, ZooKeeper instantly notifies the routing tier to update its internal maps.
![Figure 7-8: Using ZooKeeper to keep track of assignment of shards to nodes.](figure-7-8.png)

*(Note: While HBase, Solr, Kafka, and Kubernetes rely heavily on separate coordinators like ZooKeeper or etcd, some modern databases like TiDB, YugabyteDB, and ScyllaDB have now effectively embedded these Consensus Coordination algorithms directly into their own internal architecture, removing the need to manage a separate ZooKeeper cluster).*
---
## Related Concepts
* [[Data Intensive Applications]]

When a shard becomes too large and splits, or when a node is added/removed, the database must **Rebalance** by transferring massive gigabytes of data between nodes over the network. Should this happen automatically?

**Fully Automatic Rebalancing**
*   *Pros:* Extremely convenient. Cloud systems like DynamoDB can detect a sudden traffic spike and automatically spin up new shards and rebalance data within minutes without any human intervention.
*   *Cons (The Danger):* Rebalancing is a brutally intensive network and CPU operation. If the automation triggers during peak traffic due to a false alarm (e.g., a node is temporarily slow, the cluster assumes it is dead, and frantically begins reshuffling terabytes of data to "save" it), the massive network load of the rebalance can actually crash the remaining healthy nodes, triggering a catastrophic cascading failure across the entire datacenter.

**Manual (Human-in-the-loop) Rebalancing**
Systems like Couchbase or Riak will automatically *calculate* the optimal new shard layout, but will halt and require a human administrator to click "Commit" before the gigabytes of data begin moving.
*   *Pros:* Significantly safer. It prevents automated cascading failures. It also allows Ops teams to preemptively rebalance a cluster *before* a scheduled traffic spike (like Cyber Monday or the World Cup) rather than a totally reactive automatic algorithm panicking mid-spike.
When the leader receives a write, it must decide whether to wait for its followers to copy the data before telling the user the write was "Successful".

![Figure 6-2: Leader-based replication with one synchronous and one asynchronous follower.](data_intensive_applications/figure-6-2.png)

#### Synchronous Replication
The leader waits until the follower formally confirms it received and wrote the data before reporting success to the client.
*   **Advantage:** Guarantees the follower has an up-to-date copy. If the leader instantly dies, no data is lost; the follower can safely take over.
*   **Disadvantage:** If the synchronous follower crashes, or the network is slow, the leader physically *cannot process any writes*. It must block all writes until the follower recovers.

**Semi-Synchronous Configuration**
Because making *all* followers synchronous would cause the entire database to grind to a halt the moment *one* node hiccups, databases use a hybrid approach. Usually, **one** follower is made synchronous, and the rest are asynchronous. If the synchronous follower slows down or dies, one of the asynchronous followers is instantly promoted to be the new synchronous follower. This guarantees the data lives on at least two nodes at all times without crippling the system.

#### Asynchronous Replication
The leader sends the replication log to the followers but does not wait for any response before telling the client "Success".
*   **Advantage:** The leader can continue processing non-stop writes at maximum speed, even if all followers fall minutes or hours behind.
*   **Disadvantage (Lost Data):** If the leader fails and is irrecoverable, any writes that had not yet streamed to the followers are permanently lost, even though the client was told they were successful. This weakens durability.

Despite the risk of data loss, fully asynchronous replication is widely used, especially for applications with many followers or geographically distributed nodes across the globe where network latency is high.
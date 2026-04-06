### Pattern: HeartBeat (Patterns of Distributed Systems)

A **HeartBeat** pattern ensures server availability by periodically sending messages across the cluster to signal liveness.

#### Problem: Timely Failure Detection
In a distributed cluster, each server is responsible for specific data partitions or replicas. If a server fails, the cluster must quickly detect it to take corrective actions (e.g., electing a new leader or re-assigning responsibilities). Without heartbeats, failure detection can be slow or inconsistent.

#### Solution: Periodic Liveness Messaging
Servers periodically broadcast a "heartbeat" to all other servers. Listening servers wait for a specified **timeout interval** before declaring a sending server as "failed."

**The Timing Rule:**
`Timeout Interval` > `HeartBeat Interval` > `Network Round Trip Time (RTT)`

*Example:* If the network RTT is 20ms, heartbeats could be sent every 100ms, with a failure detection timeout of 1 second to account for temporary network jitters and prevent false negatives.

**Core Implementation (Pseudo-Java):**

##### 1. The HeartBeat Scheduler
This scheduler is used by both the sender (to trigger hearts) and the receiver (to check for missing ones).
```java
public class HeartBeatScheduler {
    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private Runnable action;
    private Long heartBeatInterval;

    public HeartBeatScheduler(Runnable action, Long heartBeatIntervalMs) {
        this.action = action;
        this.heartBeatInterval = heartBeatIntervalMs;
    }

    public void start() {
        executor.scheduleWithFixedDelay(new HeartBeatTask(action), 
            heartBeatInterval, heartBeatInterval, TimeUnit.MILLISECONDS);
    }
}
```

##### 2. The Sending Server
Triggers the heartbeat message at the defined interval.
```java
class SendingServer {
    private void sendHeartbeat() throws IOException {
        socketChannel.blockingSend(newHeartbeatRequest(serverId));
    }
}
```

##### 3. The Receiving Server & Failure Detector
Handles incoming heartbeats and periodically checks for "silence" that indicates a failed node.
```java
abstract class AbstractFailureDetector<T> {
    private HeartBeatScheduler heartbeatScheduler = new HeartBeatScheduler(this::heartBeatCheck, 100L);
    
    // Periodically called by the scheduler to check for failures
    abstract void heartBeatCheck();

    // Called whenever a heartbeat is actually received from a peer
    abstract void heartBeatReceived(T serverId);
}

class ReceivingServer {
    private void handleRequest(Message<RequestOrResponse> request, ClientConnection conn) {
        RequestOrResponse clientRequest = request.getRequest();
        if (isHeartbeatRequest(clientRequest)) {
            failureDetector.heartBeatReceived(heartbeatRequest.getServerId());
            sendResponse(conn, request.getRequest().getCorrelationId());
        }
    }
}
```

#### Trade-offs: Latency vs. Accuracy
*   **Smaller Intervals:** Faster detection, but higher risk of **false failure detections** due to minor network spikes.
*   **Context Dependency:** Heartbeat settings are often tuned differently for small, localized clusters vs. large, geographically distributed ones.

#### Consensus-Based Systems (Small Clusters)
Consensus implementations like **Raft** or **ZooKeeper** (typically 3–5 nodes) use heartbeats sent from the **Leader** to all **Followers**. 

**Timeout-Based Failure Detection:**
The receiving node records the arrival time of each heartbeat. If a heartbeat is not received within a fixed window (the timeout), the leader is considered crashed, triggering a new leader election.

```java
class TimeoutBasedFailureDetector<T> extends AbstractFailureDetector<T> {
    private Map<T, Long> heartbeatReceivedTimes = new ConcurrentHashMap<>();

    @Override
    public void heartBeatReceived(T serverId) {
        Long currentTime = System.nanoTime();
        heartbeatReceivedTimes.put(serverId, currentTime);
        markUp(serverId); // Mark node as alive
    }

    @Override
    void heartBeatCheck() {
        Long now = System.nanoTime();
        for (T serverId : heartbeatReceivedTimes.keySet()) {
            Long lastHeartbeat = heartbeatReceivedTimes.get(serverId);
            if (now - lastHeartbeat >= timeoutNanos) {
                markDown(serverId); // Node considered failed
            }
        }
    }
}
```

*   **Generation Clock:** To handle false failure detections due to network delays, a "Generation Clock" (or Term) should be used to detect and ignore stale leaders.

---

### Technical Considerations for HeartBeats

Reliable heartbeat implementation requires addressing common distributed systems bottlenecks:

1.  **Head-of-Line (HoL) Blocking:** In single-socket channels, a slow request can block subsequent heartbeat messages. 
    *   **Solution:** Use **Request Pipelining** to ensure heartbeats are sent without waiting for previous responses.
2.  **Asynchronous Heartbeat Threads:** If using a **Singular Update Queue**, heavy operations like disk writes can delay the processing of timing interrupts. 
    *   **Solution:** Send and receive heartbeats on a **dedicated background thread** (as done by HashiCorp Consul and Akka).
3.  **Handling Local Pauses (GC Jitter):** JVM Garbage Collection or other runtime events can cause "local pauses" that falsely mimic a network failure.
    *   **Solution:** If the `heartBeatCheck` logic itself is delayed by a significant period (e.g., > 5 seconds), the system should ignore the timeout and defer the failure decision to the next cycle (as implemented in **Cassandra**).

#### Large Clusters (Gossip-Based Protocols)
All-to-all heartbeating does not scale to clusters with hundreds or thousands of nodes because the consumed network bandwidth would overwhelm actual data transfer. 

**Core Scaling Requirements:**
1.  **Message Limits:** Fixed limit on the number of messages generated per server.
2.  **Bandwidth Cap:** Total heartbeat traffic should be limited (e.g., a few hundred kilobytes).

**Failure Detection in Large Clusters:**
Instead of all-to-all pings, large clusters use **Gossip Dissemination** protocols to propagate failure information. 
*   **Suspicion Number:** Processes are assigned a suspicion number that increments if no gossip includes that process within a bounded time. Only when the suspicion reaches an upper limit is the node marked as failed.
*   **Implementation Examples:**
    *   **Phi Accrual Failure Detector:** Used in **Akka** and **Cassandra**.
    *   **SWIM with Lifeguard:** Used in **HashiCorp Consul** and **memberlist**.
    *   **Rapid:** A more recent framework for high-efficiency large-scale failure detection.

**Piggybacking Logic:**
Heartbeats do not always require a distinct message type. If cluster nodes are already communicating (e.g., during data replication), those existing messages serve as implicit heartbeats, further saving bandwidth.

---

### Summary Table: HeartBeat Strategies

| Property | Small Clusters (Consensus) | Large Clusters (Gossip) |
| :--- | :--- | :--- |
| **Logic** | Point-to-point (Leader to Follower) | Gossip dissemination across peers |
| **Failover Delay** | Short (detects crashes quickly) | Longer but bounded (prefers accuracy) |
| **Implementations** | Raft, ZooKeeper, etcd | Cassandra, Akka, Consul, SWIM |
| **Scalability** | 3–5 Nodes | Thousands of Nodes |

*(Awaiting the final sections of the chapter...)*

### Pattern: Majority Quorum (Patterns of Distributed Systems)

A **Majority Quorum** ensures that two groups of servers in a cluster cannot make conflicting, independent decisions by requiring a majority ($n/2 + 1$) to agree on every action.

#### Problem: Safety vs. Liveness
Distributed systems must balance two critical properties:
1.  **Safety:** Ensuring the system is always in a correct state (e.g., no lost updates).
2.  **Liveness:** Ensuring the system always makes progress (e.g., responsive to requests).

Waiting for *all* replicas to acknowledge an update maximizes safety but kills liveness (one failed node stops the entire system). Waiting for too few replicas risks losing data if those nodes crash.

#### Solution: Majority-Based Acknowledgment
A cluster accepts an update once a majority (a "quorum") of nodes has acknowledged it.
*   **Formula:** For $n$ nodes, the quorum size is $n/2 + 1$.
*   **Failure Tolerance:** To tolerate $f$ failures, a cluster size of **$2f + 1$** is required.
    *   *Example:* A 5-node cluster needs 3 for a quorum and can tolerate 2 failures.

##### Typical Quorum Use Cases:
*   **Data Updates:** Using the **High-Water Mark** to reveal only data that has reached a quorum.
*   **Leader Election:** A leader is only valid if they receive votes from a majority of servers.

#### Impact on Throughput and Scalability
Write throughput in quorum-based systems (like ZooKeeper or Raft) generally degrades as the number of servers $n$ increases ($O(1/n^2)$).
*   **Write Latency:** Directly proportional to the number of nodes in the quorum.
*   **Practical Limit:** Most systems use cluster sizes of **3 or 5**. 
    *   A 5-server cluster provides a sweet spot: tolerating 2 failures while maintaining throughput of a few thousand requests per second.
    *   Adding a 4th node to a 3-node cluster increases the quorum size (from 2 to 3) without increasing failure tolerance (both still tolerate only 1 failure).

| Number of Servers | Quorum Size | Tolerated Failures | Representative Throughput |
| :--- | :--- | :--- | :--- |
| 1 | 1 | 0 | 100% |
| 2 | 2 | 0 | 85% |
| 3 | 2 | 1 | 82% |
| 5 | 3 | 2 | 48% |
| 7 | 4 | 3 | 36% |

#### Flexible Quorums (Quorum Intersection)
Consistency is maintained as long as any two quorums **overlap** in at least one node.
*   **Optimization:** Frequent operations (like reads) can use a smaller quorum (e.g., 2), while infrequent operations (like writes) use a larger quorum (e.g., 4) in a 5-node cluster.
*   **Two-Phase Execution:** Leader election (less frequent) can use a large quorum, while all other client operations use a smaller one to improve global throughput and latency.

*(Awaiting the final sections of the chapter...)*

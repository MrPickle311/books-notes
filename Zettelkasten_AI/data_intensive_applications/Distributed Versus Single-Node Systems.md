A system involving several machines (nodes) communicating over a network is a distributed system.

**Reasons to Distribute:**
*   **Fault Tolerance/High Availability:** Redundancy to handle machine failures.
*   **Scalability:** Spread load across multiple machines to handle large data volumes or traffic.
*   **Latency:** Place servers geographically close to users.
*   **Elasticity:** Scale resources up or down to meet variable demand.

**Problems with Distributed Systems:**
*   **Complexity:** Dealing with network failures and timeouts is difficult.
*   **Performance:** A network call is vastly slower than a local function call.
*   **Troubleshooting:** Diagnosing problems is much harder; requires **observability** tools like distributed tracing.
*   **Consistency:** Maintaining data consistency across multiple services becomes the application's responsibility.

> If you can do something on a single machine, this is often much simpler and cheaper compared to setting up a distributed system.
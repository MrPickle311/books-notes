
This technique places shared functionality into its own separately deployed service, which other services call at runtime.

![Figure 8-6: A diagram showing three services making runtime API calls to a central, separately deployed "Shared Service."](figure-8-6.png)

#### Trade-offs

*   **Change Risk:** Changes are easy to deploy (just update the shared service), but they are **runtime changes**. A bug in the shared service can instantly bring down the entire system. Versioning becomes much harder, often requiring messy API endpoint versioning.
    ![Figure 8-7: A diagram showing a change being deployed to a shared service without affecting other services' deployments.](figure-8-7.png)
    ![Figure 8-8: A diagram showing a bad change being deployed to a shared service, which causes all dependent services to fail at runtime.](figure-8-8.png)
*   **Performance:** Every call to the shared service incurs network and security latency, making it much slower than an in-process call to a shared library.
    ![Figure 8-9: A diagram illustrating the network and security latency added by a remote call to a shared service.](figure-8-9.png)
*   **Scalability:** The shared service becomes a bottleneck and must be scaled to handle the combined load of all services that depend on it.
    ![Figure 8-10: A diagram showing that as dependent services scale up, the central shared service must also scale up to handle the increased traffic.](figure-8-10.png)
*   **Fault Tolerance:** The shared service is a single point of failure. If it goes down, all dependent services become non-operational.
    ![Figure 8-11: A diagram showing a "FAIL" sign on the shared service, which causes all dependent services to fail as well.](figure-8-11.png)

#### Trade-offs for Shared Services

| Advantages                                  | Disadvantages                                              |
| ------------------------------------------- | ---------------------------------------------------------- |
| Good for high code volatility (frequent changes) | Versioning changes can be difficult                     |
| No code duplication in heterogeneous codebases | Performance is impacted due to latency                  |
| Preserves the bounded context               | Fault tolerance and availability issues due to dependency |
| No static code sharing                      | Scalability and throughput issues due to dependency        |
|                                             | Increased risk due to runtime changes                      |

*   **When to Use:** Good for polyglot environments and for shared functionality that changes very frequently. Be aware of the significant operational downsides.
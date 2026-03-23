### 3. Replicated Caching Pattern

This pattern uses a replicated in-memory cache to share data. The owning service writes to the cache, and that data is automatically replicated to read-only cache instances within other services.

#### Caching Model Comparison
*   **Single In-Memory Cache:** Each service has its own private cache. Data is not shared. Not useful for this data access problem.
    ![Figure 10-4: Each service has its own unique, unsynchronized in-memory cache.](figure-10-4.png)
*   **Distributed Cache:** Data is held in an external, centralized cache server. This is not ideal for data access as it simply shifts the dependency from the service to the cache server, still involving a remote call.
    ![Figure 10-5: Services make remote calls to a central, external distributed cache server.](figure-10-5.png)
*   **Replicated Cache:** Each service hosts an in-memory replica of the same cache. Updates in one are propagated to all others. This is the model used for the data access pattern.
    ![Figure 10-6: Each service has an identical copy of the in-memory data, which is kept synchronized.](figure-10-6.png)

#### Applying the Pattern
The `Catalog` service owns a cache of product descriptions, and a read-only replica of this cache is maintained within the `Wishlist` service.

![Figure 10-7: The Catalog service owns a replicated cache, and the Wishlist service has a read-only replica, providing instant in-memory access to the data.](figure-10-7.png)

*   **Advantages:** Excellent performance (in-memory access), high fault tolerance (the `Wishlist` service can run even if the `Catalog` service is down after startup), and services can scale independently.
*   **Trade-offs:**
    *   **Startup Dependency:** The `Catalog` service (owner) must be running when the first `Wishlist` service instance starts up to populate the cache.
    *   **Data Volume:** Not suitable for large data volumes (e.g., >500MB), as the cache is replicated in every service instance.
    *   **Data Volatility:** Not suitable for data with a high rate of change (like inventory counts). Works best for relatively static data.
    *   **Configuration:** Can be complex to configure in cloud/containerized environments.

| Advantages                        | Disadvantages                                |
| --------------------------------- | -------------------------------------------- |
| Good data access performance      | Cloud and containerized configuration can be hard |
| No scalability and throughput issues | Not good for high data volumes               |
| Good level of fault tolerance     | Not good for high update rates               |
| Data remains consistent           | Initial service startup dependency           |
| Data ownership is preserved       |                                              |

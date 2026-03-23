
The decision to decompose a database is a trade-off analysis between two opposing sets of forces.

![Figure 6-1: A diagram with a central question "When to Decompose Data?". Arrows point to it from "Data Disintegrators" (justifying splitting) and "Data Integrators" (justifying keeping it together), illustrating the need for balance.](figure-6-1.png)

#### Data Disintegrators (Reasons to Split Data Apart)

1.  **Change Control:** In a shared database, a breaking change (e.g., altering a column) can impact dozens of services, requiring a difficult and risky coordinated deployment. Forgotten services will fail in production.
    ![Figure 6-3: A diagram showing a database change. A service that was not updated now has a "FAIL" sign over it because its code is incompatible with the new schema.](figure-6-3.png)
    *   **Solution: Bounded Context.** By splitting the database, each service (or group of services) owns its own data. Changes are isolated within that bounded context. Other services needing that data must ask the owning service, which provides a stable contract (e.g., JSON), abstracting them from the underlying database schema changes.
    ![Figure 6-4: A diagram showing services with their own databases. A change in one database only affects its owning service.](figure-6-4.png)

2.  **Connection Management:** In a distributed architecture, each service instance typically has its own connection pool. With a shared database, the total number of connections can quickly become saturated as services scale, leading to connection waits and timeouts.
    ![Figure 6-6: A diagram showing multiple service instances, each with its own connection pool, all connecting to a single database, which is overwhelmed with connections.](figure-6-6.png)
    *   **Solution: Splitting the database** reduces the number of connections required for each individual database server, improving performance and stability.

3.  **Scalability:** A monolithic database can become a performance bottleneck. As services scale out, they place increasing load on the single database, which may not be able to keep up.
    ![Figure 6-7: A diagram showing multiple scaled-up services all hitting a single database, which is shown with a "bottleneck" symbol.](figure-6-7.png)
    *   **Solution: Database per service (or domain)** allows each database to be scaled independently according to the needs of its services.

4.  **Fault Tolerance:** A shared database is a single point of failure (SPOF). If the database goes down, every service that depends on it becomes non-operational.
    ![Figure 6-9: A diagram showing a single database with a "FAIL" sign. Arrows point from it to all services, which also have "FAIL" signs.](figure-6-9.png)
    *   **Solution: Splitting the database** increases fault tolerance. If one database fails, only the services within that bounded context are affected; the rest of the system can continue to operate.

5.  **Architectural Quanta:** As discussed in Chapter 2, a shared database acts as a powerful static coupling point, forcing all services that use it into a single architectural quantum. This means they cannot have independent architectural characteristics.
    ![Figure 6-11: A diagram showing five services and one database all enclosed in a single large "Quantum" box.](figure-6-11.png)
    *   **Solution: Breaking the database apart** allows for the creation of multiple, independent architecture quanta.

6.  **Database Type Optimization:** A monolithic database forces a one-size-fits-all approach. However, different types of data are better suited to different types of databases (e.g., relational, document, graph).
    *   **Solution: Polyglot Persistence.** Breaking the database apart allows architects to choose the most optimal database technology for each specific data domain.

#### Data Integrators (Reasons to Keep Data Together)

1.  **Data Relationships:** Relational databases use artifacts like foreign keys, views, and triggers to enforce data integrity and create relationships between tables. These are powerful data integrators.
    ![Figure 6-13: An ERD showing tables connected by foreign keys (FK) and a view that joins multiple tables.](figure-6-13.png)
    *   **Problem:** When you split a database, these cross-domain relationships must be broken. Referential integrity can no longer be enforced by the database and must be managed at the application level, which is much more complex.

2.  **Database Transactions:** A monolithic database allows for ACID (Atomicity, Consistency, Isolation, Durability) transactions. A service can update multiple tables as a single, atomic unit of work.
    ![Figure 6-15: A diagram showing a service making multiple writes to a single database within a single transaction boundary.](figure-6-15.png)
    *   **Problem:** When data is split across multiple databases, a single ACID transaction is no longer possible. This requires implementing complex distributed transaction patterns (like Sagas, discussed in Chapter 12), which introduces significant complexity and moves to an eventual consistency model.
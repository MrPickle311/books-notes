
### Granularity Integrators (Reasons to Combine Services)

These drivers help answer the question: "When should I consider putting services back together?"

#### 1. Database Transactions
Is a single, atomic ACID transaction required across what would be separate services?
*   **Example:** A new customer registration process requires creating a `profile` record and a `password` record. If "Profile Service" and "Password Service" are separate, they have separate databases. The Profile service can succeed, but the Password service might fail, leaving inconsistent data (a profile with no password).
    ![Figure 7-10: A process flow shows a UI calling "Profile Service" (which succeeds and commits to its DB) and then "Password Service" (which fails), resulting in an inconsistent state.](architecture_the_hard_parts/figure-7-10.png)
*   **Solution:** Combine the services. A single "Customer Service" can write to both tables within a single ACID transaction, guaranteeing that the operation is all-or-nothing.
    ![Figure 7-11: A single "Customer Service" writes to both the Profile and Password tables within a single transaction boundary in its database.](architecture_the_hard_parts/figure-7-11.png)

#### 2. Workflow and Choreography
Do the services need to communicate with each other constantly to fulfill a business request?
*   **Problem 1: Fault Tolerance.** If Service A calls B, and B calls C, a failure in C will cascade and cause A and B to fail as well. High levels of synchronous, chained calls negate the fault tolerance benefits of splitting services.
    ![Figure 7-12: A web of services (A, B, C, D, E) is shown. Service C has a "FAIL" sign, and because all other services directly or indirectly depend on it, they all fail too.](architecture_the_hard_parts/figure-7-12.png)
*   **Problem 2: Performance.** Each synchronous remote call adds network latency. A request that requires five separate service calls to complete can become unacceptably slow for the end user.
    ![Figure 7-13: A request is shown making five sequential hops between services A, B, C, D, and E, with latency being added at each hop.](architecture_the_hard_parts/figure-7-13.png)
*   **Solution:** If services are too "chatty" and interdependent, combine them. This replaces slow, unreliable network calls with fast, reliable in-process calls.

#### 3. Shared Code
Do the services share a significant amount of common *domain* logic (not just utility code)?
*   **Problem:** Five separate services all depend on `shared-domain-library.jar`. When a defect is found in that library, all five services must be re-tested and re-deployed in a coordinated fashion. This creates a "distributed monolith" where the services are separately deployed but not independently deployable.
    ![Figure 7-15: Five services are shown, each with a dependency on a central "Shared Domain Code" library. An arrow shows that a change in the library forces a change in all five services.](architecture_the_hard_parts/figure-7-15.png)
*   **Solution:** If the shared domain code is large, volatile, and tightly coupled to the services, consider combining them into a single service to simplify change management.

#### 4. Data Relationships
Do the functions you want to split have tightly coupled data dependencies?
*   **Example:** A service has functions A, B, and C. Function B owns `Table 3` but needs to read from `Table 5`. Function C owns `Table 5` but needs to read from `Table 3`.
    ![Figure 7-16: A diagram showing the table relationships for a consolidated service.](architecture_the_hard_parts/figure-7-16.png)
*   **Problem:** If you split them into Service B and Service C, each with its own database, they can no longer read each other's tables directly. Service B must constantly call Service C for data, and vice-versa. This creates a "chatty" workflow (see Integrator #2).
    ![Figure 7-17: The services are split. Service B now has to make a network call to Service C to get data, and C has to call B.](architecture_the_hard_parts/figure-7-17.png)
*   **Solution:** Consolidate the services. The tight data dependencies suggest that the functions belong together in a single service with a shared data model.

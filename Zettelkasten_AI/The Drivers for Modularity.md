---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: processed
---
Architectural change should be driven by clear business needs. The primary business drivers are **speed-to-market** and **competitive advantage**. These are achieved through improvements in five key architectural characteristics.

![Figure 3-3: A diagram showing how technical drivers (Maintainability, Testability, Deployability, Scalability, Fault Tolerance) lead to the business drivers of Speed-to-Market and Competitive Advantage.](architecture_the_hard_parts/figure-3-3.png)

#### 1. Maintainability

Maintainability is the ease of adding, changing, or removing features. It is inversely proportional to component coupling.

*   **Monoliths (Application-Level Scope):** In a monolith, a simple change often requires touching multiple layers (UI, business, data), coordinating multiple teams, and has an application-wide scope.
    ![Figure 3-4: A layered architecture diagram where a single change impacts all layers, illustrating an application-level change scope.](figure-3-4.png)

*   **Service-Based (Domain-Level Scope):** In a service-based architecture, change is isolated to a single, coarse-grained domain service, making it easier to manage.
    ![Figure 3-5: A service-based architecture where a change is contained within a single "Wishlist Service."](figure-3-5.png)

*   **Microservices (Function-Level Scope):** In a microservices architecture, the change is isolated to a single, fine-grained service, offering the highest maintainability.
    ![Figure 3-6: A microservices architecture where a change is contained within a tiny, single-purpose "Wishlist Expiry Service."](figure-3-6.png)

#### 2. Testability

Testability is the ease and completeness of testing.

*   **Impact of Modularity:** Modularity drastically reduces the testing scope. Instead of running thousands of tests for a small change in a monolith, you only need to run the small, targeted test suite for the specific service that was changed.
*   **Warning:** High inter-service communication destroys this benefit. If Service A's functionality requires calls to Service B and C, a change in A now requires testing all three, pushing you back towards monolithic testing complexity.

    ![Figure 3-7: A diagram showing three services. Initially decoupled, testing is isolated. After adding communication between them, the testing scope expands to include all three.](figure-3-7.png)

#### 3. Deployability

Deployability is the ease, frequency, and risk of deployment.

*   **Impact of Modularity:** Breaking an application into smaller, independently deployable units reduces deployment risk and ceremony (e.g., code freezes). This allows for more frequent releases, increasing agility.
*   **Warning (The Distributed Big Ball of Mud):** If your "microservices" must all be deployed together in a specific order, they are not truly modular. As Matt Stine says, *"If your microservices must be deployed as a complete set in a specific order, please put them back in a monolith and save yourself some pain."*

#### 4. Scalability and Elasticity

*   **Scalability:** The ability to handle a gradual increase in user load over time.
*   **Elasticity:** The ability to handle sudden, instantaneous spikes in user load.

    ![Figure 3-8: A graph showing the difference between scalability (a smooth, upward-sloping line) and elasticity (a line with sharp, erratic peaks and valleys).](figure-3-8.png)

*   **Modularity vs. Granularity:** Modularity (breaking the app apart) primarily improves **scalability**. Granularity (making the pieces small) primarily improves **elasticity**, because smaller services have a lower Mean Time To Startup (MTTS) and can be spun up quickly to handle a spike.

    ![Figure 3-9: Star ratings showing that Scalability and Elasticity are low for a Layered architecture, better for Service-Based, and highest for Microservices.](figure-3-9.png)

#### 5. Availability / Fault Tolerance

Fault tolerance is the ability for parts of the system to remain available even when other parts fail.

*   **Impact of Modularity:** In a monolith, an unhandled exception (like an OutOfMemoryError) in one minor feature can bring down the entire application. Modularity isolates the fault to a single service, allowing the rest of the system to continue functioning.
*   **Warning:** This benefit is nullified by synchronous coupling. If the "Search" service makes a synchronous call to the "Recommendations" service, and the Recommendations service fails, the Search feature will also fail for that user. Asynchronous communication is essential for true fault tolerance.
---
## Related Concepts
* [[Architecture]]

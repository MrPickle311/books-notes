Static coupling concerns all the dependencies an architecture quantum needs to simply operate. It's about how things are wired together at a component and infrastructure level. Answering the question, "What is all the wiring required to bootstrap this service from scratch?" reveals its static coupling.

The number of quanta in an architecture is determined by its static coupling points.

*   **Monolithic Architectures (Quantum = 1):** Any architecture that is deployed as a single unit and/or relies on a single database will always have a quantum of one. The shared database is a powerful static coupling point.
    ![Figure 2-2](figure-2-2.png)
*   **Service-Based Architectures (Quantum = 1):** This hybrid style has separately deployed services but still uses a monolithic database, making it a single quantum.
    ![Figure 2-3](figure-2-3.png)
*   **Mediated Event-Driven Architectures (Quantum = 1):** Even if services are distributed, a central mediator (like an orchestrator) and a shared database act as static coupling points, resulting in a single quantum.
    ![Figure 2-4](figure-2-4.png)
*   **Broker Event-Driven Architectures:** A broker-style EDA *can* have a single quantum if all services share a database (Figure 2-5). However, if services have their own data stores and no other static dependencies, the architecture can have multiple quanta (Figure 2-6).
    ![Figure 2-5](figure-2-5.png)
    ![Figure 2-6](figure-2-6.png)
*   **Microservices Architectures:** The goal of microservices is high decoupling, allowing each service to be its own quantum (Figure 2-7). This allows each service to have its own, independent architectural characteristics.
    ![Figure 2-7](figure-2-7.png)
*   **The User Interface as a Coupling Point:** A centrally coupled monolithic UI forces the entire system into a single quantum (Figure 2-8). However, using a **micro-frontends** approach allows each service and its corresponding UI piece to form an independent quantum (Figure 2-9).
    ![Figure 2-8](figure-2-8.png)
    ![Figure 2-9](figure-2-9.png)
*   **Integration Databases:** A shared database used to integrate two otherwise separate systems statically couples them into a single, large architecture quantum.
    ![Figure 2-10](figure-2-10.png)
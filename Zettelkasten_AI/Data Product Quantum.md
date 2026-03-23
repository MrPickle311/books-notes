The core architectural element of the data mesh. The DPQ is built adjacent but coupled to a service, acting as an interface for analytical data.

![Figure 14-1: Structure of a Data Product Quantum](figure-14-1.png)

A DPQ is an operationally independent but highly coupled set of behaviors and data that serves the analytical needs of the system.

##### Types of DPQs:
*   **Source-aligned (native) DPQ:** Provides analytical data on behalf of its collaborating service.
*   **Aggregate DQP:** Aggregates data from multiple input DPQs.
*   **Fit-for-Purpose DPQ:** A custom DPQ built to serve a specific requirement (e.g., ML, BI).

![Figure 14-2: The data product quantum acts as a separate but highly coupled adjunct to a service](figure-14-2.png)

##### Cooperative Quantum
A DPQ acts as a **cooperative quantum** for its service. It is operationally separate and communicates asynchronously with its cooperator, featuring tight contract coupling with the service but looser coupling to the wider analytical plane.

#### Data Mesh, Coupling, and Architecture Quantum

*   **Static Coupling:** The DPQ and its communication mechanism are part of the static coupling of an architecture quantum.
*   **Dynamic Coupling:** The communication between a service and its DPQ should always be asynchronous with eventual consistency (e.g., Parallel Saga or Anthology Saga) to minimize impact on operational characteristics.

#### When to Use Data Mesh

Data Mesh is most suitable for modern distributed architectures like microservices with well-isolated services. It is more difficult in architectures where analytical and operational data must remain in perfect sync.

| Advantage                                                      | Disadvantage                                              |
| -------------------------------------------------------------- | -------------------------------------------------------- |
| Highly suitable for microservices architectures                | Requires contract coordination with data product quantum |
| Follows modern architecture principles and engineering practices | requires asynchronous communication and eventual consistency |
| Allows excellent decoupling between analytical and operational data |                                                          |
| Carefully formed contracts allow loosely-coupled evolution of analytical capabilities |                                                          |

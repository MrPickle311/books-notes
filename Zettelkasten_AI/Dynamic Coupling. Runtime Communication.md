---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: processed
---
Dynamic coupling concerns how architecture quanta interact with each other at runtime to perform a workflow. It is a multi-dimensional problem space.

![Figure 2-13: A 3D graph with axes for Communication (Sync/Async), Consistency (Atomic/Eventual), and Coordination (Orchestrated/Choreographed).](figure-2-13.png)

#### The Three Dimensions of Dynamic Coupling

1.  **Communication:** The synchronicity of the connection.
    *   **Synchronous:** The caller makes a request and blocks, waiting for a response. This creates tight dynamic coupling, temporarily entangling the operational characteristics (performance, availability) of the two quanta.
        ![Figure 2-11: A diagram showing a caller making a request and waiting for a response from the receiver.](figure-2-11.png)
    *   **Asynchronous:** The caller sends a message (e.g., to a queue) and continues its work without waiting. This decouples the quanta, allowing them to operate independently.
        ![Figure 2-12: A diagram showing a caller sending a message to a queue and continuing to process, receiving a reply later via another queue.](figure-2-12.png)

2.  **Consistency:** The transactional integrity required.
    *   **Atomic:** All-or-nothing consistency, where all participants in a workflow must succeed or fail together. This is very difficult in distributed systems.
    *   **Eventual Consistency:** The system will become consistent at some point in the future, but temporary inconsistencies are allowed.

3.  **Coordination:** How the workflow is managed.
    *   **Orchestration:** A central service (the orchestrator) is responsible for coordinating the workflow steps.
    *   **Choreography:** Services coordinate amongst themselves, typically by reacting to each other's events, with no central coordinator.

#### The Matrix of Dynamic Coupling Patterns

The intersections of these three dimensions define a set of fundamental architectural patterns, each with a different level of overall coupling.

| Pattern Name          | Communication | Consistency | Coordination | Coupling  |
| --------------------- | ------------- | ----------- | ------------ | --------- |
| **Epic Saga**         | synchronous   | atomic      | centralized  | Very high |
| **Phone Tag Saga**    | synchronous   | atomic      | distributed  | High      |
| **Fairy Tale Saga**   | synchronous   | eventual    | centralized  | High      |
| **Time Travel Saga**  | synchronous   | eventual    | distributed  | Medium    |
| **Fantasy Fiction Story** | asynchronous  | atomic      | centralized  | High      |
| **Horror Story**      | asynchronous  | atomic      | distributed  | Medium    |
| **Parallel Saga**     | asynchronous  | eventual    | centralized  | Low       |
| **Anthology Saga**    | asynchronous  | eventual    | distributed  | Very low  |
---
## Related Concepts
* [[Architecture]]

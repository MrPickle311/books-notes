---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: processed
---
> **1. Untangle Architectural Problems Before Analyzing Them.** Deconstruct complex problems into their constituent parts. For distributed workflows, those parts are Communication (sync/async), Consistency (atomic/eventual), and Coordination (orchestration/choreography).
>
> **2. Use the Architecture Quantum as Your Unit of Analysis.** Identify the quanta in your system. A quantum defines the scope of architectural characteristics. Everything inside a single quantum shares the same scalability, reliability, and performance profile.
>
> **3. Identify Static Coupling Points to Define Quanta.** The number of quanta in your architecture is determined by your static coupling points. Be ruthless in identifying them, especially shared databases, monolithic UIs, and central mediators.
>
> **4. Understand That Synchronous Calls Create Temporary Entanglement.** When one quantum makes a synchronous call to another, their operational characteristics become temporarily coupled. The performance of the caller is now limited by the performance of the callee.
>
> **5. Use Asynchronous Communication to Preserve Quantum Independence.** Asynchronous messaging acts as a buffer or shock absorber between quanta, allowing them to operate and scale independently, preserving their individual architectural characteristics.
>
> **6. Map Your Workflows to the Dynamic Coupling Matrix.** Use the matrix of eight patterns to understand the inherent coupling level of a proposed design. If you need low coupling, you must move towards asynchronous, eventually consistent, and choreographed solutions.
---
## Related Concepts
* [[Architecture]]

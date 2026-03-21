---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

This is the most common technique, where shared code is packaged into an external artifact (e.g., JAR, DLL) and bound to each service at compile-time.

![Figure 8-3: A diagram showing three services all with a compile-time dependency arrow pointing to a single "Shared Library" artifact.](figure-8-3.png)

#### Granularity Trade-offs

*   **Coarse-Grained (e.g., `SharedStuff.jar`):** This approach uses a single, large library for all shared code.
    *   **Pro:** Simple dependency management (everyone depends on one thing).
    *   **Con:** Terrible change control. A change to any part of the library forces *all* dependent services to re-test and re-deploy, even if the change is irrelevant to them.
    ![Figure 8-4: A diagram showing a single large shared library. A change to one class inside it forces all dependent services to be updated.](figure-8-4.png)
*   **Fine-Grained (e.g., `Security.jar`, `Formatters.jar`):** This approach breaks shared code into smaller, functionally-cohesive libraries.
    *   **Pro:** Excellent change control. A change to the `Calculators.jar` only impacts services that actually use it.
    *   **Con:** Complex dependency management. With hundreds of services and dozens of libraries, the dependency matrix can become a mess.
    ![Figure 8-5: A diagram showing multiple small, fine-grained libraries. A change to one library only impacts the two services that depend on it.](figure-8-5.png)
*   **Advice:** Favor **fine-grained libraries** to optimize for change control over dependency management.

#### Versioning Strategies
Versioning is critical for shared libraries to provide agility and backward compatibility.

*   **Complexities:** Versioning is hard. It requires clear communication about changes and a well-defined deprecation strategy for old versions.
*   **Deprecation:** A global strategy (e.g., "only support 4 versions back") is simple but can cause churn for frequently-changing libraries. A custom, per-library strategy is more flexible but harder to manage.
*   **Advice:** Always use versioning, but avoid depending on the `LATEST` tag, as it can introduce unexpected breaking changes during emergency deployments.

#### Trade-offs for Shared Libraries

| Advantages                               | Disadvantages                                |
| ---------------------------------------- | -------------------------------------------- |
| Ability to version changes (provides agility) | Dependencies can be difficult to manage     |
| Shared code is compile-based, reducing runtime errors | Code duplication in heterogeneous codebases |
| Good agility for shared code changes     | Version deprecation can be difficult         |
|                                          | Version communication can be difficult       |

*   **When to Use:** The best approach for homogeneous environments where the rate of change for shared code is low to moderate.
---
## Related Concepts
* [[Architecture]]

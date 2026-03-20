---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: processed
---
How do architects ensure their designs and principles are actually followed during implementation? The answer is **Architecture Fitness Functions**, which automate architectural governance.

*   **Definition:** "Any mechanism that performs an objective integrity assessment of some architecture characteristic or combination of architecture characteristics."
*   **Key Aspects:**
    *   **Objective:** Fitness functions must test against objective, measurable values (e.g., "response time < 100ms"), not vague goals ("high performance").
    *   **Atomic vs. Holistic:** They can be **atomic** (testing one characteristic, like code cycles) or **holistic** (testing a combination, like how a security change impacts performance).
    *   **Architecture vs. Domain:** Fitness functions validate *architectural* concerns (modularity, layers, performance) and generally don't require domain knowledge. Unit tests validate *domain* logic (e.g., a mailing address format).

#### Fitness Function Examples

1.  **Preventing Component Cycles:** A common goal is to prevent cyclic dependencies between components, which destroys modularity. A fitness function can automate this check.

    ![Figure 1-1: A diagram showing three components with arrows indicating that each component references the others, creating a damaging cycle.](figure-1-1.png)

    ```java
    // Example 1-1: A fitness function using JDepend to detect cycles between packages.
    public class CycleTest {
        private JDepend jdepend;

        @BeforeEach
        void init() {
          jdepend = new JDepend();
          jdepend.addDirectory("/path/to/project/persistence/classes");
          jdepend.addDirectory("/path/to/project/web/classes");
          jdepend.addDirectory("/path/to/project/thirdpartyjars");
        }

        @Test
        void testAllPackages() {
          Collection packages = jdepend.analyze();
          assertEquals("Cycles exist", false, jdepend.containsCycles());
        }
    }
    ```

2.  **Enforcing Layered Architecture:** An architect can define the allowed communication paths between layers and enforce them with a fitness function. ArchUnit is useful library here

    ![Figure 1-2: A diagram of a traditional three-layered architecture: Presentation, Service, and Persistence.](figure-1-2.png)

    ```java
    // Example 1-2: An ArchUnit fitness function to govern layer dependencies in Java.
    layeredArchitecture()
        .layer("Controller").definedBy("..controller..")
        .layer("Service").definedBy("..service..")
        .layer("Persistence").definedBy("..persistence..")

        .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
        .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
        .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service")
    ```
    ```csharp
    // Example 1-3: A NetArchTest fitness function for layer dependencies in .NET.
    // Classes in the presentation should not directly reference repositories
    var result = Types.InCurrentDomain()
        .That()
        .ResideInNamespace("NetArchTest.SampleLibrary.Presentation")
        .ShouldNot()
        .HaveDependencyOn("NetArchTest.SampleLibrary.Data")
        .GetResult()
        .IsSuccessful;
    ```

*   **Real-World Impact (Equifax Breach):** The Equifax data breach was caused by a known vulnerability in the Struts framework. If Equifax had used fitness functions in a continuous deployment pipeline, the security team could have inserted a simple test to check for the vulnerable library version, failing the build on every affected project and providing immediate, enterprise-wide feedback.
---
## Related Concepts
* [[Architecture]]

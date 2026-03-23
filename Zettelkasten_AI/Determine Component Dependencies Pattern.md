
This pattern analyzes the coupling between components to answer three critical migration questions:
1.  Is it feasible to break apart the monolith?
2.  What is the rough level of effort? (A "golfball," "basketball," or "airliner"?)
3.  Will this require a refactor or a full rewrite?

#### Pattern Description

A component dependency is formed when a class in one component interacts with a class in another component. This pattern focuses on these inter-component dependencies, not the internal coupling within a component. Visualizing these dependencies is crucial.

*   **Minimal Dependencies (Golfball):** A diagram with few connections between components indicates a straightforward refactoring effort.
    ![Figure 5-13: A dependency graph with only a few lines connecting the component boxes. Feasible to break apart.](figure-5-13.png)
*   **High Dependencies (Basketball):** A diagram with many connections, especially in certain areas, suggests a much harder effort that will likely require a mix of refactoring and rewriting.
    ![Figure 5-14: A dependency graph with a moderate number of lines, showing a "tangled" area on the left and a cleaner area on the right.](figure-5-14.png)
*   **Too Many Dependencies (Airliner):** A diagram where everything is connected to everything else indicates that a migration is likely not feasible and a total rewrite is necessary.
    ![Figure 5-15: A dependency graph that looks like a complete mesh, with lines connecting almost every component to every other component. Not feasible.](figure-5-15.png)

Analyzing both incoming (afferent, `CA`) and outgoing (efferent, `CE`) coupling helps identify opportunities to refactor and reduce dependencies *before* starting the migration.

#### Fitness Functions for Governance

*   **Fitness Function: No component shall have more than < some number > of total dependencies.**
    This function calculates the total coupling (`CA` + `CE`) for each component and alerts the architect if it exceeds a predefined threshold (e.g., 15), preventing any single component from becoming too entangled.
    ```
    # Example 5-8: Pseudo-code for limiting the total number of dependencies
    # ... (logic to calculate incoming and outgoing dependencies) ...
    IF total_count > 15 {
      send_alert(component, total_count)
    }
    ```

*   **Fitness Function: < some component > should not have a dependency on < another component >.**
    This function enforces specific architectural rules, preventing undesirable coupling between certain components. This is often implemented with tools like ArchUnit.
    ```java
    // Example 5-9: ArchUnit code for governing dependency restrictions
    public void ticket_maintenance_cannot_access_expert_profile() {
       noClasses().that()
       .resideInAPackage("..ss.ticket.maintenance..")
       .should().accessClassesThat()
       .resideInAPackage("..ss.expert.profile..")
       .check(myClasses);
    }
    ```

#### Sysops Squad Saga

Addison generates a dependency diagram for the Sysops Squad application, which initially looks discouragingly complex.

![Figure 5-16: The full dependency diagram for the Sysops Squad application, showing a large number of connections between components.](figure-5-16.png)

However, after filtering out the shared components (`Notification`, `Reporting Shared`, `Ticket Shared`), which would likely become shared libraries, the picture becomes much clearer. The core functional components have minimal dependencies, confirming that the application is a good candidate for migration.

![Figure 5-17: The filtered dependency diagram. With shared components removed, the graph is much cleaner, showing that the core domains are largely independent.](figure-5-17.png)
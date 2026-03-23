
This is the final pattern, where the logically defined component domains are physically extracted from the monolith into separately deployed **domain services**.

#### Pattern Description

This pattern creates a **Service-Based Architecture**, a pragmatic first step towards a more distributed system. In its basic form, this involves a user interface accessing multiple coarse-grained domain services that all still share a single monolithic database.

![Figure 5-20: A diagram of a basic Service-Based Architecture. A "User Interface" block at the top communicates with several "Domain Service" blocks, which in turn all communicate with a single "Monolithic Database" at the bottom.](figure-5-20.png)

The process involves taking a defined domain (e.g., everything under the `ss.reporting` namespace) and moving that code into a new, independent project that is deployed as its own service. This should only be done *after* all the previous patterns have been applied to ensure the domain boundaries are stable.

![Figure 5-21: An illustration showing the "Reporting" domain being physically pulled out of the monolith block and becoming its own separate "Reporting Service" block.](figure-5-21.png)

#### Fitness Functions for Governance

*   **Fitness Function: All components in < some domain service > should start with the same namespace.**
    Once a domain service is created, this fitness function ensures that no code from other domains is accidentally added to it. For example, it verifies that all code in the `Ticket` service resides under the `ss.ticket` namespace.
    ```java
    // Example 5-11: ArchUnit code for governing components within the Ticket domain service
    public void restrict_domain_within_ticket_service() {
       classes().should().resideInAPackage("..ss.ticket..")
       .check(myClasses);
    }
    ```

#### Sysops Squad Saga

The team develops a plan to migrate each of the five defined domains (`Ticketing`, `Reporting`, `Customer`, `Admin`, `Shared`) into its own separately deployed domain service. This staged migration transforms the Sysops Squad application from a monolith into a Service-Based Architecture.

![Figure 5-22: The final architecture diagram. The monolith has been replaced by five separate services (Ticketing, Reporting, Customer, Admin, Shared), all still connected to the single monolithic database.](figure-5-22.png)
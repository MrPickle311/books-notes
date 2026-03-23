
This pattern logically groups related components into coarse-grained **domains**. This is a critical step in preparing to create domain services for a Service-Based Architecture.

#### Pattern Description

A service often contains multiple components. This pattern identifies those groupings. Component domains are physically represented by refactoring the application's namespaces. For example, components like `ss.billing.payment` and `ss.supportcontract` are related to the customer. Their namespaces should be refactored to reflect this, such as `ss.customer.billing.payment` and `ss.customer.supportcontract`. This aligns the codebase structure with the business domains.

![Figure 5-18: A diagram showing how namespace nodes map to domains. `ss.customer` is the domain, `.billing` is the sub-domain, and `.payment` is the component.](figure-5-18.png)

#### Fitness Functions for Governance

*   **Fitness Function: All namespaces under < root namespace node > should be restricted to < list of domains >.**
    This function enforces the defined domain structure, preventing developers from inadvertently creating new, unapproved top-level domains by checking that all component namespaces fall under an allowed domain (e.g., `ss.ticket`, `ss.customer`).
    ```java
    // Example 5-10: ArchUnit code for governing domains within an application
    public void restrict_domains() {
       classes()
         .should().resideInAPackage("..ss.ticket..")
         .orShould().resideInAPackage("..ss.customer..")
         .orShould().resideInAPackage("..ss.admin..")
         .check(myClasses);
    }
    ```

#### Sysops Squad Saga

Addison and Austen work with the product owner to identify five main domains: `Ticketing`, `Reporting`, `Customer`, `Admin`, and `Shared`. They create a diagram to validate that all existing components fit logically within these domains.

![Figure 5-19: A diagram showing five boxes, each representing a domain (Ticketing, Customer, etc.). Inside each box are the components that belong to that domain.](figure-5-19.png)

Addison then creates architecture stories to refactor the namespaces of components like `ss.kb.maintenance` and `ss.billing.payment` to align them with their new domains (e.g., changing them to `ss.ticket.kb.maintenance` and `ss.customer.billing.payment`, respectively).
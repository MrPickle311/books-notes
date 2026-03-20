---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

This pattern ensures that components are clearly defined and not nested within each other, which creates ambiguity. It establishes that a component should only exist as a **leaf-node** in a namespace or directory structure.

#### Pattern Description

If a namespace `ss.survey` contains source files, but there is also a namespace `ss.survey.templates`, a problem arises. `ss.survey` is no longer a component but a **root namespace** (or sub-domain), and the files within it are **orphaned classes** because they don't belong to a leaf-node component.

![Figure 5-6: A diagram illustrating the definitions. `ss.survey.templates` and `ss.ticket.assign` are components (leaf nodes). `ss.survey` and `ss.ticket` are root namespaces because they are extended. The code inside them (marked with 'C') is orphaned.](figure-5-6.png)

To fix this, the hierarchy must be "flattened":
1.  **Flatten Down:** Move the code from the child namespace (`ss.survey.templates`) into the parent (`ss.survey`), making the parent the new leaf-node component.
    ![Figure 5-7: An illustration of flattening down. The code from `.templates` is moved into `.survey`, making `.survey` a single component.](figure-5-7.png)
2.  **Flatten Up:** Move the orphaned classes from the root namespace (`ss.survey`) into new, more specific child components (e.g., `ss.survey.create` and `ss.survey.process`).
    ![Figure 5-8: An illustration of flattening up. The orphaned code in `.survey` is moved into new leaf-node components, `.create` and `.process`.](figure-5-8.png)
3.  **Shared Component:** If the orphaned classes represent shared code for other components in that sub-domain, move them to a new, dedicated shared component (e.g., `ss.survey.shared`).
    ![Figure 5-10: An illustration of moving shared code. The orphaned shared code in `.survey` is moved to a new `ss.survey.shared` component.](figure-5-10.png)

#### Fitness Functions for Governance

*   **Fitness Function: No source code should reside in a root namespace.**
    This function automates the detection of orphaned classes by scanning the directory structure and alerting the architect if any source files are found in a namespace that is not a leaf-node.
    ```
    # Example 5-6: Pseudo-code for finding code in root namespaces
    FOREACH component IN component_list {
      LIST component_node_list = get_nodes(component)
      FOREACH node IN component_node_list {
        IF contains_code(node) AND NOT last_node(component_node_list) {
          send_alert(component)
        }
      }
    }
    ```

#### Sysops Squad Saga

Addison identifies that both the `Ticket` and `Survey` namespaces contain orphaned classes.

![Figure 5-11: A diagram highlighting that the `ss.ticket` and `ss.survey` namespaces contain code, but they are not leaf nodes, meaning they have orphaned classes.](figure-5-11.png)

*   For the complex `Ticket` domain, Addison chooses to **flatten up**, breaking the orphaned code in `ss.ticket` into three new components: `Ticket Shared`, `Ticket Maintenance`, and `Ticket Completion`.
*   For the simpler `Survey` domain, Addison chooses to **flatten down**, moving the code from `ss.survey.templates` into `ss.survey` and deleting the templates component.

![Figure 5-12: The "after" diagram showing the results. The `Survey` component is now a single, flat component. The `Ticket` root namespace is now a sub-domain with no orphaned code, and its functionality is split into several new leaf-node components.](figure-5-12.png)

---
## Related Concepts
* [[Architecture]]

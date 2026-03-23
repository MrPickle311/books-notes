
This pattern is used to identify and consolidate common business logic that may be duplicated across the application.

#### Pattern Description

This helps eliminate redundant services when the monolith is broken apart. For example, if three different components each have their own logic for sending notifications, this common functionality can be gathered into a single `Notification` component. This is distinct from shared *infrastructure* logic (like logging), as it relates to business processing.

Identifying this commonality is often manual but can be aided by looking for:
*   Classes shared across multiple components.
*   Common naming patterns in namespaces (e.g., `ss.ticket.audit`, `ss.billing.audit`).

Consolidating common logic can create a shared service or a shared library. The trade-offs are discussed later in the book. It's crucial to analyze the impact on coupling; sometimes consolidating code can create a new component with an unacceptably high level of incoming dependencies.

#### Fitness Functions for Governance

*   **Fitness Function: Find common names in leaf nodes of component namespace.**
    This function scans component namespaces for common leaf node names (e.g., multiple components ending in `.audit`) and alerts the architect, who can then investigate if this indicates duplicated domain logic.
    ```
    # Example 5-4: Pseudo-code for finding common namespace leaf node names
    # ... (logic to get leaf node of each namespace) ...
    FOREACH component IN component_list {
      leaf_name = get_last_node(component)
      IF leaf_name IN leaf_node_list AND
         leaf_name NOT IN excluded_leaf_node_list {
        ADD component TO common_component_list
      } ELSE {
        ADD leaf_name TO leaf_node_list
      }
    }
    # ... (send alert) ...
    ```

*   **Fitness Function: Find common code across components.**
    This function identifies source files that are used by multiple components, which can be an indicator of shared domain functionality that could be consolidated.
    ```
    # Example 5-5: Pseudo-code for finding common source files between components
    # ... (logic to map source files to components) ...
    FOREACH source_file IN source_file_list {
      SET count TO 0
      FOREACH component,component_source_file_list IN component_source_file_map {
        IF source_file IN component_source_file_list {
          ADD 1 TO count
        }
      }
      IF count > 1 AND source_file NOT IN excluded_source_file_list {
        ADD source_file TO common_source_file_list
      }
    }
    # ... (send alert) ...
    ```

#### Sysops Squad Saga

Addison identifies three separate notification-related components: `Customer Notification`, `Ticket Notify`, and `Survey Notify`.

![Figure 5-4: A diagram showing three separate components—Customer Notification, Ticket Notify, and Survey Notify—all containing similar notification logic.](figure-5-4.png)

After analyzing the coupling impact, Addison confirms that consolidating them will not negatively affect the overall architecture. An architecture story is created, and the three components are refactored into a single, common `Notification` component (`ss.notification`).

![Figure 5-5: A diagram showing the three original notification components being merged into a single new "Notification" component.](figure-5-5.png)

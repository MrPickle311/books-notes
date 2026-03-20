---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

This is the first step in the process. Its purpose is to catalog the application's logical components and ensure they are properly and consistently sized.

#### Pattern Description

Services are built from components, so it's critical that components aren't too large (hard to break apart) or too small (not doing enough). The number of files or lines of code is a poor metric. A better metric is the **total number of statements** within a component's source files.

Key metrics to gather are:
*   **Component Name:** A clear, self-describing name.
*   **Component Namespace:** The physical location (e.g., package or directory) of the component's code.
*   **Percent:** The component's size as a percentage of the total codebase's statements. This helps identify outliers.
*   **Statements:** The sum of all statements in the component's source files.
*   **Files:** The total number of source files. A high statement count with very few files indicates a need for refactoring.

Components should generally fall within one to two standard deviations of the mean component size. Large components should be broken up using functional or domain-driven decomposition.

#### Fitness Functions for Governance

*   **Fitness Function: Maintain component inventory.**
    This function automatically detects when new components are added or removed by scanning the codebase's directory structure and comparing it to a stored list. This keeps the architect aware of structural changes.
    ```
    # Example 5-1: Pseudo-code for maintaining component inventory
    # Get prior component namespaces that are stored in a datastore
    LIST prior_list = read_from_datastore()

    # Walk the directory structure, creating namespaces for each complete path
    LIST current_list = identify_components(root_directory)

    # Send an alert if new or removed components are identified
    LIST added_list = find_added(current_list, prior_list)
    LIST removed_list = find_removed(current_list, prior_list)
    IF added_list NOT EMPTY {
      add_to_datastore(added_list)
      send_alert(added_list)
    }
    IF removed_list NOT EMPTY {
      remove_from_datastore(removed_list)
      send_alert(removed_list)
    }
    ```

*   **Fitness Function: No component shall exceed < some percent > of the overall codebase.**
    This function identifies components that are too large by calculating the percentage of total statements for each component and alerting if it exceeds a defined threshold (e.g., 10%).
    ```
    # Example 5-2: Pseudo-code for maintaining component size based on percent of code
    # Walk the directory structure, creating namespaces for each complete path
    LIST component_list = identify_components(root_directory)

    # Walk through all of the source code to accumulate total statements
    total_statements = accumulate_statements(root_directory)

    # Walk through the source code for each component, accumulating statements
    # and calculating the percentage of code each component represents. Send
    # an alert if greater than 10%
    FOREACH component IN component_list {
      component_statements = accumulate_statements(component)
      percent = component_statements / total_statements
      IF percent > .10 {
        send_alert(component, percent)
      }
    }
    ```

*   **Fitness Function: No component shall exceed < some number of standard deviations > from the mean component size.**
    This function uses statistical analysis to find outlier components based on the number of statements, alerting the architect if a component's size is, for example, more than three standard deviations from the mean.
    \[ s = \sqrt{\frac{1}{N-1} \sum_{i=1}^{N} (x_i - \bar{x})^2} \]
    ```
    # Example 5-3: Pseudo-code for for maintaining component size based on number of standard deviations
    # ... (Calculation of mean and standard deviation) ...
    FOREACH component,size IN component_size_map {
      diff_from_mean = absolute_value(size - mean);
      num_std_devs = diff_from_mean / std_dev
      IF num_std_devs > 3 {
        send_alert(component, num_std_devs)
      }
    }
    ```

#### Sysops Squad Saga

Addison applies this pattern and finds that the `Reporting` component (`ss.reporting`) constitutes **33%** of the entire codebase, making it a significant outlier.

![Figure 5-2: A pie chart showing the relative sizes of components. The "Reporting" component is a massive slice, dwarfing all others.](figure-5-2.png)

The team breaks the single `Reporting` component into four new, more granular components based on functionality: `Reporting Shared`, `Ticket Reports`, `Expert Reports`, and `Financial Reports`. This results in a much more balanced distribution of component sizes.

![Figure 5-3: A pie chart showing the component sizes after refactoring. The single large "Reporting" slice has been replaced by four smaller, more reasonably sized slices.](figure-5-3.png)
---
## Related Concepts
* [[Architecture]]

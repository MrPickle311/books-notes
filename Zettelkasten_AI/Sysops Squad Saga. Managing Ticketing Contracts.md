*   **Problem:** The team needs to define the contract types for the new orchestrated ticketing workflow.
*   **Analysis:**
    *   **Orchestrator to Ticket Services:** The `Ticket Management` and `Ticket Assignment` services are tightly related to the orchestrator's domain. Changes are likely to be coordinated. A **strict contract** is appropriate here.
    *   **Orchestrator to Notification/Survey:** These services are more loosely related and the data changes slowly. A **loose contract** is better to avoid brittle coupling.
    *   **Orchestrator to Mobile App:** The key constraint is that the mobile app is deployed via a public App Store, which can have long and unpredictable approval times. A strict contract would be a disaster, as a backend change could break the app for weeks.
*   **Decision:** The contract with the mobile app must be a **loose, name-value pair contract** to provide maximum flexibility and tolerance for slow deployment cycles.

![Figure 13-9: The final contract design for the ticket management workflow, showing a mix of strict and loose contracts based on context.](figure-13-9.png)

*   **ADR: Loose contract for Sysops Squad expert mobile application**
    *   **Context:** The mobile app deployment cycle through the App Store is slow and unpredictable.
    *   **Decision:** We will use a loose, name-value pair contract for communication between the orchestrator and the mobile application. We will also build an extension mechanism for temporary flexibility.
    *   **Consequences:** More validation logic is required in both the orchestrator and the mobile app. This decision should be revisited if the App Store deployment process becomes faster.

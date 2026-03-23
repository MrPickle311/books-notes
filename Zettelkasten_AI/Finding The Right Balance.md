
Getting granularity right is about analyzing the trade-offs between these opposing forces and collaborating with business stakeholders to make an informed decision.

| Disintegrator Driver   | Reason for Applying Driver                        |
| ---------------------- | ------------------------------------------------- |
| **Service scope**      | Single-purpose services with tight cohesion       |
| **Code volatility**    | Agility (reduced testing scope and deployment risk) |
| **Scalability**        | Lower costs and faster responsiveness             |
| **Fault tolerance**    | Better Overall uptime                             |
| **Security access**    | Better Security access control to certain functions |
| **Extensibility**      | Agility (ease of adding new functionality)        |

| Integrator Driver      | Reason for Applying Driver                 |
| ---------------------- | ------------------------------------------ |
| **Database transactions** | Data integrity and consistency             |
| **Workflow**           | Fault tolerance, performance, and reliability |
| **Shared code**        | Maintainability                            |
| **Data relationships** | Data integrity and correctness             |

#### Trade-off Examples

*   **Example 1 (Agility vs. Consistency):** "We can split the service to isolate frequent changes (better agility), but we'll lose ACID transactions. Which is more important: faster time-to-market or stronger data consistency?"
*   **Example 2 (Security vs. Consistency):** "We can split the service for better security, but we can't guarantee an all-or-nothing registration. Which is more important: better security or stronger data consistency?"
*   **Example 3 (Extensibility vs. Performance):** "We can split the payment service to make it easier to add new payment types, but it will make checkout slower. Which is more important: agility in payments or a faster checkout experience?"

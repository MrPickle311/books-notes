Reliability means "continuing to work correctly, even when things go wrong."

*   **Fault:** A component stops working (e.g., a disk crash).
*   **Failure:** The system as a whole stops providing service to the user.
*   **Fault Tolerance:** The ability to prevent faults from becoming failures.

*   **Single Point of Failure (SPOF):** A component that, if faulty, escalates to cause the failure of the entire system.
*   **Fault Injection:** Deliberately triggering faults (e.g., killing processes) to ensure the system can tolerate them.
*   **Chaos Engineering:** A discipline of experimenting on a system to build confidence in its capability to withstand turbulent conditions (like fault injection).

#### Types of Faults
1.  **Hardware Faults:** Hard disks crashing, RAM errors, power outages. Usually handled by **redundancy** (RAID, dual power supplies, multiple data centers).
2.  **Software Faults:** Bugs, cascading failures, leap second bugs. These are often **correlated** (all nodes fail at once) and harder to prevent. Solutions include process isolation, crashing and restarting, and monitoring.
3.  **Human Errors:** Configuration mistakes are the leading cause of outages. Mitigated by testing, rollbacks, and clear interfaces.

> **Blameless Postmortems:** A culture where incidents are investigated to understand the *systemic* causes rather than blaming individuals. This leads to better long-term reliability.
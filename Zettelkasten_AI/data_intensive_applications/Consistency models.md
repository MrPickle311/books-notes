At a high level, there are two competing philosophies for dealing with replication inconsistencies:

### 1. Eventual Consistency
*   **The Philosophy:** The system makes no attempt to hide the reality of replication. The application developer is fully responsible for handling the chaos, inconsistencies, and write conflicts. 
*   **Where it's used:** Multi-leader and Leaderless replication architectures. 
*   **When to use it:** When high availability is critical, or when applications must work offline (e.g., Local-First software).

### 2. Strong Consistency
*   **The Philosophy:** The database completely hides the messy reality of replication. To the application developer, the database behaves exactly as if it were a single, perfect, fault-free node.
*   **The Cost:** While it makes application development dramatically simpler, ensuring strong consistency incurs heavy performance penalties and can cause total system outages if certain faults occur (faults that an Eventually Consistent system would easily survive).
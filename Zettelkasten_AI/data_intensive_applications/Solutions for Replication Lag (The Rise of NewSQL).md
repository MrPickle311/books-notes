---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
When building an eventually consistent application, developers must actively consider what happens if replication lag balloons to minutes or hours. If an application requires guarantees like Read-Your-Writes, pretending that asynchronous replication is synchronous will eventually lead to catastrophic UX bugs. 

**The Burden on Developers:**
Fixing these replication lag anomalies in application code (e.g., manually timestamping client requests, enforcing leader-routing rules, and handling region-aware device sync) is incredibly complex and error-prone. 

**The NoSQL vs NewSQL Debate:**
*   **The NoSQL Era:** In the early 2010s, the NoSQL movement told everyone that to achieve massive scale, you *had* to abandon ACID transactions and accept Eventual Consistency. Developers were forced to handle these terrible anomalies in their application code.
*   **The NewSQL Movement:** Recently, engineers realized this burden was unfair. Systems dubbed "NewSQL" (like CockroachDB, TiDB, or Spanner) emerged. These databases offer the massive fault tolerance, high availability, and horizontal scalability of distributed NoSQL databases, but implement brilliant consensus algorithms to provide **Strong Consistency and ACID Transactions**. They allow developers to treat the massive distributed cluster exactly like a single, perfectly consistent machine.

Despite NewSQL's power, weaker consistency models (Eventual Consistency) remain highly popular because they offer unparalleled resilience to network partitions and possess lower computational overhead than strict transactional systems.
---
## Related Concepts
* [[Data Intensive Applications]]

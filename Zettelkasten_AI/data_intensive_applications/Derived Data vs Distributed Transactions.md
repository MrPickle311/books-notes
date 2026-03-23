Historically, if an enterprise wanted to keep two different databases perfectly in sync, they used **Distributed Transactions (Two-Phase Commit / XA)**. Today, the modern approach is using **Derived Data (Log-based streaming)**. Let's compare them:

| Feature | Distributed Transactions (XA) | Derived Data (Log-based CDC) |
| :--- | :--- | :--- |
| **Ordering Mechanism** | Uses **Locks** for mutual exclusion. | Uses an **Append-Only Log** for total ordering. |
| **Fault Tolerance** | **Atomic Commit** (all or nothing success). | **Deterministic Retry & Idempotence**. |
| **Consistency Scope** | Guarantees immediate "Read Your Own Writes". | Inherently Asynchronous (Eventual Consistency). |
| **Performance** | Terrible throughput and catastrophic failure modes. | Highly scalable and massively fault-tolerant. |

While Distributed Transactions provide beautiful guarantees (you can immediately read your own writes), their physical performance overhead and vulnerability to partial network failures make them practically unusable at massive scale. Log-based derived data is the clear winner for the future, but architects must learn to build applications that operate safely on top of asynchronous "eventual consistency."

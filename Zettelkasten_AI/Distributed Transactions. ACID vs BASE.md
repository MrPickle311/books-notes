Once ownership is established, transactions spanning multiple services become a key challenge.

#### ACID Transactions
ACID (Atomicity, Consistency, Isolation, Durability) properties define a traditional, single-database transaction. All operations succeed or all fail as a single unit of work.

![Figure 9-11: An illustration of an ACID transaction. An error inserting billing data causes the prior inserts to the profile and contract tables to be rolled back automatically.](architecture_the_hard_parts/figure-9-11.png)

#### BASE Transactions (Distributed)
In a distributed architecture, a business request spanning multiple services is a **distributed transaction** and **cannot be ACID**. Instead, it has **BASE** properties:
*   **B**asic **A**vailability: The system is expected to be available.
*   **S**oft State: The overall state of the business request is in flux and not complete until all services finish.
*   **E**ventual Consistency: Given enough time, all data sources involved in the transaction will become consistent.

![Figure 9-12: An illustration of a distributed transaction. An error in the Billing service does NOT roll back the committed data in the Profile and Contract services, leaving the data in an inconsistent state that must be fixed.](figure-9-12.png)

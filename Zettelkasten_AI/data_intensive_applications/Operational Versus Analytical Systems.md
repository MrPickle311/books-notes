A primary distinction in data systems is between those that serve live user traffic and those used for analysis and reporting.

*   **Operational Systems (OLTP):**
    *   Handle **Online Transaction Processing**.
    *   Serve end-users of applications (web, mobile).
    *   Characterized by **point queries** (looking up a small number of records by key) and frequent, low-latency writes.
    *   Represents the latest state of data.
*   **Analytical Systems (OLAP):**
    *   Handle **Online Analytic Processing**.
    *   Serve internal business analysts and data scientists for decision support.
    *   Characterized by large, complex queries that **aggregate** over a huge number of records.
    *   Represents a history of events over time.

**Table 1-1. Comparing characteristics of operational and analytic systems**

| Property | Operational systems (OLTP) | Analytical systems (OLAP) |
| :--- | :--- | :--- |
| **Main read pattern** | Point queries (fetch individual records by key) | Aggregate over large number of records |
| **Main write pattern** | Create, update, and delete individual records | Bulk import (ETL) or event stream |
| **Human user** | End user of web/mobile application | Internal analyst, for decision support |
| **Machine use example** | Checking if an action is authorized | Detecting fraud/abuse patterns |
| **Type of queries** | Fixed set of queries, predefined by application | Analyst can make arbitrary queries |
| **Data represents** | Latest state of data (current point in time) | History of events that happened over time |
| **Dataset size** | Gigabytes to terabytes | Terabytes to petabytes |
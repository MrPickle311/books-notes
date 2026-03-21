---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 1: Foundations"
status: pending
---
This architecture is designed from the ground up to leverage cloud services.
*   **Key Idea:** Build higher-level services upon lower-level cloud primitives (like object storage).
*   **Separation of Storage and Compute:** A core principle where storage (e.g., Amazon S3) and computation (e.g., EC2 instances) are handled by separate, independently scalable services. This contrasts with traditional architectures where the same machine is responsible for both.
*   **Multitenancy:** Data and computation from several different customers are handled on the same shared hardware by the same service. This enables better resource utilization and easier management but requires careful engineering for isolation.

**Table 1-2. Examples of self-hosted and cloud-native database systems**

| Category | Self-hosted systems | Cloud-native systems |
| :--- | :--- | :--- |
| **Operational/OLTP** | MySQL, PostgreSQL, MongoDB | AWS Aurora, Azure SQL DB Hyperscale, Google Cloud Spanner |
| **Analytical/OLAP** | Teradata, ClickHouse, Spark | Snowflake, Google BigQuery, Azure Synapse Analytics |

#### Operations in the Cloud Era
The role of operations (DBA, Sysadmin) has evolved into **DevOps** and **Site Reliability Engineering (SRE)**, with a greater emphasis on automation, enabling frequent updates, and learning from incidents. In the cloud, capacity planning becomes financial planning, and performance optimization becomes cost optimization.
---
## Related Concepts
* [[Data Intensive Applications]]

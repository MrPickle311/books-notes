---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

This is an evolutionary and iterative process for safely breaking apart a monolithic database.

![Figure 6-17: A flowchart showing the five steps: 1. Analyze and Create Data Domains, 2. Assign Tables to Data Domains, 3. Separate Connections, 4. Move Schemas to Servers, 5. Switch Over.](figure-6-17.png)

#### The Data Domain Concept

A **data domain** is a collection of coupled database artifacts (tables, views, foreign keys) that are related to a particular business domain. The first step is to identify these domains.

![Figure 6-18: An illustration of a soccer ball, where each white hexagon represents a data domain containing a group of related tables. Dotted lines between hexagons represent cross-domain dependencies that must be broken.](figure-6-18.png)

#### The Steps

*   **Step 1: Analyze Database and Create Data Domains.**
    *   **Start State:** All services access all tables in a single shared database.
    *   **Action:** Analyze the existing tables and group them into logical data domains based on business functionality (e.g., Customer, Payment, Ticketing).
    ![Figure 6-20: The "before" state, with multiple services all connected to one large database.](figure-6-20.png)

*   **Step 2: Assign Tables to Data Domains (via Schemas).**
    *   **Action:** Create a new schema in the database for each data domain and move the corresponding tables into it (e.g., `ALTER SCHEMA payment TRANSFER sysops.billing;`). This is a logical, not physical, separation. Cross-schema access still exists.
    ![Figure 6-21: The database now has logical schemas (SchemaA, SchemaB, etc.), with tables moved inside them. Services can still access multiple schemas.](figure-6-21.png)

*   **Step 3: Separate Database Connections to Data Domains.**
    *   **Action:** This is the hardest step. Refactor the application code. Each service must now connect *only* to the schema for the data it owns. If it needs data from another domain, it must make a service call to the owning service. All direct cross-schema database access is eliminated.
    ![Figure 6-22: Services are now tied to a single schema. Service C, needing data from SchemaD, must now call Service D instead of accessing the schema directly.](figure-6-22.png)

*   **Step 4: Move Schemas to Separate Database Servers.**
    *   **Action:** Physically move each schema to its own dedicated database server. This can be done via backup/restore (requiring downtime) or replication (no downtime, more complex).
    ![Figure 6-23: An illustration of replication, where the schemas from the original database are being copied to new, separate database servers.](figure-6-23.png)

*   **Step 5: Switch Over to Independent Database Servers.**
    *   **Action:** Switch the service connections to point to the new, independent database servers. Decommission the old monolithic database. The migration is complete.
    ![Figure 6-24: The final state, with each service (or group of services) connected to its own independent database server.](figure-6-24.png)
---
## Related Concepts
* [[Architecture]]

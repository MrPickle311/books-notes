---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 1: Foundations"
status: pending
---
To avoid impacting operational systems, analytics are run on a separate database called a **data warehouse**.

*   **ETL (Extract-Transform-Load):** The process of getting data from various OLTP systems into the data warehouse. Data is extracted, transformed into an analysis-friendly schema, and loaded.
    *   **Description**: This diagram provides a simplified outline of the ETL process. Data from multiple OLTP databases and application servers is extracted, transformed into a consistent format, and then loaded into a central data warehouse, which is then used by business analysts for reporting and analysis.
    ![Figure 1-1: Simplified outline of ETL into a data warehouse.](figure-1-1.png)

*   **Data Lake:** An evolution of the data warehouse, designed to accommodate the needs of data scientists. It's a centralized repository that holds a copy of data in its **raw file format**, without imposing a rigid schema. This flexibility is better for machine learning and complex data transformations. It follows the **sushi principle**: "raw data is better".

---
## Related Concepts
* [[Data Intensive Applications]]

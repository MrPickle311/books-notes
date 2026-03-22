---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 2: Internals"
status: pending
---
In relational databases, a *virtual view* is just a shortcut for a query—when you query the view, the database expands your query into the underlying SQL on the fly. 

A **materialized view**, however, is an actual *cached copy of the query results* written to disk. If the underlying data changes, the materialized view must be updated, incurring a write overhead but vastly speeding up repetitive read queries.

#### Data Cubes (OLAP Cubes)
A common type of materialized view in data warehouses is the **Data Cube** (or OLAP Cube). It is a grid of aggregates grouped by various dimensions.

*   **How it Works:** Rather than crunching through raw transaction data every time someone asks for "Total Sales", the database precomputes the `SUM` (or count/avg) grouped by key dimensions (e.g. date and product).
*   **Advantage:** Queries that match the cube's dimensions become extremely fast since the data has effectively been precomputed.
*   **Disadvantage:** Cubes lack the flexibility of raw data. If you suddenly need to calculate a percentage based on an attribute that *wasn't* defined as a dimension in the cube, you can't use the cube. For this reason, data warehouses use cubes merely as performance boosters, while still retaining the raw underlying data.

*   **Description:** This figure shows a two-dimensional data cube aggregating data by summing values dynamically across the 'Date' and 'Product' axes, allowing for instant lookups of the intersections.
![Figure 4-10: Two dimensions of a data cube, aggregating data by summing.](data_intensive_applications/figure-4-10.png)
---
## Related Concepts
* [[Data Intensive Applications]]

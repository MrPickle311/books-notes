---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

### Previous Approaches

The split between operational and analytical data is a long-standing problem. As architectures evolved, so did the approaches to handling these different data needs.

#### The Data Warehouse

An early attempt to provide queryable analytical data, the Data Warehouse pattern involves extracting data from various operational sources, transforming it into a unified schema (often a Star Schema), and loading it into a massive, centralized data store for analysis.

##### Characteristics of the Data Warehouse Pattern:
*   **Data Extracted from many sources:** Data is pulled from individual operational databases.
*   **Transformed to single schema:** Data is transformed from various operational formats into a unified, denormalized schema (like a Star Schema) to simplify queries.
*   **Loaded into warehouse:** The transformed data is loaded into the central warehouse.
*   **Analysis done on the warehouse:** Heavy analytical queries are run against the warehouse, isolating operational systems from the load.
*   **Used by data analysts:** Specialized data analysts are required to build reports and business intelligence assets.
*   **BI reports and dashboards:** The output includes reports and dashboards to aid strategic decisions.
*   **SQL-ish interface:** Query tools typically provide a familiar SQL-like language.

##### The Star Schema
A popular dimensional modeling pattern that separates data into quantifiable **facts** (e.g., hourly rate, time to repair) and descriptive **dimensions** (e.g., squad member specialties, store locations). It is purposely denormalized to facilitate simpler, faster queries and aggregations.

##### Failings of the Data Warehouse
*   **Integration brittleness:** Changes to operational schemas require changes to the transformation logic, creating tight coupling.
*   **Extreme partitioning of domain knowledge:** Domain knowledge is lost in the transformation and must be recreated by specialists to build meaningful reports.
*   **Complexity:** A data warehouse is a separate, complex ecosystem that is highly coupled to operational systems.
*   **Limited functionality for intended purpose:** Often failed to deliver business value commensurate with the huge investment required.
*   **Synchronization creates bottlenecks:** The need to synchronize data from many sources creates operational and organizational bottlenecks.
*   **Operational versus analytical contract differences:** Introducing transformation in the ingestion pipeline creates contractual brittleness.

| Advantage                           | Disadvantage                                |
| ----------------------------------- | ------------------------------------------ |
| Centralized consolidation of data   | extreme partitioning of domain knowledge   |
| Dedicated analytics silo provides isolation | integration brittleness                  |
|                                     | Complexity                                 |
|                                     | Limited functionality for intended purpose |

#### The Data Lake

As a reaction to the complexity of the data warehouse, the Data Lake pattern inverts the model from "transform and load" to "load and transform." It keeps the centralized model but stores data in its raw, native format, shifting the burden of transformation to the consumer on an as-needed basis.

##### Characteristics of the Data Lake Pattern:
*   **Data Extracted from many sources:** Data is extracted from operational systems, often with less transformation.
*   **Loaded into the lake:** Data is stored in its "raw" or native form in a central repository (the lake).
*   **Used by data scientists:** Data consumers find the data and perform their own transformations to answer specific questions.

##### Limitations of the Data Lake
*   **Difficulty in discovery of proper assets:** Understanding data relationships is difficult as domain context is lost when data flows into the unstructured lake.
*   **PII and other sensitive data:** Dumping unstructured data risks exposing sensitive information that can be stitched together to violate privacy.
*   **Still technically, not domain partitioned:** Like the data warehouse, the data lake is partitioned by technical capability (ingestion, storage, serving) rather than by business domain, obscuring important domain context.

| Advantage                             | Disadvantage                              |
| ------------------------------------- | ------------------------------------------ |
| Less structured than data warehouse   | Sometimes difficult to understand relationships |
| Less up-front transformation          | Required ad-hoc transformations          |
| Better suited to distributed architectures |                                          |
---
## Related Concepts
* [[Architecture]]

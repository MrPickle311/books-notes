OLAP involves scanning large volumes of records to calculate aggregates (sums, counts, averages) for business intelligence, rather than fetching specific individual user records.

#### Data Warehouses vs. OLTP
While data warehouses often use a SQL interface (making them look like relational databases on the surface), their internals are drastically different and optimized for analytic queries. Some systems attempt hybrid approaches (HTAP), but separating OLTP and OLAP is common.

#### Cloud Data Warehouses
Modern analytics has moved toward **cloud data warehouses** (like Amazon Redshift, Google BigQuery, Snowflake) and **Data Lakes**.
*   **Decoupled Architecture:** They separate compute engines from the storage layer. Data is inherently stored on scalable object storage (like S3 or GCS).
*   **Elasticity:** Storage capacity and compute resources can be scaled completely independently.
*   **Data Lake Evolution (Open Source):** Tools like Apache Hive have evolved. Storage and computation are now typically split into distinct components:
    1. **Query Engine:** (e.g., Trino, Presto) Parses SQL and coordinates distributed execution against data.
    2. **Storage Format:** (e.g., Parquet, ORC) Determines how data bytes are physically structured in object storage.
    3. **Table Format:** (e.g., Apache Iceberg, Delta Lake) Sits on top of the immutable storage files to provide ACID transactions, schema evolution, inserts/deletes, and time travel. 
    4. **Data Catalog:** Acts as the central metadata repository mapping tables to files and providing governance.
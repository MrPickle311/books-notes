Analytics databases use different schemas than operational (OLTP) ones.

*   **Star Schema:** A central **Fact Table** (events, e.g., sales) references many **Dimension Tables** (who, what, where).
    *   **Description**: This diagram shows a classic Star Schema. The central `fact_sales` table contains metrics (quantity, price) and foreign keys. Surrounding it are dimension tables: `dim_date`, `dim_product`, `dim_store`, `dim_customer`, and `dim_promotion`.
    ![Figure 3-5: Example of a star schema for use in a data warehouse.](data_intensive_applications/figure-3-5.png)

*   **Snowflake Schema:** Dimensions are normalized into sub-dimensions (e.g., Product -> Brand -> Manufacturer). Each dimensions is split into several subdomains. Harder for analysts to use.
*   **One Big Table (OBT):** A completely denormalized schema where dimension tables are removed entirely, and all attributes are stored directly in the fact table (essentially pre-computing the joins). While this uses more storage, it can sometimes enable faster queries by avoiding joins altogether.

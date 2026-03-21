---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

#### a. Table Split Technique

Break the single shared table into multiple tables, giving each service ownership over its specific data.
*   **Example:** Split the `Product` table. The `Catalog` service owns the new `Product` table (with static info), and the `Inventory` service owns a new `Inventory` table (with `product_id` and `inv_cnt`).
    ```sql
    -- Example 9-1: DDL source code for splitting up the Product table
    CREATE TABLE Inventory (
      product_id VARCHAR(10),
      inv_cnt INT
    );

    INSERT INTO Inventory VALUES (product_id, inv_cnt)
      AS SELECT product_id, inv_cnt FROM Product;

    COMMIT;

    ALTER TABLE Product DROP COLUMN inv_cnt;
    ```
*   **Result:** This converts joint ownership to single ownership but introduces the need for data synchronization between the services, forcing a trade-off between consistency and availability.

![Figure 9-5: The resulting architecture after a table split. The Catalog service now has to communicate with the Inventory service to keep data consistent when products are added or removed.](figure-9-5.png)

| Advantages                     | Disadvantages                              |
| ------------------------------ | ------------------------------------------ |
| Preserves bounded context      | Tables must be altered and restructured    |
| Single data ownership          | Possible data consistency issues           |
|                                | No ACID transaction between table updates  |
|                                | Data synchronization is difficult          |
|                                | Data replication between tables may occur  |

#### b. Data Domain Technique

The services share ownership of the table by placing it in a common schema or database, forming a broader bounded context.
*   **Result:** This resolves performance and consistency issues by allowing direct database access, but it breaks the principle of a tight bounded context. Changes to the shared schema now require coordination between all owning services, increasing risk and testing scope.

> **Tip:** When choosing the data domain technique, always re-evaluate why separate services are needed since the data is common to each of the services. Justifications might include scalability differences, fault tolerance needs, throughput differences, or isolating code volatility (see Chapter 7).

![Figure 9-6: A diagram showing the Product table in a separate "Data Domain" box, shared by the Catalog and Inventory services.](figure-9-6.png)

| Advantages                        | Disadvantages                                |
| --------------------------------- | -------------------------------------------- |
| Good data access performance      | Data schema changes involve more services    |
| No scalability and throughput issues | Increased testing scope for data schema changes |
| Data remains consistent           | Data ownership governance (write responsibility) |
| No service dependency             | Increased deployment risk for data schema changes |

#### c. Delegate Technique

One service is assigned as the single owner (the "delegate"), and other services must communicate with it to perform updates on their behalf.
*   **Choosing the Delegate:**
    1.  **Primary Domain Priority:** The service that handles most of the primary CRUD operations owns the table (e.g., `Catalog Service` owns `Product` table). This is generally preferred.
    2.  **Operational Characteristics Priority:** The service with higher performance/scalability needs owns the table (e.g., `Inventory Service` owns `Product` table because inventory updates are more frequent).
*   **Result:** This establishes single ownership but creates tight service coupling and introduces performance and fault tolerance issues for the non-owning services, which must now make remote calls for data updates.

![Figure 9-7: Delegation by domain priority. The Inventory service must make a remote call to the Catalog service to update inventory.](figure-9-7.png)
![Figure 9-8: Delegation by operational priority. The Catalog service must make a remote call to the Inventory service to update product info.](figure-9-8.png)

| Advantages                               | Disadvantages                                |
| ---------------------------------------- | -------------------------------------------- |
| Forms single table ownership           | High level of service coupling               |
| Good data schema change control          | Low performance for non-owner writes         |
| Abstracts data structures from other services | No atomic transaction for non-owner writes |
|                                          | Low fault tolerance for non-owner services   |

#### d. Service Consolidation Technique

Combine the services that jointly own the table into a single, more coarse-grained service.
*   **Result:** This moves joint ownership to single ownership and resolves all data dependency and performance issues. However, it creates a larger service, which can negatively impact scalability (all parts must scale together), fault tolerance (all parts fail together), testing scope, and deployment risk.

![Figure 9-9: A diagram showing the Catalog and Inventory services being merged into a single "Product Service" that now has sole ownership of the Product table.](figure-9-9.png)

| Advantages                     | Disadvantages                    |
| ------------------------------ | -------------------------------- |
| Preserves atomic transactions  | More coarse-grained scalability  |
| Good overall performance       | Less fault tolerance             |
|                                | Increased deployment risk        |
|                                | Increased testing scope          |

---
## Related Concepts
* [[Architecture]]

---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

Once data is physically separated, services must be assigned ownership over specific tables. This is crucial for establishing clear bounded contexts.

> **Tip:** The general rule-of-thumb for data ownership is that the service that performs write operations to a table is the owner of that table. However, joint ownership makes this simple rule complex!

![Figure 9-1: A diagram showing three services (Wishlist, Catalog, Inventory) and the tables they perform write operations on. This illustrates single, common, and joint ownership scenarios.](figure-9-1.png)

#### 1. Single Ownership Scenario

This is the simplest case, where only one service writes to a table.
*   **Example:** The `Wishlist` service is the only one that writes to the `Wishlist` table.
*   **Resolution:** The `Wishlist` service becomes the unambiguous owner of the `Wishlist` table, forming a clear bounded context.

![Figure 9-2: A diagram showing the Wishlist table being placed inside the bounded context of the Wishlist service, indicating clear ownership.](figure-9-2.png)

#### 2. Common Ownership Scenario

This occurs when most or all services need to write to the same table.
*   **Example:** The `Wishlist`, `Catalog`, and `Inventory` services all need to write to the `Audit` table.
*   **Resolution:** Create a new, dedicated service (e.g., an `Audit Service`) that becomes the sole owner of the common table. Other services send data to this dedicated service (often asynchronously via a persistent queue) which then performs the write operation. This avoids re-creating a shared database.

![Figure 9-3: A diagram showing a new Audit Service that owns the Audit table. The other services now send asynchronous messages to a queue, which the Audit Service consumes to write to the table.](figure-9-3.png)

#### 3. Joint Ownership Scenario

This common and complex scenario occurs when a few services, typically within the same domain, write to the same table.
*   **Example:** The `Catalog` service inserts and updates product information in the `Product` table, while the `Inventory` service updates the inventory count in the same table.

![Figure 9-4: A diagram focusing on the joint ownership problem, with both the Catalog and Inventory services performing write operations on the same Product table.](figure-9-4.png)

Four techniques can be used to resolve this.
---
## Related Concepts
* [[Architecture]]

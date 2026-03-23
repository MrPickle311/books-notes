
#### Secondary Indexes
While primary key indexes enforce uniqueness, **secondary indexes** allow searching by other columns (e.g., `user_id` in an `orders` table). 
*   Because values in a secondary index are not necessarily unique, they are typically implemented by appending a row identifier to make the entry unique, or by making each value a list of matching row identifiers.
*   Both B-trees and log-structured storage can be used for secondary indexes.

**Storing Values in Secondary Indexes:**
1.  **Clustered Index:** The actual row data is stored directly within the index structure (e.g., MySQL InnoDB's primary key).
2.  **Heap File (Non-Clustered/Reference Index):** The index only stores a reference to the actual row data, which lives in an unordered "heap file" (PostgreSQL does this). This avoids duplicating data when multiple secondary indexes are present.
3.  **Covering Index (Include Columns):** A middle ground. The index stores a copy of some of the table's columns alongside the index key. If a query only needs those columns, the index "covers" the query, avoiding a hit to the heap file.

#### In-Memory Databases
Disks are durable and cheap, but dealing with them adds overhead. As RAM becomes cheaper, keeping datasets entirely in memory becomes feasible, leading to **in-memory databases**.
*   Some are only for caching (e.g., Memcached), while others offer durability via battery-powered RAM, write-ahead logs (WAL), or periodic disk snapshots (e.g., Redis, VoltDB, SingleStore).
*   **Performance:** Counterintuitively, the performance advantage isn't solely because they avoid reading from disk (the OS caches disk files in RAM anyway). They are faster because they avoid the CPU overhead of encoding/serializing data structures into a format suitable for disk storage.
*   **Capabilities:** They can easily provide data structures like sets and priority queues that are hard to implement on disk.
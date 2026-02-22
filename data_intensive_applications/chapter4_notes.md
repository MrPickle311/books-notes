### Chapter 4: Storage and Retrieval - Summary

This chapter explores how databases handle storage and retrieval internally. A database essentially needs to do two things: store data when you give it, and return the data when you ask for it.

The chapter differentiates between **transactional workloads (OLTP)** and **analytical workloads (OLAP)**, and examines two families of storage engines for OLTP: log-structured storage (immutable data files) and update-in-place storage (like B-trees).

---

### Storage and Indexing for OLTP

#### The Simplest Database
Consider a simple key-value store implemented with two Bash functions:
*   `db_set`: Appends a key-value pair to a text file (like CSV).
*   `db_get`: Scans the file and returns the most recent value for a key.

**Performance Characteristics:**
*   **Writes:** Very efficient. Appending to a file (a log) is the simplest and fastest possible write operation. Many real databases use append-only logs internally.
*   **Reads:** Terrible performance for a large number of records. Needs to scan the entire file (O(n) cost).

#### Indexes
To efficiently find the value for a specific key, we need an **index**. An index is an additional data structure derived from the primary data, structured in a way (e.g., sorted) that makes locating data faster.

**The Crucial Trade-off:**
*   Well-chosen indexes speed up read queries.
*   Every index consumes additional disk space.
*   Indexes slow down writes, because the index must be updated every time data is written.
*   Databases generally require manual index selection based on the application's query patterns.

---

### Log-Structured Storage

One way to speed up reads on an append-only log is to keep an **in-memory hash map** where every key is mapped to the byte offset in the file where the most recent value is located. This requires zero disk I/O if the offset is already in the filesystem cache.

*   **Description**: This diagram illustrates a log of key-value pairs stored in an append-only file, indexed by an in-memory hash map that points to the byte offset of each key. 
![Figure 4-1: Storing a log of key-value pairs in a CSV-like format, indexed with an in-memory hash map.](figure-4-1.png)

**Limitations of the Hash Map Approach:**
*   **Memory Bound:** Entire hash table must fit in memory. On-disk hash maps perform poorly (random access I/O, expensive to grow).
*   **Range Queries:** Not efficient (e.g., cannot easily scan keys between `10000` and `19999`).
*   **Rebuilding:** Hash map is not persisted and must be rebuilt on restart.
*   **Disk Space:** Old overwritten log entries take up space without periodic compaction.

#### SSTables (Sorted String Tables)
Instead of relying purely on hash tables, a common optimization is to require the sequence of key-value pairs to be **sorted by key** and ensure each key only appears once. This format is called a **Sorted String Table (SSTable)**.

*   **Description**: This diagram depicts an SSTable containing compressed blocks of key-value pairs sorted by key. A sparse in-memory index stores offsets for the first key of each block, allowing queries to quickly jump to the right block without keeping every key in memory.
![Figure 4-2: An SSTable with a sparse index, allowing queries to jump to the right block.](figure-4-2.png)

**Advantages of SSTables:**
1.  **Sparse Index:** You do not need to keep all keys in memory. You can group records into blocks and keep an index to the start of each block.
2.  **Fast Lookups:** If you look for `handiwork`, and know it sits between `handbag` and `handsome` (which are in the sparse index), you can just jump to `handbag`'s offset and scan a small block.
3.  **Compression:** Blocks of records can be compressed before writing to disk, saving space and reducing I/O bandwidth usage.

#### Constructing and Merging SSTables (LSM-Trees)
While SSTables are great for reading, appending directly to a sorted file is impossible if keys are written in random order. Rewriting the whole file for every insertion is far too expensive.

The solution is a log-structured approach (a hybrid between append-only logs and sorted files), forming the basis of **LSM-Trees (Log-Structured Merge-Trees)**:

1. **Memtable (In-Memory Tree):** When a write comes in, it is added to an in-memory balanced tree structure (like a red-black tree, skip list, or trie). This keeps the incoming keys sorted. This structure is called a **memtable**.
2. **Writing to Disk (SSTable Segment):** When the memtable reaches a size threshold (e.g., a few megabytes), it is written to disk as a new SSTable file. This becomes the most recent segment. While it's writing to disk, new writes go to a new active memtable instance.
3. **Reading:** To find a key, first check the active memtable. If not found, check the most recent on-disk SSTable segment, then the next-older segment, and so on until the oldest segment is reached.
4. **Compaction and Merging:** Periodically, a background process merges segment files and discards overwritten or deleted values (using an algorithm similar to *mergesort*).

*   **Description:** This figure shows the merging of several SSTable segments in the background. The process looks at the first key in each input file, copies the lowest key to the output, and if the same key appears multiple times, it keeps only the most recent value.
![Figure 4-3: Merging several SSTable segments, retaining only the most recent value for each key.](figure-4-3.png)

**Handling Deletions and Crashes:**
*   **Tombstones:** To delete a key, a special deletion marker called a **tombstone** is appended. During the merge/compaction process, the tombstone instructs the database to discard any earlier values for that key.
*   **Write-Ahead Log (WAL):** To prevent data loss if the database crashes before the memtable is flushed to disk, every write is also immediately appended to an unsorted log file. This unsorted log's only purpose is to restore the memtable after a crash. Once the memtable is written as an SSTable, the corresponding section of this unsorted log can be discarded.

**Performance and Design Advantages of Immutable Segments:**
*   **Write-Optimized:** Segment files are written in a single sequential pass, which is significantly faster than random write I/O.
*   **Immutability:** Once written, SSTables are never modified. This greatly simplifies concurrency and crash recovery (if a crash happens during merging, just delete the unfinished output file; no data corruption).
*   **Background Maintenance:** Merging and compaction happen in the background without blocking ongoing reads and writes.

Storage engines based on this principle of merging and compacting sorted files are known as **LSM storage engines**. This architecture powers databases like RocksDB, Cassandra, Scylla, and HBase.

#### Bloom Filters for Fast Rejections
A problem with LSM-trees is that looking up a key that does not exist can be slow, because the database must check the memtable and every single SSTable segment all the way back to the oldest one.

![Figure 4-4: A Bloom filter uses multiple hash functions to check if a key is in the SSTable.](figure-4-4.png)

To optimize this, LSM storage engines include a **Bloom filter**, a space-efficient probabilistic data structure used to check if an element is in a set.
*   **How it Works:** It uses a bit array and multiple hash functions. If you want to check if a key is present, you hash it. If any of the bits corresponding to those hashes are `0`, the key is **definitely not in the SSTable** (true negative). If all bits are `1`, the key is **probably in the SSTable** (possible false positive), so the database will go and read the block.
*   **Result:** It saves many unnecessary disk reads for non-existent keys.

#### Compaction Strategies
Compaction is necessary to clean up deleted/overwritten data and keep the number of SSTables low. There are two common strategies:

1.  **Size-tiered Compaction (e.g., Cassandra default):** Newer and smaller SSTables are merged into older and larger ones. Great for **write-heavy workloads** but can require a lot of temporary disk space for merges and slower for reads (must check more SSTables).
2.  **Leveled Compaction (e.g., RocksDB, LevelDB):** SSTables are organized into "levels" (L0, L1, L2...). Each level is exponentially larger than the previous and contains non-overlapping keyed SSTables (except L0). When a level fills up, files are merged into the next level. This is much better for **read-heavy workloads** and more predictable disk space usage.

---

### Embedded Storage Engines
Many databases are run as separate server processes accessed over a network. However, **embedded databases** are built as a library that runs in the same process as your application code. They access the local disk directly via regular function calls.
*   **Examples:** SQLite, RocksDB, LMDB, DuckDB.
*   **Use Cases:** Local mobile app storage, web browsers, edge devices, or multi-tenant backends where each tenant gets its own isolated embedded DB instance.

---

### B-Trees
While log-structured indexes (LSM-trees) are popular, the **B-tree** remains the most widely used index structure and is the standard for almost all relational databases.

Like SSTables, B-trees keep key-value pairs sorted by key. However, their design philosophy is completely different:
*   **Log-structured (LSM-tree):** Breaks the database into variable-size segments that are written sequentially and are strictly immutable.
*   **Update-in-place (B-tree):** Breaks the database into **fixed-size blocks or pages** (traditionally 4 KiB, though often 8 KiB or 16 KiB now) and reads/writes one page at a time. It updates pages by overwriting them *in-place*.

#### How B-Trees Work
Pages can be identified by a page number, which acts like a pointer on disk. These pointers are used to construct a tree of pages.

*   **Lookups:** You start at the **root page**, which contains keys and references to child pages. You scan the keys to find the boundaries that encompass the key you are looking for, and follow the reference down to the next level. This continues until you reach a **leaf page**, which contains either the inline value or a reference to where the value is stored.

*   **Description:** This figure shows how a B-tree lookup works. Starting from the root, we follow the pointer to the range 200–300, then the pointer for the range 250–270, eventually reaching the leaf node for key 251.
![Figure 4-5: Looking up the key 251 using a B-tree index.](figure-4-5.png)

*   **Branching Factor:** The number of references to child pages in one page is called the branching factor. It is typically several hundred, allowing a B-tree to be very shallow (usually 3-4 levels deep even for huge datasets). $O(\log n)$ depth keeps lookups fast.
*   **Inserts and Splits:** To insert a new key, you find the leaf page whose range encompasses the key. If the page doesn't have enough free space, it is **split** into two half-full pages, and the parent page is updated to point to both new children.

*   **Description:** This figure shows what happens when a B-tree page gets full. To insert key 334, the page covering the range 333–345 is split into two, and the parent page is updated to add the new boundary key (337).
![Figure 4-6: Growing a B-tree by splitting a page on the boundary key 337.](figure-4-6.png)

#### Making B-Trees Reliable (Crash Recovery)
B-trees overwrite pages in-place, which is dangerous: what if the database crashes halfway through writing a split page? It would result in corrupted pointers and orphan pages.

To ensure resilience, B-tree implementations use a **Write-Ahead Log (WAL)** (sometimes called a *redo log* or *journal* in filesystems).
1.  **WAL First:** The WAL is an append-only file. Any modification to the tree must be written and flushed to the WAL *before* the actual tree pages are modified.
2.  **Recovery:** If the database crashes, the WAL is read to restore the B-tree to a consistent state.

#### B-tree Optimizations and Variants
Decades of optimization have produced many variants:
1.  **Copy-on-Write (LMDB):** Instead of overwriting pages and using a WAL, modified pages are written to a *new* location, and new parent pages are created to point to them (useful for concurrency).
2.  **Key Abbreviation:** Not storing entire keys, especially in interior pages, just enough to act as boundaries. This compresses pages and increases the branching factor.
3.  **Leaf Page Layout:** Trying to lay out the tree so that leaf pages appear sequentially on disk, optimizing large scan queries (hard to maintain as the tree grows).
4.  **Sibling Pointers:** Adding pointers to sibling pages (left and right) in leaf nodes, allowing sequential range scans without jumping back up to the parent page (sometimes called a B+ Tree).

---

### Comparing B-Trees and LSM-Trees
As a general rule of thumb: **LSM-trees are typically faster for writes, whereas B-trees are thought to be faster for reads.** However, this depends heavily on the specific workload. 

#### 1. Read Performance and Range Queries
*   **B-Trees (Reads):** Generally faster and have more predictable latency. You typically only need to follow a few pointers to read a single page at each level.
*   **LSM-Trees (Reads):** Can be slower because the engine must check the memtable and potentially several SSTable segments at different stages of compaction. (Bloom filters help mitigate this).
*   **Range Queries:** Easy and fast on B-trees due to their inherent sorting and traversal pointers. On LSM-trees, range queries require scanning and combining results from all SSTable segments in parallel, making them more expensive.

#### 2. Write Performance: Sequential vs. Random Writes
*   **B-Trees (Random Writes):** B-trees update pages in place. If keys are scattered, the disk writes will also be scattered across the disk, leading to random I/O.
*   **LSM-Trees (Sequential Writes):** LSM-trees strictly append to logs and write out large, contiguous SSTable segments. They turn random incoming writes into **sequential writes** on disk.
*   *Note on SSDs:* While SSDs don't have mechanical seek times like HDDs, they still perform sequential writes much faster than random writes because of how their internal garbage collection works. Random writes on SSDs result in higher wear and tear and slower performance due to moving valid data around before erasing blocks.

#### 3. Write Amplification
**Write amplification** is the ratio of actual bytes written to the disk versus the bytes the application requested to write. A high ratio means you hit the disk bandwidth bottleneck faster.

*   **LSM-Trees:** Suffer from write amplification because data must be rewritten multiple times during background compaction. However, they only write sequential data and use efficient compression. Latency spikes can occur if the write rate exceeds the background compaction rate.
*   **B-Trees:** Also suffer from write amplification because every write must first go to the WAL, and then to the tree page. Further, if a page is dirtied, the *entire page* (e.g., 4 KiB or 16 KiB) often must be rewritten to disk, even if only a few bytes changed.
*   **Conclusion:** For write-heavy workloads, LSM-trees generally have lower write amplification overall and can handle higher write throughput on the same hardware.

#### 4. Disk Space Usage and Fragmentation
*   **B-Trees (Fragmentation):** Suffer from fragmentation. When pages are split or data is deleted, empty space is left inside pages that cannot easily be returned to the OS. 
*   **LSM-Trees (More Compact):** Do not suffer from internal fragmentation. Background compaction continually re-packs data into tight files. Due to linear block writing, SSTables often achieve better compression ratios than B-tree pages.
*   **Snapshots:** LSM-Trees make taking point-in-time snapshots very easy—just flush the memtable and keep references to the immutable SSTable files. B-Trees make point-in-time snapshots more complex.

---

### Other Indexing Considerations

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

---

### Data Storage for Analytics (OLAP)

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

---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 2: Internals"
status: pending
---
#### SSTables (Sorted String Tables)
Instead of relying purely on hash tables, a common optimization is to require the sequence of key-value pairs to be **sorted by key** and ensure each key only appears once. This format is called a **Sorted String Table (SSTable)**.

*   **Description**: This diagram depicts an SSTable containing compressed blocks of key-value pairs sorted by key. A sparse in-memory index stores offsets for the first key of each block, allowing queries to quickly jump to the right block without keeping every key in memory.
![Figure 4-2: An SSTable with a sparse index, allowing queries to jump to the right block.](data_intensive_applications/figure-4-2.png)

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
![Figure 4-3: Merging several SSTable segments, retaining only the most recent value for each key.](data_intensive_applications/figure-4-3.png)

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

![Figure 4-4: A Bloom filter uses multiple hash functions to check if a key is in the SSTable.](data_intensive_applications/figure-4-4.png)

To optimize this, LSM storage engines include a **Bloom filter**, a space-efficient probabilistic data structure used to check if an element is in a set.
*   **How it Works:** It uses a bit array and multiple hash functions. If you want to check if a key is present, you hash it. If any of the bits corresponding to those hashes are `0`, the key is **definitely not in the SSTable** (true negative). If all bits are `1`, the key is **probably in the SSTable** (possible false positive), so the database will go and read the block.
*   **Result:** It saves many unnecessary disk reads for non-existent keys.

#### Compaction Strategies
Compaction is necessary to clean up deleted/overwritten data and keep the number of SSTables low. There are two common strategies:

1.  **Size-tiered Compaction (e.g., Cassandra default):** Newer and smaller SSTables are merged into older and larger ones. Great for **write-heavy workloads** but can require a lot of temporary disk space for merges and slower for reads (must check more SSTables).
2.  **Leveled Compaction (e.g., RocksDB, LevelDB):** SSTables are organized into "levels" (L0, L1, L2...). Each level is exponentially larger than the previous and contains non-overlapping keyed SSTables (except L0). When a level fills up, files are merged into the next level. This is much better for **read-heavy workloads** and more predictable disk space usage.
---
## Related Concepts
* [[Data Intensive Applications]]

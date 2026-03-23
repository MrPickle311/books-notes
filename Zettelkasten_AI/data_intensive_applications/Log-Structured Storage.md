One way to speed up reads on an append-only log is to keep an **in-memory hash map** where every key is mapped to the byte offset in the file where the most recent value is located. This requires zero disk I/O if the offset is already in the filesystem cache.

*   **Description**: This diagram illustrates a log of key-value pairs stored in an append-only file, indexed by an in-memory hash map that points to the byte offset of each key. 
![Figure 4-1: Storing a log of key-value pairs in a CSV-like format, indexed with an in-memory hash map.](data_intensive_applications/figure-4-1.png)

**Limitations of the Hash Map Approach:**
*   **Memory Bound:** Entire hash table must fit in memory. On-disk hash maps perform poorly (random access I/O, expensive to grow).
*   **Range Queries:** Not efficient (e.g., cannot easily scan keys between `10000` and `19999`).
*   **Rebuilding:** Hash map is not persisted and must be rebuilt on restart.
*   **Disk Space:** Old overwritten log entries take up space without periodic compaction.
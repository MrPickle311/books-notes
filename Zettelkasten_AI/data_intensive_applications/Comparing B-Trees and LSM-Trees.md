---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 2: Internals"
status: pending
---
s a general rule of thumb: **LSM-trees are typically faster for writes, whereas B-trees are thought to be faster for reads.** However, this depends heavily on the specific workload. 

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
## Related Concepts
* [[Data Intensive Applications]]

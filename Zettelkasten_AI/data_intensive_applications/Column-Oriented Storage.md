---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 2: Internals"
status: pending
---
In analytics, **fact tables** are often very wide (e.g., over 100 columns), but a typical data warehouse query (like calculating the sum of quantities sold) accesses only a small handful of them at a time (e.g., 3-5 columns). `SELECT *` is rarely used.

**The Problem with Row-Oriented Storage:**
*   Most OLTP databases (and Document databases) are **row-oriented**: they store all values from one row contiguously on disk.
*   To answer an analytical query referencing 3 columns, a row-oriented database still has to load all 100+ columns from disk into memory, parse them, and filter them. This is very slow and wastes disk bandwidth.

**The Columnar Solution:**
*   **Column-oriented (columnar) storage** stores all the values for *each column* together in separate files or blocks.
*   A query only needs to load and read the specific columns it actually uses, saving a massive amount of disk I/O.
*   To reconstruct a row, the database relies on the fact that every column stores the rows in the exact same order (e.g., the 23rd entry in the `date_key` column belongs to the same record as the 23rd entry in the `quantity` column).

*   **Description:** This figure contrasts row-oriented vs. column-oriented storage physically. Instead of storing entire rows together, it shows separate blocks holding just the `date_key`, just the `product_sk`, etc.
![Figure 4-7: Storing relational data by column, rather than by row.](figure-4-7.png)

Columnar storage powers data warehouses (Snowflake, BigQuery), embedded DBs (DuckDB), and storage formats (Parquet, ORC, Apache Arrow). *(Note: Do not confuse this with "wide-column" databases like Cassandra or Bigtable, which are actually row-oriented under the hood).*

#### Column Compression
Another massive benefit of columnar storage is that it lends itself beautifully to compression. Because a single column contains values of the exact same data type (often highly repetitive), compression algorithms work extremely well.

**Bitmap Encoding:**
A common and highly effective technique in data warehouses is **bitmap encoding**.
*   If a column has a small number of distinct values compared to the number of rows (e.g., 100,000 products vs. billions of sales), the database can create a separate bitmap for each distinct value.
*   Each bitmap contains one bit per row: `1` if the row has that value, `0` if it does not.

*   **Description:** This figure shows how bitmap encoding works on a repetitive column. It maps the distinct values to their own bit arrays, which can then be tightly run-length encoded.
![Figure 4-8: Compressed, bitmap-indexed storage of a single column.](figure-4-8.png)

**Run-length Encoding:**
Because these bitmaps will contain mostly zeros, they are considered *sparse* and can be further compressed using **run-length encoding** (e.g., storing "15 zeros, then a 1, then 30 zeros"). Techniques like roaring bitmaps switch representations automatically to keep it as compact as possible.

**Speeding up Queries with Bitmaps:**
Bitmap indexes make evaluating `WHERE` clauses incredibly fast using vector/bitwise operations directly on the CPU:
*   `WHERE product_sk IN (31, 68)` translates to a bitwise **OR** operation between the bitmaps for 31 and 68.
*   `WHERE product_sk = 30 AND store_sk = 3` translates to a bitwise **AND** operation between the respective bitmaps for those two columns.

#### Sort Order in Column Storage
In a column store, it is crucial that the kth item in one column belongs to the same row as the kth item in another column. Therefore, **we cannot sort each column independently**. The data must be sorted an entire row at a time.

*   **Primary Sort Key:** The database administrator can choose columns to sort by, optimizing for common queries. For example, sorting by `date_key` first makes queries targeting date ranges extremely fast (scanning only the relevant contiguous block).
*   **Secondary Sort Key:** A second column (e.g., `product_sk`) can dictate the sort order of rows that share the same primary sort key. 

**Sorting Boosts Compression:**
Sorting brings an enormous secondary benefit: it dramatically improves compression. 
*   If the table is sorted by `date_key`, there will be massive contiguous stretches of rows with the exact same date. 
*   A simple run-length encoding can compress billions of rows down to a few kilobytes for that sorted column. The compression effect is strongest on the first sort key and diminishes for subsequent sort keys as values become more "jumbled up".

#### Writing to Column-Oriented Storage
Column-oriented storage, compression, and sorting are optimized for reads (analytics). 
*   **The Write Problem:** Inserting a single record into the middle of a sorted, compressed columnar table is disastrous—you would have to rewrite every massive, compressed column file from the insertion point to the end.
*   **The Solution (Bulk & LSM):** Writes in data warehouses are typically executed as bulk imports (ETL processes). 
*   To handle this, analytics databases often borrow the **log-structured approach (LSM-trees)**:
    1.  New writes go into an **in-memory, row-oriented store** where they are immediately sorted.
    2.  When the in-memory store fills up, the data is dumped in bulk to disk as compressed columnar files (often to object storage).
    3.  Queries must merge data from the on-disk columnar files and the in-memory recent writes, but the query engine handles this transparently for the user.
---
## Related Concepts
* [[Data Intensive Applications]]

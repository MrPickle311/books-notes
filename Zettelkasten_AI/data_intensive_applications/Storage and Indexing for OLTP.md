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
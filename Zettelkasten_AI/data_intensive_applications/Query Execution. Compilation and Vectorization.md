Analyzing millions of rows means CPU time becomes a major bottleneck, not just reading from disk. 
A naive query executor acts like an interpreter: it iterates over each row one by one, checking conditions iteratively. This is far too slow for analytics. Two alternative approaches make execution much faster:

#### 1. Query Compilation (JIT)
*   The query engine takes a SQL query and dynamically generates efficient machine code specifically for that exact query (often using LLVM).
*   It operates like a Just-In-Time (JIT) compiler. The generated code has tight loops that directly evaluate conditions and copy values to an output buffer, avoiding the overhead of interpreting abstract operators row by row.

#### 2. Vectorized Processing
*   Instead of compiling the query, the query relies on an "interpreter" but makes it fast by processing a **batch of values (a vector) at a time** rather than one row at a time.
*   For example, an equality operator takes an *entire column* and a target value (e.g. "bananas"), and returns an entire bitmap of matches.

*   **Description:** This figure shows how a bitwise AND between two bitmaps maps perfectly to vectorized execution, enabling high-speed processing without branching.
![Figure 4-9: A bitwise AND between two bitmaps lends itself to vectorization.](data_intensive_applications/figure-4-9.png)

*   **Advantages of both approaches:** Both optimize heavily for modern CPUs by utilizing SIMD (Single-Instruction-Multi-Data) instructions, operating directly on compressed data, staying within tight CPU inner loops (avoiding branch mispredictions), and preferencing continuous memory accesses.
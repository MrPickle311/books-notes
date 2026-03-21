---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
To scale, systems use various tricks to distribute ID generation, but they all sacrifice ordering guarantees:

1.  **Sharded ID Assignment:** Node A generates only even numbers (2, 4, 6), Node B generates only odd numbers (1, 3, 5).
    *   *The Flaw:* If you see a row with ID `16` and a row with ID `17`, you don't actually know which one was created first. Node B might just be processing requests faster than Node A.
2.  **Preallocated Blocks:** Node A grabs the block [1 - 1,000]. Node B grabs the block [1,001 - 2,000]. They hand them out locally.
    *   *The Flaw:* A message generated today by Node A might get ID `500`. A message generated *yesterday* by Node B might have the ID `1,002`. Order is meaningless.
3.  **Random UUIDs (v4):** Every node randomly generates a 128-bit string locally. The probability of collision is functionally zero.
    *   *The Flaw:* The output is completely random. There is zero chronological ordering.
4.  **Wall-clock Timestamps (Snowflake, UUID v7, ULID):** The nodes grab the current time from an NTP server and place it in the first half of the ID, filling the second half with random bytes or machine IDs to avoid collisions. 
    *   *The Flaw:* Wall clocks drift and jump backwards (Chapter 9). If Node A has a slightly fast clock and Node B has a slightly slow clock, their timestamps will mathematically lie about the order of events.

All of these distributed methods can guarantee **Uniqueness**, but they completely fail to guarantee **Causal Ordering**.
---
## Related Concepts
* [[Data Intensive Applications]]

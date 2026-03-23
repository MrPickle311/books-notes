hile log-structured indexes (LSM-trees) are popular, the **B-tree** remains the most widely used index structure and is the standard for almost all relational databases.

Like SSTables, B-trees keep key-value pairs sorted by key. However, their design philosophy is completely different:
*   **Log-structured (LSM-tree):** Breaks the database into variable-size segments that are written sequentially and are strictly immutable.
*   **Update-in-place (B-tree):** Breaks the database into **fixed-size blocks or pages** (traditionally 4 KiB, though often 8 KiB or 16 KiB now) and reads/writes one page at a time. It updates pages by overwriting them *in-place*.

#### How B-Trees Work
Pages can be identified by a page number, which acts like a pointer on disk. These pointers are used to construct a tree of pages.

*   **Lookups:** You start at the **root page**, which contains keys and references to child pages. You scan the keys to find the boundaries that encompass the key you are looking for, and follow the reference down to the next level. This continues until you reach a **leaf page**, which contains either the inline value or a reference to where the value is stored.

*   **Description:** This figure shows how a B-tree lookup works. Starting from the root, we follow the pointer to the range 200–300, then the pointer for the range 250–270, eventually reaching the leaf node for key 251.
![Figure 4-5: Looking up the key 251 using a B-tree index.](data_intensive_applications/figure-4-5.png)

*   **Branching Factor:** The number of references to child pages in one page is called the branching factor. It is typically several hundred, allowing a B-tree to be very shallow (usually 3-4 levels deep even for huge datasets). $O(\log n)$ depth keeps lookups fast.
*   **Inserts and Splits:** To insert a new key, you find the leaf page whose range encompasses the key. If the page doesn't have enough free space, it is **split** into two half-full pages, and the parent page is updated to point to both new children.

*   **Description:** This figure shows what happens when a B-tree page gets full. To insert key 334, the page covering the range 333–345 is split into two, and the parent page is updated to add the new boundary key (337).
![Figure 4-6: Growing a B-tree by splitting a page on the boundary key 337.](data_intensive_applications/figure-4-6.png)

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
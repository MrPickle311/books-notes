---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 2: Internals"
status: pending
---
tandard B-trees and LSM-trees natively support range queries over a single attribute. For example, finding all users whose name starts with an "L". However, they are insufficient for more complex queries.

**Concatenated Indexes vs. Multidimensional Indexes**
The core difference lies in how they process multi-column data and how rigidly they require you to format your queries.

*   **Concatenated Indexes (The "Phone Book" Approach):** Takes the values of multiple columns and literally sticks them together in a strict, predefined order (e.g., `lastname` + `firstname`, yielding `SmithJohn`).
    *   *Strength:* Incredibly fast if you search using the *exact order* of the index, or just the *prefix* (e.g., find all people whose last name is "Smith").
    *   *Flaw (Order Dependency):* Completely useless if you want to search skipping the first column (e.g., find everyone whose first name is "John", regardless of last name). It also struggles to do optimal range queries on *both* metrics at exactly the same time.

*   **Multidimensional Indexes (The "Coordinate Plane" Approach):** Treats your columns not as a single string, but as independent axes (dimensions) in space.
    *   *Strength:* Allows you to query multiple variables simultaneously *without* worrying about their order. You can ask for dynamic ranges across both dimensions at once.
    *   *Use Case:* Mandatory for things like geospatial data (e.g. `latitude BETWEEN 10 AND 20` AND `longitude BETWEEN 50 AND 60`) or multidimensional combinations like searching for all weather observations in the year 2013 where the temperature was exactly between 25 and 30℃.

#### R-Trees (Region Trees)
Because standard 1D B-trees fail when trying to sort data that has simultaneous horizontal and vertical properties, databases use specialized spatial indexes like **R-trees** (Region Trees).
*   **Bounding Boxes:** R-Trees solve this by dividing multidimensional space into **bounding boxes** (or rectangles/regions). It groups nearby data points together and draws a minimal bounding box around them. It then groups those boxes into even larger parent boxes, building a tree structure.
*   **Execution:** When you run a query like "find all coffee shops in this map square," the database starts at the top of the R-Tree and checks the largest bounding boxes. If a box does not overlap with your query square, the R-tree instantly ignores everything inside it, only descending into boxes that intersect with your search area.
*   **Adoption:** R-trees are the standard underlying data structure for advanced spatial database extensions, most notably **PostGIS** for PostgreSQL.
---
## Related Concepts
* [[Data Intensive Applications]]

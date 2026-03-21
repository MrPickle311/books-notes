---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 1: Foundations"
status: pending
---
*   **Relational Model (SQL):** Data is organized into relations (tables) of tuples (rows). Dominant since the 1980s. Best for highly structured data with regular schemas.
*   **Document Model (NoSQL):**
    *   **Best Use Case:** Data with a document-like structure (tree of one-to-many relationships) where the **entire tree is loaded at once**.
    *   **Pros:** Better **locality** (fetch entire profile in one query), conceptually simpler for tree structures. Supports item **ordering** natively via arrays (e.g., reorderable to-do lists), whereas relational DBs require complex workarounds like renumbering or fractional indexing.
    *   **Cons:** Harder to refer to nested items directly (must say "2nd item in list" vs. direct ID). If you need to access nested items individually, relational is often better.

#### Data Locality
*   **Document Model:** Documents are stored as a single continuous string (JSON, XML, BSON).
    *   **Advantage:** If you need the *entire* document (e.g., to render a profile page), it requires only one disk seek. Splitting data across relational tables requires multiple index lookups/seeks.
    *   **Disadvantage:** The database must load the *entire* document even to access a small field. This is wasteful for large documents. Updates usually require rewriting the entire document.
    *   **Recommendation:** Keep documents fairly small and avoid frequent small updates.
*   **Relational Model:** Can also achieve locality.
    *   **Google Spanner:** Allows rows to be **interleaved** (nested) within a parent table.
    *   **Oracle:** Uses **multi-table index cluster tables** to store related rows from different tables together.
    *   **Column Families:** (Bigtable, HBase) also group related data for locality.

#### The Object-Relational Mismatch
*   **Impedance Mismatch:** The friction between object-oriented code and relational tables.
*   **Shredding:** The relational technique of splitting a document-like structure into multiple tables (e.g., `positions`, `education`). This can lead to cumbersome schemas and complex application code.
*   **ORMs (Object-Relational Mapping):** Frameworks like Hibernate reduce boilerplate but can't fully hide the mismatch. They often lead to inefficient queries (e.g., the **N+1 query problem**).

#### One-to-Many Relationships
Consider a LinkedIn profile. A user has *many* jobs, *many* schools, etc.

**Relational Approach:**
*   **Description**: This diagram shows a relational schema for a LinkedIn profile. It normalizes data by separating `users`, `positions`, `education`, and `contact_info` into distinct tables. Foreign keys (like `user_id`) link the tables together.
![Figure 3-1: Representing a LinkedIn profile using a relational schema.](data_intensive_applications/figure-3-1.png)

**Document Approach (JSON):**
*   Stores the entire profile as a single JSON document.
*   **Pros:** Better **locality** (fetch entire profile in one query), conceptually simpler for tree structures.
*   **Cons:** Harder to refer to nested items directly.

*   **Description**: This diagram illustrates how one-to-many relationships naturally form a tree structure. The root "User" branches out into multiple "Positions", "Education" history, and "Contact Info" nodes, matching the structure of a JSON document.
![Figure 3-2: One-to-many relationships forming a tree structure.](data_intensive_applications/figure-3-2.png)

#### Many-to-One and Many-to-Many Relationships
*   **One-to-Many:** one résumé has several positions, but each position belongs only to one résumé
*   **Many-to-One:** many people live in the same region, but we assume that each person lives in only one region at any one time
*   **Many-to-Many:** A person can work at many companies; a company has many employees.

**Relational Approach:** Uses a join table (associative table).
*   **Normalized Representation:** The relationship is stored in only one place (the join table).
*   **Indexing:** To query efficiently in both directions (e.g., "find all employees of Company X" AND "find all companies Employee Y worked for"), **secondary indexes** are created on both the `user_id` and `org_id` columns of the `positions` table.

*   **Description**: This diagram shows a many-to-many relationship in a relational database. A `positions` table acts as a join table, linking `users` to `organizations` via foreign keys (`user_id`, `org_id`).
![Figure 3-3: Many-to-many relationships in the relational model.](data_intensive_applications/figure-3-3.png)

**Document Approach:** References other documents by ID (similar to foreign keys). The application must run multiple queries to "join" them.
*   **Indexing in Documents:** In the document model (like the JSON example below), the database needs to index the `org_id` field inside the `positions` array. Most modern document databases (and relational DBs with JSON support) can create indexes on values nested inside documents or arrays.

*   **Description**: This diagram shows how the document model handles many-to-many relationships. The main document contains the user's details, but for organizations and schools, it stores references (IDs) to separate organization/school documents, rather than embedding the full data.
![Figure 3-4: Many-to-many relationships in the document model.](data_intensive_applications/figure-3-4.png)


```json
{
    "user_id": 251,
    "first_name": "Barack",
    "last_name": "Obama",
    "positions": [
        {"start": 2009, "end": 2017, "job_title": "President", "org_id": 513},
        {"start": 2005, "end": 2008, "job_title": "US Senator (D-IL)", "org_id": 514}
    ],
    ...
}
```
So entries in `positions` field may be duplicated across `organization` and `user` documents. It may lead to incosisteny. 
---
## Related Concepts
* [[Data Intensive Applications]]

# Chapter 4: Table and Index Mistakes

---

## 4. Table and Index Mistakes

This chapter covers:
*   Table inheritance, an unusual feature.
*   Why partitioning is important and how to get it right.
*   Using the right type of key and index for your tables.

Some PostgreSQL features enable powerful designs but can become pitfalls if misunderstood.

### 4.1 Table Inheritance
Table Inheritance is a legacy feature inspired by Object-Oriented Programming (OOP). It allows one table to "inherit" columns from another.

**The Concept**:
*   `CREATE TABLE meetings (...) INHERITS (events);`
*   `meetings` has all its own columns **plus** all columns from `events`.
*   Querying `events` returns data from both `events` and `meetings`.

**The Trap**:
1.  **Fundamental Incompatibility**: Inheritance is **incompatible** with modern Declarative Partitioning (`PARTITION BY`).
    *   If you start with inheritance, you cannot easily convert to partitioning later when your table grows.
    *   Error: `cannot attach inheritance parent as partition`.
2.  **Complexity**: It creates hidden dependencies that complicate Schema changes (DDL) and ORM integration.

**Real-world advice**:
*   **Don't use Table Inheritance** for new projects. It is effectively obsolete.
*   **Use Declarative Partitioning** (Section 4.2) if you need to split data.
*   **Use Foreign Keys** if you need relational modeling.

**Migrating Away (The Fix)**:
If you are stuck with inheritance, convert it to a standard Foreign Key relationship.
1.  Create a standard table (`new_meetings`).
2.  Add a FK column (`event_id`).
3.  Migrate data.
4.  Swap tables.

> **Takeaway**: Table Inheritance is a relic of the "Object-Relational" 90s era. Avoid it. Use standard Foreign Keys or modern Partitioning instead.

### 4.2 Neglecting Table Partitioning
Partitioning splits a large logical table into smaller, physical tables (partitions). Neglecting this for massive tables leads to unmanageable performance and maintenance headaches.

#### The Problem: The 150 Million Row Scan
Imagine a `payments` table with 150 million rows (9GB).
*   **Query**: `SELECT * FROM payments WHERE tstamp = '...'`
*   **Result**: 60 seconds (Seq Scan reading 1.1M buffers).
*   **Delete**: `DELETE FROM payments WHERE tstamp < '2021-10-01'`
    *   **Time**: 65 seconds (Massive I/O, potential locks, bloat).

#### The Solution: Declarative Partitioning
Splitting the table by **Range** (e.g., Monthly) drastically reduces the work.

**Setup**:
```sql
CREATE TABLE payments_p (
    ...
) PARTITION BY RANGE (tstamp);

-- Create partitions for each month
CREATE TABLE payments_p_2022_03 PARTITION OF payments_p 
FOR VALUES FROM ('2022-03-01') TO ('2022-04-01');
```

**Automating Partition Creation**:
Managing partitions manually is tedious. Here is a script to create monthly partitions for the past 28 months:

```sql
DO $$
BEGIN
   FOR i IN 0..28 LOOP
      EXECUTE format(
         'CREATE TABLE erp.payments_p_%s PARTITION OF erp.payments_p 
          FOR VALUES FROM (''%s'') TO (''%s'')',
         -- Name format: payments_p_YYYY_M
         to_char(date_trunc('month', now() - (i * interval '1 month')), 'YYYY_MM'),
         -- Start Date
         date_trunc('month', now() - (i * interval '1 month')),
         -- End Date (Next month)
         date_trunc('month', now() + ((1 - i) * interval '1 month'))
      );
   END LOOP;
END $$;
```

**The Performance Boost (Partition Pruning)**:
*   **Query**: `SELECT * FROM payments_p WHERE tstamp = '2022-03-09...'`
*   **Mechanism**: Postgres knows the data *must* be in `payments_p_2022_03`. It ignores (prunes) the other 99 partitions.
*   **Result**: 2.5 seconds (vs 60s). **24.5x Faster**.

**The Maintenance Boost (Instant Delete)**:
*   **Delete**: Instead of `DELETE FROM`, you simply **drop** the old partition.
*   `DROP TABLE payments_p_2021_09;`
*   **Time**: **43 milliseconds**. (vs 65,000 ms).

#### Benefits Summary
**Performance** —You have sequential and index scans of smaller amounts of data due to partition pruning.

**Maintenance** —You can DROP TABLE to delete old data, and VACUUM of multiple smaller tables can parallelize and complete quicker than one very long-running operation for one huge table.

**Disk size limitations** —You can put partitions on different tablespaces (which in PostgreSQL can live on different filesystems or disks). Consequently, you can put different partitions on slower and cheaper disks, and you can decide whether to create indexes on some of them and not others.

**Circumventing a pitfall of extremely large tables** —Tables are split into 1 GB files, and PostgreSQL loops through some code that’s the same for each 1 GB segment, so it would execute that 32,000 times for a 32 TB table.

> **Warning**: Ensure your queries **include the partition key** in the `WHERE` clause. Otherwise, Postgres must scan *every* partition, negating the benefit.

### 4.3 Partitioning by Multiple Keys (Sub-partitioning)
Sometimes one key isn't enough. You might want to split data by **Month** first, and then by **Branch ID**.

#### The Trap: `PARTITION BY (x, y)`
A common mistake is thinking `PARTITION BY RANGE (date, branch)` creates a grid of "Date + Branch" buckets.
*   **Reality**: It creates a **continuous sort order**.
*   **Result**: Values overlap heavily in ways you don't expect. `(Jan, Branch 20)` might technically fall *after* `(Feb, Branch 1)` in certain sort logic, causing "Overlap" errors during creation.

#### The Solution: Sub-Partitioning (Multi-Level)
Instead of one complex key, use **two levels** of simple keys.
1.  **Level 1**: Partition by `reading_time` (Monthly).
2.  **Level 2**: Partition `energy_usage_2024_01` by `branch_id`.

**Step 1: Create Main Table (Level 1)**
```sql
CREATE TABLE energy_usage (
    branch_id int,
    reading_time timestamptz,
    ...
) PARTITION BY RANGE (reading_time);
```

**Step 2: Create a Partition... that is ALSO a Table (Level 2)**
```sql
CREATE TABLE energy_usage_2024_01 
PARTITION OF energy_usage
FOR VALUES FROM ('2024-01-01') TO ('2024-02-01')
PARTITION BY RANGE (branch_id);  -- <--- The Magic
```

**Step 3: Create the Leaf Partitions (The actual storage)**
```sql
-- Branch 1-10 in Jan 2024
CREATE TABLE energy_usage_2024_01_b1_10
PARTITION OF energy_usage_2024_01
FOR VALUES FROM (1) TO (10);

-- Branch 11-20 in Jan 2024
CREATE TABLE energy_usage_2024_01_b11_20
PARTITION OF energy_usage_2024_01
FOR VALUES FROM (11) TO (20);
```

**Structure**:
```text
energy_usage
├── energy_usage_2024_01 (Virtual Container)
│   ├── energy_usage_2024_01_b1_10 (Real Table)
│   └── energy_usage_2024_01_b11_20 (Real Table)
├── energy_usage_2024_02 (Virtual Container)
│   └── ...
```

> **Takeaway**: If you need multi-dimensional buckets (e.g., "All January data for Branch 5"), use **Sub-Partitioning** (Recursive Partitioning), not Multi-Column Partitioning.

### 4.4 Using the Wrong Index Type
Postgres offers many index types (`BTREE`, `GIN`, `GiST`, `BRIN`, `HASH`). Using the default `BTREE` for everything is a common performance mistake. Each type has a specific algorithmic strength.

#### Case Study: JSONB Search (ArXiv Articles)
We have a table with **2.4 million rows** of JSON research articles.

**Attempt 1: B-Tree on Text (The "Substring" mistake)**
We want to find titles *containing* "modeling of hydrogen".
*   **Query**: `SELECT ... WHERE lower(data->>'title') LIKE '%modeling of hydrogen%'`
*   **Index**: `CREATE INDEX ON arxiv (lower(data->>'title') text_pattern_ops);`
*   **Result**:
    *   **Plan**: `Seq Scan` (Full Table Scan).
    *   **Time**: **8.6 seconds** (Disaster).
*   **The Physics**: A B-Tree is like a sorted phone book. It is amazing for finding "Stickley, A..." (Prefix/Equality). It is useless for finding "...contains 'ick'..." because the book isn't sorted by middle syllables.

**Attempt 2: GIN Index (Full-Text Search)**
We switch to standard Full-Text Search ("Contains" logic).
*   **Query**: `WHERE to_tsvector(...) @@ plainto_tsquery('modeling of hydrogen')`
*   **Index**: `CREATE INDEX ... USING GIN (to_tsvector('english', data->>'title'));`
*   **Result**:
    *   **Plan**: `Bitmap Heap Scan`.
    *   **Time**: **4 ms** (2000x Faster).
    *   **Size**: 100 MB.
*   **The Physics**: **GIN (Generalized Inverted Index)** maps individual *elements* (words in a text, items in an array) to the rows that contain them.

**Attempt 3: GIN Index (Full JSONB)**
What if we want to search *any* field in the JSON document?
*   **Index**: `CREATE INDEX ON arxiv USING GIN (data);`
*   **Query**: `WHERE data @> '{"doi": "10.1039/..."}'` (Containment Operator).
*   **Result**: **0.15 ms**.
*   **The Trade-off**:
    *   **Size**: **1680 MB** (Huge! It indexes every key and value in the blob).
    *   **Write Penalty**: Inserting data becomes significantly slower maintenance.

**Attempt 4: Targeted B-Tree Expression Index**
We realize we only ever query the `DOI` field by exact match.
*   **Index**: `CREATE INDEX ON arxiv ((data->>'doi'));`
*   **Query**: `WHERE data->>'doi' = '10.1039/...'`
*   **Result**: **0.07 ms** (Fastest).
*   **Size**: **61 MB** (Tiny).
*   **Takeaway**: Precise, targeted B-Trees beat generic "Index Everything" GINs for specific equality lookups.

#### Summary of Index Types

| Index Type | Good For | Weakness |
| :--- | :--- | :--- |
| **B-Tree** (Default) | Equality (`=`), Range (`<`, `>=`), Sorting (`ORDER BY`), Prefix (`LIKE 'abc%'`) | Cannot do "Contains", Arrays, or Geo. |
| **GIN** | Full-Text Search, JSONB, Arrays, "Contains" operations. | Slow updates (Maintenance overhead), Large size. |
| **GiST** | Geometric data (PostGIS), Range Types (`daterange`), Nearest Neighbor (`<->`). | Lossy (checks re-run), slower than B-Tree for simple scalar lookups. |
| **BRIN** | **Huge** Time-Series data (correlates with physical disk order). Tiny size. | Only imprecise "Block Range" checks. |
| **HASH** | Equality (`=`) only. | No Range scans, no sorting. Rarely used over B-Tree. |

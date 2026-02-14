# Chapter 9: Metadata Tribbles (Tables per Year/User)

> **"Our `Bugs` table is too big. Let's split it into `Bugs_2020`, `Bugs_2021`, etc."**

This is the **Metadata Tribbles** antipattern. It occurs when developers try to partition data by creating new tables or columns for each new data value (like Year, User, or Country). It's named after the reproducing alien pets from Star Trek.

---

## 9.1 The Objective: Support Scalability and Archiving
You want query performance to remain high as data grows.
*   **The Idea**: "If I only query 2021 data, why scan 2020 data?"
*   **The Solution**: Manually splitting the table into `Bugs_2020`, `Bugs_2021`.

---

## 9.2 The Antipattern: Spawning Tables
You create a new table for every distinct value of an attribute (e.g., Year).
```sql
CREATE TABLE Bugs_2020 (id SERIAL, summary TEXT, date_reported DATE);
CREATE TABLE Bugs_2021 (id SERIAL, summary TEXT, date_reported DATE);
CREATE TABLE Bugs_2022 (id SERIAL, summary TEXT, date_reported DATE);
```

### Why it fails
1.  **Code Maintenance Nightmare**:
    *   On Jan 1st, 2023, your app crashes because `Bugs_2023` doesn't exist.
    *   You must constantly `ALTER` the schema or create tables dynamically.

2.  **Referential Integrity Breaks**:
    *   A `Comments` table can't point to the bugs.
    *   `FOREIGN KEY (bug_id) REFERENCES ???`. You can't reference `Bugs_2020 OR Bugs_2021`.

3.  **Cross-Table Queries are Slow (Union Hell)**:
    *   "How many bugs did we fix in total?"
    ```sql
    SELECT COUNT(*) FROM Bugs_2020
    UNION ALL
    SELECT COUNT(*) FROM Bugs_2021
    UNION ALL
    SELECT COUNT(*) FROM Bugs_2022
    ...
    ```
    *   This query grows indefinitely. If you forget one table, your stats are wrong.

4.  **Moving Data is Hard**:
    *   If you correct a date from '2022-01-01' to '2021-12-31', you must **DELETE** from `Bugs_2022` and **INSERT** into `Bugs_2021`. A simple `UPDATE` doesn't work.
    *   **Primary Key Collision**: `id=100` might exist in both tables. Moving it causes a crash.

5.  **Synchronizing Metadata**:
    *   Boss says: "Add a `severity` column".
    *   You must run `ALTER TABLE Bugs_20XX ADD COLUMN ...` on **every single table**.

### Legitimate Uses of the Antipattern
*   **Archiving**: Moving 10-year-old data to `Bugs_Archive` is fine if you rarely query it.
*   **Massive Scale (Sharding)**: WordPress.com famously creates a separate database for every blog. This is not "Metadata Tribbles"; it's a calculated **Sharding Strategy** for isolation and backup management.

## 9.3 The Solutions: Partition and Normalize
Don't split tables manually. Let the database or schema do it properly.

### Solution 1: Horizontal Partitioning
Use the database's built-in Partitioning feature.
```sql
CREATE TABLE Bugs (
  id SERIAL,
  date_reported DATE
) PARTITION BY RANGE (date_reported);

CREATE TABLE Bugs_2020 PARTITION OF Bugs 
  FOR VALUES FROM ('2020-01-01') TO ('2021-01-01');
```
*   **Pros**:
    *   **Single Logical Table**: Query `SELECT * FROM Bugs` works perfectly. No `UNION`.
    *   **Performance**: The DB automatically scans only the relevant partition ("Partition Pruning").
    *   **Management**: Dropping old data is instant (`DROP TABLE Bugs_2015`).

### Solution 2: Vertical Partitioning
If the table is slow because it has huge columns (`description TEXT`, `screenshot BLOB`), split it by **Column Usage**.
*   **Concept**: Separate "Hot Data" (scanned frequently, small) from "Cold Data" (bulky, rarely used).
*   **The Trap**: `SELECT *` on a table with a 5MB BLOB reads 5MB per row, even if you only needed the date.

```sql
-- 1. Main Table (Fixed Width, High Performance)
-- Only stores columns needed for filtering/sorting lists.
CREATE TABLE Bugs (
  id            SERIAL PRIMARY KEY,
  summary       CHAR(80),   -- Fixed width is faster in some engines
  date_reported DATE,
  status        VARCHAR(10)
);

-- 2. Details Table (Variable Width, Bulky)
-- Stores huge text/blobs. Only joined when viewing a specific bug.
CREATE TABLE BugDescriptions (
  bug_id      BIGINT PRIMARY KEY REFERENCES Bugs(id),
  description TEXT,
  screenshot  BLOB
);
```
*   **Why it wins**:
    *   **RAM Density**: Index pages store more rows because they are smaller. `SELECT * FROM Bugs` checks 1000s of bugs per disk read instead of 1.
    *   **I/O Savings**: You don't load the 10MB Screenshot until the user actually clicks "View Bug".
*   **Cons**: Requires a `JOIN` to show the full detail page.

### Solution 3: Fix the Columns (Dependent Table)
If you have columns like `bugs_2020`, `bugs_2021`, Normalize them!
```sql
CREATE TABLE ProjectHistory (
  project_id BIGINT,
  year       INT,
  bugs_fixed INT,
  PRIMARY KEY (project_id, year)
);
```
*   **Result**: You can store data for 2050 without changing the schema.

> **Takeaway**: If you need to split data, use **Partitioning** (Horizontal) or **Normalization** (Vertical/Dependent). Don't manage tables in application code.

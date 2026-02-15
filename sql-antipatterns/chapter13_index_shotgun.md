# Chapter 13: Index Shotgun

> **"It's slow? Just add an index to everything!"**

This is the **Index Shotgun** antipattern. It occurs when developers either add no indexes (out of fear of overhead) or add too many/wrong indexes (hoping one will work).

---

## 13.1 The Objective: Optimize Performance
You want your queries to be fast.
*   `SELECT * FROM Bugs WHERE id = 1234` should be instant.
*   Searching by `date_reported` shouldn't scan the whole table.

---

## 13.2 The Antipattern: Using Indexes Without a Plan
You guess.

### Type A: "Indexes are Expensive" (No Indexes)
You read that "indexes slow down writes," so you create none.
```sql
CREATE TABLE Bugs (
  id INT, -- No Primary Key?!
  status VARCHAR(10)
);
```
*   **Result**: Every query is a **Full Table Scan**.
*   **Analogy**: Finding "Smith" in a phone book that isn't alphabetized. You have to read every single line.

### Type B: "Index Everything" (Too Many)
You index every column individually.
```sql
CREATE TABLE Bugs (
  id INT,
  status VARCHAR(20),
  date_reported DATE,
  -- 5 Indexes for 3 columns?
  INDEX(status),
  INDEX(date_reported),
  INDEX(id, status), -- Redundant?
  INDEX(status, date_reported)
);
```
*   **Write Penalty**: Every `INSERT`, `UPDATE`, or `DELETE` must update **All 5 Indexes**.
*   **Disk Space**: Indexes take up space. It's common for indexes to be larger than the table itself.
*   **Redundancy**: `INDEX(status, date_reported)` matches queries for `status`. You don't need a separate `INDEX(status)`.

### Type C: "The Magic Index" (Wrong Index)
You create an index that doesn't help the query.
1.  **Wrong Order**:
    *   Index: `(last_name, first_name)`
    *   Query: `WHERE first_name = 'Charles'`
    *   **Fail**: A phone book sorted by Last Name doesn't help you find First Names.
2.  **Wildcards**:
    *   Index: `(summary)`
    *   Query: `WHERE summary LIKE '%crash%'`
    *   **Fail**: An index only helps with *prefixes* (`'crash%'`), not mid-string searches.
3.  **Functions**:
    *   Index: `(date_reported)`
    *   Query: `WHERE YEAR(date_reported) = 2023`
    *   **Fail**: Modifying the column (`YEAR()`) disables the index usage.

## 13.3 The Solution: MENTOR Your Indexes
Don't guess. Follow the **MENTOR** methodology.

### 1. **M**easure
Identify the slow queries first.
*   **MySQL**: Enable `slow_query_log` and set `long_query_time = 1`.
*   **Postgres**: Use `pg_stat_statements`.
*   **Goal**: Find the query that takes the most aggregate time.

### 2. **E**xplain
Ask the database *why* it is slow.
```sql
EXPLAIN SELECT * FROM Bugs WHERE summary LIKE '%crash%';
```
*   Look for **type: ALL** (Full Table Scan). This is bad.
*   Look for **key: NULL**. It means no index is being used.

### 3. **N**ominate
Propose an index that fits the query pattern.
*   **Query**: `WHERE status = 'OPEN' AND date_reported > '2023-01-01'`
*   **Nomination**: `INDEX(status, date_reported)` (Equality first, Range second).

**Pro-Tip: Covering Indexes**
If an index contains *all* the columns you need, the DB reads the index **only** (skipping the table).
```sql
-- Query: SELECT bug_id FROM Bugs WHERE status = 'FIXED'
CREATE INDEX idx_status_id ON Bugs(status, bug_id);
-- The DB reads the small index, never touching the big table files.
```

### 4. **T**est
Create the index and run the query again.
*   Check `EXPLAIN` again. Is **type** now `ref` or `range`?
*   Did the time drop from 500ms to 5ms?

### 5. **O**ptimize
Indexes must fit in RAM to be fast.
*   **MySQL**: Tune `innodb_buffer_pool_size` (Set to 70-80% of RAM on a dedicated server).
*   If your indexes are larger than RAM, performance drops off a cliff (Disk Trashing).

### 6. **R**ebuild
Indexes get fragmented over time (gaps in pages).
*   **MySQL**: `OPTIMIZE TABLE Bugs;`
*   **Postgres**: `VACUUM;` / `REINDEX TABLE Bugs;`
*   Schedule this during off-hours (e.g., Weekly).

> **Takeaway**: Stop guessing. **Measure** the slow logs, **Explain** the plan, and **Test** your specific index.

### Mini-Antipattern: Indexing Every Combination
**"I don't know which queries will run, so I'll create `INDEX(a,b)`, `INDEX(b,a)`, `INDEX(a,c)`..."**

*   **The Trap**: You try to "future-proof" the table by covering every possible query pattern.
*   **The Math (Factorials)**:
    *   Indexes depend on column order (`(a,b)` helps sort by `a`, but `(b,a)` helps sort by `b`).
    *   To cover *all* combinations of just 5 columns, you need **5! (120)** indexes.
    *   Most databases have a hard limit (e.g., 64 indexes per table).

*   **The Reality**:
    *   **Writes Halt**: Inserting 1 row requires updating 120 B-Trees.
    *   **Disk Full**: The indexes will be 100x larger than the data.

**Solution**: Only index the queries that exist *today*. YAGNI (You Ain't Gonna Need It) applies to indexes too.

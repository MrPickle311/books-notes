# Chapter 6: Optimizing Queries for Good Performance

---

### A Practical Example: The 3-Table Join
To demonstrate how the optimizer makes decisions, let's look at a classic training scenario involving large datasets.

**The Setup**
Imagine three massive tables with millions of rows:
```sql
CREATE TABLE a (aid int, ...); -- 100 million rows
CREATE TABLE b (bid int, ...); -- 200 million rows
CREATE TABLE c (cid int, ...); -- 300 million rows
```

**The Indexes**
We have simple B-Tree indexes on the IDs:
```sql
CREATE INDEX idx_a ON a (aid);
CREATE INDEX idx_b ON b (bid);
CREATE INDEX idx_c ON c (cid);
```

**The View**
A view `v` that joins tables `a` and `b`:
```sql
CREATE VIEW v AS
SELECT *
FROM a, b
WHERE aid = bid;
```

**The User Query**
Now, a user runs a query joining the view `v` with table `c`, filtering on a specific ID:
```sql
SELECT *
FROM v, c
WHERE v.aid = c.cid
  AND cid = 4;
```

### The Planner's Dilemma
The optimizer (planner) has to figure out the most efficient way to execute this. It has several options (paths).
*   Should it join `a` and `b` first (as defined in the view)?
*   Should it filter `c` first?
*   Should it ignore the view structure and flatten the join?

**Evaluating Join Options**
If the planner used a naive approach, it might try to calculate the entire view `v` first.
*   **Problem**: Joining 100M rows (A) with 200M rows (B) creates a massive intermediate result.
*   **Impact**: This would be incredibly slow and resource-heavy.


### 1. Nested Loops (The "For-Each" Strategy)

```text
for x in table1:          -- Outer Loop
    for y in table2:      -- Inner Loop
        if x.field == y.field
            return row
```

*   **Best for**: Very small datasets (e.g., one side has 10 rows).
*   **Worst for**: Large datasets. In our example (100M x 200M iterations), the runtime would explode.
*   **Performance**: Generally `O(N * M)`. It is ruled out for calculating the full view of `a` and `b`.

### 2. Hash Joins (The "HashMap" Strategy)
1.  **Build Phase**: PostgreSQL scans the smaller table and builds a Hash Table (HashMap) in memory.
2.  **Probe Phase**: It scans the larger table, hashes the joining key, and probes the Hash Table for matches.

```text
Hash Join
  -> Hash (Build Hash Table on Table 1)
       -> Seq Scan Table 1
  -> Seq Scan Table 2 (Probe Table)
```

*   **Best for**: Joining large, unsorted sets where the hash table fits in memory (`work_mem`).
*   **Problem**: If datasets are massive (100M+ rows), hashing everything requires huge memory or disk spilling.

### 3. Merge Joins (The "Zipper" Strategy)
The idea is to use **sorted lists** to "zip" the results together. If both lists are sorted by the join key, the system can just iterate through both once.

**Scenario A: Explicit Sort**
If data is not sorted, PostgreSQL must sort it first.
```text
Merge Join
  -> Sort Table 1
       -> Seq Scan Table 1
  -> Sort Table 2
       -> Seq Scan Table 2
```
*   **Cost**: Sorting is `O(N * log(N))`. Sorting 300M rows is very expensive.

**Scenario B: Index Scan (Pre-sorted)**
If an index exists on the join key, the data is *already* sorted.
```text
Merge Join
  -> Index Scan Table 1
  -> Index Scan Table 2
```
*   **Best for**: Joining large datasets that are already sorted (via Index).
*   **Caveat**: Random I/O on the index can be slow if you need the *entire* table. Sequential scans are faster for full-table reads.

### Summary of Join Strategies
| Algorithm | Best Used When... | Complexity Risk |
| :--- | :--- | :--- |
| **Nested Loop** | One side is **very small** (e.g., 1-100 rows). | `O(N*M)` - Explodes with size. |
| **Hash Join** | Large, unsorted sets. Hash table fits in RAM. | Memory usage. |
| **Merge Join** | Large sets that are **already sorted** (Index) or cheap to sort. | Sorting overhead if not indexed. |
### Applying Transformations
Simply joining the view first makes no sense (Hash Join is too much memory, Nested Loop is too slow, Sorting is too expensive). The solution is **Logical Transformation**. The optimizer transforms the query structure *before* creating a plan.

**Step 1: View Inlining**
First, the optimizer "inlines" the view, treating it as a subquery rather than a black box.
*   **Original**: `FROM v, c`
*   **Transformed**:
    ```sql
    SELECT *
    FROM (
        SELECT * FROM a, b WHERE aid = bid
    ) AS v, c
    WHERE v.aid = c.cid AND cid = 4;
    ```
*   **Benefit**: Opens the door for further optimization.

**Step 2: Flattening Subselects**
The next step is to "flatten" the subselect. The optimizer pulls the tables out to the top level.
*   **Flattened Query**:
    ```sql
    SELECT *
    FROM a, b, c
    WHERE a.aid = c.cid -- was v.aid = c.cid
      AND a.aid = b.bid -- from view definition
      AND c.cid = 4;    -- user filter
    ```

**Impact of Flattening**
By getting rid of the subselect structure, the optimizer now sees all three tables (`a`, `b`, `c`) and all conditions at once. It is no longer forced to join `a` and `b` first.
*   It can now choose to start with `c` (since `cid=4` is a very strong filter).
*   This transformation is automatic; you do not need to rewrite your SQL manually.
### Applying Equality Constraints (Transitive logic)
Once the query is flattened, the optimizer applies **Transitivity** to deduce new constraints.
This is the logic:
1.  If `aid = cid` AND `aid = bid`, then `bid` must also equal `cid`.
2.  If `cid = 4` AND all others are equal, then `aid` and `bid` **must also be 4**.

**The Optimized Query**
The planner internally transforms the query to:
```sql
SELECT *
FROM a, b, c
WHERE a.aid = c.cid
  AND aid = bid
  AND cid = 4
  AND bid = cid  -- Deduced
  AND aid = 4    -- Deduced: Now we can use Index on A!
  AND bid = 4    -- Deduced: Now we can use Index on B!
```

**Why is this a Game-Changer?**
The importance of this optimization can’t be stressed enough.
*   **Original**: We seemingly only had a filter on `c.cid`.
*   **Optimized**: We now have filters for `a.aid` and `b.bid` too.
*   **Result**: The optimizer can index scan **all three tables** for the value `4`. It grabs a tiny handful of rows from each table and joins them. The 100M/200M/300M row problem has vanished.

**Caveat: Logic limits**
Equality constraints work great for `EQUALS (=)`, but transitivity does not always apply to other operators (like `<` or `>`) across columns in the same way.
*   `WHERE x = 1 AND x = y` implies `y = 1`. (Transitive)
*   `WHERE x < 1 AND x = y` implies `y < 1`. (Transitive)
*   But complex expressions might typically be treated as Filters rather than Index Conditions depending on the operator class.

**Example: Equality vs Inequality Plan**
```sql
-- Equality: Transitivity implies reltype = 1
EXPLAIN SELECT * FROM pg_class WHERE oid = 1 AND oid = reltype;
-- Result: 'Index Cond: (oid = '1'::oid)' AND 'Filter: (reltype = '1'::oid)'

-- Inequality: Transitivity is harder/different
EXPLAIN SELECT * FROM pg_class WHERE oid < 1 AND oid = reltype;
-- Result: 'Index Cond: (oid < '1'::oid)' AND 'Filter: (oid = reltype)' (Keeps the join condition as check)
```

### Exhaustive Searching
Once the formal transformations (Inlining, Flattening, Transitivity) are complete, PostgreSQL performs an **Exhaustive Search**.

**What does this mean?**
The planner tries out virtually *all* possible valid execution plans (combinations of scan methods, join algorithms, and join orders) and uses its **Cost Model** to calculate the cheapest solution.

**Join Order Flexibility**
In our original query, the join order seemed fixed by the view (`A` filters `B`, then `C`).
However, thanks to the equality constraints we discovered (`aid=4`, `bid=4`, `cid=4`), the planner is free to rearrange the join order entirely:
*   `A` → `B` → `C`
*   `B` → `C` → `A`
*   `C` → `A` → `B`

All options are open. The planner will estimate the cost of each path (e.g., "Scanning index on B is slightly cheaper than C because the index is smaller") and pick the winner.

> **Key Takeaway**: The optimizer is not bound by the order you write your tables in the `FROM` clause. It actively searches for the most efficient path through your data.

## Checking out Execution Plans
Now that we understand the theory (Transformations & Exhaustive Search), let's see the **actual result** produced by PostgreSQL.

### 1. The Plan (Empty Tables)
First, we disable parallel workers (to keep the plan simple) and run the query on empty tables.
```sql
SET max_parallel_workers_per_gather TO 0;
EXPLAIN SELECT * FROM v, c WHERE v.aid = c.cid AND cid = 4;
```
**Sample Output**:
```text
Nested Loop
  -> Nested Loop
       -> Bitmap Heap Scan on a (Recheck Cond: aid = 4)
             -> Bitmap Index Scan on idx_a (Index Cond: aid = 4)
       -> Materialize
             -> Bitmap Heap Scan on b (Recheck Cond: bid = 4)
                   -> Bitmap Index Scan on idx_b
  -> Materialize
       -> Bitmap Heap Scan on c (Recheck Cond: cid = 4)
             -> Bitmap Index Scan on idx_c
```
**Observation**: Even with empty tables, the planner sees the equality constraints and applies filters to `a`, `b`, and `c` individually. It uses `Bitmap Index Scans` because it expects potentially scattered data or specific access patterns.

### 2. The Plan (Populated Tables)
Let's add 1 Million rows to each table and run `ANALYZE` to update statistics.
```sql
-- Insert 1M rows into a, b, c...
ANALYZE;
EXPLAIN SELECT * FROM v, c WHERE v.aid = c.cid AND cid = 4;
```

**Optimized Output**:
```text
Nested Loop (cost=1.27..13.35 rows=1)
  -> Nested Loop
       -> Index Only Scan using idx_a on a (Index Cond: aid = 4)
       -> Index Only Scan using idx_b on b (Index Cond: bid = 4)
  -> Index Only Scan using idx_c on c (Index Cond: cid = 4)
```

**Analysis of the Result**:
1.  **Transitivity in Action**: Notice `Index Cond: (aid = 4)` on table `A` and `(bid = 4)` on table `B`. The optimizer successfully propagated `cid=4` to all tables.
2.  **Index Only Scan**: Since we are only selecting IDs (or if the index covers all columns), it doesn't even need to touch the heap table. It gets the answer directly from the B-Tree index.
3.  **Nested Loop**: Why correct? We are fetching `aid=4`. In a unique or low-cardinality scenario, this returns very few rows (maybe 1). Joining 1 row from A, 1 from B, and 1 from C is instantaneous. A Nested Loop is the perfect algorithm here.

> **Note**: Your exact costs and plan might vary slightly based on data alignment and random seed, but the logic—using indexes on all three tables—remains the same.

## Making the process fail (Optimization Barriers)
PostgreSQL is smart, but it needs smart users. You can accidentally "cripple" the optimizer by introducing constructs that block transformations. A classic example is the "Optimization Barrier".

### The `OFFSET 0` Trick
Let's recreate our view but add `OFFSET 0` at the end.
```sql
DROP VIEW v;
CREATE OR REPLACE VIEW v AS
    SELECT * FROM a, b WHERE aid = bid
    OFFSET 0;  -- Logically does nothing, but changes everything!
```

**Why is this a problem?**
*   Logically: `OFFSET 0` means "skip zero rows", so the data is identical.
*   Technically: The SQL standard implies that if you have an `OFFSET`, the result set order usually matters or limits are applied. PostgreSQL treats this as a **fencing** operation.
*   **Result**: The planner **cannot inline** this view. It must calculate the view *first*, apply the offset, and *then* join to table `c`.

### The "Crippled" Plan
Let's run the same query again:
```sql
EXPLAIN SELECT * FROM v, c WHERE v.aid = c.cid AND cid = 4;
```
**Sample Output**:
```text
Nested Loop (cost=1.62..79463.79 !)
  -> Subquery Scan on v (Filter: v.aid = 4)
       -> Merge Join (cost=1.19..66959.34 rows=1000000)
            -> Index Only Scan using idx_a
            -> Index Only Scan using idx_b
  -> Index Only Scan using idx_c (Index Cond: cid = 4)
```

**Analysis**:
1.  **Costs Skyrocketed**: From `13.35` (previous plan) to `79463.79`.
2.  **No Transitivity for `a` and `b`**: The planner was forced to calculate the specific join of `a` and `b` (Merge Join of 1,000,000 rows!) simply to apply the `OFFSET 0`. It could *not* push the `aid=4` filter down to the index lookup on table `a`.
3.  **Subquery Scan**: The view is scanned as a "black box" subquery.

> **Takeaway**: Be very careful with `OFFSET`, `LIMIT`, or `optimization fences` inside views if you expect them to be highly optimized when joined with other tables. Sometimes this behavior is desired (to force a materialization), but usually, it is a performance bug introduced by the developer.

## Constant Folding
Constant folding is the process where the optimizer evaluates calculable constant expressions *during the planning phase*, turning them into single values.

### 1. Successful Folding (Right-Hand Side)
Consider this query:
```sql
EXPLAIN SELECT * FROM a WHERE aid = 3 + 1;
```
**Optimized output**:
```text
Index Only Scan using idx_a on a
Index Cond: (aid = 4)
```
**Why?**
The optimizer sees `3 + 1`. Since both are constants, it calculates the result `4` immediately. The query effectively becomes `WHERE aid = 4`. Because `aid` is on the left side (naked column), the index `idx_a` can be used.

### 2. Failed Optimization (Left-Hand Side)
What happens if the expression involves the column itself?
```sql
EXPLAIN SELECT * FROM a WHERE aid - 1 = 3;
```
**Sample Output**:
```text
Seq Scan on a (Filter: (aid - 1) = 3)
```
**Why?**
*   Math is happening on the column: `aid - 1`.
*   The index stores `aid`, not `aid - 1`.
*   PostgreSQL cannot magically reverse every mathematical function (e.g., it doesn't automatically rewrite `aid - 1 = 3` to `aid = 4`).
*   Therefore, it must read **every row**, extract `aid`, subtract 1, and compare to 3. This forces a **Sequential Scan**.

> **Rule of Thumb**: Keep your indexed columns "naked" on one side of the operator. Do math on the user input (constants), not on the table columns.
> *   **Bad**: `target_date + 7 = now()`
> *   **Good**: `target_date = now() - 7`

### Concept: What is a Sequential Scan?
A **Sequential Scan** (or Seq Scan) is the most basic way PostgreSQL reads data.
*   **Process**: It starts at the beginning of the table (heap) and reads every single row, one by one, until it reaches the end.
*   **Analogy**: It is like reading a book from cover to cover to find a specific word.
*   **Performance**:
    *   **Good**: When you need to read a large portion of the table (e.g., reporting on > 50% of rows), because reading sequentially from disk is faster than jumping around randomly.
    *   **Bad**: When filtering for a few specific rows (e.g., `WHERE id = 5`). Reading millions of pages to find one record is extremely inefficient compared to looking it up in an Index (the "Table of Contents").

### Understanding Function Inlining
Optimizations aren't just about joins and constants. PostgreSQL can also **Inline** simple SQL functions.
*   **Goal**: Reduce function call overhead and expose the underlying logic to the optimizer.
*   **Requirement**: The function is typically `LANGUAGE sql` and marked `IMMUTABLE`.

**Example: A wrapper function**
Let's create a function `ld` that calculates the log base 2.
```sql
CREATE OR REPLACE FUNCTION ld(int) RETURNS numeric AS $$
    SELECT log(2, $1);
$$ LANGUAGE 'sql' IMMUTABLE;
```

**Indexing the Function**
If we index this function:
```sql
CREATE INDEX idx_ld ON a (ld(aid));
```
We might expect the index to work when we call `WHERE ld(aid) = 10`.

**The Magic of Inlining**
```sql
EXPLAIN SELECT * FROM a WHERE ld(aid) = 10;
```
**Sample Output**:
```text
Bitmap Heap Scan on a (cost=4.67..52.77 rows=50 width=4)
  Recheck Cond: (log('2'::numeric,
      (aid)::numeric) = '10'::numeric)
    -> Bitmap Index Scan on idx_ld
    (cost=0.00..4.66 rows=50 width=0)
    Index Cond: (log('2'::numeric,
      (aid)::numeric) = '10'::numeric)
```
**Observation**:
The plan does **not** say `Index Cond: ld(aid)`. It says `log('2', aid)`.
1.  The optimizer "inlined" the function `ld`.
2.  It replaced `ld(aid)` with its definition `log(2, aid)`.
3.  Because the index `idx_ld` is technically stored as the result of that expression, the optimizer matches them up!

**Side Benefit**:
Because of this inlining, you can query using the *underlying logic* directly, and it will *still* use the index you defined on the wrapper function!
```sql
-- We query using raw log(), but it matches the index on ld()!
EXPLAIN SELECT * FROM a WHERE log(2, aid) = 10;
```
**Result**: Uses `idx_ld`.
This is powerful because it decouples the index definition from the specific function name used in queries, provided they compile to the same expression.

### Concept: Index Cond vs. Recheck Cond
In execution plans (especially Bitmap Scans), you will often see these two terms. It is important to know the difference.

**1. Index Cond (Index Condition)**
*   **Where**: Appears in `Index Scan` or `Bitmap Index Scan`.
*   **What**: This is the "exact match" logic used *inside* the index structure (B-Tree) to find relevant leaf pages.
*   **Meaning**: "I used the index to find row pointers where `log(2, aid) = 10`."

**2. Recheck Cond (Recheck Condition)**
*   **Where**: Appears in `Bitmap Heap Scan`.
*   **What**: A validation step performed when reading the actual table (Heap) data.
*   **Why is it needed?**
    *   **Lossy Bitmaps**: If the bitmap is too large to fit in memory (`work_mem`), PostgreSQL might downgrade it to a "lossy" bitmap. A lossy bitmap remembers which *Page* (block) contains the row, but not exactly which specific *Row* inside that page.
    *   **Verification**: Therefore, when the scanner visits that Page, it must "Recheck" every row on that page to see if it *actually* matches the condition `log(2, aid) = 10`, just in case it was a false positive from a lossy page marker.
    *   **Visibility**: It also checks if the row is visible to the current transaction (MVCC), though `Recheck Cond` specifically refers to the WHERE clause validation.

> **Summary**: `Index Cond` finds the candidates. `Recheck Cond` verifies them (and is often just a formality unless memory was low).

### Introducing Join Pruning
Another powerful optimization is **Join Pruning**.
*   **Concept**: The planner removes joins entirely if they do not contribute to the final result.
*   **Common Use Case**: Queries generated by ORMs that frequently join "everything" but only select fields from the main table.

**The prerequisites for Pruning**
1.  **No Columns Selected**: You are not asking for any column from the joined table.
2.  **Uniqueness**: The join must not duplicate rows. (e.g., if you join to a table with multiple matching rows, the result count typically grows. But if the joined column has a `UNIQUE` or `PRIMARY KEY` constraint, the row count is guaranteed to stay the same).

**Example: Pruning in Action**
Let's create two tables with Primary Keys (ensuring uniqueness).
```sql
CREATE TABLE x (id int, PRIMARY KEY (id));
CREATE TABLE y (id int, PRIMARY KEY (id));
```

**Scenario 1: No Pruning (Selecting All)**
```sql
EXPLAIN SELECT * FROM x LEFT JOIN y ON (x.id = y.id) WHERE x.id = 3;
---
Nested Loop Left Join (cost=0.31..16.36 rows=1 width=8)
  Join Filter: (x.id = y.id)
  -> Index Only Scan using x_pkey on x
      (cost=0.15..8.17 rows=1 width=4)
      Index Cond: (id = 3)
  -> Index Only Scan using y_pkey on y
      (cost=0.15..8.17 rows=1 width=4)
      Index Cond: (id = 3)
(6 rows)
```
**Result**: `Nested Loop Left Join`.
Reason: We asked for `SELECT *`, so we *need* the data from `y`. It cannot be pruned.

**Scenario 2: Pruning Activated (Selecting Left Only)**
```sql
EXPLAIN SELECT x.* FROM x LEFT JOIN y ON (x.id = y.id) WHERE x.id = 3;
-----------------------------------------------------------
Index Only Scan using x_pkey on x
    (cost=0.15..8.17 rows=1 width=4)
  Index Cond: (id = 3)
(2 rows)
```
**Observation**: Table `y` is completely gone from the plan!
*   **Why?**
    *   We only asked for `x.*`.
    *   Since `y.id` is a Primary Key, joining `x` to `y` will match *at most one* row. It will never multiply the result set.
    *   Since it affects neither the columns returned nor the number of rows, the join is redundant.

> **Advice**: Only select the columns you need. `SELECT *` often kills optimization opportunities like Join Pruning and Index Only Scans.

### Concept: Nested Loop Left Join & Join Filter
In the plan above, we saw two specific terms.

**1. Nested Loop Left Join**
*   **What**: It is a Nested Loop algorithm (For-Each loop), but respecting the rules of a `LEFT JOIN`.
*   **Logic**:
    1.  Fetch a row from the Outer Table (Left side).
    2.  Try to find a match in the Inner Table (Right side).
    3.  If a match is found, return `(Left + Right)`.
    4.  **Crucially**: If *no* match is found, return `(Left + NULLs)`.
*   **Contrast**: A standard `Nested Loop` (Inner Join) would discard the Left row if no match was found.

**2. Join Filter**
*   **What**: A condition checked *after* the join pair is formed but *before* the row is finalized.
*   **Logic**: `Join Filter: (x.id = y.id)`
    *   Normally, `id` lookups happen via `Index Cond`.
    *   However, if the planner matched rows tentatively but needs to double-check a condition that wasn't fully satisfied by the index (or strictly part of the JOIN ON clause that didn't drive the lookup), it applies this filter.
    *   In the example above, it's slightly redundant visually because the Index Cond `(id=3)` on both sides implicitly handles it, but the executor fundamentally ensures the join condition `x.id = y.id` holds true for the returned tuple.

### Speedup Set Operations (UNION / UNION ALL)
Set operations (`UNION`, `INTERSECT`, `EXCEPT`) combine results from multiple queries. PostgreSQL optimizes these by **Pushing Down** restrictions.

**1. Pushing Filters Down**
Consider a query where we filter the *result* of a union:
```sql
SELECT *
FROM (
    SELECT aid AS xid FROM a
    UNION ALL
    SELECT bid FROM b
) AS y
WHERE xid = 3;  -- Filter is logically "outside"
```

**What PostgreSQL does:**
It pushes `xid = 3` down into the individual branches.
*   `SELECT aid FROM a WHERE aid = 3`
*   `SELECT bid FROM b WHERE bid = 3`

**The Plan**:
```text
Append (cost=0.29..8.76 rows=2 width=4)
  -> Index Only Scan using idx_a on a
      (cost=0.29..4.30 rows=1 width=4)
      Index Cond: (aid = 3)
  -> Index Only Scan using idx_b on b
      (cost=0.42..4.44 rows=1 width=4)
      Index Cond: (bid = 3)
```
**Benefit**: Instead of reading *everything*, combining it, and then filtering for `3`, it uses the indexes on tables `a` and `b` directly. This is a massive speedup.

**2. UNION vs. UNION ALL (Hidden Costs)**
It is crucial to remember the difference:
*   **`UNION ALL`**: Just appends data. Fast.
*   **`UNION`**: Removes duplicates. Requires **Sorting** and **Unique** checks.

**Example Plan for `UNION` (without ALL)**:
```text
Unique (cost=8.79..8.80 rows=2 width=4)
  -> Sort (cost=8.79..8.79 rows=2 width=4)
    Sort Key: a.aid
  -> Append (cost=0.29..8.78 rows=2 width=4)
    -> Index Only Scan using idx_a on a
        (cost=0.29..4.30 rows=1 width=4)
        Index Cond: (aid = 3)
    -> Index Only Scan using idx_b on b
        (cost=0.42..4.44 rows=1 width=4)
        Index Cond: (bid = 3)
```
**Impact**: The `Sort` and `Unique` nodes add overhead.
> **Advice**: If you know your data is unique (or don't care about duplicates), always use `UNION ALL`. Only use `UNION` if you explicitly need to filter out duplicates. Using `UNION` blindly is a common cause of poor performance on large datasets.

### Concept: Append, Sort, and Sort Key
In the plan above, we see new node types. Here is what they mean:

**1. Append**
*   **What**: A node that executes its children one by one and returns all their rows.
*   **Analogy**: Taking two stacks of paper and stacking them on top of each other.
*   **Context**: Used in `UNION`, `UNION ALL`, or Inheritance/Partitioning scans.

**2. Sort**
*   **What**: The **Operation** (Action). It takes an input stream of rows and reorders them.
*   **Cost**: This is typically an expensive operation (`O(N log N)`). It requires memory (`work_mem`). If rows don't fit in RAM, it spills to disk (temp files), effectively becoming very slow.

**3. Sort Key**
*   **What**: The **Parameter** (Configuration). It tells the Sort node *which columns* to verify.
*   **Difference**:
    *   **Sort** is the *Process*: "I am sorting these rows."
    *   **Sort Key** is the *Definition*: "I am using `a.aid` to decide who comes first."
    *   In the plan `Sort (Sort Key: a.aid)`, it means "Perform the Sort Operation using a.aid as the criteria".

**4. Unique**
*   **What**: A node that removes **adjacent** duplicates.
*   **Mechanism**: It reads the input stream row by row. If the current row is identical to the previous row, it is discarded. If it is different, it is returned.
*   **Requirement**: Because it only checks *adjacent* rows, the input **must be sorted** first!
*   **Context**: This is why `UNION` (without ALL) almost always triggers a `Sort` node before the `Unique` node. It needs the data ordered so duplicates sit next to each other to easily remove them.

## Understanding Execution Plans

### 1. EXPLAIN vs EXPLAIN ANALYZE
*   **`EXPLAIN`**: Shows the *estimated* plan. It runs virtually instantly because it does **not** execute the query. It only plans it.
*   **`EXPLAIN ANALYZE`**: Actually **executes** the query and returns the plan with **real** runtime information.
    *   *Warning*: Be careful running `EXPLAIN ANALYZE` on `DELETE` or `UPDATE` statements, as it will actually modify your data! (Wrap it in a specific transaction with rollback if testing).

### 2. Reading a Plan Systematically (Inside-Out)
Plans look like trees or nested steps. The golden rule is: **Read from the inside out.**
*   Start at the most indented node (the one with no children).
*   Work your way up to the top.

**Example Plan:**
```sql
EXPLAIN ANALYZE SELECT *
FROM (SELECT * FROM b LIMIT 1000000) AS b
ORDER BY cos(bid);
```
**Output**:
```text
Sort (cost=146173..148673 rows=1000000 width=12) (actual time=494.0..602.7 loops=1)
  Sort Key: (cos((b.bid)::double precision))
  Sort Method: external merge Disk: 25496kB
  -> Subquery Scan on b (cost=0.0..29425 rows=1000000) (actual time=6.2..208.2 loops=1)
       -> Limit (cost=0.0..14425 rows=1000000) (actual time=5.9..105.2 loops=1)
            -> Seq Scan on b b_1 (cost=0.0..14425 rows=1000000) (actual time=0.0..55.4 loops=1)
JIT: ...
Execution Time: 699.903 ms
```

**Step-by-Step Analysis (Inside-Out)**
1.  **Seq Scan on b** (Bottom): The database scans table `b`.
    *   *Estimated Cost*: `0.00..14425.00`
    *   *Actual Time*: It took ~55 ms to start delivering rows.
2.  **Limit**: It passes rows up until it hits 1,000,000.
    *   *Actual Time*: Included the time to get rows from scan (~105 ms total).
3.  **Subquery Scan**: A structural node representing the `(...) AS b` wrapper. Relatively cheap processing (~208 ms total).
4.  **Sort** (Top): This is where the work happens.
    *   *Observation*: Time jumps from ~208ms (Subquery) to ~602ms (Sort).
    *   *Conclusion*: **Sorting is the bottleneck**. It took nearly 400ms just to sort the data.
    *   *Disk Usage*: `Sort Method: external merge Disk: 25496kB`. This means the sort was too big for RAM (`work_mem`) and spilled to disk, slowing it down.

### 3. Cost vs. Actual Time
*   **Cost**: `(startup_cost..total_cost)`. Arbitrary units used by the internal planner algorithm. Useful for comparing relative expense of two potential plans.
*   **Actual Time**: `(startup_time..total_time)`. Real milliseconds. This is hard evidence.

> **Tip**: When debugging slow queries, look at the `actual time` block. Find the node where the time **jumps** significantly compared to its child. That is your performance bottleneck.

### Making EXPLAIN more verbose
```sql
EXPLAIN (ANALYZE, VERBOSE, COSTS, TIMING, BUFFERS)
SELECT * FROM a ORDER BY random();
```

**Understanding the Options**
*   **`ANALYZE`**: Executes the query to get real timings (Required for Timing/Buffers).
*   **`VERBOSE`**: specific details about *what* is being output (e.g., specific columns `Output: aid, random()`). Useful to see if a specific column is forcing a table read.
*   **`COSTS`**: (Default) Displays the startup and total cost.
*   **`TIMING`**: (Default with Analyze) Displays the actual startup and total time.
*   **`BUFFERS`**: **Crucial**. Displays memory and disk I/O usage.

**Example Output (with Buffers)**:
```text
Sort (cost=834.39..859.39 rows=10000 width=12)
    (actual time=4.124..4.965 rows=10000 loops=1)
  Output: aid, (random())
  Sort Key: (random())
  Sort Method: quicksort Memory: 853kB
  Buffers: shared hit=45
  -> Seq Scan on public.a
    (cost=0.00..170.00 rows=10000 width=12)
    (actual time=0.057..1.457 rows=10000 loops=1)
    Output: aid, random()
    Buffers: shared hit=45
Planning Time: 0.109 ms
Execution Time: 5.895 ms
```

**Why is `BUFFERS` so important?**
It tells you *where* the data came from:
*   **Shared Hit**: Found in RAM (PostgreSQL Shared Buffers). Fast.
*   **Read**: Had to read from Disk (OS limits apply). Slower.
*   **Dirtied/Written**: Had to write data back to disk.

Comparing "Rows" vs "Buffers" is a great way to detect efficiency. (e.g., If you read 10,000 buffers to find 1 row, your index is missing or inefficient).

### Spotting Problems
Now that you can read plans, look for these specific red flags.

#### 1. Spotting Runtime Jumps
Always ask two questions:
1.  **Is the total time justified?** (Does it feel too slow for the data size?)
2.  **Where does the time jump?**
    *   Look at the `actual time` of a node and compare it to its children.
    *   *Example*: If a Scan takes 1ms, but the Sort on top of it takes 5ms, the Sort is the bottleneck (80% of runtime).

#### 2. Inspecting Estimates (Rows vs. Actual)
The most common cause of poor plans is **Bad Estimates**. The planner picks a strategy based on how many rows it *thinks* it will get. If it guesses wrong, it picks the wrong strategy (e.g., Nested Loop instead of Hash Join).

**The Check**: Compare `rows=X` (Estimate) vs `rows=Y` (Actual).
*   If `rows=1000` and `real rows=1000` -> **Good**.
*   If `rows=1` and `real rows=1000000` -> **Disaster**.

**Example: The "Hidden" Statistics Problem**
PostgreSQL stores statistics for columns, but *not* for complex functions by default.
```sql
CREATE TABLE t_estimate AS SELECT * FROM generate_series(1, 10000) AS id;
ANALYZE t_estimate;

-- Query with a function
EXPLAIN ANALYZE SELECT * FROM t_estimate WHERE cos(id) < 4;
```
**Bad Estimate Output**:
```text
Seq Scan on t_estimate (cost=0.00..220.00 rows=3333)
(actual time=0.010..4.006 rows=10000 loops=1)
```
*   **Estimate**: 3333 rows (Default guess of 33% for unknown range queries).
*   **Actual**: 10000 rows (Everyone implies `cos(id) < 4` is true for this dataset).
*   **Result**: Underestimation.

**The Fix: Functional Indexes**
If you create an index on the expression, PostgreSQL starts collecting statistics for that specific expression!
```sql
CREATE INDEX idx_cosine ON t_estimate (cos(id));
ANALYZE t_estimate;
```
Now the planner knows exactly how `cos(id)` behaves, fixing the estimate and likely using the index.

#### 3. Cross-Column Correlation
Another estimation trap is assuming columns are independent.
*   **The "Skiers in Africa" Paradox**:
    *   Assume 20% of people like to Ski.
    *   Assume 20% of people are from Africa.
    *   *Naive Match*: 0.2 * 0.2 = 4% of people are African Skiers.
    *   *Reality*: 0% (or very close). These two facts are **correlated**.
*   **PostgreSQL Default**: Assumes independence.
*   **Solution**: Use **Multivariate Statistics** (PostgreSQL 10+) to tell the planner that columns are related.

#### 4. Inspecting Buffer Usage (Hidden I/O Costs)
Sometimes a query looks fine (Index Scan), but runs slowly. The culprit is often **scattered data** causing massive Disk I/O.

**Example: Random Data Distribution**
```sql
CREATE TABLE t_random AS
SELECT * FROM generate_series(1, 10000000) AS id ORDER BY random();
ANALYZE t_random;
```

If we select 1000 rows, we might see:
```text
Seq Scan on t_random
  (cost=0.00..169247.71 rows=1000 width=4)
  (actual time=0.976..856.663 rows=999 loops=1)
  Filter: (id < 1000)
  Rows Removed by Filter: 9999001
  Buffers: shared hit=2080
    read=42168 dirtied=14248 written=13565
Planning Time: 0.099 ms
Buffers: shared hit=5 dirtied=1
Execution Time: 856.808 ms
```
*   **Shared Hit=2080**: Found in RAM. Fast.
*   **Read=42168**: Had to go to DISK. Slow.

**The "Clustering" Problem**
Imagine fetching 5,000 phone call records for one user.
*   If the table is ordered by time, your call records are scattered randomly across the entire 100GB table.
*   To fetch 5,000 rows, you might need to visit 5,000 different physical blocks on disk.
*   5,000 reads * 5ms (HDD seek) = **25 Seconds**.
*   If the data were packed together, you would read 1 block -> **5 ms**.

**The Fix: CLUSTER**
You can physically rewrite the table to match an index order.
```sql
CLUSTER t_random USING t_random_idx;
```
*   **Pros**: Packs related data together. Massive speedup for range queries on that specific key.
*   **Cons**:
    *   **Exclusive Lock**: The table is locked during rewrite (not suitable for 24/7 live OLTP).
    *   **Not Maintained**: New inserts will not be clustered. You must re-run it periodically.
    *   **Alternative**: Use `pg_squeeze` extension for online reorganization.

### Understanding and Fixing Joins
Performance problems are often actually **Semantic Problems**. A query might be slow because it is fundamentally doing something wrong logic-wise. A classic example is the "Outer Join Filter Trap".

**1. The Setup**
```sql
CREATE TABLE a (aid int); INSERT INTO a VALUES (1), (2), (3);
CREATE TABLE b (bid int); INSERT INTO b VALUES (2), (3), (4);
```

**2. The Classic Left Join**
```sql
SELECT * FROM a LEFT JOIN b ON (aid = bid);
```
**Result**: Returns 3 rows. (1+NULL, 2+2, 3+3).

**3. The "Surprise" Trap**
Many developers try to filter the result by adding an `AND` inside the `ON` clause:
```sql
SELECT * FROM a LEFT JOIN b ON (aid = bid AND bid = 2);
```
**Expectation**: "I want rows where the join matches AND bid is 2. So I expect only the `2|2` row."
**Reality**:
```text
 aid | bid
-----+-----
   1 | NULL  <-- Kept! (Join condition failed for 1 vs 2)
   3 | NULL  <-- Kept! (Join condition failed for 3 vs 3)
   2 | 2     <-- Kept! (Join condition succeed)
```
**Why?**
*   A `LEFT JOIN` **always** returns all rows from the Left table.
*   The `ON` clause only determines *how* we link to `b`.
*   If `bid = 2` is false, the join fails... effectively treating it as "No Match Found".
*   In a `LEFT JOIN`, "No Match Found" simply means "Return the Left Row + NULLs". It does **not** filter out the row.

**4. The Implication**
This often manifests as a "Performance Problem" where a user complains "The query scans the whole table instead of filtering for bid=2".
*   **The Fix**: Move the filter to the `WHERE` clause if you want to remove rows!
    ```sql
    ... WHERE bid = 2
    ```
> **Advice**: Always sanity-check the *logic* of an Outer Join before trying to optimize it. You might be optimizing a query that shouldn't exist in that form.

### Processing Outer Joins (The Reordering Constraint)
While the optimizer can freely shuffle **Inner Joins** to find the fastest order (A+B+C is the same as C+A+B), **Outer Joins** are much more restrictive. Reordering them blindly would break the logical result.

**1. Allowed Transformations**
PostgreSQL only performs outer join reordering if it is logically safe. Here are the three main rules:

*   **Rule 1: Tunneling Inner Joins**
    *   `(A LEFT B) INNER C` can become `(A INNER C) LEFT B`
    *   *Condition*: The join predicate for C (`Pac`) only references A, not B.
    *   *Benefit*: We can filter A against C early, reducing the set before joining B.

*   **Rule 2: Swapping Left Joins**
    *   `(A LEFT B) LEFT C` can become `(A LEFT C) LEFT B`
    *   *Condition*: Both join to A directly.
    *   *Benefit*: If C is smaller or more selective, doing it first is faster.

*   **Rule 3: Associativity (Grouping)**
    *   `(A LEFT B) LEFT C` can become `A LEFT (B LEFT C)`
    *   *Condition*: The predicate for C (`Pbc`) is "strict" for B (i.e., it must be NULL if B is NULL).

**2. The "Impossible" Chain**
Consider a chain of dependencies:
```sql
SELECT ...
FROM a
LEFT JOIN b ON (a.id = b.id)
LEFT JOIN c ON (b.id = c.id)  -- C depends on B
LEFT JOIN d ON (c.id = d.id)  -- D depends on C
```
*   **Problem**: The optimizer implies `a` must be joined to `b` first. Then the result to `c`. Then to `d`.
*   **Constraint**: It cannot jump ahead to join `c` and `d` instantly if they depend on the existence of `b`.
*   **Result**: Join reordering is effectively disabled for this chain. If `a` and `b` are huge, you are stuck joining them first.

> **Optimization Tip**: Check if you *really* need Outer Joins. Often, developers use `LEFT JOIN` by default when `INNER JOIN` (which allows full reordering) would suffice. Changing a chain of Left Joins to Inner Joins can sometimes unleash massive performance gains by unblocking the optimizer.
> **Optimization Tip**: Check if you *really* need Outer Joins. Often, developers use `LEFT JOIN` by default when `INNER JOIN` (which allows full reordering) would suffice. Changing a chain of Left Joins to Inner Joins can sometimes unleash massive performance gains by unblocking the optimizer.

## Enabling and Disabling Optimizer Settings
Sometimes we need to guide the planner manually, especially when dealing with complex queries where the default exhaustive search becomes too expensive or makes mistakes.

### Understanding `join_collapse_limit`
The PostgreSQL planner prefers to "flatten" your query into a single list of tables and then try all possible join permutations to find the best order.
*   **Problem**: As the number of tables grows, the number of permutations grows factorially. Joining 20 tables could take minutes just to *plan*!
*   **Solution**: The `join_collapse_limit` variable controls this behavior.

**Implicit vs. Explicit Joins**
To the planner, these are often identical (if below the limit):
1.  **Implicit**: `FROM tab1, tab2, tab3 WHERE ...`
2.  **Explicit**: `FROM tab1 JOIN tab2 ON ... JOIN tab3 ON ...`

**How the Limit Works**
*   **Default**: Usually 8.
*   **Behavior <= Limit**: The planner "collapses" explicit `JOIN`s into the main list and tries to reorder them freely (Exhaustive Search).
*   **Behavior > Limit**: The planner **stops** trying to reorder the explicit joins. It assumes *you* wrote them in a specific order for a reason and processes them largely as written (or in blocks).

**The Trade-off**
*   **High Limit**:
    *   *Pros*: Finds the absolute best plan.
    *   *Cons*: Planning time can skyrocket for many tables.
*   **Low Limit** (or exceeding the default with many tables):
    *   *Pros*: Planning is instant.
    *   *Cons*: You might get a suboptimal plan if you wrote the joins in a bad order (e.g., joining two massive tables first instead of filtering them against smaller ones).

> **Practical Use**: If you have a query with 50 joins generated by an ORM and "Planning Time" is taking seconds, lower `join_collapse_limit` or rely on the fact that Postgres stops reordering after 8 tables by default. This forces the planner to trust the written order.
### Forcing Plans (The `enable_*` switches)
Sometimes you know better than the optimizer, or you want to debug *why* the optimizer refused to use a specific strategy (e.g., "Why didn't it use a Hash Join?").
PostgreSQL provides runtime variables to **discourage** specific node types.

**Common Switches**:
*   `enable_async_append`
*   `enable_bitmapscan`
*   `enable_gathermerge`
*   `enable_hashagg`
*   `enable_hashjoin`
*   `enable_incremental_sort`
*   `enable_indexscan`
*   `enable_indexonlyscan`
*   `enable_material`
*   `enable_memoize`
*   `enable_mergejoin`
*   `enable_nestloop`
*   `enable_parallel_append`
*   `enable_parallel_hash`
*   `enable_partition_pruning`
*   `enable_partitionwise_join`
*   `enable_partitionwise_aggregate`
*   `enable_presorted_aggregate`
*   `enable_seqscan`
*   `enable_sort`
*   `enable_tidscan`
*   `enable_group_by_reordering`

**How to use them**:
```sql
SET enable_hashjoin TO off;
EXPLAIN SELECT * ...;
```

**Important Caveat**:
Turning a switch "off" does **not** make it impossible.
*   It simply adds a massive penalty cost (usually 10^10) to that node type.
*   If *no other option is possible* (e.g., you disabled hash, merge, and nestloop), PostgreSQL will *still* use one of them to execute the query, but the cost will look astronomical in the plan.

**Example: Forcing a specific join**
```sql
-- Standard Plan: Hash Join
EXPLAIN SELECT * FROM a, b WHERE a.id = b.id;

-- Force Merge Join
SET enable_hashjoin = off;
EXPLAIN SELECT * FROM a, b WHERE a.id = b.id;

-- Force Nested Loop (Desperation)
SET enable_mergejoin = off;
EXPLAIN SELECT * FROM a, b WHERE a.id = b.id;
```

> **Warning**: Never use these switches globally (`postgresql.conf`). Use them only for debugging or locally within a specific transaction/session to fix a pathological query. Disabling `enable_seqscan` globally, for example, could crash the system's ability to run simple maintenance queries.

## Genetic Query Optimization (GEQO)
When a query joins a huge number of tables, "Exhaustive Search" becomes impossible (12 tables = millions of permutations).
At this point, PostgreSQL switches to **GEQO** (Genetic Query Optimizer).

### How it Works (Evolutionary Algorithm)
1.  **Inspiration**: Derived from the "Traveling Salesman Problem".
2.  **Encoding**: Joins are encoded as integer strings (e.g., `4-1-3-2` means Join 4 & 1, then 3, then 2).
3.  **Process**:
    *   Generate a pool of random plans.
    *   Evaluate fitness (cost).
    *   Discard bad plans.
    *   "Breed" new plans from the genes of the best ones.
    *   Repeat.

### Configuration
*   **`geqo`**: Main switch (`on`/`off`). Default is `on`.
*   **`geqo_threshold`**: The table count trigger. Default is **12**.
    *   If you join < 12 tables, standard exhaustive search is used.
    *   If you join >= 12 tables, GEQO takes over.

### Pros and Cons
*   **Pros**: Prevents the planner from hanging for minutes trying to calculate the perfect plan for 20 tables.
*   **Cons**: **Non-Deterministic**. Because it uses random seeds and evolutionary cycles, running the *exact same SQL* twice might produce different execution plans.

> **Advice**: Avoid GEQO if possible. If you are hitting the 12-table limit, consider if you can simplify the query or use `join_collapse_limit` to force a specific structure. If you *must* use it, be aware that performance stability might vary slightly.

## Partitioning Data
Partitioning splits a large table into smaller, more manageable pieces. While a single PostgreSQL table can comfortably hold 32TB+, maintaining such a beast (indexes, vacuum, etc.) is difficult.

### 1. Classical Inheritance (The "Old Way")
Before PostgreSQL 10, partitioning was done via **Table Inheritance**. It is useful to understand this mechanism because modern declarative partitioning is built on similar principles.

**Creating Inherited Tables**
```sql
-- 1. Create Parent
CREATE TABLE t_data (
    id SERIAL,
    t_date DATE,
    payload TEXT
);

-- 2. Create Children that INHERIT
CREATE TABLE t_data_2016 () INHERITS (t_data);
CREATE TABLE t_data_2015 () INHERITS (t_data);
```
*   **Result**: The child tables automatically have `id`, `t_date`, and `payload`. They also share the `id` sequence.
*   **Flexibility**: You can even add extra columns to specific children! (e.g., `CREATE TABLE t_data_2013 (special text) INHERITS (t_data);`)

**Querying the Data**
When you query the **Parent**, PostgreSQL basically queries *all* children and combines them.
```sql
EXPLAIN SELECT * FROM t_data;
```
**Output**:
```text
Append
  -> Seq Scan on t_data
  -> Seq Scan on t_data_2016
  -> Seq Scan on t_data_2015
  -> ...
```
**Key Concept**: The `Append` node simply glues the results together. To the optimizer, it looks like a `UNION ALL` of all partitions.

> **Note**: While Inheritance provides the structure, it doesn't automatically route data or prune partitions. In the old days, you needed Triggers and Check Constraints. Modern PostgreSQL (see next section) handles this much better.

### 2. Applying Table Constraints (Constraint Exclusion)
By default, PostgreSQL scans *every* child table because it doesn't know that `t_data_2016` contains only 2016 data. To the database, it's just a name.
To enable optimization, we must add **CHECK constraints**.

**Adding Constraints**
```sql
ALTER TABLE t_data_2013 ADD CHECK (t_date < '2014-01-01');
ALTER TABLE t_data_2014 ADD CHECK (t_date >= '2014-01-01' AND t_date < '2015-01-01');
ALTER TABLE t_data_2015 ADD CHECK (t_date >= '2015-01-01' AND t_date < '2016-01-01');
ALTER TABLE t_data_2016 ADD CHECK (t_date >= '2016-01-01' AND t_date < '2017-01-01');
```

**The Effect: Constraint Exclusion**
Now, if we query for a specific date:
```sql
EXPLAIN SELECT * FROM t_data WHERE t_date = '2016-01-04';
```
**Optimized Output**:
```text
Append
  -> Seq Scan on t_data (Empty parent)
  -> Seq Scan on t_data_2016 (Matches constraint!)
```
**Observation**: Tables `2013`, `2014`, and `2015` are **gone**.
*   **Mechanism**: The planner looks at the `WHERE` clause (`t_date = ...`).
*   It compares it against the `CHECK` constraints of all children.
*   If `WHERE` conflicts with `CHECK` (e.g., `2016` date vs `2015` range), the planner proves the table *cannot* contain the data and **excludes** it from the plan.

> **Crucial Requirement**: For this to work, constraints must be **valid** and **immutable**. If you disable `constraint_exclusion` in `postgresql.conf` (default is `partition` or `on`), this optimization stops.
### 3. Modifying Inherited Structures
Managing partitioned tables involves schema changes.
*   **Columns**: Propagate **Automatically**.
    *   `ALTER TABLE parent ADD COLUMN x int` -> Adds `x` to *all* child tables.
*   **Indexes**: Do **NOT** propagate automatically.
    *   If you create an index on the Parent, it stays on the Parent.
    *   **Task**: You must manually create indexes on every single partition.
    *   **Pro**: Flexibility (Partition 2016 can have different indexes than 2015).
    *   **Con**: Maintenance overhead.

> **Note**: This applies to "Classical Inheritance". Modern "Declarative Partitioning" (PostgreSQL 10+) handles indexes much better (indexes on parent are automatically propagated).

### 4. Moving Tables In and Out (Archiving)
One of the biggest advantages of partitioning is **Instant Data Management**.
You can "move" millions of rows from "Active" to "History" instantly, not by `DELETE` + `INSERT`, but by changing the parent.

**The Scenario**
*   `t_data` (Active parent) has a child `t_data_2013`.
*   We want to move `t_data_2013` to `t_history` (Archive parent).

**The Process**
1.  **Create Archive Parent**:
    ```sql
    CREATE TABLE t_history (LIKE t_data); -- Copies structure perfectly
    ```
2.  **Detach from Active**:
    ```sql
    ALTER TABLE t_data_2013 NO INHERIT t_data;
    ```
3.  **Attach to History**:
    ```sql
    ALTER TABLE t_data_2013 INHERIT t_history;
    ```

**Impact**
*   **Zero Data Movement**: The physical files on disk remain untouched. Only metadata is updated.
*   **Atomic**: Wrap it in `BEGIN; ... COMMIT;` to make the switch instantaneous for users.
*   **Performance**: Queries on `t_data` effectively "lose" the 2013 data instantly (Constraint Exclusion no longer sees the table), speeding up queries that scan the active set.

### 5. Cleaning Up Data (Fast Deletes)
Partitioning provides a massive advantage for data retention policies (e.g., "Keep 5 years of data").
*   **The Problem with DELETE**: Running `DELETE FROM table WHERE date < '2014-01-01'` is slow, generates massive WAL logs, and leaves "dead tuples" requiring Vacuum.
*   **The Partitioning Solution**: `DROP TABLE`.

**Dropping a Partition**
```sql
DROP TABLE t_data_2014;
```
*   **Speed**: Instant (millisecond), regardless of whether it held 1 million vs 1 billion rows.
*   **Overhead**: Zero VACUUM needed. It unlinks the file on disk.

**Dropping the Parent**
If you try to drop the parent table:
```sql
DROP TABLE t_data;
-- ERROR: cannot drop table t_data because other objects depend on it
```
PostgreSQL protects child tables. To delete the parent and *all* children, you must use `CASCADE`:
```sql
DROP TABLE t_data CASCADE;
-- NOTICE: drop cascades to table t_data_2016...
```

> **Summary of Classical Partitioning**: It works via Inheritance, Check Constraints, and Constraint Exclusion optimization. While flexible, it required manual management. Next, we look at the modern way.

## Understanding Modern Partitioning (Declarative)
Since PostgreSQL 10, **Declarative Partitioning** is the standard. It automates much of the manual work required by the old inheritance method.

### 1. Partitioning Strategies
PostgreSQL supports three main strategies:
1.  **Range Partitioning**: For time-series or sequential data (e.g., Dates, IDs).
2.  **List Partitioning**: For finite sets of values (e.g., Country Code, Status).
3.  **Hash Partitioning**: For even distribution of data (Load Balancing) across multiple disks.

### 2. Example: Range Partitioning
**Creating the Parent**
You no longer use `INHERITS`. You define the strategy upfront.
```sql
CREATE TABLE data (
    payload integer
) PARTITION BY RANGE (payload);
```

**Creating Partitions**
You use `PARTITION OF` instead of `INHERITS`.
```sql
CREATE TABLE negatives PARTITION OF data
    FOR VALUES FROM (MINVALUE) TO (0);

CREATE TABLE positives PARTITION OF data
    FOR VALUES FROM (0) TO (MAXVALUE);
```

### 3. Key Features
*   **Automatic Routing**: `INSERT INTO data` automatically routes the row to `positives` or `negatives`. No triggers needed.
*   **Row Movement (Update)**: If you update a row (`payload = -10`), PostgreSQL (v11+) automatically moves it from `positives` to `negatives`.
*   **Index Propagation**: Creating an index on the Parent (`data`) automatically creates indexes on all current and future partitions.
    ```sql
    CREATE INDEX idx_payload ON data (payload);
    -- Automatically propagates to 'positives' and 'negatives'
    ```
*   **Default Partition**: You can create a "catch-all" partition for data that doesn't fit elsewhere.
    ```sql
    CREATE TABLE p_def PARTITION OF data DEFAULT;
    ```

> **Takeaway**: Modern partitioning is far superior to classical inheritance. It handles routing, indexes, and constraints automatically. Always use this for new projects.
### 4. Utilizing List Partitioning
List partitioning is ideal for distinct, finite values (e.g., Country, Status, Department).

**Example: Partitioning by Country**
```sql
CREATE TABLE t_turnover (
    id SERIAL,
    country TEXT,
    turnover NUMERIC
) PARTITION BY LIST (country);

-- Single Value
CREATE TABLE t_austria PARTITION OF t_turnover FOR VALUES IN ('Austria');
CREATE TABLE t_usa     PARTITION OF t_turnover FOR VALUES IN ('USA');

-- Multiple Values (Grouping)
CREATE TABLE t_dach    PARTITION OF t_turnover FOR VALUES IN ('Germany', 'Switzerland');
```

**Handling Unknowns (Default Partition)**
If you try to insert 'Uganda' above, it will **fail** because no partition matches.
Solution:
```sql
CREATE TABLE t_rest PARTITION OF t_turnover DEFAULT;
```
Now, all unmapped countries fall into `t_rest`.

**Verifying Routing (The `tableoid` Trick)**
You can use the hidden system column `tableoid` to actually see *where* your data landed.
```sql
INSERT INTO t_turnover (country, turnover) VALUES ('Uganda', 200)
RETURNING tableoid::regclass, *;
```
**Output**:
```text
 tableoid | id | country | turnover
----------+----+---------+----------
 t_rest   |  1 | Uganda  |      200
```
> **Tip**: `tableoid::regclass` is an incredibly useful debugging tool to verify which physical partition a row is currently residing in.

### 5. Utilizing Hash Partitioning
When your data has **no natural grouping** (no date ranges, no specific categories) or when your natural groups are uneven (e.g., 'China' has 1B people, 'Vatican' has 800), **Hash Partitioning** is the solution.

**The Goal**: Evenly distribute data across $N$ buckets.

**How it works**: Postgres hashes the partition key and divides it by a defined number (Modulus). The remainder determines the destination.

**Example: Creating 4 Even Buckets**
```sql
CREATE TABLE t_data (
    a int,
    b int
) PARTITION BY HASH (a);

-- We need to define the Modulus (total buckets) and Remainder (bucket ID) for each
CREATE TABLE t_hash_0 PARTITION OF t_data FOR VALUES WITH (MODULUS 4, REMAINDER 0);
CREATE TABLE t_hash_1 PARTITION OF t_data FOR VALUES WITH (MODULUS 4, REMAINDER 1);
CREATE TABLE t_hash_2 PARTITION OF t_data FOR VALUES WITH (MODULUS 4, REMAINDER 2);
CREATE TABLE t_hash_3 PARTITION OF t_data FOR VALUES WITH (MODULUS 4, REMAINDER 3);
```
> **Note**: If you insert data now, Postgres essentially "deals the cards," ensuring each table gets roughly ~25% of the rows, regardless of the values themselves.

---

### 6. Managing Partitions (Detach & Attach)
One of the most powerful features of partitioning is the ability to instantaneously add or remove massive chunks of data without slow `DELETE` or `INSERT` operations. This applies to **all** partition types (Range, List, Hash).

**Detaching a Partition**
This turns a partition back into a regular, standalone table. The data is **not deleted**, just disconnected from the parent.
```sql
ALTER TABLE t_data
DETACH PARTITION t_hash_3;
```
*   **Use Case**: Archiving data. You detach '2023_data', back it up, and drop it, all while the main table stays live.

**Attaching a Partition**
You can re-connect a table as a partition.
```sql
ALTER TABLE t_data
ATTACH PARTITION t_hash_3
FOR VALUES WITH (MODULUS 4, REMAINDER 3);
```
*   **Zero Downtime**: In modern Postgres, you can use `ALTER TABLE ... DETACH PARTITION ... CONCURRENTLY` to avoid locking the parent table, allowing queries to continue uninterrupted during the operation.

---

## Adjusting Parameters for Query Performance

### 1. Understanding `work_mem`
`work_mem` defines the maximum amount of memory PostgreSQL can use for **internal sort operations** and **hash tables** *per operation*.

**The Scenario**:
We create a table with 200,000 rows.
*   **Case A**: Small number of groups (2 groups: 'hans', 'paul').
*   **Case B**: Large number of groups (200,000 groups: unique IDs).

```sql
CREATE TABLE t_test (id serial, name text);
INSERT INTO t_test (name) SELECT 'hans' FROM generate_series(1, 100000);
INSERT INTO t_test (name) SELECT 'paul' FROM generate_series(1, 100000);
-- 200k rows total.
```

#### Case A: Few Groups (Fits in RAM)
```sql
SELECT name, count(*) FROM t_test GROUP BY 1;
---
HashAggregate
  (actual time=49.150..49.152 rows=2 loops=1)
  (cost=4082.00..4082.02 rows=2 width=13)
  Group Key: name
  Batches: 1  Memory Usage: 24kB
  -> Seq Scan on t_test
    (cost=0.00..3082.00 rows=200000 width=5)
(actual time=0.023..12.739 rows=200000 loops=1)
```
**Plan**:
*   **Strategy**: `HashAggregate`
*   **Memory Usage**: `24kB` (Tiny!)
*   **Why**: The hash table only needs to store 2 entries ('hans', 'paul'). It fits easily in default memory.

### What is a HashAggregate?
Think of `HashAggregate` as creating a set of **buckets** in memory to tally up results.
1.  **Scan**: The database reads the table row by row.
2.  **Hash**: It calculates a hash value for the group key (e.g., 'hans').
3.  **Bucket**: It looks up the bucket for that hash.
    *   **New Group**: If the bucket is empty, create a new entry and set count = 1.
    *   **Existing Group**: If found, just increment the counter (count = count + 1).

*   **Pros**: Very fast because it doesn't need to sort the data first.
*   **Cons**: Needs enough RAM to hold **all** unique groups at once. If you have millions of groups (like unique IDs), the buckets won't fit in memory, forcing it to spill to disk or failover to sorting.

#### Case B: Many Groups (Spills to Disk)
Now we group by `id` (200k unique groups).
```sql
SELECT id, count(*) FROM t_test GROUP BY 1;
```
**Plan (Default `work_mem` e.g. 4MB)**:
*   **Strategy**: `HashAggregate` (Postgres 13+) or `Sort` + `GroupAggregate` (Older).
*   **Disk Usage**: `3688kB` (Hash spilled to disk) or `External merge` (Sort spilled to disk).
*   **Performance**: **Slower** due to Disk I/O.

> **Note**: In older Postgres versions (or if `enable_hashagg = off`), the planner sees the hash won't fit and switches to **Sort** -> **GroupAggregate**. The Sort step will also spill to disk ("External merge").

#### The Fix: Increasing `work_mem`
If we give the query enough RAM to hold the hash table (or sort) in memory, performance improves.

```sql
SET work_mem TO '1 GB';
```

**New Plan**:
*   **Strategy**: `HashAggregate`
*   **Memory Usage**: `28689kB` (~28MB)
*   **Disk Usage**: **0kB** (Everything happened in RAM).
*   **Result**: Faster execution time.

> 4.  It is safe and recommended to `SET work_mem = '...'` locally for specific heavy reporting queries without changing the global default.

### 2. Speeding up Sorting
Before we sort data, we must understand *how* Postgres sorts it. `work_mem` is the bottleneck here too.

#### The 4 Sorting Algorithms
Postgres chooses an algorithm based on available memory and the query structure.

**1. External Merge (Disk) - The "Slow" One**
*   **Trigger**: Data > `work_mem`.
*   **Mechanism**: Sorts chunks of data in RAM, writes them to temp files on disk, then merges them back.
*   **Plan Signature**:
    ```sql
    Sort Method: external merge  Disk: 3736kB
    ```

**2. Quicksort (Memory) - The "Fast" One**
*   **Trigger**: Data < `work_mem`.
*   **Mechanism**: Sorts everything in RAM using Quicksort.
*   **Plan Signature**:
    ```sql
    Sort Method: quicksort  Memory: 12395kB
    ```

**3. Top-N Heapsort (Memory) - The "Smart" One**
*   **Trigger**: Query uses `LIMIT N` (e.g., `ORDER BY id, name LIMIT 10`).
*   **Mechanism**: Doesn't sort the whole table! It keeps a small "heap" structure of just the top 10 items found so far.
*   **Plan Signature**:
    ```sql
    Limit
    -> Sort
       Sort Method: top-N heapsort  Memory: 25kB
    ```

**4. Incremental Sort (Postgres 13+)**
*   **Trigger**: Data is *partially* sorted by an index.
*   **Example**: Index on `(id)`, Query `ORDER BY id, name`.
*   **Mechanism**: Data is already read in `id` order. Postgres only needs to sort the `name` column *within* each group of duplicate IDs.
*   **Plan Signature**:
    ```sql
    Incremental Sort
    Sort Key: id, name
    Presorted Key: id
    -> Index Scan using idx_id on t_test
    ```

#### Practical Optimization Strategy
1.  **Check the Plan**: Look for `Sort Method: external merge Disk`.
2.  **Increase Memory**: `SET work_mem` higher until it switches to `Sort Method: quicksort Memory`.
3.  **Use Limits**: If you only need 10 rows, ensure your application sends `LIMIT 10` so Postgres can use Heapsort.

> **Critical Warning**: `work_mem` is allocated **per operation**. If a complex query has 3 sorts and 2 hash joins, it could use `5 * work_mem`.
> *   **Do NOT** set global `work_mem` to 1GB on an OLTP server with 1000 concurrent connections (1000 * 1GB = Crash).
> *   **DO** set it locally for specific, isolated reporting tasks.

### 3. Speeding up Administrative Tasks
While `work_mem` handles daily queries, **`maintenance_work_mem`** handles the "heavy lifting" tasks like `CREATE INDEX`, `VACUUM`, and `ALTER TABLE`.

**The Role of Memory**
Most administrative tasks are just giant sorting operations in disguise.
*   **Creating an Index** = Sorting the column values + building a tree.
*   **Vacuum** = sorting dead row identifiers to remove them from indexes.

**Example: Impact on Index Creation**
```sql
-- 1. Low Memory (1 MB) -> Slow
SET maintenance_work_mem TO '1 MB';
CREATE INDEX idx_id ON t_test (id);
-- Time: ~104 ms (Spills to disk)

-- 2. High Memory (1 GB) -> Fast
SET maintenance_work_mem TO '1 GB';
CREATE INDEX idx_id2 ON t_test (id);
-- Time: ~46 ms (Sorts in RAM)
```
> **Tip**: You can safely set `maintenance_work_mem` much higher than `work_mem` globally because these tasks are rare and usually run one at a time (unlike user queries).

#### Parallel Index Creation
Since PostgreSQL 11, building B-Tree indexes isn't just about memory; it's about CPU too.

**The Parameter**: `max_parallel_maintenance_workers` (Default: 2)
This controls how many CPU cores can help build a single index.

*   **Small Tables**: 1 worker is enough.
*   **Large Tables**: Postgres splits the table into chunks, and multiple workers sort them concurrently.
*   **Result**: Massive speedup for creating indexes on big data.

> **Takeaway**: If you are migrating data or restoring a backup, increasing **both** `maintenance_work_mem` and `max_parallel_maintenance_workers` can reduce downtime significantly.

---

## Making Use of Parallel Queries
Since version 9.6, PostgreSQL can use multiple CPU cores to answer a single query.

### The Anatomy of a Parallel Plan
Let's analyze a simple count on a large table (25M rows).

```sql
EXPLAIN SELECT count(*) FROM t_parallel;
```
**Output**:
```sql
Finalize Aggregate
-> Gather (Workers Planned: 2)
   -> Partial Aggregate
      -> Parallel Seq Scan on t_parallel
```
1.  **Parallel Seq Scan**: Multiple workers read block ranges of the table simultaneously.
2.  **Partial Aggregate**: Each worker calculates its own count (e.g., Worker 1 counts 1M, Worker 2 counts 1M).
3.  **Gather**: The main process collects these partial results.
4.  **Finalize Aggregate**: The main process sums up the partial counts (1M + 1M = 2M) to get the final result.

### Controlling Parallelism
PostgreSQL decides how many workers to use based on table size and configuration.

#### 1. The "Per Gather" Limit
`max_parallel_workers_per_gather` (Default: 2)
This sets the upper limit of workers *per parallel node* in the plan.

#### 2. The Table Size Rule
Small tables don't need parallelism.
*   **Threshold**: `min_parallel_table_scan_size` (Default: 8MB).
*   **Scaling Rule**: The table size must **triple** to gain 1 additional worker.
    *   8MB = 0 workers (Too small)
    *   Example: 24MB = 1 worker, 72MB = 2 workers, etc. (The "x3" rule).

#### 3. Forcing Parallelism (The "Sledgehammer" Approach)
You can override the size-based logic for specific tables using `ALTER TABLE`.
```sql
ALTER TABLE t_parallel SET (parallel_workers = 9);
```
Now, Postgres will try to use 9 workers for this table, regardless of size (capped by `max_parallel_workers_per_gather`).

### Implementation Limits
If you ask for 9 workers, you might not get them.
```sql
Workers Planned: 9
Workers Launched: 7
```
**Why?** Two global limits exist:
1.  **`max_worker_processes`**: Total background processes allowed (for *everything*).
2.  **`max_parallel_workers`**: Total processes allowed for *parallel queries*.

> **Best Practice**: Set `max_worker_processes` to the number of CPUs on your server. Using more usually doesn't help because the bottleneck becomes the hardware threads.

### What Can PostgreSQL Do in Parallel?
Support for parallelism has grown steadily. As of modern versions, here are the operations that can benefit from multi-core execution:

**Queries (Read Operations)**
*   **Scans**: Sequential Scan, B-Tree Index Scan (**Only** B-Tree), Bitmap Heap Scan.
*   **Joins**: All join types (Nested Loop, Hash, Merge) can run in parallel.
*   **Aggregates**: `GROUP BY` and standard aggregates (`sum`, `count`, etc.).
*   **Unique**: `SELECT DISTINCT`.
*   **Append**: `UNION ALL` can process different branches in parallel.

**Maintenance (Write/Heavy Operations)**
*   **Indexes**: `CREATE INDEX` (B-Tree **Only**).
*   **Cleanup**: `VACUUM`.

> **Note**: Standard `ORDER BY` sorting is **not** fully parallelized yet. Parallel Index Creation relies on `max_parallel_maintenance_workers` (as discussed earlier), separate from the query workers.

### Parallelism in Practice: The "Communication Cost"
Often, developers are confused when they see a plan like this for a huge table:
```sql
EXPLAIN SELECT * FROM t_big_table;
-- Result: Simple "Seq Scan". No Gathering. No Parallelism.
```
**Why?**
Parallelism isn't free. The "worker" processes have to talk to the "main" process to send it the data. This **Inter-Process Communication (IPC)** is expensive.

*   **The Bottleneck**: Moving data between processes (Gathering) takes time.
*   **The Cost Parameter**: `parallel_tuple_cost` (Default: 0.1).
    *   This is the "tax" the optimizer adds for every single row that has to be shipped from a worker to the leader.

**The Balancing Act**
*   **Aggregation (`COUNT(*)`)**: Workers send **1 row** (the partial count) to the leader. IPC cost is **tiny**. Parallelism is a huge win.
*   **Selection (`SELECT *`)**: Workers send **millions of rows** to the leader. IPC cost is **massive**. It might be faster for a single process to just read it directly than to coordinate shipping millions of packets between processes.

> **Takeaway**: More cores $\neq$ Faster queries. If the overhead of coordination (IPC) outweighs the benefit of splitting the work, Postgres will (correctly) choose a single-threaded plan.

---

## Introducing Just-In-Time (JIT) Compilation
**JIT** is one of the most exciting modern features (Postgres 11+).

### The Concept: "Generic vs. Custom"
*   **Without JIT (Generic)**: When you install Postgres, it comes with a "one-size-fits-all" execution engine. It has to handle *any* possible query (Ints, Strings, Dates, JSON). It has a lot of `if` statements and branches to handle these possibilities. usage is generic.
*   **With JIT (Custom)**: At runtime, Postgres sees *exactly* what your query does (e.g., "Add 5 integer columns together"). It generates minimal, highly, highly optimized machine code **specifically for that one query**, reducing CPU overhead significantly.

> **Analogy**: It's the difference between buying a one-size-fits-all poncho (Generic) vs. having a tailor sew a suit exactly for your measurements right before you walk into the room (JIT).

### Configuring JIT
JIT relies on **LLVM** (Low-Level Virtual Machine). It is enabled by default in modern versions, but it's not "always on." It's triggered by **Query Cost**.

**Why not use it always?**
Compiling code takes time (e.g., 100ms). If your query only takes 10ms to run, compiling it for 100ms is a waste of time.

**The 3 Thresholds**
1.  **`jit_above_cost`** (Default: 100,000)
    *   **The Go/No-Go Signal**. If the query cost > 100k, JIT is activated.
2.  **`jit_optimize_above_cost`** (Default: 500,000)
    *   **The Optimizer**. If query cost > 500k, spend extra effort optimizing the generated code.
3.  **`jit_inline_above_cost`** (Default: 500,000)
    *   **Inlining**. If query cost > 500k, attempt to inline tiny functions and operators directly into the loop.

> **Summary**: JIT is a "Turbo Button" for expensive, CPU-intensive queries (like analytical reporting). For short, transactional queries (OLTP), it stays out of the way to avoid compilation overhead.

### JIT in Practice: A Real-World Comparison
Let's witness JIT in action by running a heavy mathematical query on 50 million rows.

**The Setup**
We generate 50M rows of random integers. We disable parallel workers (`max_parallel_workers_per_gather = 0`) to ensure we are testing JIT's CPU efficiency, not parallel distribution.
```sql
CREATE TABLE t_jit AS
SELECT (random()*10000)::int AS x,
       (random()*100000)::int AS y,
       (random()*1000000)::int AS z
FROM generate_series(1, 50000000) AS id;

VACUUM ANALYZE t_jit; -- Ensure hint bits are set for a fair CPU test
SET max_parallel_workers_per_gather TO 0;
```

**The Query**
A nightmare for the CPU: lots of floating point math (`pi()`, division, stats).
```sql
SELECT avg(z+y-pi()), avg(y-pi()), max(x/pi())
FROM t_jit
WHERE ((y+z)) > ((y-x)*0.000001);
```

#### Test 1: JIT Disabled (The Interpreter)
```sql
SET jit TO off;
EXPLAIN (ANALYZE, VERBOSE) ...;
```
**Sample Plan**:
```text
Aggregate  (cost=1936901.68..1936901.69 rows=1)
  (actual time=20617.425..20617.425 rows=1 loops=1)
  ->  Seq Scan on t_jit  (cost=0.00..1520244.00 rows=16M)
        (actual time=0.061..15322.555 rows=50M loops=1)
        Filter: (((y + z))::numeric > (((y - x))::numeric * 0.000001))
Execution Time: 20617.473 ms
```
The generic engine iterates row by row, handling every expression through generic function pointers.

#### Test 2: JIT Enabled (The Turbo)
```sql
SET jit TO on;
EXPLAIN (ANALYZE, VERBOSE) ...;
```
**Sample Plan**:
```text
Aggregate  (cost=1936901.68..1936901.69 rows=1)
  (actual time=15585.788..15585.789 rows=1 loops=1)
  ->  Seq Scan on t_jit
        (actual time=81.991..13396.227 rows=50M loops=1)
Planning Time: 0.135 ms
JIT:
  Functions: 5
  Options: Inlining true, Optimization true, Expressions true, Deforming true
  Timing: Generation 2.942 ms, Inlining 15.717 ms, Optimization 40.806 ms, Emission 25.233 ms, Total 84.698 ms
Execution Time: 15588.851 ms
```
**Result**: **~25% Faster**.

**Analyzing the JIT Overhead**
If you look at the plan with `EXPLAIN (ANALYZE, VERBOSE)`, you will see the cost of this speedup:
```yaml
JIT:
  Functions: 5
  Options: Inlining true, Optimization true, Expressions true, Deforming true
  Timing: Generation 2.9 ms, Inlining 15.7 ms, Optimization 40.8 ms, Emission 25.2 ms, Total 84.7 ms
```
*   **The Cost**: It took **~85ms** to compile the code.
*   **The Benefit**: It saved **5,000ms** in execution.
*   **Verdict**: Worth it.

> **Final Chapter Summary**:
> You have now mastered the PostgreSQL Optimizer!
> 1.  **Optimization**: Understanding Join types, Join Order, and Transformations.
> 2.  **Execution**: Reading Plans (`EXPLAIN`), handling Memory (`work_mem`).
> 3.  **Architecture**: Utilizing Partitioning for massive data.
> 4.  **Modern Speed**: Leveraging Parallel Queries and JIT Compilation.


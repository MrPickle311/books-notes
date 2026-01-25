# Chapter 4: Handling Advanced SQL

This chapter covers modern SQL features that move business logic into the database, improving efficiency and reducing client code.

## 4.1 Supporting Range Types
Handling time intervals or numeric ranges usually involves two columns (`start`, `end`) and messy `CHECK` constraints. PostgreSQL **Range Types** solve this elegantly.

### The Old Way vs The Range Way
*   **Old Way**:
    ```sql
    CREATE TABLE t_price (
        price_from DATE,
        price_until DATE,
        CHECK (price_until >= price_from)
    );
    ```
    *   *Problem*: Hard to query for overlaps, requires manual constraints.
*   **The Range Way**:
    ```sql
    CREATE TABLE t_price_range (
        price_range daterange
    );
    ```
    *   *Benefit*: Single column, implicit validation (start <= end), powerful operators.

### Creating Ranges
*   **inclusive/exclusive**:
    *   `[` or `]` = Inclusive.
    *   `(` or `)` = Exclusive.
*   **Examples**:
    ```sql
    SELECT int4range(10, 20);                  -- [10,20)  (10 included, 20 excluded)
    SELECT daterange('2025-10-04', '2027-05-01'); -- [2025-10-04, 2027-05-01)
    ```

### Important Range Operators
PostgreSQL provides specialized operators for these types.

| Operator | Description | Example |
| :--- | :--- | :--- |
| `@>` | Contains Range/Element | `int4range(1,10) @> 5` (True) |
| `<@` | Is Contained By | `5 <@ int4range(1,10)` (True) |
| `&&` | Overlap | `range1 && range2` (True if they share points) |
| `<<` | Strictly Left Of | `range1 << range2` (True if range1 ends before range2 starts) |
| `>>` | Strictly Right Of | `range1 >> range2` |
| `-` | Difference | `range1 - range2` (Subtracts the interval) |
| `*` | Intersection | `range1 * range2` (Returns shared interval) |
| `+` | Union | `range1 + range2` |

### Multiranges (PostgreSQL 14+)
Allows storing *multiple* non-contiguous ranges in a single column.
```sql
SELECT int4multirange('{(10, 20), (30, 40)}');
-- Result: {[11,20),[31,40)}
```
*   **Automatic Folding**: If ranges touch/overlap, PostgreSQL merges them automatically.
    *   `{(10, 20), (15, 30)}` becomes `{[10, 30)}`.

### Tip: Usage in Apps
If your app doesn't support Range types natively, use a **View** to abstract them:
```sql
CREATE VIEW v_prices AS
SELECT lower(price_range) as start, upper(price_range) as end FROM t_price_range;
```

---

## 4.2 Grouping Sets, Rollup, and Cube
Standard `GROUP BY` is great, but Reporting often needs "Subtotals" and "Grand Totals".

### ROLLUP
`ROLLUP` is a shorthand for defining **hierarchical** grouping sets. It is useful for calculating subtotals and grand totals in a defined order (e.g., `Year -> Month -> Day`).

**How it works**:
`ROLLUP (a, b, c)` generates the following grouping sets:
1.  `(a, b, c)`: Detailed aggregation (e.g., specific day).
2.  `(a, b)`: Subtotal for `b` (e.g., specific month).
3.  `(a)`: Subtotal for `a` (e.g., specific year).
4.  `()`: Grand Total (all rows).

**Example**:
```sql
SELECT region, country, AVG(production)
FROM t_oil
GROUP BY ROLLUP (region, country)
ORDER BY region, country;
```
*   **Output Levels**:
    1.  **Level 1**: `Middle East`, `Iran`, `3631` (Specific Country in Region)
    2.  **Level 1**: `Middle East`, `Oman`, `586` (Specific Country in Region)
    3.  **Level 2**: `Middle East`, `NULL`, `2142` (**Subtotal** for Middle East)
    4.  **Level 3**: `NULL`, `NULL`, `2607` (**Grand Total** for entire table)
*   **Key Behavior**: It moves from "right to left", dropping the last column from the group key each step. It does **not** generate `(country)` alone (independent of region). For that, you would need `CUBE`.

### CUBE
Generates **ALL possible combinations** of groupings (The Power Set).
While `ROLLUP` assumes a hierarchy (`Year > Month`), `CUBE` assumes that dimensions are independent (cross-tabulation).

**How it works**:
`CUBE (a, b)` calculates **all permutations**: `2^N` grouping sets.
1.  `(a, b)`: Region + Country
2.  `(a)`: Region only (Subtotal)
3.  `(b)`: Country only (Subtotal across all regions)
4.  `()`: Grand Total

**Example**:
```sql
SELECT region, country, sum(production)
FROM t_oil
GROUP BY CUBE (region, country);
```
**Output**:
*   `Middle East, Iran`: Total for Iran in ME.
*   `Middle East, NULL`: Total for all ME.
*   `NULL, Iran`: Total for Iran (Global context - same as above because Iran is only in ME, but critical if "East Coast" exists in multiple countries).
*   `NULL, NULL`: Grand Total.

**Detailed Example Output**:
*   `CUBE` injects rows for all aggregation levels.
*   In the example below, we filter for specific countries to keep it readable, but you can see `NULL` appearing in `region` and `country` columns representing aggregates.
```text
 region        | country |          avg
---------------+---------+-----------------------
 Middle East   | Iran    | 3631.6956521739130435
 Middle East   | Oman    | 586.4545454545454545
 Middle East   |         | 2142.9111111111111111  <-- Region Total
 North America | Canada  | 2123.2173913043478261
 North America | USA     | 9141.3478260869565217
 North America |         | 5632.2826086956521739  <-- Region Total
               | Canada  | 2123.2173913043478261  <-- Country Total (Global)
               | Iran    | 3631.6956521739130435  <-- Country Total (Global)
               | Oman    | 586.4545454545454545   <-- Country Total (Global)
               | USA     | 9141.3478260869565217  <-- Country Total (Global)
               |         | 3906.7692307692307692  <-- Grand Total
```
*   **Observation**:
    *   Rows with `Region` + `Country`: Standard Group By.
    *   Rows with `Region` + `NULL`: Subtotals for Region.
    *   Rows with `NULL` + `Country`: Subtotals for Country (ignoring Region).
    *   Row with `NULL` + `NULL`: The average of the entire dataset.

**ROLLUP vs CUBE**:
*   **ROLLUP**: Good for **Drill-down** Hierarchies (State -> City). Generates `N+1` sets.
*   **CUBE**: Good for **Cross-Tab** reports (Product vs Region). Generates `2^N` sets. (Calculates every possible subtotal).

### GROUPING SETS
Manually specify exactly which aggregates to calculate. `ROLLUP` and `CUBE` are just shortcuts for this.
```sql
SELECT region, country, avg(prod)
FROM t_oil
GROUP BY GROUPING SETS ( (), region, country );
```
*   Calculates: Grand Total `()`, Region Total, Country Total. Does *not* calculate (Region, Country).

**Detailed Example Output**:
```text
 region        | country |          avg
---------------+---------+-----------------------
 Middle East   |         | 2142.9111111111111111  <-- Region Total
 North America |         | 5632.2826086956521739  <-- Region Total
               | Canada  | 2123.2173913043478261  <-- Country Total
               | Iran    | 3631.6956521739130435  <-- Country Total
               | Oman    | 586.4545454545454545   <-- Country Total
               | USA     | 9141.3478260869565217  <-- Country Total
               |         | 3906.7692307692307692  <-- Grand Total ()
```
*Note: The specific combination (Region, Country) is missing because we didn't ask for it!*

### Performance (MixedAggregate vs GroupAggregate)
Grouping Sets logic can be complex for the database engine.
*   **Old Way (`GroupAggregate`)**: It had to SORT the data first, then group it. Sorting is `O(N log N)`.
*   **New Way (`MixedAggregate`)**: It uses **Hash Tables** (`HashAggregate`) where possible to avoid sorting. This is typically `O(N)` and much faster, provided there is enough memory (`work_mem`).

**EXPLAIN Analysis**:
```text
QUERY PLAN
-----------------------------------------------------------
 Sort
   Sort Key: region, country
   ->  MixedAggregate  (cost=0.00..18.17 rows=17 width=52)
         Hash Key: region
         Hash Key: country
         Group Key: ()
         ->  Seq Scan on t_oil
               Filter: (country = ANY ('{USA,Canada,Iran,Oman}'::text[]))
```
*   **Interpretation**:
    1.  **Seq Scan**: Filters the rows (USA, Canada...).
    2.  **MixedAggregate**: The workhorse. It calculates multiple groups at once.
        *   `Hash Key: region`: Uses a hash table to aggregate by region.
        *   `Hash Key: country`: Uses a hash table to aggregate by country.
        *   `Group Key: ()`: Maintains a running total for the Grand Total.
    3.  **Sort**: Finally, it sorts the result for the user (only 17 aggregated rows, so this is cheap).

*   **Tip**: If you see `GroupAggregate` and want speed, try increasing `work_mem` or ensuring `enable_hashagg = on`.

### Filter Clause (Pivoting)
Use `FILTER` instead of `CASE WHEN` for pivoting data inside aggregates.
```sql
SELECT region,
       avg(prod) FILTER (WHERE year < 1990) AS old_avg,
       avg(prod) FILTER (WHERE year >= 1990) AS new_avg
FROM t_oil
GROUP BY ROLLUP(region);
```
**Tip: WHERE vs FILTER?**
*   **Use `WHERE`**: When a condition applies to **ALL** aggregates.
    *   *Why?*: It filters data *before* aggregation, reducing the workload earlier (scan level).
*   **Use `FILTER`**: When you need **partial/different** conditions for specific columns (Pivoting).
    *   *Why?*: It allows one query to calculate "1990 data" and "2000 data" side-by-side. Trying to do this with `WHERE` would require two separate queries.

---

## 4.3 Ordered Sets (Median & Mode)
**What are they?**
Normal aggregates (`SUM`, `AVG`) don't care about order. `1+2` is the same as `2+1`.
**Ordered Sets** are special aggregates that **require** the input data to be sorted to calculate the result effectively.

**Why use them?**
*   **The Median Problem**: The Average (`AVG`) is sensitive to outliers (one billionaire skews the average income). The **Median** (the guy in the middle) is a distinct, robust metric for "inequality" or "typical value".
*   **Implementation**: Calculating the median requires sorting the group (`ORDER BY`) and picking the value at the 50% position.
*   **Syntax**: We use `WITHIN GROUP (ORDER BY ...)` to tell PostgreSQL exactly how to sort the data *before* the function picks the winner.

### Median (`percentile_disc` / `percentile_cont`)
Finding the middle value (50th percentile).
*   **`percentile_disc(0.5)`**: Discrete. Returns an *actual value* from the dataset.
*   **`percentile_cont(0.5)`**: Continuous. Interpolates the value if needed (e.g., returns 3.5 if middle is between 3 and 4).

**Syntax**:
```sql
SELECT region,
       percentile_disc(0.5) WITHIN GROUP (ORDER BY production)
FROM t_oil
GROUP BY 1;
```
**Sample Output**:
```text
    region     | percentile_disc
---------------+-----------------
 Middle East   |            1082
 North America |            3054
```

### Mode
Finds the **most frequent value** (closest thing to a "common" value).
```sql
SELECT mode() WITHIN GROUP (ORDER BY production) FROM t_oil;
```
**Sample Output**:
```text
 mode
------
   48
```
*Note: If multiple values appear equally often (e.g., 5 times), `mode()` arbitrarily picks one.*

---

## 4.4 Hypothetical Aggregates
Answers: *"What would the rank be IF this row existed?"*
*   **Function**: `rank(val) WITHIN GROUP (...)`
```sql
SELECT rank(9000) WITHIN GROUP (ORDER BY production DESC)
FROM t_oil;
```
*   **Meaning**: "If we inserted a row with production=9000, what rank position would it get?" (e.g., 27th place).

**Tip: Handling NULLs (`NULLS LAST`)**
By default, `ASC` sort puts NULLs at the end, but `DESC` puts NULLs at the *beginning* (conceptually "larger" than numbers).
*   **Best Practice**: Explicitly specify `NULLS LAST` to ensure NULLs don't mess up your top rankings.
    ```sql
    rank(9000) WITHIN GROUP (ORDER BY production DESC NULLS LAST)
    ```

---

## 4.5 Window Functions and Analytics
Aggregation hides rows. Window functions add analytics to **every row** without hiding them.
**Syntax**: `function() OVER (PARTITION BY ... ORDER BY ... window_frame)`

### Basic Concepts
*   `OVER ()`: Apply to entire dataset.
*   `PARTITION BY country`: Reset calculation for each country.
*   `ORDER BY year`: Calculate running/cumulative values.

**Caution: The `OVER` Clause is Mandatory**
If you use an aggregate function (like `avg`) alongside regular columns *without* `GROUP BY` or `OVER`, PostgreSQL will error.
```sql
SELECT country, avg(production) FROM t_oil;
-- ERROR: column "t_oil.country" must appear in the GROUP BY clause
```
*   *Why?*: Sstandard SQL requires knowing the scope of the average. The `OVER` clause defines this scope (the window) so it can coexist with non-aggregated columns.

### Partitioning Data (`PARTITION BY`)
A `PARTITION BY` splits the data into groups *conceptually* for the calculation, but keeps all rows.

**Example 1: Average by Country**
Calculate the average production *per country* next to each row.
```sql
SELECT country, year, production,
       avg(production) OVER (PARTITION BY country) as country_avg
FROM t_oil;
```
*   *Result*: All 'Canada' rows will show the Canada average (2123). All 'Iran' rows will show the Iran average (3631).
**Sample Output**:
```text
 country | year | production | country_avg
---------+------+------------+-------------
 Canada  | 1965 |        920 |   2123.2173
 Canada  | 2010 |       3332 |   2123.2173
 ...
 Iran    | 1966 |       2132 |   3631.6956
 Iran    | 2010 |       4352 |   3631.6956
```
*   *Note*: The rows are NOT implicitly sorted unless you add `ORDER BY` to the main query.

**Example 2: Partition by Expression**
You can partition by logic (True/False groups).
```sql
SELECT year, production,
       avg(production) OVER (PARTITION BY year < 1990) as period_avg
FROM t_oil
WHERE country = 'Canada';
```
*   *Result*: Two groups.
    1.  `year < 1990`: Shows the "Old Era" average (1631) for 1965-1989 rows.
    2.  `year >= 1990`: Shows the "New Era" average (2708) for 1990+ rows.
**Sample Output**:
```text
 year | production | period_avg
------+------------+-----------
 1965 |        920 |  1631.60
 1966 |       1012 |  1631.60
 ...
 1990 |       1967 |  2708.47
 1991 |       1983 |  2708.47
```

**Ordering Data inside a Window**
`ORDER BY` inside an `OVER` clause changes the calculation from "Global" to "Running/Cumulative".

**Example 1: Running Minimum**
Calculate the minimum production *seen so far* for each country.
```sql
SELECT country, year, production,
       min(production) OVER (PARTITION BY country ORDER BY year) as running_min
FROM t_oil
WHERE year BETWEEN 1978 AND 1983 AND country IN ('Iran', 'Oman');
```
**Sample Output**:
```text
 country | year | production | running_min
---------+------+------------+-------------
 Iran    | 1978 |       5302 |        5302  (First row, min is itself)
 Iran    | 1979 |       3218 |        3218  (New low found!)
 Iran    | 1980 |       1479 |        1479  (New low found!)
 Iran    | 1981 |       1321 |        1321  (New low found!)
 Iran    | 1982 |       2397 |        1321  (2397 > 1321, so 1321 persists)
 Iran    | 1983 |       2454 |        1321
 ...
 Oman    | 1978 |        314 |         314  (Partition reset for Oman)
```

**Crucial Logic: With vs Without `ORDER BY`**
*   **Without `ORDER BY`**: The window is the **Entire Partition**. Result is static.
*   **With `ORDER BY`**: The window is **Range from Start to Current Row**. Result is dynamic/running.

```sql
SELECT year, production,
       min(production) OVER () as global_min,            -- 1321 (Static min of all rows)
       min(production) OVER (ORDER BY year) as running_min -- Changes as we go
FROM t_oil WHERE country = 'Iran' AND year BETWEEN 1978 AND 1983;
```
**Sample Output**:
```text
 year | production | global_min | running_min
------+------------+------------+-------------
 1978 |       5302 |       1321 |        5302
 1979 |       3218 |       1321 |        3218
 ...
 1981 |       1321 |       1321 |        1321
 1982 |       2397 |       1321 |        1321
```

### Ranking Functions: `rank()` and `dense_rank()`
The `rank()` and `dense_rank()` functions are ranking functions.

**`rank()`**
The `rank()` function returns the number of the current row within its window. The counting starts at 1.
*   **Gap Behavior**: If there are ties (equal values), the rank stays the same, but the *next* rank jumps (e.g., 1, 2, 2, 4).

**Example: `rank()`**
```sql
SELECT
    year,
    production,
    rank() OVER (ORDER BY production)
FROM t_oil
WHERE country = 'Other Middle East'
ORDER BY rank
LIMIT 7;
```
**Sample Output**:
```text
 year | production | rank
------+------------+------
 2001 |         47 |    1
 2004 |         48 |    2  <-- Tie
 2002 |         48 |    2  <-- Tie
 1999 |         48 |    2  <-- Tie (Rank stays 2)
 2000 |         48 |    2
 2003 |         48 |    2
 1998 |         49 |    7  <-- Jumps to 7 (1 + 5 ties)
```
The rank column will number those tuples in your dataset. Note that many rows in my sample are equal. Therefore, the rank will jump from 2 to 7 directly, because many production values are identical.

**`dense_rank()`**
If you want to avoid gaps, the `dense_rank()` function is the way to go.
*   **No-Gap Behavior**: Ties share the same rank, but the next rank is simply the next integer (e.g., 1, 2, 2, 3). PostgreSQL packs the numbers more tightly.

**Example: `dense_rank()`**
```sql
SELECT
    year,
    production,
    dense_rank() OVER (ORDER BY production)
FROM t_oil
WHERE country = 'Other Middle East'
ORDER BY dense_rank
LIMIT 7;
```
**Sample Output**:
```text
 year | production | dense_rank
------+------------+------------
 2001 |         47 |          1
 2004 |         48 |          2
 2002 |         48 |          2
 2003 |         48 |          2
 ...
 1998 |         49 |          3  <-- Next number is 3, not 7
```
There will be no more gaps.

**`row_number()`**
Simply counts rows (1, 2, 3, 4) regardless of content.
**`ntile(n)`**
Splits data into `n` buckets (quartiles, deciles, etc.) as evenly as possible.

**Example: Quartiles (4 buckets)**
```sql
SELECT
    year,
    production,
    ntile(4) OVER (ORDER BY production) as  quartile
FROM t_oil
WHERE country = 'Other Middle East'
ORDER BY quartile;
```
**Sample Output**:
```text
 year | production | quartile
------+------------+----------
 2001 |         47 |        1
 2004 |         48 |        1
 2002 |         48 |        2
 2003 |         48 |        2
 1999 |         48 |        3
 2000 |         48 |        3
 1998 |         49 |        4
```
Here, 7 rows are split into 4 buckets. PostgreSQL tries to balance the buckets:
*   Bucket 1: 2 rows
*   Bucket 2: 2 rows
*   Bucket 3: 2 rows
*   Bucket 4: 1 row
(Total 7 rows)

### Value Access Functions
These functions allow you to access data from other rows relative to the current row without a self-join.

**1. `lag(column, offset)`**
Accesses data from a previous row. This is incredibly useful for calculating growth, decline, or generic deltas.

**Example: Calculating Year-over-Year Change**
```sql
SELECT country, year, production,
       lag(production, 1) OVER (PARTITION BY country ORDER BY year) as prev_year_prod,
       production - lag(production, 1) OVER (PARTITION BY country ORDER BY year) as change
FROM t_oil
WHERE country = 'USA' AND year BETWEEN 1970 AND 1973;
```
**Sample Output**:
```text
 country | year | production | prev_year_prod | change
---------+------+------------+----------------+--------
 USA     | 1970 |      11297 |           NULL |   NULL  (First row causes NULL, as there is no prior year)
 USA     | 1971 |      11156 |          11297 |   -141
 USA     | 1972 |      11185 |          11156 |     29
 USA     | 1973 |      10946 |          11185 |   -239
```

**2. `lead(column, offset)`**
Accesses data from a following row (the reverse of `lag`).

**Example: Anticipating Next Year's Output**
```sql
SELECT country, year, production,
       lead(production, 1) OVER (PARTITION BY country ORDER BY year) as next_year_prod
FROM t_oil
WHERE country = 'Canada' AND year BETWEEN 1980 AND 1982;
```
**Sample Output**:
```text
 country | year | production | next_year_prod
---------+------+------------+----------------
 Canada  | 1980 |       1764 |           1610 (Value from 1981)
 Canada  | 1981 |       1610 |           1590 (Value from 1982)
 Canada  | 1982 |       1590 |           NULL (No following row in this range)
```

**3. Other Value Functions**
*   **`first_value(col)`**: Returns the value from the first row in the window frame.
*   **`last_value(col)`**: Returns the value from the last row in the window frame.
*   **`nth_value(col, n)`**: Returns the value from the `n`-th row in the window frame.

### Using Sliding Windows
So far, the window we have used inside our query has been static. However, for calculations such as a **moving average**, this is not enough. A moving average needs a sliding window that moves along as data is processed.

**Example 1: Moving Average (1 Previous, Current, 1 Following)**
Here is an example of how a moving average (using `min` in this specific case, though `avg` is typical) can be achieved:
```sql
SELECT
    country,
    year,
    production,
    min(production) OVER (
        PARTITION BY country
        ORDER BY year
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) AS smoothed_min
FROM
    t_oil
WHERE
    year BETWEEN 1978 AND 1983
    AND country IN ('Iran', 'Oman');
```
*   *Note*: The most important thing is that a moving window **must** be used with an `ORDER BY` clause. Otherwise, feeding data to a sliding window without ordering it first will simply lead to random results.

**Sample Output**:
```text
 country | year  | production | smoothed_min
---------+-------+------------+--------------
 Iran    | 1978  |       5302 |         3218
 Iran    | 1979  |       3218 |         1479
 Iran    | 1980  |       1479 |         1321
 Iran    | 1981  |       1321 |         1321
 Iran    | 1982  |       2397 |         1321
 Iran    | 1983  |       2454 |         2397
 Oman    | 1978  |        314 |          295
 Oman    | 1979  |        295 |          285
 Oman    | 1980  |        285 |          285
 Oman    | 1981  |        330 |          285
 Oman    | 1982  |        338 |          330
 Oman    | 1983  |        391 |          338
```

**Understanding the Frame (`ROWS BETWEEN`)**
`ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING` defines the window. In this example, up to three rows will be in use:
1.  The one before the current row.
2.  The current row.
3.  The one after the current row.

To illustrate how the sliding window works, we can use `array_agg` to visualize exactly which rows are in the window for each step:
```sql
SELECT *,
       array_agg(id) OVER (
           ORDER BY id
           ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
       )
FROM generate_series(1, 5) AS id;
```
**Sample Output**:
```text
 id |  array_agg
----+-----------
  1 | {1,2}      -- No predecessor
  2 | {1,2,3}
  3 | {2,3,4}
  4 | {3,4,5}
  5 | {4,5}      -- No follower
```
*   *Note*: PostgreSQL does not add null entries for missing predecessors/followers; they are simply excluded.

**Keywords for Window Frames**
You can use special keywords to define larger ranges.

*   **UNBOUNDED PRECEDING**: Everything from the start of the partition up to the current definition.
    ```sql
    SELECT *,
           array_agg(id) OVER (
               ORDER BY id
               ROWS BETWEEN UNBOUNDED PRECEDING AND 0 FOLLOWING
           ) -- Equivalent to default "Running Total" frame
    FROM generate_series(1, 5) AS id;
    ```
    **Sample Output**:
    ```text
     id |  array_agg
    ----+-------------
      1 | {1}
      2 | {1,2}
      3 | {1,2,3}
      4 | {1,2,3,4}
      5 | {1,2,3,4,5}
    ```

*   **UNBOUNDED FOLLOWING**: Everything from the current definition to the end of the partition.
    ```sql
    SELECT *,
           array_agg(id) OVER (
               ORDER BY id
               ROWS BETWEEN 2 FOLLOWING AND UNBOUNDED FOLLOWING
           ) -- Look strictly into the future
    FROM generate_series(1, 5) AS id;
    ```
    **Sample Output**:
    ```text
     id | array_agg
    ----+-----------
      1 | {3,4,5}
      2 | {4,5}
      3 | {5}
      4 | {NULL}  -- Or empty, depending on aggregation
      5 | {NULL}
    ```

**Advanced Exclusion (`EXCLUDE CURRENT ROW`)**
In some cases, you might want to exclude the current row from the calculation (e.g., "Compare me to my neighbors, but don't count me").
```sql
SELECT
    year,
    production,
    array_agg(production) OVER (
        ORDER BY year
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
        EXCLUDE CURRENT ROW
    )
FROM t_oil
WHERE country = 'USA' AND year < 1970;
```
**Sample Output**:
```text
 year | production |   array_agg
------+------------+---------------
 1965 |       9014 | {9579}        -- 1965 is excluded
 1966 |       9579 | {9014,10219}  -- 1966 is excluded
 1967 |      10219 | {9579,10600}
 1968 |      10600 | {10219,10828}
 1969 |      10828 | {10600}
```
As you can see, PostgreSQL is very flexible here, allowing windows accessing past, future, or excluding proper rows.

### Understanding the subtle difference between ROWS and RANGE
So far, you have seen sliding windows using `OVER ... ROWS`. However, there is more. Let’s take a look at the SQL specification taken directly from the PostgreSQL documentation:
```sql
{ RANGE | ROWS | GROUPS } frame_start [ frame_exclusion ]
{ RANGE | ROWS | GROUPS } BETWEEN frame_start AND frame_end [ frame_exclusion ]
```
There is more than just `ROWS`. In real life, we have seen that many people struggle to understand the difference between `RANGE` and `ROWS`. In many cases, the result is the same, which adds even more to the confusion.

To understand the problem, let’s first create some simple data:
```sql
SELECT *, x / 3 AS y
FROM generate_series(1, 15) AS x;
```
**Sample Output**:
```text
 x  | y
----+---
  1 | 0
  2 | 0
  3 | 1
  4 | 1
  5 | 1
  6 | 2
  7 | 2
  ...
 15 | 5
```
This is a simple dataset. Be particularly aware of the second column (`y`), which contains a couple of duplicates; they will be relevant in a minute.

**The Comparison Test**
```sql
SELECT *, x / 3 AS y,
    array_agg(x) OVER (
        ORDER BY x
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) AS rows_1,
    array_agg(x) OVER (
        ORDER BY x
        RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) AS range_1,
    array_agg(x/3) OVER (
        ORDER BY (x/3)
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) AS rows_2,
    array_agg(x/3) OVER (
        ORDER BY (x/3)
        RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) AS range_2
FROM generate_series(1, 15) AS x;
```
**Sample Output**:
```text
 x  | y |   rows_1   |   range_1  |   rows_2   |       range_2
----+---+------------+------------+------------+---------------------
  1 | 0 | {1,2}      | {1,2}      | {0,0}      | {0,0,1,1,1}
  2 | 0 | {1,2,3}    | {1,2,3}    | {0,0,1}    | {0,0,1,1,1}
  3 | 1 | {2,3,4}    | {2,3,4}    | {0,1,1}    | {0,0,1,1,1,2,2,2}
  4 | 1 | {3,4,5}    | {3,4,5}    | {1,1,1}    | {0,0,1,1,1,2,2,2}
  5 | 1 | {4,5,6}    | {4,5,6}    | {1,1,2}    | {0,0,1,1,1,2,2,2}
  6 | 2 | {5,6,7}    | {5,6,7}    | {1,2,2}    | {1,1,1,2,2,2,3,3,3}
  7 | 2 | {6,7,8}    | {6,7,8}    | {2,2,2}    | {1,1,1,2,2,2,3,3,3}
  8 | 2 | {7,8,9}    | {7,8,9}    | {2,2,3}    | {1,1,1,2,2,2,3,3,3}
  9 | 3 | {8,9,10}   | {8,9,10}   | {2,3,3}    | {2,2,2,3,3,3,4,4,4}
 10 | 3 | {9,10,11}  | {9,10,11}  | {3,3,3}    | {2,2,2,3,3,3,4,4,4}
 11 | 3 | {10,11,12} | {10,11,12} | {3,3,4}    | {2,2,2,3,3,3,4,4,4}
 12 | 4 | {11,12,13} | {11,12,13} | {3,4,4}    | {3,3,3,4,4,4,5}
 13 | 4 | {12,13,14} | {12,13,14} | {4,4,4}    | {3,3,3,4,4,4,5}
 14 | 4 | {13,14,15} | {13,14,15} | {4,4,5}    | {3,3,3,4,4,4,5}
 15 | 5 | {14,15}    | {14,15}    | {4,5}      | {4,4,4,5}
```

**Why the difference?**
After listing the `x` and `y` columns, we applied windowing functions on `x`.
*   **rows_1 vs range_1**: The results are identical. Since `x` is unique (1, 2, 3...), the logical value (RANGE) implies the same single row as the physical position (ROWS).
*   **rows_2 vs range_2**: The situation changes with duplicates (`y`).
    *   **ROWS**: Simply takes the previous and the next physical rows. Finding `y=1`? Previous is 0 (or 1), next is 1 (or 2). It doesn't care about values.
    *   **RANGE**: Takes the **entire group** of duplicates and peers within the value range. For `y=1`, `RANGE BETWEEN 1 PRECEDING` includes `0` (1-1), so it grabs all `0`s and `1`s (and `2`s from 1 FOLLOWING). Hence, the array is much longer.

### Removing duplicates using EXCLUDE TIES and EXCLUDE GROUP
Sometimes, you want to make sure that duplicates don’t make it into the result of your windowing function. The `EXCLUDE TIES` clause helps you to achieve exactly that. If a value shows up in a window twice, it will be removed. This is a neat way to avoid complicated workarounds, which can be costly and slow.

**Example: EXCLUDE TIES**
```sql
SELECT *,
    x / 3 AS y,
    array_agg(x/3) OVER (
        ORDER BY x/3
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) AS rows_1,
    array_agg(x/3) OVER (
        ORDER BY x/3
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
        EXCLUDE TIES
    ) AS rows_2
FROM generate_series(1, 10) AS x;
```
**Sample Output**:
```text
 x  | y |  rows_1  | rows_2
----+---+----------+--------
  1 | 0 | {0,0}    | {0}
  2 | 0 | {0,0,1}  | {0,1}
  3 | 1 | {0,1,1}  | {0,1}
  4 | 1 | {1,1,1}  | {1}
  5 | 1 | {1,1,2}  | {1,2}
  6 | 2 | {1,2,2}  | {1,2}
  7 | 2 | {2,2,2}  | {2}
  8 | 2 | {2,2,3}  | {2,3}
  9 | 3 | {2,3,3}  | {2,3}
 10 | 3 | {3,3}    | {3}
```
I have again used the `generate_series` function to create data. `array_agg` will turn all values added to the window into an array. As you can see in the last column (`rows_2`), however, the array is a lot shorter. Duplicates (ties) have been removed automatically.

**Example: EXCLUDE GROUP**
In addition to the `EXCLUDE TIES` clause, PostgreSQL also supports `EXCLUDE GROUP`. The idea here is that you want to remove an **entire set** of rows from the dataset before it makes it to the aggregation function.

Let’s take a look at the following example. We have four windowing functions here. The first one is the classic `ROWS BETWEEN` example you have already seen. I have included this column so that it is easier to spot the differences between the standard and the `EXCLUDE GROUP` version.

```sql
SELECT *,
    x / 3 AS y,
    array_agg(x/3) OVER (
        ORDER BY x/3
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) AS rows_1,
    avg(x/3) OVER (
        ORDER BY x/3
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) AS avg_1,
    array_agg(x/3) OVER (
        ORDER BY x/3
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
        EXCLUDE GROUP
    ) AS rows_2,
    avg(x/3) OVER (
        ORDER BY x/3
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
        EXCLUDE GROUP
    ) AS avg_2
FROM generate_series(1, 10) AS x;
```
**Sample Output**:
```text
 x  | y |  rows_1  |   avg_1  | rows_2 |   avg_2
----+---+----------+----------+--------+----------
  1 | 0 | {0,0}    | 0.000000 | {}     |      NULL
  2 | 0 | {0,0,1}  | 0.333333 | {1}    | 1.000000
  3 | 1 | {0,1,1}  | 0.666666 | {0}    | 0.000000
  4 | 1 | {1,1,1}  | 1.000000 | {}     |      NULL
  5 | 1 | {1,1,2}  | 1.333333 | {2}    | 2.000000
  6 | 2 | {1,2,2}  | 1.666666 | {1}    | 1.000000
  7 | 2 | {2,2,2}  | 2.000000 | {}     |      NULL
  8 | 2 | {2,2,3}  | 2.333333 | {3}    | 3.000000
  9 | 3 | {2,3,3}  | 2.666666 | {2}    | 2.000000
 10 | 3 | {3,3}    | 3.000000 | {}     |      NULL
```
The entire group containing the same value is removed. That, of course, also impacts the average calculated on top of this result.

### The `WINDOW` Clause
Abstracts the window definition to avoid repetition. This should be used when you have multiple windowing functions that use the same window definition.

**Example: Defining a window `w` for reuse**
```sql
SELECT
    country,
    year,
    production,
    min(production) OVER w AS min_prod,
    max(production) OVER w AS max_prod,
    avg(production) OVER w AS avg_prod
FROM t_oil
WINDOW w AS (PARTITION BY country ORDER BY year);
```
In this example:
1.  We define `w` at the end of the query.
2.  `min`, `max`, and `avg` all use `OVER w` instead of rewriting `(PARTITION BY country ORDER BY year)`.
3.  This keeps the code clean and ensures consistency across calculations.

### Writing your own Aggregates
PostgreSQL allows you to define custom aggregate functions. An aggregate function in PostgreSQL is essentially a state machine that processes one row at a time.
To define an aggregate, you need at least:
1.  **State Transition Function (`SFUNC`)**: Called for each row. It takes the *current state* and the *new value* as input and returns the *new state*.
2.  **State Data Type (`STYPE`)**: The data type for the state.
3.  **Initial Condition (`INITCOND`)**: (Optional) The starting value for the state.
4.  **Final Function (`FINALFUNC`)**: (Optional) Called once after all rows are processed to transform the final state into the return value.

**Example: Calculating Taxi Trip Price**
Let's imagine a taxi service that charges:
*   **Base fee**: 2.50
*   **Per km**: 0.40
*   **Waiting time**: 0.20 per min (optional)

We want to calculate the total cost for a set of trips (km, wait_min) using a custom aggregate `taxi_price(km, wait_min)`.

**Step 1: Define the State Transition Function**
The state will simulate the running total.
```sql
CREATE FUNCTION taxi_accum(numeric, numeric, numeric)
RETURNS numeric AS
$$
BEGIN
    -- Arguments: current_accumulated_state, km, wait_min
    RETURN $1 + ($2 * 0.40) + ($3 * 0.20);
END;
$$ LANGUAGE plpgsql IMMUTABLE;
```

**Step 2: Define the Aggregate**
```sql
CREATE AGGREGATE taxi_price (numeric, numeric) (
    SFUNC = taxi_accum,      -- The function we just made
    STYPE = numeric,         -- The type of the state
    INITCOND = 2.50          -- Base fee starts at 2.50
);
```
*   *Note*: This simple example adds 2.50 *once* for the whole group if we aggregate many rows, which might be correct if we view "Aggregate" as "The cost of a single complex journey composed of legs". But typically, `INITCOND` initializes the state. If we wanted 2.50 per row, we'd add it in the accumulator. Let's assume for this example that the `taxi_price` aggregate calculates the cost of *one* trip composed of multiple segments, where the base fee applies only once at the start.

**Testing the Aggregate**
```sql
-- Trip with 3 segments:
-- 1. 10km, 5min wait
-- 2. 5km, 2min wait
-- 3. 2km, 0min wait
VALUES (10::numeric, 5::numeric), (5, 2), (2, 0);

SELECT taxi_price(km, wait_min)
FROM ( VALUES (10::numeric, 5::numeric), (5, 2), (2, 0) ) AS t(km, wait_min);
```
**Calculation Logic**:
1.  Start: 2.50
2.  Row 1 (10, 5): 2.50 + (10*0.4) + (5*0.2) = 2.50 + 4 + 1 = 7.50
3.  Row 2 (5, 2): 7.50 + (5*0.4) + (2*0.2) = 7.50 + 2 + 0.4 = 9.90
4.  Row 3 (2, 0): 9.90 + (2*0.4) + 0 = 9.90 + 0.8 = 10.70
**Result**: 10.70

**Using Aggregates as Window Functions**
In PostgreSQL, an aggregate can automatically be used as a windowing function too. No additional steps are needed—you can use the aggregate directly.

**Example: Running Cost**
```sql
SELECT *,
       taxi_price(km, wait_min) OVER (PARTITION BY trip_id ORDER BY km)
FROM t_taxi;
```
**Sample Output**:
```text
 trip_id | km  | taxi_price
---------+-----+------------
       1 | 3.2 |       9.54  -- Price up to this point
       1 | 4.0 |      18.34  -- Accumulates 9.54 + cost of this segment
       1 | 4.5 |      28.24
       2 | 1.9 |       6.68
       2 | 4.5 |      16.58
```
What the query does is give us the price *up to a given point* on the trip.

### Using `FINALFUNC`
The aggregate we have defined calls one function per line (`SFUNC`). However, how would users be able to calculate an average or add a final markup? Without adding a `FINALFUNC` function, transformations at the end are not possible.

**Example: Adding a 10% Tip**
Suppose the customer wants to give the taxi driver a 10% tip as soon as they leave the taxi. That 10% has to be added *at the end*, as soon as the total price is known. This is the point where `FINALFUNC` kicks in.

**Step 1: Define the Final Function**
It takes the accumulated state (final internal value) and returns the result.
```sql
CREATE FUNCTION taxi_final (numeric)
RETURNS numeric AS
$$
    SELECT $1 * 1.1; -- Add 10%
$$
LANGUAGE sql IMMUTABLE;
```

**Step 2: Recreate the Aggregate**
```sql
DROP AGGREGATE IF EXISTS taxi_price(numeric, numeric);

CREATE AGGREGATE taxi_price (numeric, numeric) (
    SFUNC = taxi_accum,
    STYPE = numeric,
    INITCOND = 2.50,
    FINALFUNC = taxi_final
);
```

**Step 3: Test**
Finally, the price will simply be a bit higher than before.
```sql
SELECT trip_id, taxi_price(km, wait_min)
FROM t_taxi
GROUP BY 1;
```
**Sample Output**:
```text
 trip_id | taxi_price
---------+------------
       1 |     31.064  -- (28.24 * 1.1)
       2 |     18.238  -- (16.58 * 1.1)
```

**Complex States: Using Composite Types**
For simple calculations, simple data types (numeric, int) can be used for the intermediate state. However, not all operations can be done by just passing simple numbers around. Fortunately, PostgreSQL allows the use of **composite data types** as intermediate results.

Imagine you want to calculate an average of some data. An intermediate result requires keeping track of *both* the sum and the count.
```sql
CREATE TYPE my_intermediate AS (c int4, s numeric);

-- SFUNC would take 'my_intermediate' and return 'my_intermediate'
-- FINALFUNC would take 'my_intermediate' and return numeric (s / c)
```
Feel free to compose any arbitrary type that serves your purpose.

### Adding support for parallel queries
What you have just seen is a simple aggregate, which has no support for parallel queries. To solve those challenges, we can speed things up by enabling parallelism.

When creating an aggregate, you can optionally define the following modes:
`PARALLEL { UNSAFE | RESTRICTED | SAFE }`

By default, an aggregate does not support parallel queries. For performance reasons, it does make sense to explicitly state what the aggregate is capable of:

1.  **`UNSAFE`**: (Default) In this mode, no parallel queries are allowed.
2.  **`RESTRICTED`**: In this mode, the aggregate *can* be executed in parallel mode, but the execution is limited to the parallel group leader. Workers cannot participate.
3.  **`SAFE`**: In this mode, it provides full support for parallel queries.

**Requirements for SAFE mode**
If you mark a function as `SAFE`:
*   The function must **not have side effects**.
*   The execution order must not have an impact on the result of the query.
*   Only then should PostgreSQL be allowed to execute operations in parallel.

**Examples**:
*   `sin(x)` and `length(s)` are safe (no side effects).
*   `IMMUTABLE` functions are good candidates since they return the same result given the same inputs.
*   `STABLE` functions can work if certain restrictions apply.

### Improving Efficiency with Sliding Windows
The aggregates we’ve defined so far can already achieve quite a lot. However, if you are using sliding windows, the number of function calls will simply explode.
For every line, PostgreSQL will process the full window. If the sliding window is large, efficiency will fall from `O(N)` to `O(N*W)` where W is window size.

To fix that, PostgreSQL supports **Inverse Transition Functions**.
*   **`MSFUNC`** (Moving State Function): Adds the *next* row entering the window to the current state.
*   **`MINVFUNC`** (Moving Inverse Function): Removes the *old* row leaving the window from the current state.

**Example: Optimizing Taxi Price**
Instead of recalculating the whole window, we just add the new value and subtract the old one.

**Step 1: Define Moving Accumulator (`MSFUNC`)**
```sql
CREATE FUNCTION taxi_msfunc(numeric, numeric)
RETURNS numeric AS
$$
BEGIN
    -- RAISE NOTICE 'taxi_msfunc called with % and %', $1, $2;
    RETURN $1 + $2;
END;
$$ LANGUAGE 'plpgsql' STRICT;
```

**Step 2: Define Moving Inverter (`MINVFUNC`)**
```sql
CREATE FUNCTION taxi_minvfunc(numeric, numeric)
RETURNS numeric AS
$$
BEGIN
    -- RAISE NOTICE 'taxi_minvfunc called with % and %', $1, $2;
    RETURN $1 - $2;
END;
$$ LANGUAGE 'plpgsql' STRICT;
```

**Step 3: Create Optimized Aggregate**
```sql
DROP AGGREGATE IF EXISTS taxi_price(numeric);

CREATE AGGREGATE taxi_price (numeric) (
    SFUNC    = taxi_accum,       -- Standard accum (for non-window use)
    STYPE    = numeric,
    INITCOND = 0,
    -- Moving Window Optimizations:
    MSFUNC   = taxi_msfunc,      -- Called when window grows
    MINVFUNC = taxi_minvfunc,    -- Called when window shrinks
    MSTYPE   = numeric           -- Type for moving state
);
```

**Result**
The number of function calls decreases dramatically. Only a fixed handful of calls per row have to be performed (one add, one sub). There is no longer any need to calculate the same frame all over again.

### Hypothetical Aggregates (`HYPOTHETICAL`)
Hypothetical aggregates allow you to ask "What if?" questions, like "What would be the rank of valid 'X' if it were inserted into this set?". Standard SQL examples include `rank(val) WITHIN GROUP (ORDER BY col)`.

PostgreSQL allows you to define your own hypothetical aggregates. The key difference is that a hypothetical aggregate takes **extra arguments** that are not part of the aggregation itself but are used as the "hypothetical" value to compare against the group.

To enable this, you use the `HYPOTHETICAL` flag when defining the aggregate.

**Example: Creating a simple Hypothetical Rank**
Let's recreate a simplified version of `rank()`. It will count how many existing values are smaller than our hypothetical value and add 1.

**Step 1: Define State Function**
We need a function that:
1.  Takes the current state (count of smaller items).
2.  Takes the current row's value.
3.  Takes the *hypothetical* value (passed as a specialized argument).
*Note*: In `CREATE AGGREGATE`, the arguments are split. The hypothetical args follow `WITHIN GROUP`.

```sql
CREATE OR REPLACE FUNCTION hypothetical_rank_step(
    curr_count integer,  -- Current state (count)
    val_in_table integer, -- Value from the table row
    val_hypo integer      -- The static hypothetical value
)
RETURNS integer AS
$$
BEGIN
    IF val_in_table < val_hypo THEN
        RETURN curr_count + 1;
    ELSE
        RETURN curr_count;
    END IF;
END;
$$ LANGUAGE plpgsql IMMUTABLE;
```

**Step 2: Define Aggregate**
We define the aggregate to accept one direct argument (table column) and one hypothetical argument.
```sql
CREATE AGGREGATE my_hypo_rank (integer) WITHIN GROUP (ORDER BY integer) (
    SFUNC = hypothetical_rank_step,
    STYPE = integer,
    INITCOND = 1,
    HYPOTHETICAL
);
```

**Step 3: Usage**
Calculate the rank of a hypothetical value '5' if it were in the list `{1, 2, 6, 7}`.
```sql
SELECT my_hypo_rank(5) WITHIN GROUP (ORDER BY val)
FROM (VALUES (1), (2), (6), (7)) AS t(val);
```
**Result**: `3` (Because 1 and 2 are smaller, so it effectively sits at position 3).

### Handling recursions
Recursions are an important aspect and are supported by the most advanced SQL database engines, including PostgreSQL. Using recursions, many types of operations can be done fairly easily. So, let us dissect the most simplistic recursion and try to understand how recursion works.

**Example: A Simple Recursive Counter**
here is an example:
```sql
WITH RECURSIVE x(n, dummy) AS (
    -- 1. Initialization Step (Non-recursive term)
    SELECT 1 AS n, 'a'::text AS dummy
    
    UNION ALL
    
    -- 2. Recursive Step
    --    - Increment n by 1
    --    - Append 'a' to string
    --    - Stop when n < 5 (Termination Condition)
    SELECT n + 1, dummy || 'a'
    FROM x
    WHERE n < 5
)
SELECT * FROM x;
```
**Sample Output**:
```text
 n | dummy
---+-------
 1 | a
 2 | aa
 3 | aaa
 4 | aaaa
 5 | aaaaa
```

**How it works**
The goal of this query is to recursively return numbers and compile a string at the end. Basically, the query consists of two parts: the `WITH RECURSIVE` part and the `SELECT` statement at the end starting the recursion.

1.  **Initialization (`UNION ALL` Top)**: The `SELECT` statement before `UNION ALL` represents the start condition of the recursion. In our case, we start with `1` and `'a'`.
2.  **Recursive Call (`UNION ALL` Bottom)**: The second SQL statement recursively calls `x` (the CTE itself). Each iteration will increment the number by one and add a character to the end of the string.
3.  **Termination**: We abort when `n` reaches 5 (via `WHERE n < 5` inside the recursive term). Note that the last iteration will already display `n + 1`, so the last value returned is 5 and not 4.

All basic components of recursions are therefore to be found in the query: an **init condition**, a **recursive call**, and a **condition to terminate**.

### UNION versus UNION ALL in Recursion
In any recursion, loops can happen. The problem is that if the loop is infinite, your query will not terminate and will run forever. This is not desirable.
*   `UNION`: Prevents such loops by discarding duplicate rows (i.e., if a recursive step produces a row already seen, it stops expanding that path).
*   `UNION ALL`: Does **not** check for duplicates. It is faster but allows infinite loops if the recursion logic is flawed.

This difference is really important because it can protect us from bugs in the data by just skipping over instead of entering an infinite loop.

**Example 1: The Infinite Loop (`UNION ALL`)**
```sql
WITH RECURSIVE x(n) AS (
    SELECT 1 AS n
    UNION ALL
    -- Bug: We do NOT increment 'n', so it stays 1 forever.
    -- Since it's < 5, it keeps looping.
    SELECT n FROM x WHERE n < 5
)
SELECT * FROM x;
```
**Result**:
`ERROR: canceling statement due to user request` (Runs forever, must be cancelled).

**Example 2: Loop Prevention (`UNION`)**
```sql
WITH RECURSIVE x(n) AS (
    SELECT 1 AS n
    UNION -- Note: Just UNION, not UNION ALL
    SELECT n FROM x WHERE n < 5
)
SELECT * FROM x;
```
**Result**:
```text
 n
---
 1
(1 row)
```
**Why?**
1.  Init: Returns `1`.
2.  Recursion 1: Selects `n` (which is `1`).
3.  `UNION` checks: Is `1` a duplicate? Yes.
4.  The duplicate is discarded. No new rows are produced.
5.  Recursion terminates.

The second query exits quickly and returns just one row because PostgreSQL figures that it has seen those values before and can therefore terminate the recursion. Use `UNION` (instead of `UNION ALL`) when you need to handle cyclic graphs or potential duplicate paths safely.

### Handling JSON and JSONB
Two data types have been added: `json` and `jsonb`.
*   **`json`**: validates the JSON document but stores it as plain text (as it is). It does not store it in binary format. During insertions, there might be a small benefit, but if you want to access the document again later on, it will cost you dearly (re-parsing).
*   **`jsonb`**: is parsed and stored in binary format for easy access later on. Many functions and operators only exist for the binary representation (e.g., `jsonb_pretty`).

### Displaying and creating JSON documents
To create some data, we can use `VALUES` (a SQL instruction to return a dataset).

**Turning Rows to JSON (`row_to_json`)**
We can turn a generic data structure into a JSON document.
```sql
SELECT row_to_json(x)
FROM (VALUES (1, 2, 3), (4, 5, 6)) AS x;
```
**Sample Output**:
```text
              row_to_json
---------------------------------------
 {"column1":1,"column2":2,"column3":3}
 {"column1":4,"column2":5,"column3":6}
```
The important observation is that **each row** will be turned into **one JSON document**.

**Aggregating entire result sets (`json_agg`)**
Often, we want the entire result set to be a *single* document (array of objects).
```sql
SELECT json_agg(x)
FROM (VALUES (1, 2, 3), (4, 5, 6)) AS x;
```
**Sample Output**:
```text
 json_agg
------------------------------------------
 [{"column1":1,"column2":2,"column3":3}, +
  {"column1":4,"column2":5,"column3":6}]
```
*Note*: The `+` symbol is added by `psql` to indicate a line break.

**Formatting (`jsonb_pretty`)**
The `jsonb_pretty` function helps us to properly format the output. Note we cast to `::jsonb`.
```sql
SELECT jsonb_pretty(json_agg(x)::jsonb)
FROM (VALUES (1, 2, 3), (4, 5, 6)) AS x;
```
**Sample Output**:
```json
[
    {
        "column1": 1,
        "column2": 2,
        "column3": 3
    },
    {
        "column1": 4,
        "column2": 5,
        "column3": 6
    }
]
```

### Turning JSON documents into rows (`json_populate_record`)
JSON does not end up in a database by itself—we have to put it there. Sometimes, we have to map a document to an existing table.

**Example: Mapping JSON to Table Columns**
```sql
CREATE TABLE t_json (x int, y int);

SELECT *
FROM json_populate_record(
    NULL::t_json,   -- Trick: Pass NULL cast to the table type
    '{"x":54,"y":65}'
);
```
**Sample Output**:
```text
 x  | y
----+----
 54 | 65
```
This is really powerful. If you have a table that matches your JSON document (at least partially), you are mostly done. It is really easy to insert data under those circumstances:
```sql
INSERT INTO t_json
SELECT * FROM json_populate_record(NULL::t_json, '{"x":54,"y":65}');
```

### Accessing a JSON document
Let us first create a table table and insert a document.
```sql
CREATE TABLE t_demo (id int, doc jsonb);

INSERT INTO t_demo
VALUES (1, '{
    "product": "shoes",
    "colors": {
        "red": "red",
        "blue": "blue",
        "green": "green"
    }
}');
```

**Extracting Subtrees (`->`)**
The `->` operator finds a subtree and returns it as `jsonb`.
```sql
SELECT jsonb_pretty(doc -> 'colors') FROM t_demo;
```
**Sample Output**:
```json
{
    "red": "red",
    "blue": "blue",
    "green": "green"
}
```

**Nested Extraction**
We can dig one level deeper:
```sql
SELECT jsonb_pretty(doc -> 'colors' -> 'red') FROM t_demo;
```
**Result**: `"red"` (Note: it is quoted because it is still `jsonb` type).

**Extracting Text Values (`->>`)**
If we want the *real* text value (unquoted), we use `->>`.
```sql
SELECT doc -> 'product' AS raw_json,
       doc ->> 'product' AS text_value
FROM t_demo;
```
**Sample Output**:
```text
 raw_json | text_value
----------+------------
 "shoes"  | shoes
```
*   `->`: Returns `object` or `string` as a JSON type (e.g., `jsonb`).
*   `->>`: Returns `text`. You often need to cast this if you want an integer/numeric (e.g., `(doc->>'id')::int`).

**Looping over elements**
*   **`jsonb_each()`**: Loops over a subtree and returns elements as rows of `(key, value)` where value is `jsonb`.
*   **`jsonb_each_text()`**: Same, but value is `text`.

```sql
SELECT (jsonb_each_text(doc -> 'colors')).*
FROM t_demo;
```
**Sample Output**:
```text
  key  | value
-------+-------
 red   | red
 blue  | blue
 green | green
```

**Extracting Keys**
To just fetch keys:
```sql
SELECT jsonb_object_keys(doc) FROM t_demo;
```
**Sample Output**:
```text
 colors
 product
```

### Making use of JSONPath
One of the more recent features of PostgreSQL is the ability to make use of **JSONPath** (query language to navigate and extract specific values from JSON data). It is similar to XPath for XML.

**Syntax Basics**
*   `$`: The root of the JSON document.
*   `.` / `/`: Navigation operators (e.g., `$.person.name`).
*   `[ ]`: Indexing operator for arrays.
*   `@`: Values of the current path element (used in filters).
*   `?`: Filtering operator (e.g., `$.people[? (@.age < 25)]`).

**Common Examples**
*   `$.name`: Top level name property.
*   `$.address.street`: Nested property.

**Using `jsonb_path_query`**
This function executes JSONPath expressions against a JSONB value.

**Example 1: Array Indexing (`last`)**
```sql
SELECT jsonb_path_query('[1,2,3]', '$[last - 1]');
```
**Result**: `2` (Second to last element)

**Example 2: Data Type Filtering (`@.type()`)**
Check if the last element is a number.
```sql
SELECT jsonb_path_query('[1,2,3]', '$[last ? (@.type() == "number")]');
```
**Result**: `3` (It matches, so it returns the value).

**Example 3: Complex Subtree Extraction**
Find sub-objects where property `a` equals `b + 1`.
```sql
-- Search recursively ($.**) for objects where a == b + 1
SELECT jsonb_path_query(
    '{"c": {"a": 2, "b":1}}',
    '$.** ? (@.a == (@.b + 1))'
);
```
**Result**:
```json
{"a": 2, "b": 1}
```
This is extremely powerful for validation and extracting deeply nested logic without writing complex procedural code.

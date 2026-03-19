# Chapter 3: Improper Data Type Usage

---

## 3. Improper Data Type Usage

This chapter covers:
*   Avoiding using the wrong data type.
*   Time zone/daylight savings shenanigans.
*   Data types that should be avoided altogether.

PostgreSQL is very rich in data types and probably supports more than most databases. We will now take a look at some popular data types and how their use or misuse can lead to consequential mistakes.

### 3.1 TIMESTAMP (WITHOUT TIME ZONE)
Timestamps are a really popular type used to store date and time. If you type `TIMESTAMP`, PostgreSQL will, by default, assume that you want `TIMESTAMP WITHOUT TIME ZONE` because that is a behavior required by the SQL Standard. This is also known as a "naive" timestamp.

**The Scenario**:
Frogge Emporium stores ticket times in the local time of the customer's location.
*   **Opened**: Oct 28, 2023 at 16:00 PDT (UTC-8).
*   **Closed**: Oct 29, 2023 at 09:00 GMT (UTC+0).

**The Calculation Error**:
If you store these as naive timestamps and try to subtract them:
```sql
SELECT closed_at - opened_at FROM support.tickets WHERE id = 132591;
-- Result: 17:00:00 (WRONG)
```
*   **Why?**: 16:00 to 09:00 next day looks like 17 hours if you ignore time zones.
*   **Real Duration**: 16:00 PDT is 00:00 GMT next day. So 00:00 GMT to 09:00 GMT is **9 hours**.

**The "Unified Time Zone" Attempt (and DST trap)**:
Even if you agree to store everything in "London Time":
*   London changed clocks (DST end) on Oct 29 at 2:00am.
*   This creates a duplicate hour or a gap that naive timestamps cannot represent or calculate correctly.

**The Solution: `TIMESTAMPTZ`**:
Use `TIMESTAMP WITH TIME ZONE` (shorthand `TIMESTAMPTZ`). It stores the point in time in UTC internally and converts to the client's time zone for display.

```sql
INSERT INTO support.tickets (content, status, opened_at, closed_at) VALUES
('Kindly close our account', '10', '2023-10-28 16:00 PDT', '2023-10-29 09:00 GMT');
```

**Correct Calculation**:
```sql
SELECT closed_at - opened_at FROM tickets;
-- Result: 10:00:00 (Correct, accounting for zone diffs)
```
*   *Note: Detailed implementation shows 10 hours in the text logic or 9 depending on specific GMT offset, but the key is that Postgres handles the math.*

**Displaying Time Zones**:
```sql
SELECT opened_at AT TIME ZONE 'PDT' AS "Ticket opened" FROM tickets;
-- Output: 2023-10-28 16:00:00
```

> **Takeaway**: `TIMESTAMP WITHOUT TIME ZONE` is like a photo of a clock—context-less. Always use `TIMESTAMPTZ` for events. It uses the same 8 bytes of storage!

### 3.2 TIME WITH TIME ZONE (`TIMETZ`)
It is easy to assume `TIME WITH TIME ZONE` is good for storing times (without dates).
*   **Scenario**: Smart meter readings.
*   **Problem**: Time zones like "DST" rely on **Dates** to know when they apply. Without a date, an offset like `+01` is ambiguous or meaningless for math.

**Math Failure**:
```sql
SELECT '01:17:27+01'::timetz - '01:17:21+00'::timetz;
-- ERROR: operator does not exist: time with time zone - time with time zone
```
Postgres does not support subtraction for this type because the duration is undefined without dates.

> **Takeaway**: `TIMETZ` exists solely for SQL Standard compliance. It takes 8 bytes (same as `TIMESTAMPTZ`). **Never use it.** Use `TIMESTAMPTZ`.

### 3.3 CURRENT_TIME
`CURRENT_TIME` is a function that returns the `TIME WITH TIME ZONE` (TIMETZ) data type.
```sql
SELECT CURRENT_TIME, pg_typeof(CURRENT_TIME);
-- 20:46:27.09+00 | time with time zone
```
Since it returns the problematic `TIMETZ` type, avoid using it for logic or storage.

**Table 3.1: Recommended Time Functions**

| Function | Return Type | Sample Output | Note |
| :--- | :--- | :--- | :--- |
| **`CURRENT_TIMESTAMP`** / `now()` | `timestamp with time zone` | `2023-11-20 21:03:34+00` | **Recommended** |
| `CURRENT_DATE` | `date` | `2023-11-20` | Good for dates |
| `LOCALTIMESTAMP` | `timestamp without time zone` | `2023-11-20 21:03:34` | Wall clock time |
| `CURRENT_TIME` | `time with time zone` | `21:03:34+00` | **Avoid** |

> **Conclusion**: Use `now()` or `CURRENT_TIMESTAMP` for almost everything.

### 3.4 CHAR(n)
`CHAR(n)` (or `BPCHAR`) is a fixed-length, blank-padded textual type.
*   **Behavior**: Always stores exactly `n` characters. Shorter strings are padded with spaces.
*   **Example**: `'postgres'::CHAR(10)` -> `'postgres  '` (2 trailing spaces).

#### The Problems:
1.  **Padding affects Matching**:
    *   Equality (`=`) usually ignores padding. `'a'::char(10) = 'a'::text` -> `True`.
    *   **LIKE / Regex FAIL**: Padding is **NOT** ignored.
        ```sql
        SELECT 'postgres'::CHAR(10) LIKE '%ostgres';
        -- Result: FALSE (Because it ends with spaces, not 's')
        
        SELECT 'postgres'::CHAR(10) ~ '.*ostgres$';
        -- Result: FALSE (Regex anchor $ sees spaces)
        ```

2.  **Silent Truncation**:
    *   Casting a long string to `CHAR(n)` truncates it **without error** (SQL Standard behavior).
    *   `'I heart PostgreSQL'::CHAR(10)` -> `'I heart Po'`

3.  **Wasted Performance & Storage**:
    *   It is **not** a fixed-width type on disk (Postgres uses variable-length storage for everything). You waste space storing spaces.
    *   CPU wastes cycles stripping spaces for comparisons.

4.  **Indexing Issues**:
    *   Indexes on `CHAR(n)` might be ignored when querying with `TEXT` parameters from drivers.

> **Takeaway**: **Never use `CHAR(n)`**. It has no performance benefits in Postgres and causes logical bugs with padding. Always use `TEXT` or `VARCHAR`.

### 3.5 VARCHAR(n)
`VARCHAR(n)` (or `CHARACTER VARYING`) is a variable-length text field with a length limit.
*   **Behavior**: Stores strings up to `n` characters. Errors if longer.
*   **Storage**: Identical to `TEXT` (no performance advantage).

#### The Problems:
1.  **Arbitrary Limits are Painful**:
    *   You define `VARCHAR(50)` for names. A customer with a 52-character name signs up.
    *   **Result**: App crash (`value too long`). You must now run `ALTER TABLE` (locking the table) to resize it.
    
2.  **Silent Truncation (Space Exception)**:
    *   If the "extra" characters over the limit are *spaces*, Postgres truncates them **silently** (no error).
    *   `INSERT INTO t (varchar5) VALUES ('1234  ')` -> Stores `'1234 '` (5 chars).

3.  **No Performance Gain**:
    *   In some other DBs, VARCHAR is faster than TEXT. **Not in Postgres**. They rely on the same internal C-structure (`varlena`).

#### The Solution: TEXT + CHECK
If you *really* need a hard limit (e.g., for compliance), use a `CHECK` constraint. It is easier to change later (just drop the constraint, no table rewrite needed).

```sql
CREATE TABLE test (
    col TEXT CHECK (length(col) <= 5)
);
```

#### New Type: BPCHAR (Postgres 16+)
A new type `BPCHAR` (Blank-Padded Character) without length limit behaves differently regarding **Trailing Whitespace**.
*   **VARCHAR**: `'a'` != `'a  '` (Trailing space matters).
*   **BPCHAR**: `'a'` == `'a  '` (Trailing space is ignored).

> **Takeaway**: Stop using `VARCHAR(n)`. It buys you nothing but future maintenance headaches. Use **`TEXT`** for everything. Add a `CHECK` constraint if you strictly need length validation.

### 3.6 MONEY
The `MONEY` type seems like the obvious choice for financial data, but it is **counterintuitively terrible**.

#### The Problems:
1.  **Locale Component**:
    *   `MONEY` does not store the currency (USD, GBP, EUR). It blindly uses the server's `LC_MONETARY` setting (e.g., assumes `£` if server is `en_GB`).
    *   If you migrate data to a server in a different locale, your currency symbol might change!
    
2.  **Precision Loss (Rounding)**:
    *   `MONEY` often fails at fractional math (like calculating 25% discount).
    *   Example: `£99.99 * 0.25` = `£24.9975` (Mathematically correct).
    *   `MONEY` type result: `£25.00` (Rounded automatically).
    *   **Result**: You lose financial precision instantly.
    
3.  **Garbage Input**:
    *   It accepts weird strings like `',123,456,,7,8.1,0,9'::MONEY` without error.

#### What about Floating Point (`REAL`, `DOUBLE`)?
**NEVER** use floating-point types for money.
*   Floats are **inexact** (IEEE 754).
*   Stored value might be `99.990000000001` instead of `99.99`.
*   Accumulating these errors leads to accounting failures.

#### The Solution: `NUMERIC`
Use `NUMERIC` (alias `DECIMAL`) for exact storage.
*   **Format**: `NUMERIC(precision, scale)`. E.g., `NUMERIC(10, 2)` or `NUMERIC(14, 4)` for high-precision accounting.
*   **Best Practice**: Store the Amount and Currency separately.

```sql
CREATE TABLE payments (
    amount NUMERIC(10, 2) NOT NULL, -- Exact math
    currency CHAR(3) NOT NULL       -- 'USD', 'GBP'
);
```

> **Takeaway**: Deprecate `MONEY` in your mind. It is dangerously imprecise and locale-dependent. Use `NUMERIC` for all financial math.

### 3.7 SERIAL data type
`SERIAL` (and `BIGSERIAL`) is the legacy PostgreSQL way to create auto-incrementing columns. It is **not** standard SQL and comes with baggage.

#### The Problems:
1.  **Permission Hell**:
    *   `SERIAL` creates a background sequence (e.g., `table_id_seq`).
    *   Granting `INSERT` on the table to a user **does not** automatically grant usage on the sequence.
    *   **Result**: `ERROR: permission denied for sequence`. You must manage permissions on both objects manually.

2.  **Copying Pitfalls (`CREATE TABLE ... LIKE`)**:
    *   If you clone a table structure: `CREATE TABLE new_t (LIKE old_t INCLUDING ALL)`.
    *   The new table uses the **old table's sequence**!
    *   **Result**: Inserting into `new_t` advances the ID counter for `old_t`. Dropping `old_t` breaks `new_t`.

#### The Solution: Identity Columns
Use the SQL Standard `GENERATED ALWAYS AS IDENTITY` (or `BY DEFAULT`).
*   **Syntax**:
    ```sql
    CREATE TABLE transactions (
        id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        amount NUMERIC
    );
    ```
*   **Benefits**:
    *   Permissions are managed with the table.
    *   Cloning the table creates a **new** isolated sequence for the new table.
    *   Standard SQL compliance.

#### A Note on "Gapless" Sequences
Database sequences (both `SERIAL` and `IDENTITY`) **never guarantee a gapless sequence**.
*   **Why?**: If a transaction grabs ID #5 and then rolls back, ID #5 is "burned" and never reused.
*   **Requirement**: If you legally need gapless invoice numbers (1, 2, 3...), **do not use DB sequences**. Generate them in your application code (serialized) or use a specialized lock-heavy counter table.

> **Takeaway**: Stop using `SERIAL`. Use `GENERATED ALWAYS AS IDENTITY`. It behaves more sanely and standardly.

### 3.8 XML
Postgres supports the `XML` type, but you should avoid it in favor of `JSONB` for almost all use cases.

#### The Problems:
1.  **Verbosity (Storage Waste)**:
    *   XML is notoriously chatty.
    *   **XML Example**: `<property><key>color</key><value>0</value></property>` (~72 bytes).
    *   **JSON Example**: `{"color": 0}` (~15 bytes).
    *   **Result**: Massive waste of disk space and I/O bandwidth.

2.  **Indexing & Searching Limit**:
    *   **Comparisons**: There is no standard "Equality" operator for XML. You cannot say `WHERE xml_col = '<xml>...'`.
    *   **Indexing**: Because you can't compare it, you can't standardly index it.
    *   *Contrast*: `JSONB` has first-class GIN/GiST index support for rapid searching.

3.  **Parsing Nightmares**:
    *   **Encoding**: XML files often declare their own encoding (e.g., `<?xml encoding="ISO-8859-1"?>`). If stored in a UTF-8 Postgres database, this declaration might become invalid or cause conflicts during conversion.
    *   **External Entities**: Parsing XML exposes you to complex DTD/XXE vulnerabilities if not handled strictly.

#### Benchmark: Size
```sql
SELECT 
    pg_column_size('<p><k>c</k><v>0</v></p>'::xml) AS xml_size,  -- 32 bytes (struct) + overhead
    pg_column_size('{"c":0}'::jsonb) AS json_size;               -- 15 bytes
```

> **Takeaway**: XML is "Error-prone, inefficient, and slow". Unless you are forced to integrate with legacy XML systems, always use **`JSONB`**. It is binary-optimized, indexable, and smaller.

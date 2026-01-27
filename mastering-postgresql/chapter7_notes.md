# Chapter 7: Writing Stored Procedures

---

## 1. Understanding Stored Procedure Languages

In most databases (SQL Server, Oracle), you are forced to use a specific proprietary language (T-SQL, PL/SQL).
**PostgreSQL is different.** It is designed to be **polyglot**. You can write stored procedures in almost *any* language you like (Python, Perl, JavaScript/V8, etc.), not just the default PL/pgSQL.

### The Anatomy of a Language
How does Postgres plug in a new language? via the `CREATE LANGUAGE` command.

```sql
CREATE [TRUSTED] LANGUAGE name
    HANDLER call_handler
    [VALIDATOR valfunction]
    [INLINE inline_handler];
```

| Component | Role | Analogy |
| :--- | :--- | :--- |
| **HANDLER** | The **Glue**. It maps Postgres data structures (rows, columns) to the target language's format and executes the code. | The Translator. |
| **VALIDATOR** | The **Police Officer**. It checks the syntax of your function *when you create it*. If the language supports compilation/parsing, this prevents you from saving broken code. | The Spellchecker. |
| **INLINE** | Optional. Allows running **Anonymous Code Blocks** (`DO $$ ... $$`) without creating a stored function first. | The "Scratchpad". |

> **Key Takeaway**: Postgres separates the *database engine* from the *procedural logic*, connecting them via Handlers. This is why you must "install" languages (like `CREATE EXTENSION plpython3u`) before using them.

---

## 2. Functions vs. Procedures
The term "Stored Procedure" is often used loosely, but in PostgreSQL, the distinction is **critical**.

### 1. Functions (`CREATE FUNCTION`)
*   **Usage**: Invoked dynamically inside a query (e.g., `SELECT my_func(id) FROM table`).
*   **Transaction Control**: **FORBIDDEN**.
    *   **Why?** If you run `SELECT my_func(id) FROM huge_table`, the function runs 50 million times *inside* the statement. You cannot `COMMIT` in the middle of a `SELECT` statement, as it would break the atomicity and snapshot consistency of the running query.
*   **Return**: Returns a value (scalar or set).

### 2. Procedures (`CREATE PROCEDURE`)
*   **Usage**: Invoked standalone via the `CALL` command (e.g., `CALL my_proc(id)`).
*   **Transaction Control**: **ALLOWED**.
    *   **Why?** Since it sits at the top level and isn't nested inside a data-fetching query, a Procedure *can* issue `COMMIT` or `ROLLBACK` commands. This allows it to run multiple transactions in sequence (e.g., batch processing 1 million rows, committing every 10,000).
*   **Return**: Does not return a value (use `INOUT` parameters to pass data back).

> **Summary Table**

| Feature | Function (`SELECT`) | Procedure (`CALL`) |
| :--- | :--- | :--- |
| **Invocation** | Inside Query (`SELECT func()`) | Standalone (`CALL proc()`) |
| **Transactions** | No (`COMMIT` fails) | **Yes** (Can `COMMIT`/`ROLLBACK`) |
| **Use Case** | Calculations, logic, reading data. | Batch jobs, heavy maintenance, ETL. |

---

## 3. The Anatomy of a Function
Let's dissect a simple function that adds two integers.

```sql
CREATE OR REPLACE FUNCTION mysum(int, int)
RETURNS int AS
'
    SELECT $1 + $2;
' LANGUAGE 'sql';
```

### Key Concepts
1.  **The "Black Box" String**:
    *   Notice that the function body is enclosed in quotes `'...'`.
    *   PostgreSQL treats the code as a **String**. It doesn't parse it deeply until execution (or validation). This creates an abstraction layer: the SQL engine sees a string, and it hands that string to the language handler (e.g., Python or SQL handler) to execute.
    *   *Note*: In modern Postgres, we usually use `$$` (dollar quoting) instead of `'` to handle complex strings easier, but the concept is the same.

2.  **Function Overloading**:
    *   PostgreSQL allows multiple functions with the **same name** but **different arguments**.
    *   `mysum(int, int)` is distinct from `mysum(int8, int8)`.
    *   **Warning**: `CREATE OR REPLACE` only replaces a function if the *arguments match exactly*. If you change an argument type, it creates a *new, separate* function. This can lead to "ghost functions" cluttering your schema if you aren't careful to `DROP` the old ones.

3.  **Language Definition**:
    *   You **must** specify `LANGUAGE`. This tells Postgres which "Handler" to use to interpret the string body.

**Execution**:
```sql
SELECT investment_calculator(1000, 0.1, 2);
-- Result: 1210.00
```

---

## 12. Understanding Advanced Error Handling

### The EXCEPTION Block Structure
If code in the `BEGIN` block fails, execution jumps to `EXCEPTION`.

```sql
CREATE FUNCTION error_test1(int, int) RETURNS int AS $$
BEGIN
    RAISE NOTICE 'Debug: % / %', $1, $2;
    RETURN $1 / $2; -- Potential Error here
EXCEPTION
    WHEN division_by_zero THEN
        RAISE NOTICE 'Caught division by zero: %', sqlerrm;
        RETURN 0;
    WHEN others THEN
        RAISE NOTICE 'Caught unexpected error: %', sqlerrm;
        RETURN 0;
END;
$$ LANGUAGE 'plpgsql';
```
*   **Mechanism**: This creates a "sub-transaction" (Savepoint). If an error occurs, the block rolls back to the savepoint, and the exception handler runs.
*   **Cost**: Entering a block with an Exception handler has a small performance cost due to Savepoint management.

### Making use of `GET DIAGNOSTICS`
Sometimes you need to know *what happened* (e.g., "How many rows did I just update?").

**1. Inspecting Row Count**
```sql
EXECUTE 'UPDATE my_table SET x = 1';
-- GET DIAGNOSTICS variable = ROW_COUNT;
GET DIAGNOSTICS v_count = ROW_COUNT;
```

**2. Getting Error Context (Stack Trace)**
Inside an `EXCEPTION` block, you can dig deeper than just the error message.
```sql
EXCEPTION WHEN OTHERS THEN
    GET STACKED DIAGNOSTICS 
        v_state   = RETURNED_SQLSTATE,
        v_msg     = MESSAGE_TEXT,
        v_context = PG_EXCEPTION_CONTEXT;
        
    RAISE NOTICE 'Error %: % \nContext: %', v_state, v_msg, v_context;
END;
```
> **Tip**: `PG_EXCEPTION_CONTEXT` is incredibly useful for debugging because it tells you exactly *where* in the call stack the error occurred (line number, function name).

---

## 13. Using Cursors to Fetch Data in Chunks
When dealing with massive result sets (e.g., 10 billion rows), you cannot load everything into memory at once. Cursors allow you to **stream** results, fetching them row by row (or in chunks).

### 1. Implicit Cursors (`FOR` Loop)

```sql
CREATE OR REPLACE FUNCTION c(int) RETURNS SETOF text AS $$
DECLARE
    v_rec record;
BEGIN
    -- Implicitly creates a cursor to loop over results
    FOR v_rec IN 
        SELECT tablename FROM pg_tables LIMIT $1 
    LOOP
        RETURN NEXT v_rec.tablename;
    END LOOP;
    RETURN;
END;
$$ LANGUAGE 'plpgsql';
```
*   **Memory Safe**: Usually safe for iteration.
*   **Warning**: If this is a `SETOF` function (SRF), Postgres might still materialize the *output* of the function in memory before returning it to the client, negating the memory benefit for the *caller*, even if the *loop* was efficient.

### 2. Explicit Cursors (Unbound)

```sql
CREATE OR REPLACE FUNCTION d(limit_val int) RETURNS SETOF text AS $$
DECLARE
    v_cur refcursor;  -- Unbound cursor variable
    v_data text;
BEGIN
    OPEN v_cur FOR SELECT tablename FROM pg_tables LIMIT limit_val;
    
    WHILE true LOOP
        FETCH v_cur INTO v_data;
        IF NOT FOUND THEN
            EXIT; -- Break loop when no more rows
        END IF;
        RETURN NEXT v_data;
    END LOOP;
    CLOSE v_cur;
END;
$$ LANGUAGE 'plpgsql';
```

### 3. Bound Cursors (Parameterized)
You can declare the cursor logic in the `DECLARE` block with arguments.

```sql
DECLARE
    -- Define the query structure upfront
    v_cur CURSOR (param1 int) FOR
        SELECT tablename FROM pg_tables LIMIT param1;
    v_data text;
BEGIN
    OPEN v_cur($1); -- Pass argument at runtime
    ...
```

### 4. Returning Cursors to Client
You can create a cursor inside a function and pass the *reference* back to the client/application. The application then fetches rows.

```sql
CREATE FUNCTION cursor_test(c_name refcursor) RETURNS refcursor AS $$
BEGIN
    OPEN c_name FOR SELECT * FROM generate_series(1, 10);
    RETURN c_name;
END;
$$ LANGUAGE plpgsql;
```

**Usage (Must be in a Transaction):**
```sql
BEGIN;
SELECT cursor_test('my_cursor'); -- Returns 'my_cursor'
FETCH NEXT FROM my_cursor;       -- Returns 1
FETCH NEXT FROM my_cursor;       -- Returns 2
COMMIT;
```

> **Critical Caveat**: Functions returning `SETOF` (SRF) are effectively materialized by the executor in many contexts. If you truly need to stream 10GB of data to a client without memory spikes, returning a **Refcursor** (Option 4) is often safer than returning `SETOF`, as the client controls the FETCH size.

---

## 14. Utilizing Composite Types
You can pass **Rows**, **Composite Types**, or **Custom Types** into and out of functions.

### 1. Defining a Custom Type
```sql
CREATE TYPE my_cool_type AS (s text, t text);
```

### 2. Passing and Returning the Type
We can write a function that accepts this complex object, reads its fields, and returns a new object of the same type.

```sql
CREATE OR REPLACE FUNCTION f(my_cool_type)
RETURNS my_cool_type AS
$$
DECLARE
    v_row my_cool_type; -- Declare variable of custom type
BEGIN
    -- Access fields using Dot Notation ($1.field)
    RAISE NOTICE 'schema: (%) / table: (%)', $1.s, $1.t;
    
    -- Populate the variable from a query
    SELECT schemaname, tablename
    INTO v_row
    FROM pg_tables
    WHERE tablename = trim($1.t)
      AND schemaname = trim($1.s)
    LIMIT 1;
    
    RETURN v_row;
END;
$$ LANGUAGE 'plpgsql';
```

### 3. Calling the Function
Note the syntax for casting a string literal to a composite type: `'(val1, val2)'::type`.

```sql
SELECT (f).s, (f).t
FROM f('("public", "t_test")'::my_cool_type);

-- Output:
-- s      | t
-- -------+--------
-- public | t_test
```
*   **Result**: We successfully passed a structured object in, processed it using its internal fields, and returned a structured object out.

---

## 15. Writing Triggers in PL/pgSQL
Triggers allow you to react to `INSERT`, `UPDATE`, `DELETE`, or `TRUNCATE` events.
Unlike many commercial DBs, PostgreSQL allows **multiple triggers** on the same table. They fire in **Alphabetical Order**.

### 1. The Anatomy of a Trigger Function
A trigger function must:
1.  Return type `TRIGGER`.
2.  Use special variables `NEW` (for the incoming row) and `OLD` (for the existing row).
3.  Return `NEW` (to proceed with the change), `OLD`, or `NULL` (to cancel the operation silently in a BEFORE trigger).

**Example: Temperature Correction**
If a sensor sends a value below absolute zero (-273C), correct it to 0.

```sql
CREATE OR REPLACE FUNCTION trig_func() RETURNS trigger AS
$$
BEGIN
    IF NEW.temperature < -273 THEN
        NEW.temperature := 0; -- Modify data on the fly
    END IF;
    RETURN NEW; -- Pass the (potentially modified) row to the table
END;
$$ LANGUAGE 'plpgsql';

-- Attach the trigger
CREATE TRIGGER sensor_trig
    BEFORE INSERT ON t_sensor
    FOR EACH ROW
    EXECUTE PROCEDURE trig_func();
```

### 2. Trigger Context Variables
Trigger functions have access to "Magic Variables" that describe the context.
*   `TG_NAME`: Name of the trigger.
*   `TG_OP`: Operation (`INSERT`, `UPDATE`, `DELETE`).
*   `TG_TABLE_NAME`: The table being touched.
*   `TG_WHEN`: `BEFORE` or `AFTER`.
*   `TG_LEVEL`: `ROW` or `STATEMENT`.

### 3. Transition Tables (Statement-Level Triggers)
Standard Row-Level triggers fire once *per row* (`N` times). Statement-Level triggers fire once *per query* (1 time).
Since Postgres 10, Statement triggers can see **all changed rows** via **Transition Tables**.

**Mechanism**:
*   `REFERENCING NEW TABLE AS new_table`: Gives you a "virtual table" containing all rows inserted/updated by the statement.
*   `REFERENCING OLD TABLE AS old_table`: Gives you a "virtual table" of deleted/old rows.

**Example: Inspecting Bulk Changes**
```sql
CREATE OR REPLACE FUNCTION transition_trigger() RETURNS TRIGGER AS $$
DECLARE
    v_rec record;
BEGIN
    IF (TG_OP = 'INSERT') THEN
        RAISE NOTICE 'New Bulk Data:';
        -- Loop over the "Virtual Table" new_table
        FOR v_rec IN SELECT * FROM new_table LOOP
            RAISE NOTICE '%', v_rec;
        END LOOP;
    END IF;
    RETURN NULL; -- Result ignored for AFTER triggers
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER transition_test_trigger_ins
    AFTER INSERT ON t_sensor
    REFERENCING NEW TABLE AS new_table -- Magic: Exposes the set of new rows
    FOR EACH STATEMENT
    EXECUTE PROCEDURE transition_trigger();
```
> **Warning**: Accessing Transition Tables requires the database to collect all changes in memory. Be careful with massive bulk updates (millions of rows).

---

## 11. Managing Scopes
Just like C, Java, or Python, PL/pgSQL allows **nested blocks**. You can define a new `DECLARE` section inside an existing `BEGIN ... END` block.

**The Shadowing Concept**
If you declare a variable inside an inner block with the same name as an outer variable, the inner one **shadows** (hides) the outer one.

```sql
CREATE FUNCTION scope_test () RETURNS int AS
$$
DECLARE
    i int := 0; -- Outer 'i'
BEGIN
    RAISE NOTICE 'Outer i: %', i; -- Prints 0

    -- Inner Block
    DECLARE
        i int; -- Inner 'i' (Shadows the outer one)
    BEGIN
        RAISE NOTICE 'Inner i: %', i; -- Prints NULL (not initialized)
        i := 5; -- Assigns 5 to INNER i
    END;

    -- Back in Outer Scope
    RAISE NOTICE 'Outer i after block: %', i; -- Still 0!
    RETURN i;
END;
$$ LANGUAGE 'plpgsql';
```

**Execution**:
```sql
SELECT scope_test();
-- NOTICE: Outer i: 0
-- NOTICE: Inner i: <NULL>
-- NOTICE: Outer i after block: 0
-- Result: 0
```

---

## 10. Handling Quoting & Preventing SQL Injection
One of the most dangerous patterns in database programming is **Dynamic SQL** created via string concatenation.

### The Attack Vector (SQL Injection)
Imagine a function that takes a table name and queries it:
```sql
-- BAD CODE: Naive concatenation
v_sql := 'SELECT * FROM ' || v_table_name;
EXECUTE v_sql;
```
If a malicious user passes `t_test; DROP TABLE t_test;`, the database executes **both** commands. Boom. Date gone.

### The Defensive Toolkit
PostgreSQL provides native functions to sanitize inputs.

| Function | Use Case | Example Input | Example Output |
| :--- | :--- | :--- | :--- |
| **`quote_literal(text)`** | For string values (user data). | `O'Reilly` | `'O''Reilly'` |
| **`quote_ident(text)`** | For identifiers (table/column names). | `My Table` | `"My Table"` |
| **`quote_nullable(val)`** | Handles NULLs safely. | `NULL` | `NULL` (Not `'NULL'`) |

**Example: Manual Quoting**
```sql
SELECT quote_literal(NULL);       -- NULL
SELECT quote_nullable(NULL);      -- NULL
SELECT quote_nullable('Hello');   -- 'Hello'
```

### The Best Practice: `format()` Function
Instead of messy concatenation with helper functions, use `format()`. It works like `sprintf` in C.

**Syntax**: `format(string, args...)`
*   `%s`: Formats as a simple string.
*   `%I`: Formats as an **Identifier** (Automatic `quote_ident`).
*   `%L`: Formats as a **Literal** (Automatic `quote_nullable`).

**Example: A Safe Dynamic Query**
```sql
DECLARE
    v_sql text;
BEGIN
    -- %I safely quotes the identifiers (columns)
    -- $1, $2 are passed as parameters to EXECUTE (safest for values)
    v_sql := format('SELECT %I || ''.'' || %I FROM pg_tables WHERE schemaname = $1', 'schemaname', 'tablename');
    
    EXECUTE v_sql 
    USING 'public'; -- Binds $1 to 'public'
END;
```

**Advanced Formatting**:
*   **Positional Args**: `format('%1$s, %1$s', 'one')` -> `'one, one'`.
*   **Padding**: `format('%10s', 'hi')` -> `'        hi'`.

> **Rule**: Never concatenate strings to build SQL. Use `format(%I)` for identifiers and `EXECUTE ... USING` for values.

---

## 10. Performance Considerations (PL/pgSQL vs SQL)
**Rule of Thumb**: Not all languages are equal.
*   **Use SQL** for simple logic (no loops). It is heavily optimized (inlining).
*   **Use PL/pgSQL** only when you need flow control (IF, LOOP, RAISE Exception).

### The Benchmark
Let's compare the same investment logic in both languages using 1 million iterations.

**1. The Logic**
*   **PL/pgSQL**: `investment_calculator` (Defined above, heavily structured).
*   **SQL**:
    ```sql
    CREATE OR REPLACE FUNCTION simple_invest(numeric, numeric, numeric)
    RETURNS numeric AS
    $$
        SELECT $1 * pow(1 + $2, $3);
    $$ LANGUAGE 'sql';
    ```

**2. The Race**

**A. Running PL/pgSQL (Procedural)**
```sql
EXPLAIN ANALYZE
SELECT investment_calculator(x, 0.1, 2)
FROM generate_series(1, 1000000) AS x;
```
**Plan**:
```text
Function Scan on generate_series x  (cost=0.00..262500.00 rows=1000000)
  (actual time=83.927..1237.593 rows=1000000 loops=1)
JIT:
  Functions: 4
  Options: Inlining false, Optimization false, Expressions true, Deforming true
  Timing: Generation 1.354 ms, Inlining 0.000 ms, Optimization 4.150 ms, Emission 10.002 ms, Total 15.507 ms
Execution Time: 1345.603 ms
```
*   **Time**: ~1.3 seconds.
*   **Why**: Context switching between SQL engine and PL/pgSQL engine for every row.

**B. Running SQL (Inlined)**
```sql
EXPLAIN ANALYZE
SELECT simple_invest(x, 0.1, 2)
FROM generate_series(1, 1000000) AS x;
```
**Plan**:
```text
Function Scan on generate_series x  (cost=0.00..15000.00 rows=1000000)
  (actual time=69.795..281.639 rows=1000000 loops=1)
Execution Time: 310.512 ms
```
*   **Time**: ~0.3 seconds.
*   **Result**: **4x Faster**.
*   **Why**: The optimizer "inlined" the SQL function, effectively running raw optimized math operations without the function call overhead.

---

## 4. Introducing Dollar Quoting (`$$`)
Using single quotes (`'`) for function bodies is painful because you have to **escape** every single quote inside your code.
*   Old way: `'SELECT ''Hello'' || '' '' || ''World'';'` (Hard to read!)

**The Solution: Dollar Quoting**
You can enclose the body in `$$`. Postgres treats everything fast the `$$` pair as a raw string literal.

```sql
CREATE OR REPLACE FUNCTION mysum(int, int)
RETURNS int AS
$$
    SELECT $1 + $2;
$$ LANGUAGE 'sql';
```

### Advanced: Named Dollar Quotes ($tag$)
Sometimes, `$$` itself conflicts with the language you are using (e.g., in **Perl** or **Bash**, `$$` means "Process ID").
To solve this, or just to make nested code clearer, you can put a "tag" between the dollars.

```sql
CREATE OR REPLACE FUNCTION mysum(int, int)
RETURNS int AS
$body$ -- Start of string
    SELECT $1 + $2;
$body$ -- End of string
LANGUAGE sql;
```

> **Benefit**: As long as the start tag (`$foo$`) matches the end tag (`$foo$`), you can use whatever you want inside. This eliminates 99% of quoting headaches.

---

## 5. Making Use of Anonymous Code Blocks (`DO`)
Sometimes you need to run procedural code *once* (e.g., a complex data migration or administrative cleanup) but don't want to clutter the database by creating a persistent function.

**The Solution: `DO` Blocks**
The `DO` command executes an anonymous code block. It takes no parameters and returns no results.

```sql
DO $$
BEGIN
    RAISE NOTICE 'Current time: %', now();
END;
$$ LANGUAGE plpgsql;
```
*   **Use Case**: One-off scripts, migrations, or testing logic before wrapping it in a function.

---

## 6. New-Style SQL Functions (`BEGIN ATOMIC`)
Traditionally, SQL functions were strings passed to the handler. Recently, PostgreSQL introduced "New-Style" SQL functions that follow the SQL standard more closely.

**The Syntax**:
```sql
CREATE FUNCTION f_new_style(a text, b date)
RETURNS boolean
LANGUAGE SQL
BEGIN ATOMIC
    RETURN a = 'abcd' AND b > '2025-01-01';
END;
```

**Key Differences**:
1.  **No Quoting**: uses `BEGIN ATOMIC ... END` instead of `AS 'string'`.
2.  **Parsed Immediately**: The database parses the SQL body at creation time (unlike string-based functions which are parsed at execution/validation). This catches errors earlier.
3.  **Language**: Strictly for `LANGUAGE SQL` (not PL/pgSQL).

---

## 7. Using Functions and Transactions
A critical rule in PostgreSQL: **A Function runs within the caller's transaction.**

```sql
SELECT now(), mysum(id, id) FROM generate_series(1, 3);
```
All 3 calls to `mysum` happen in the same snapshot.
*   **Implication**: You cannot `COMMIT` or `ROLLBACK` inside a function because it would break the outer statement driving the loop.

### Autonomous Transactions (The Oracle Feature)
"Autonomous Transactions" allow a sub-routine to commit independently of the main transaction (e.g., writing to an audit log even if the main action rolls back).
*   **Status**: **Not natively supported** in PostgreSQL core yet.
*   **Workarounds**: using `dblink` to open a separate connection back to itself.
*   **Future**: Patches exist (using `PRAGMA AUTONOMOUS_TRANSACTION`), but are not yet merged.

---

## 8. Exploring Stored Procedure Languages
PostgreSQL ships with a rich set of languages out of the box. Choosing the right one is key to performance and security.

### The Core Languages
1.  **SQL**:
    *   **Best For**: Simple logic, wrapping queries.
    *   **Pro**: Gives the **Optimizer** the most freedom (it can inline the function).
    *   **Con**: No complex flow control (loops, typical `if-else`).
2.  **PL/pgSQL**:
    *   **Best For**: Complex business logic, flow control, triggers.
    *   **Pro**: Native integration with SQL types.
    *   **Con**: It's a procedural language; shifting back and forth between SQL engine and PL engine has a tiny overhead.
3.  **PL/Perl & PL/Tcl**:
    *   **Best For**: String manipulation (Perl) or rapid scripting.
4.  **PL/Python3U**:
    *   **Best For**: Heavy data processing, machine learning, using external libraries (Pandas, NumPy).

### Trusted vs. Untrusted Languages
You will often see languages paired like `plperl` (Trusted) and `plperlu` (Untrusted). The **U** stands for **Untrusted**. This distinction is vital for security.

**Trusted Languages (e.g., PL/pgSQL, PL/Perl, PL/Tcl)**
*   **Sandbox**: Run in a restricted environment.
*   **Restrictions**:
    *   Cannot access the File System.
    *   Cannot open Network Sockets.
    *   Cannot include/import external libraries that do restricted things.
*   **User**: Can be used by **any user** with permission.

**Untrusted Languages (e.g., PL/Python3U, PL/PerlU)**
*   **No Sandbox**: Run with the full privileges of the OS user running the database (usually `postgres`).
*   **Capabilities**: Can delete files, open connections, exploit infinite loops, etc.
*   **User**: Can **ONLY** be created by **Superusers**.
*   **Note**: Python is strictly **Untrusted** (`plpython3u`) by default because Python's module system is hard to sandbox effectively.

> **Guideline**: Default to **SQL** for simplicity. Use **PL/pgSQL** for logic. Use **PL/Python3U** only when you need the power of the Python ecosystem and trust your superusers explicitly.

---

## 9. Introducing PL/pgSQL
PL/pgSQL is a **Block-Structured** language. It feels very similar to Oracle's PL/SQL.

### The Structure of a Block
Every function consists of two main parts:
1.  **DECLARE**: Space for defining variables.
2.  **BEGIN ... END**: The procedural logic.

**Example: An Investment Calculator**
```sql
CREATE OR REPLACE FUNCTION investment_calculator(
    IN v_amount numeric,    -- Named Parameter
    IN v_interest numeric,
    IN v_years int)
RETURNS numeric AS
$$
DECLARE
    -- Variable Declaration & Assignment
    v_result numeric := 0;
    -- Alias (Alternative name for strict argument index)
    v_sum ALIAS FOR $1; 
BEGIN
    -- Logical assignment
    v_result := v_amount * pow(1 + v_interest, v_years);
    RETURN v_result;
END;
$$ LANGUAGE 'plpgsql';
```

### Variable Management
*   **Named Parameters**: In modern Postgres, you can name arguments directly (e.g., `v_amount`). This is cleaner than referring to `$1`, `$2`.
*   **Aliases**: You can create an alias for a positional parameter (`v_sum ALIAS FOR $1`) inside the declare block. This essentially points `v_sum` to the same memory as `v_amount`.
*   **Assignment**: Use `:=` for assignment (Pascal style), distinct from `=` for comparison.

---

## 16. Introducing PL/Python

If you don't happen to be a Perl expert, **PL/Python** might be the right thing for you. Python has been part of the PostgreSQL infrastructure for a long time and is, therefore, a solid, well-tested implementation.

### Key Caveats
*   **Untrusted Language**: PL/Python is only available as an **untrusted** language (`plpython3u`). From a security point of view, it is important to keep this in mind at all times (usually requires Superuser privileges).
*   **Python 3 Only**: Back in the old days, Postgres supported Python 2. Those days are long gone. From now on, it will only be Python 3.

### Enabling the Language
To enable PL/Python, run the following command (ensuring the `postgresql-plpython-3` packages are installed):

```sql
CREATE EXTENSION plpython3u;
-- Or: CREATE LANGUAGE plpython3u;
```

### 1. Writing Simple PL/Python Code
Writing a Python procedure is straightforward.
**Example: Tax Deduction Calculator**
If you visit a client by car in Austria, you can deduct 0.42 euros per kilometer.

```sql
CREATE OR REPLACE FUNCTION calculate_deduction(km float)
RETURNS numeric AS
$$
    if km <= 0:
        plpy.error('invalid number of kilometers')
    else:
        return km * 0.42
$$ LANGUAGE plpython3u;
```
The function ensures that only positive values are accepted. As you can see, passing a Python function doesn't differ much from PL/pgSQL.

### 2. Using the SPI Interface
Like all procedural languages, PL/Python gives you access to the **SPI (Server Programming Interface)** via the `plpy` object.

**Example: Summing Numbers with a Cursor**
We can create a cursor inside Python to fetch data in chunks.

```sql
CREATE FUNCTION add_numbers(rows_desired integer)
RETURNS integer AS
$$
    mysum = 0
    # cursor() call must be a single line logically unless continued carefully
    cursor = plpy.cursor("SELECT * FROM generate_series(1, %d) AS id" % (rows_desired))
    
    while True:
        rows = cursor.fetch(rows_desired)
        if not rows:
            break
        for row in rows:
            mysum += row['id']
            
    return mysum
$$ LANGUAGE plpython3u;
```
*   **Note**: Python is all about indentation. Ensure your cursor logic and loops are indented correctly.
*   Once the cursor is created, we loop over it. Columns inside rows are accessed by name (dictionary style).

**Execution**:
```sql
test=# SELECT add_numbers(10);
 add_numbers 
-------------
          55
(1 row)
```

### 3. Inspecting Result Sets
PL/Python offers functions to inspect the result set of a SQL statement executed via `plpy.execute`.

```sql
CREATE OR REPLACE FUNCTION result_diag(rows_desired integer)
RETURNS integer AS
$$
    rv = plpy.execute("SELECT * FROM generate_series(1, %d) AS id" % (rows_desired))
    
    plpy.notice(rv.nrows())       # Number of rows
    plpy.notice(rv.status())      # Execution status
    plpy.notice(rv.colnames())    # List of column names
    plpy.notice(rv.coltypes())    # List of OIDs of data types
    plpy.notice(rv.coltypmods())  # Type modifiers
    return 0
$$ LANGUAGE plpython3u;
```
*   **`nrows()`**: Displays the number of rows.
*   **`coltypes()`**: Returns Object IDs (OIDs). For example, OID `23` is `int4`.

**Execution Output**:
```sql
test=# SELECT result_diag(3);
NOTICE:  3
NOTICE:  5
NOTICE:  ['id']
NOTICE:  [23]
NOTICE:  [-1]
 result_diag 
-------------
           0
(1 row)
```

### 4. Handling Errors
You can access `plpy` to catch specific database errors using standard `try...except` blocks.

```sql
CREATE OR REPLACE FUNCTION trial_error()
RETURNS text AS
$$
    try:
        rv = plpy.execute("SELECT surely_a_syntax_error")
    except plpy.SPIError:
        return "we caught the error"
    else:
        return "all fine"
$$ LANGUAGE plpython3u;
```

**Execution**:
```sql
test=# SELECT trial_error();
     trial_error     
---------------------
 we caught the error
(1 row)
```

**Catching Specific Exceptions**:
You can import `spiexceptions` to handle precise error states.

```python
    # Logic snippet
    except spiexceptions.DivisionByZero:
        return "found a division by zero"
    except spiexceptions.UniqueViolation:
        return "found a unique violation"
    except plpy.SPIError as e:  # Python 3 syntax: 'as e' instead of ', e'
        return "other error, SQLSTATE %s" % e.sqlstate
```
Catching errors in Python is robust and can prevent your functions from crashing transactions unexpectedly.
Catching errors in Python is robust and can prevent your functions from crashing transactions unexpectedly.

---

## 17. Improving Functions

So far, we have covered basic functions. But how do we make them *fast*?
In this section, we focus on improving function performance by **reducing calls**, **caching plans**, and **managing costs**.

### 1. Reducing the Number of Function Calls (Volatility)
One of the main reasons for bad performance is calling a function too often. You can control this by flagging your function's **Volatility**: `VOLATILE`, `STABLE`, or `IMMUTABLE`.

**The Three Types of Volatility:**

1.  **`VOLATILE` (Default)**:
    *   **Behavior**: The function **cannot** be optimized away. It is executed over and over again for every row.
    *   **Side Effects**: Can modify the database, return different results on consecutive calls (e.g., `random()`, `timeofday()`).
    *   **Optimization**: None.

2.  **`STABLE`**:
    *   **Behavior**: Returns the *same* result given the *same* arguments **within a single statement or transaction**.
    *   **Use Case**: Lookups, reading current configuration, `now()` (transaction timestamp).
    *   **Optimization**: multiple calls in one statement can be optimized to a single call.

3.  **`IMMUTABLE` (Gold Standard)**:
    *   **Behavior**: Always returns the exact same result given the exact same input, **forever**.
    *   **Use Case**: Math (`2+2`), string manipulation (`upper()`).
    *   **Optimization**: The optimizer can pre-calculate the result effectively replacing the function call with a constant value.

**Example Comparison**:
```sql
-- VOLATILE: Changes every call
SELECT random(), random(); 
-- Result: 0.2762... | 0.7106...

-- STABLE: Constant inside transaction
SELECT now(), now();
-- Result: 2024-10-20 16:23... | 2024-10-20 16:23...

-- IMMUTABLE: Mathematical Constant
SELECT pi();
-- Result: 3.14159...
```

> **Optimization Tip**: Always mark your helper functions as `STABLE` or `IMMUTABLE` if logical. It allows the optimizer to discard redundant calls.

### 2. Using Cached Plans
When a query is executed, it goes through 4 stages: **Parser** -> **Rewrite** -> **Optimizer** -> **Executor**.
For short, frequent queries, the first 3 steps are overhead. **Plan Caching** saves the "Plan" so we can skip straight to Execution next time.

*   **PL/pgSQL**: Does this **automatically**.
*   **PL/Python & PL/Perl**: You have a choice. You can use prepared statements (SPI) to cache plans.
    *   **Short Queries**: Prepare them (cache plan).
    *   **Long Queries**: Do not prepare (allow fresh optimization based on current stats).

### 3. Assigning Costs to Functions
The optimizer treats functions like operators. By default, it guesses their cost is `100` (units of CPU effort).
*   **Problem**: Adding two numbers (`1+1`) is cheap. Intersecting complex polygons in PostGIS is expensive. The optimizer doesn't know the difference.
*   **Solution**: Tell the optimizer the true cost.

```sql
CREATE FUNCTION heavy_math(x int) RETURNS int AS $$
    ... complex logic ...
$$ LANGUAGE plpgsql COST 5000;
```
*   **`COST`**: A multiplier for `cpu_operator_cost`. Higher values discourage the optimizer from using this function in filter conditions (e.g., it might prefer to scan an index first rather than run a heavy function on every row).
*   **`ROWS`**: (For Set-Returning Functions) Estimates how many rows strictly the function will return.

### 4. Advanced: Using Functions for Custom Casts
You can use functions to define how one data type is converted (cast) to another.

**Example: Casting `inet` (IP Address) to `boolean`**
Postgres doesn't know how to turn an IP into a True/False value. Let's say we want any IP to be `True`.

**Step 1: Create the Function**
```sql
CREATE OR REPLACE FUNCTION inet_to_boolean(inet) RETURNS boolean AS $$
BEGIN
    RETURN true; -- Logic goes here
END;
$$ LANGUAGE plpgsql IMMUTABLE;
```

**Step 2: Create the Cast**
```sql
CREATE CAST (inet AS boolean)
    WITH FUNCTION inet_to_boolean(inet)
    AS IMPLICIT;
```
*   **`AS IMPLICIT`**: Allows the cast to happen automatically without typing `::boolean`.

**Testing**:
```sql
test=# SELECT '192.168.0.34'::inet::boolean;
 bool 
------
 t
(1 row)
```

**Conclusion**
We have now covered the full lifecycle of stored procedures: from basic SQL functions to advanced PL/pgSQL logic, secure Python integration, and finally optimization through volatility flags and custom costs.

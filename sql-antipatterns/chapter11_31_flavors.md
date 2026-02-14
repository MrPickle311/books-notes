# Chapter 11: 31 Flavors (Restricting Values)

> **"This column can only be 'NEW', 'OPEN', or 'FIXED'. I'll hardcode these values in the column definition!"**

This is the **31 Flavors** antipattern. It occurs when developers restrict a column's values using `ENUM` types or `CHECK` constraints that contain a hardcoded list of valid strings. While seemingly safe, this approach turns data changes into schema changes.

---

## 11.1 The Objective: Restrict a Column to Specific Values
You want to ensure data validity.
*   `Status`: Must be 'New', 'In Progress', 'Fixed'.
*   `Salutation`: Must be 'Mr.', 'Mrs.', 'Dr.'.
*   You want the database to reject 'BANANA'.

---

## 11.2 The Antipattern: Specify Values in the Definition
You define the valid set inside the `CREATE TABLE` statement.

### Strategy A: The `ENUM` type (MySQL/Postgres)
```sql
CREATE TABLE Bugs (
  id     SERIAL PRIMARY KEY,
  status ENUM('NEW', 'IN PROGRESS', 'FIXED') -- Hardcoded!
);
```

### Strategy B: The `CHECK` Constraint (Standard SQL)
```sql
CREATE TABLE Bugs (
  id     SERIAL PRIMARY KEY,
  status VARCHAR(20) CHECK (status IN ('NEW', 'IN PROGRESS', 'FIXED'))
);
```

### Why it fails
1.  **Changing the List is Expensive (Schema Change)**:
    *   Boss: "Add 'Verified' status".
    *   You: `ALTER TABLE Bugs MODIFY COLUMN status ENUM('NEW'...'VERIFIED')`.
    *   **The Problem**: `ALTER TABLE` can lock the entire table. In some DBs, it rebuilds the whole table (copying every row). Doing this for a simple dropdown change is overkill.

2.  **Getting the List is Hard (The Metadata Trap)**:
    *   App: "I need to show a dropdown of valid statuses."
    *   Query: `SELECT DISTINCT status FROM Bugs`? No, that only shows statuses *currently in use*. If no bugs are 'Fixed', 'Fixed' won't show up.
    *   **The Problem**: You must parse the database metadata (`information_schema`), which is complex and varies by vendor.

3.  **Obsolete Values Stick Around**:
    *   Boss: "Remove 'Fixed', split it into 'Code Complete' and 'QA Verified'".
    *   You can't just delete 'Fixed' from the ENUM if rows still use it. You have to migrate data first, then alter the schema.
    *   With `CHECK` constraints, you can't simply remove a value that old rows depend on without breaking integrity.

4.  **Sorting Surprises**:
    *   `ENUM`s often sort by their internal integer index (1, 2, 3), not alphabetically.
    *   If you add 'Apricot' after 'Banana', it gets index 4. So: Apple(1), Banana(2), Cherry(3), Apricot(4). Sorting is broken.

### Legitimate Uses of the Antipattern
*   **Unchanging Sets**: `Left/Right`, `On/Off`, `Internal/External`.
*   If the set of values is as immutable as math itself, `ENUM` is fine.

## 11.3 The Solution: Specify Values in Data (Lookup Table)
Create a separate table for the values.
```sql
CREATE TABLE BugStatus (
  status VARCHAR(20) PRIMARY KEY
);

INSERT INTO BugStatus (status) VALUES ('NEW'), ('IN PROGRESS'), ('FIXED');

CREATE TABLE Bugs (
  id     SERIAL PRIMARY KEY,
  status VARCHAR(20) REFERENCES BugStatus(status) ON UPDATE CASCADE
);
```

### Why it wins
1.  **Easy Changes (No Downtime)**:
    *   Add 'Verified'? `INSERT INTO BugStatus VALUES ('VERIFIED');`
    *   Rename 'Fixed' to 'Done'? `UPDATE BugStatus SET status='DONE' WHERE status='FIXED';` (With `ON UPDATE CASCADE`, this updates all Bugs instantly!)

2.  **Easy to Query**:
    *   App: `SELECT status FROM BugStatus ORDER BY status`.
    *   Result: A perfect list for your dropdown. No metadata parsing.

3.  **Support for Deprecation**:
    *   Add an `active` column to `BugStatus`.
    *   `UPDATE BugStatus SET active=false WHERE status='Fixed'`.
    *   Old bugs keep 'Fixed', but your UI can filter `WHERE active=true` to hide it from new bugs.

> **Takeaway**: If the list of allowed values can change (Status, Country, Category), use a **Lookup Table**. Only use `ENUM` for fixed binary states (On/Off).

### Mini-Antipattern: Reserved Words
**"Why does `SELECT * FROM Bugs WHERE order = 1` fail?"**

*   **The Trap**: You name a column `order`, `desc`, `key`, or `user`.
*   **The Error**: `Syntax error near 'order'`. The database thinks you mean `ORDER BY`.

**The Solution: Quote Your Identifiers**
If you *must* use a reserved word (don't), you have to quote it.
*   **Standard SQL / Postgres**: Double Quotes -> `"order"`
*   **MySQL**: Backticks -> `` `order` ``
*   **SQL Server**: Brackets -> `[order]`

**Better Solution**: Avoid reserved words. Use `sort_order`, `description`, `api_key`, `user_account`.

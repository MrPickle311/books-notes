# Chapter 26: Foreign Key Mistakes

> **"A parent can have many children, so I'll put a `child_id` in the `Parents` table... wait, why can I only add one child?"**

This chapter covers common pitfalls when implementing Foreign Key (FK) constraints—the vital links that ensure **Referential Integrity** in a relational database.

---

## 26.1 The Objective: Maintain Referential Integrity
Foreign keys ensure that relationships between tables are valid. 
*   **The Goal**: In a One-to-Many (1:M) relationship, ensure that every "Child" record correctly points to a single valid "Parent" record, and that no Parent can be deleted while Children still depend on it.

---

## 26.2 Mini-Antipattern: Reversing the Direction
The most common mistake for beginners is placing the foreign key in the wrong table. 

### The Confusion
If you think, *"The Parent HAS many children,"* you might be tempted to put a `child_id` in the `Parent` table.

```sql
-- WRONG: The "Reversed" Direction
CREATE TABLE Parent (
  parent_id INT PRIMARY KEY,
  child_id INT NOT NULL,
  FOREIGN KEY (child_id) REFERENCES Child(child_id)
);
```

### Why it Fails
1.  **Strict Limit**: A single row in the `Parent` table has only **one** `child_id` column. You can only store one child per parent.
2.  **Wrong Relationship**: This actually creates a **Many-to-One** relationship where many parents can point to the same child, but one parent cannot have multiple children. This is the exact opposite of what you intended.

---

## 26.3 The Solution: The "Many" Side Rule
To correctly implement a 1:M relationship, always place the foreign key in the table that represents the **"Many"** side.

### The Logic: "Child Belongs to Parent"
Instead of saying "Parent has many children," think **"Child belongs to exactly one Parent."**

```sql
-- CORRECT: The FK is in the Child table
CREATE TABLE Parent (
  parent_id INT PRIMARY KEY
);

CREATE TABLE Child (
  child_id INT PRIMARY KEY,
  parent_id INT NOT NULL,
  FOREIGN KEY (parent_id) REFERENCES Parent(parent_id)
);
```

### Why this works:
*   **Scalability**: The `Parent` table doesn't need to change as you add children.
*   **Flexibility**: You can add 0, 1, or 1,000,000 rows to the `Child` table that all reference the same `parent_id`.
*   **Constraint**: The database ensures that you cannot insert a `Child` with a `parent_id` that doesn't exist in the `Parent` table.

> **Takeaway**: **The Foreign Key always goes on the "Many" side.** If you can’t decide which table that is, ask: "Which one *belongs* to the other?"

---

## 26.4 Mini-Antipattern: The Creation Order Trap
SQL is a procedural engine when it comes to Schema Definition (DDL). You cannot reference a shadow.

### The Problem
If you try to create the `Child` table before the `Parent` table, the database has no target to validate the constraint against.
```sql
-- ERROR: Parent doesn't exist yet!
CREATE TABLE Child (
  parent_id INT NOT NULL,
  FOREIGN KEY (parent_id) REFERENCES Parent(parent_id)
);
```
**Result**: `ERROR 1824: Failed to open the referenced table 'Parent'`

### The Solution
1.  **Order Matters**: Always create "Root" tables (those with no dependencies) first.
2.  **Mutual References**: If Table A needs Table B and Table B needs Table A, create them both without FKs first, then use `ALTER TABLE` to add the constraints.

---

## 26.5 Mini-Antipattern: Referencing No Key
A foreign key must point to a specific, unique row. It cannot point to a "maybe."

### The Problem
You cannot create an FK that points to a non-unique column.
```sql
CREATE TABLE Parent (
  email VARCHAR(255) -- NOT a Primary Key or Unique Key
);

CREATE TABLE Child (
  parent_email VARCHAR(255),
  FOREIGN KEY (parent_email) REFERENCES Parent(email)
);
```
**Result**: `ERROR 1822: Missing index for constraint... in the referenced table 'Parent'`

### The Solution:
The target column in the `Parent` table **must** be either the `PRIMARY KEY` or have a `UNIQUE` constraint. This ensures the "1" side of the 1:M relationship is truly unique.

---

## 26.6 Mini-Antipattern: The Compound Key Split
If your parent has a composite key, your child must reference the whole set, not pieces of it.

### The Problem
```sql
CREATE TABLE Parent (
  p_id1 INT, p_id2 INT,
  PRIMARY KEY (p_id1, p_id2)
);

-- WRONG: Splitting the reference into separate constraints
CREATE TABLE Child (
  ref1 INT, ref2 INT,
  FOREIGN KEY (ref1) REFERENCES Parent(p_id1), -- Error!
  FOREIGN KEY (ref2) REFERENCES Parent(p_id2)  -- Error!
);
```

### The Solution
Treat the multi-column key as a single atomic unit in the FK definition:
```sql
CREATE TABLE Child (
  ref1 INT, ref2 INT,
  FOREIGN KEY (ref1, ref2) REFERENCES Parent(p_id1, p_id2)
);
```
*   **Warning**: You must maintain the exact same **order** of columns as defined in the Parent's primary key.

---

## 26.7 Mini-Antipattern: Mismatched Types (The "Signed" Trap)
For a foreign key to work, the columns must be "twins."

### The Problem
Even a subtle difference like `SIGNED` vs `UNSIGNED` will break the link.
```sql
CREATE TABLE Parent (parent_id INT PRIMARY KEY);

CREATE TABLE Child (
  parent_id INT UNSIGNED NOT NULL, -- Type mismatch!
  FOREIGN KEY (parent_id) REFERENCES Parent(parent_id)
);
```
**Result**: `ERROR 3780: Referencing column and referenced column are incompatible.`

### The Rules:
1.  **Integers**: Must match in size (`BIGINT` vs `INT`) and signedness.
2.  **Strings**: Must match in Character Set and Collation.
3.  **Exception**: `VARCHAR` lengths can differ (e.g., `VARCHAR(10)` can point to `VARCHAR(20)`), but this is usually a sign of poor design.

---

## 26.8 Mini-Antipattern: Mismatched Collations
If the tables speak different "alphabets," the connection fails.

### The Problem
If `Parent` uses `utf8mb4_unicode_ci` and `Child` uses `utf8mb4_general_ci`, the database cannot guarantee that comparisons (which are required for FK checks) will behave identically.

### The Solution:
Align your character sets and collations across the entire database. If you must have different collations, the columns involved in the Foreign Key **must** match exactly to ensure the comparison logic is identical.

---

## 26.9 Mini-Antipattern: Creating Orphan Data
If you add a foreign key to a table that already has data, the database will "health check" every existing row.

### The Problem
If the `Child` table contains even one value that doesn't exist in the `Parent` table, the `ALTER TABLE` command will fail.
```sql
-- Parent has 1234
-- Child has 1234 AND 5678 (where 5678 doesn't exist in Parent)
ALTER TABLE Child
  ADD FOREIGN KEY (parent_id) REFERENCES Parent(parent_id);
```
**Result**: `ERROR 1452: Cannot add or update a child row: a foreign key constraint fails`

### The Solution:
You must "clean house" before applying the constraint.
1.  **Check for Orphans**: Use a `LEFT JOIN` to find rows in `Child` where the `Parent` is `NULL`.
2.  **Fix the Data**:
    *   **Insert** the missing records into the `Parent` table.
    *   **Update** the orphan rows in `Child` to point to a valid parent or set them to `NULL`.
    *   **Delete** the orphan rows from the `Child` table.

---

## 26.10 Mini-Antipattern: SET NULL on NOT NULL
Actions speak louder than words, but they can't break the laws of physics.

### The Problem
If a column is defined as `NOT NULL`, you cannot tell the database to `SET NULL` when a parent is deleted.
```sql
CREATE TABLE Child (
  parent_id INT NOT NULL, -- Physics restriction: Cannot be NULL
  FOREIGN KEY (parent_id) REFERENCES Parent(parent_id)
    ON DELETE SET NULL -- Logic contradiction!
);
```
**Result**: `ERROR 1830: Column 'parent_id' cannot be NOT NULL: needed in a foreign key constraint SET NULL`

### The Solution:
Decide on your business logic:
*   If the child **must** belong to a parent, use `ON DELETE CASCADE` (delete the child too) or `ON DELETE RESTRICT` (prevent parent deletion).
*   If the child can survive without a parent, change the column definition to allow `NULL`.

---

## 26.11 Mini-Antipattern: Duplicate Identifiers
Every constraint in your database needs a unique social security number (its name).

### The Problem
Constraint names (identifiers) must be unique across the **entire schema**, not just within the table.
```sql
-- Table 1
CONSTRAINT fk_parent FOREIGN KEY (parent_id) ...

-- Table 2
CONSTRAINT fk_parent FOREIGN KEY (parent_id) ... -- ERROR: Name already taken!
```
**Result**: `ERROR 1826: Duplicate foreign key constraint name 'fk_parent'`

### The Solution:
Use a consistent naming convention that includes the table and column name:
*   `fk_child1_parentid`
*   `fk_child2_parentid`
*   Alternatively, let the database generate names automatically by omitting the `CONSTRAINT name` part of the syntax.

---

## 26.12 Mini-Antipattern: Incompatible Table Types
You can't link two tables if they live in different worlds.

### The Problem
A permanent table cannot have a foreign key pointing to a `TEMPORARY` table, and vice-versa (in many standard SQL implementations).
*   **The Logic**: A temporary table disappears when the session ends. If a permanent table referenced it, the database would have "zombie" references the next morning.

### The Solution:
Ensure both the Parent and Child are the same type of table (e.g., both persistent base tables).

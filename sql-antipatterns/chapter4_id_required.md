# Chapter 4: ID Required (Surrogate Key Obsession)

> **"Just add an `id` column to every table. It's best practice."**

This is the **ID Required** antipattern. While Primary Keys are mandatory, blindly adding `id SERIAL PRIMARY KEY` to *every* table—especially intersection tables—often leads to duplicate data and confusion.

---

## 4.1 The Objective: Establish Primary Keys
You need to uniquely identify every row.
*   **Natural Key**: A unique value that exists in the real world (e.g., `user_email`, `isbn`).
*   **Surrogate Key**: An artificial value (e.g., `id = 1, 2, 3`) with no business meaning.

Surrogate keys are great for mutable data (people change names, emails change). However, they are not a silver bullet.

---

## 4.2 The Antipattern: One Size Fits All
The convention "Every table must have an `id` column" is so strong it overrides common sense.

### The Problems Created
1.  **Duplicate Rows (Masked by ID)**
    In an intersection table `BugsProducts`, adding a surrogate `id` allows you to insert the same Bug-Product pair twice.
    ```sql
    CREATE TABLE BugsProducts (
      id          SERIAL PRIMARY KEY, -- The Problem
      bug_id      BIGINT REFERENCES Bugs(bug_id),
      product_id  BIGINT REFERENCES Products(product_id)
    );
    -- This inserts successfully, creating logical duplicates:
    INSERT INTO BugsProducts (bug_id, product_id) VALUES (1, 2);
    INSERT INTO BugsProducts (bug_id, product_id) VALUES (1, 2); 
    ```
    You now have two rows for the same relationship, just with different "IDs".

2.  **Obscured Meaning (Ambiguous Joins)**
    When every table has a column named `id`, joins become confusing.
    ```sql
    SELECT b.id, a.id 
    FROM Bugs b JOIN Accounts a ON b.assigned_to = a.id;
    ```
    *Result*: `id` and `id`. Which is which? You must alias them (`b.id AS bug_id`).

3.  **Redundant Keys**
    If you already have a unique natural key (e.g., `project_code VARCHAR unique`), adding an `id` column is wasteful.
    ```sql
    CREATE TABLE Projects (
      id           SERIAL PRIMARY KEY, -- Useless
      project_code VARCHAR(10) UNIQUE, -- The Real Key
    );
    ```

4.  **Disabling `USING` Syntax**
    SQL allows concise joins if column names match: `JOIN Bugs USING (bug_id)`. If you name everything `id`, you can't use this and must write `ON b.id = bp.bug_id`.

---

## 4.3 The Solution: Tailored to Fit
A Primary Key is a constraint, not a data type. It doesn't *have* to be `id SERIAL`.

### Tell It Like It Is (Descriptive Names)
Name your key after the entity it identifies.
*   **Bad**: `id` (Generic)
*   **Good**: `bug_id`, `account_id`, `article_id`.
*   **Benefit**: When you join tables, `ON bugs.bug_id = accounts.bug_id` works with `USING (bug_id)` and is self-documenting.

### Embrace Composite Keys
For intersection tables, the **Primary Key** should be the combination of the foreign keys.
```sql
CREATE TABLE ArticleTags (
  article_id  BIGINT REFERENCES Articles(id),
  tag_id      BIGINT REFERENCES Tags(id),
  PRIMARY KEY (article_id, tag_id) -- The Real Solution
);
```
*   **Guaranteed Uniqueness**: You literally *cannot* insert duplicate pairs.
*   **No Extra Column**: You save space.

### Legitimate Uses of the Antipattern
Sometimes, adding a surrogate `id`, even redundantly, is the right choice.
1.  **Framework Conventions**: If Rails or Django expects `id`, fighting it might cost more than the redundancy.
2.  **Long Natural Keys**: If your natural key is a `VARCHAR(500)` file path, indexing it is slow. A minimal `BIGINT id` is faster for joins.
3.  **Privacy**: A natural key like `ssn` or `email` shouldn't be exposed in URLs (`/users/123` is safer than `/users/bob@example.com`).

### Mini-Antipattern: "But BIGINT isn't big enough!"
Should you worry about running out of IDs?
*   **32-bit INT**: Max ~2 Billion. At 10k rows/min, it lasts **149 days**. (Risk!)
*   **64-bit BIGINT**: Max ~9 Quintillion (9,223,372,036,854,775,807).
    *   At 10,000 rows/minute: It lasts **1.7 Billion Years**.
    *   **Verdict**: You will not run out. Use `BIGINT`.

> **Takeaway**: Don't be an "ID Zombie". Use a natural unique key (like `article_id + tag_id`) if one exists. Name your columns descriptively (`bug_id`). And trust `BIGINT`.

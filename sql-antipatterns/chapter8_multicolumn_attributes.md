# Chapter 8: Multicolumn Attributes

> **"A bug can have up to 3 tags, so I'll just add `tag1`, `tag2`, and `tag3` columns."**

This is the **Multicolumn Attributes** antipattern. It occurs when developers need to store multiple values for an attribute (like tags or phone numbers) and decide to add numbered columns (`tag1`, `tag2`, `tag3`) to the table instead of creating a separate relationship table.

---

## 8.1 The Objective: Store Multivalue Attributes
You want to associate multiple values with a single entity.
*   A `Bug` can be tagged "Printing", "Performance", and "Crash".
*   A `User` can have "Home", "Work", and "Mobile" phone numbers.
*   The values are not mutually exclusive (you can have all of them).

---

## 8.2 The Antipattern: Create Multiple Columns
You define a fixed number of columns to hold the values.
```sql
CREATE TABLE Bugs (
  id          SERIAL PRIMARY KEY,
  description VARCHAR(1000),
  tag1        VARCHAR(20),
  tag2        VARCHAR(20),
  tag3        VARCHAR(20)
);
```

### Why it fails
1.  **Searching is Painful**:
    *   To find bugs tagged 'Performance', you must check *every* column.
    ```sql
    SELECT * FROM Bugs
    WHERE 'Performance' IN (tag1, tag2, tag3);
    ```
    *   **The Trap**: Finding bugs with *both* 'Performance' AND 'Printing' becomes a nightmare of nested `AND/OR` logic.
    ```sql
    WHERE ('Performance' IN (tag1, tag2, tag3))
      AND ('Printing'    IN (tag1, tag2, tag3));
    ```

2.  **Update Logic Chaos**:
    *   **Add a Tag**: Which column is empty? `tag2`? `tag3`?
    *   **Two-Step**: You `SELECT` to find a null, then `UPDATE`. **Race Condition!** User B might fill `tag2` while you were thinking.
    *   **Single-Step**: You need a massive `CASE` statement:
    ```sql
    UPDATE Bugs SET
      tag1 = COALESCE(tag1, 'NewTag'),
      tag2 = CASE WHEN tag1 IS NOT NULL THEN COALESCE(tag2, 'NewTag') ... END
    ```

3.  **No Uniqueness (Internal Duplicates)**:
    *   You can have `tag1='Crash'` and `tag2='Crash'`.
    *   The database constraints (UNIQUE) work per-column, not across `(tag1, tag2, tag3)`.

4.  **Growth Limits (Breaking the App)**:
    *   The day you need 4 tags, you must `ALTER TABLE ADD COLUMN tag4`.
    *   **Result**: You must rewrite *every* SQL query in your app to include `OR tag4 = ...`.

### Legitimate Uses of the Antipattern
If the columns have **distinct meanings**, it's not an antipattern.
*   **Example**: `reporter_id`, `assignee_id`, `qa_engineer_id`.
*   These are not "3 generic users". They are 3 specific roles. Storing them in columns is correct.

## 8.3 The Solution: Create Dependent Table
Store the multivalue attribute in a child table.
```sql
CREATE TABLE Tags (
  bug_id BIGINT REFERENCES Bugs(id),
  tag    VARCHAR(20),
  PRIMARY KEY (bug_id, tag) -- Enforces Uniqueness!
);
```

### Why it wins
1.  **Simple Search**:
    *   Find bugs with 'Performance':
    ```sql
    SELECT * FROM Bugs JOIN Tags USING (bug_id) WHERE tag = 'Performance';
    ```
    *   Find bugs with 'Performance' AND 'Printing':
    ```sql
    SELECT * FROM Bugs 
    JOIN Tags t1 ON Bugs.id = t1.bug_id AND t1.tag = 'Performance'
    JOIN Tags t2 ON Bugs.id = t2.bug_id AND t2.tag = 'Printing';
    ```

2.  **Simple Updates**:
    *   **Add**: `INSERT INTO Tags (bug_id, tag) VALUES (1, 'Crash')`. No checking "which column is empty?".
    *   **Remove**: `DELETE FROM Tags WHERE bug_id=1 AND tag='Crash'`.

3.  ** infinite Scalability**:
    *   You can add 5, 10, or 100 tags. No `ALTER TABLE` ever.

### Mini-Antipattern: Storing Prices (The Snapshot)
**"Normalization says don't duplicate data. So I shouldn't store `price` in the `Orders` table, right?"**

*   **The Scenario**: You have `Products(id, price)` and `Orders(id, product_id)`.
*   **The Trap**: Calculating order total by joining: `Orders JOIN Products`.
*   **The Problem**: Prices change! If you raise the price of a Widget today, it accidentally changes the history of every order placed last year.

**The Solution: Snapshotting**
You **MUST** store the price in the `OrderItems` table.
```sql
CREATE TABLE OrderItems (
  order_id   BIGINT,
  product_id BIGINT,
  quantity   INT,
  price      DECIMAL(10,2) -- Copy of price AT START OF TRANSACTION
);
```
*   **Is it Redundant?**: No. `Products.price` is the *Current* price. `OrderItems.price` is the *Historical* price. They are different facts.
*   **Rule**: In intersection tables (Orders, Invoices), always copy mutable attributes (Price, Address) to preserve history.

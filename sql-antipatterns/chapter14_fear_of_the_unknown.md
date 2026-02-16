# Chapter 14: Fear of the Unknown (NULL)

> **"NULL is confusing! I'll just use -1 or an empty string to mean 'Missing Data'."**

This is the **Fear of the Unknown** antipattern. It occurs when developers avoid `NULL` by using "magic values" (like `-1`, `0`, `'N/A'`, `'1900-01-01'`) or when they treat `NULL` as if it were an ordinary value (expecting `NULL = NULL` to be true).

---

## 14.1 The Objective: Distinguish Missing Values
You need to represent "Value Unknown" or "Value Inapplicable".
*   `MiddleInitial`: Not everyone has one.
*   `AssignedTo`: A bug might not be assigned yet.
*   `EndDate`: An employee currently working has no end date.

---

## 14.2 The Antipattern: Use Ordinary Values for NULL (or vice-versa)

### Trap A: Treating NULL like a value
You think `NULL` behaves like `0` or `false`.
```sql
SELECT first_name || ' ' || middle_name || ' ' || last_name 
FROM Accounts;
```
*   **Result**: If `middle_name` is NULL, the **entire string becomes NULL**.
*   **Math**: `10 + NULL = NULL`.
*   **Logic**: `NULL = NULL` is **Unknown** (not True!). `NULL <> NULL` is also Unknown.

### Trap B: "We Hate NULLs" (Magic Numbers)
You declare columns as `NOT NULL` and use fake values.
```sql
CREATE TABLE Bugs (
  id INT,
  assigned_to INT NOT NULL DEFAULT -1, -- "-1 means Unassigned"
  hours INT NOT NULL DEFAULT 0
);
```
*   **Why it fails**:
    1.  **Broken Aggregates**: `AVG(hours)` includes the `0`s, pulling the average down incorrectly. You must remember to write `WHERE hours <> 0`.
    2.  **Referential Integrity Issues**: `assigned_to = -1`. You must create a "Fake User" with ID -1 in the `Accounts` table.

## 14.3 The Solution: Use NULL as a Unique State
Embrace the 3-Valued Logic: **True**, **False**, and **Unknown**.

### 1. Mastering 3-Valued Logic
Understand that `NULL` propagates.

**Scalar Comparisons**
| Expression | Result | Reason |
| :--- | :--- | :--- |
| `NULL = 0` | `NULL` | NULL is not 0. |
| `NULL = NULL` | `NULL` | Unknown if one unknown equals another. |
| `NULL <> 123` | `NULL` | Unknown if it is different. |
| `NULL + 10` | `NULL` | 10 more than unknown is unknown. |

**Boolean Logic**
| Expression | Result | Reason |
| :--- | :--- | :--- |
| `NULL AND True` | `NULL` | Result depends on the unknown. |
| `NULL AND False` | `False` | False AND anything involves False. |
| `NULL OR True` | `True` | True OR anything covers it. |
| `NOT (NULL)` | `NULL` | Opposite of unknown is unknown. |

### 2. Searching for NULL
You cannot use `=` or `<>`. You must use `IS`.
```sql
-- Wrong
SELECT * FROM Bugs WHERE assigned_to = NULL; -- Returns Nothing

-- Right
SELECT * FROM Bugs WHERE assigned_to IS NULL;
```

**Pro-Tip: The `IS DISTINCT FROM` Predicate**
If you want to treat NULL as a "value" for comparison (so that two NULLs differ from a value but equal each other), use this:
```sql
-- Returns TRUE if assigned_to is NULL and value is 1
SELECT * FROM Bugs WHERE assigned_to IS DISTINCT FROM 1;
```

### 3. Handling Values (`COALESCE`)
If you need a default value *only for display*, use `COALESCE()`.
```sql
SELECT first_name || ' ' || COALESCE(middle_name || ' ', '') || last_name 
FROM Accounts;
```
*   If `middle_name` is NULL, `COALESCE` returns `''` (empty string), saving the concatenation.

> **Takeaway**: Use `NOT NULL` only for mandatory data (ID, Date Created). Use `NULL` for optional or missing data. Use `COALESCE` to handle it in queries.

### Mini-Antipattern: NOT IN (NULL)
**"Why does my query return ZERO rows?"**

*   **The Query**:
    ```sql
    SELECT * FROM Bugs WHERE assigned_to NOT IN (1, 2, NULL);
    ```
*   **The Expectation**: Show bugs not assigned to 1 or 2.
*   **The Reality**: **Empty Result**.

**Why?**
The query expands to:
```sql
WHERE (assigned_to <> 1) AND (assigned_to <> 2) AND (assigned_to <> NULL)
```
1.  `assigned_to <> 1` -> True
2.  `assigned_to <> 2` -> True
3.  `assigned_to <> NULL` -> **Unknown**
4.  `True AND True AND Unknown` -> **Unknown**

**The Rule**: The `WHERE` clause only returns rows that evaluate to **TRUE**. It filters out **FALSE** and **UNKNOWN**.
Since the result is **Unknown**, **Zero Rows are returned**.

**Solution**:
Use `NOT EXISTS` or ensure the subquery filters out NULLs (`WHERE col IS NOT NULL`).

# Chapter 2: Jaywalking (Comma-Separated Lists)

> **"Just store the IDs as a comma-separated string. We don't need another table."**

This phrase is the hallmark of the **Jaywalking** antipattern. It happens when developers try to implement a Many-to-Many relationship without creating an intersection table, opting instead to stuff multiple values into a single column (e.g., `account_ids = "12,34,56"`).

---

## 2.1 The Objective: Store Multiple Values
You represent a relationship where one `Product` can have multiple `Contacts`.
*   **Naive Approach**: Add an `account_id` column to the `Products` table.
*   **Problem**: A product has 3 contacts.
*   **The Hack**: Change `account_id` to `VARCHAR` and store `"12,34,56"`.

---

## 2.2 The Antipattern: Comma-Separated Lists
Validating, searching, and updating data becomes a nightmare.

### Why it fails
1.  **Querying (The Regex Pain)**
    *   Find all products for account 12:
        ```sql
        -- Can't use EQUALS
        SELECT * FROM Products WHERE account_id = 12; -- Fails
        -- Must use Pattern Matching (Slow, No Index)
        SELECT * FROM Products WHERE account_id LIKE '%12%'; -- Matches 112, 122!
        -- Must use Complex Regex
        SELECT * FROM Products WHERE account_id REGEXP '[[:<:]]12[[:>:]]';
        ```
    *   **Performance**: Full Table Scans for everything. Indexes are useless.

2.  **Joining (The Impossible Join)**
    *   You want to join `Products` to `Accounts`.
    *   You can't. You have to join on a regex or substring, which is excruciatingly slow and non-standard.

3.  **Aggregating (The Math Trick)**
    *   Count contacts per product?
    *   `SELECT count(*)` doesn't work.
    *   Hack: `(LENGTH(account_id) - LENGTH(REPLACE(account_id, ',', ''))) + 1`.
    *   This is fragile and confusing.

4.  **Updates (The String Surgery)**
    *   Remove account 34?
    *   You must fetch the string, split it in app code, remove 34, join it back, and update.
    *   **Concurrency Risk**: Two users updating the list at the same time will overwrite each other's changes.

5.  **Validation (The "Banana" Problem)**
    *   `INSERT INTO Products ... VALUES ('12,34,banana');`
    *   Database accepts it. You have lost Data Integrity. There are no Foreign Keys to protect you.

6.  **Length Limits**
    *   `VARCHAR(30)` eventually runs out of space.

---

## 2.3 The Solution: Intersection Table
Create a new table just for the relationship. Also known as a **Junction Table** or **Association Table**.

```sql
CREATE TABLE Contacts (
    product_id  BIGINT REFERENCES Products(product_id),
    account_id  BIGINT REFERENCES Accounts(account_id),
    PRIMARY KEY (product_id, account_id)
);
```

### Why it wins
1.  **Indexing**: Search by `product_id` OR `account_id` instantly.
2.  **Foreign Keys**: Database ensures `account_id` 999 exists before inserting. No "bananas".
3.  **Simple SQL**:
    ```sql
    -- Find products for account 34
    SELECT p.* 
    FROM Products p 
    JOIN Contacts c ON p.id = c.product_id 
    WHERE c.account_id = 34;
    ```
4.  **Granular Updates**:
    *   Add: `INSERT INTO Contacts ...`
    *   Remove: `DELETE FROM Contacts ...`
    *   No need to read-modify-write the whole list.

---

## 2.4 Managing the Mess (If you are stuck with it)
If you inherit a database with Jaywalking and can't fix the schema immediately, use Postgres's array functions to survive.

**Split and Unnest**:
Postgres allows you to turn a string into rows on the fly.
```sql
SELECT product_id, unnest(string_to_array(account_id, ',')) as single_account_id
FROM Products;
```
This allows you to join and query somewhat sanely, though performance will still be poor compared to a normalized design.

> **Takeaway**: Relational databases are designed for atomic values (one value per cell). If you need a list, create a new table. "Comma-Separated" is a one-way ticket to performance hell.

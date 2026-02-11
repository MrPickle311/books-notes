# Chapter 5: Keyless Entry (No Foreign Keys)

> **"Foreign Keys are too slow and restrictive. We'll handle integrity in the app."**

This is the **Keyless Entry** antipattern. It occurs when developers skip Referential Integrity (Foreign Key) constraints in the database, assuming their code is perfect enough to prevent data corruption. Spoiler: It isn't.

---

## 5.1 The Objective: Simplify Database Architecture
You want a simple, flexible database.
*   **Referential Integrity**: Ensuring that if a `Bug` says it was reported by User 123, User 123 actually exists.
*   **The Trap**: Believing that Foreign Keys complicate development, cause errors during testing, or hurt performance, leading to their removal.

---

## 5.2 The Antipattern: Leave Out the Constraints
Why write `FOREIGN KEY` when you can just... believe?

### The "Perfect Code" Fallacy
You assume your application will always:
1.  Check if `User 123` exists *before* inserting a bug.
2.  Check if `User 123` has any bugs *before* deleting the user.
    ```sql
    -- Step 1: Check existence (App Logic)
    SELECT * FROM Accounts WHERE id = 1;
    -- Step 2: Insert (App Logic)
    INSERT INTO Bugs (reported_by) VALUES (1);
    ```

### Why it fails
1.  **Race Conditions**:
    *   **Time 0**: App A checks "Does User 1 exist?" -> YES.
    *   **Time 1**: App B deletes User 1.
    *   **Time 2**: App A inserts `Bug(reported_by=1)` -> **SUCCESS (Corruption!)**.
    *   You now have an **Orphaned Row**. The bug refers to a ghost.
    
2.  **The "Catch-22" Update**:
    *   You want to change a status name from "BOGUS" to "INVALID" in both parent and child tables.
    *   **Without FKs**: You update Parent. Child is now orphaned (points to old "BOGUS"). You update Child. Data is inconsistent for a split second.
    *   **With FKs**: Attempting to update Parent fails because Children still point to it. Developers see this error and think "FKs are annoying," so they delete the constraint instead of fixing the update logic (e.g., `ON UPDATE CASCADE`).

3.  **Polluting the Database**:
    *   Scripts, manual admin fixes, and legacy code don't respect your "Application Logic".
    *   Eventually, `SELECT * FROM Bugs JOIN Accounts ...` returns fewer rows than `SELECT * FROM Bugs` because the join fails on invalid IDs.
    *   **Result**: You write "Quality Control Scripts" to find and fix orphans periodically. **You are reinventing the database engine poorly.**

### It's Not My Fault (External Polluters)
Even if your application code *is* perfect (it isn't), you are not the only one touching the database.
1.  **Multiple Applications**: A new Microservice or Analytics script connects directly. It doesn't know your validation rules.
2.  **Manual Fixes**: A DBA runs `UPDATE Bugs SET reported_by = 99999 WHERE reported_by IS NULL` to fix a report. Oops, User 99999 doesn't exist.
3.  **Future Developers**: Someone refactors the `User` deletion code and forgets the `Bug` check.

**Result**: Your database is now full of "Orphaned Rows" that crash your main application when it tries to join them.

### The Performance Myth
"Foreign Keys slow down inserts."
*   **Reality**: Yes, the DB must check the Index of the parent table.
*   **The Alternative**: You run a `SELECT` query from your app to check existence. This is **much slower** (Network Round Trip + Parsing + Execution) than the internal C-level check the DB performs.

### Legitimate Uses of the Antipattern
Sometimes you *must* drop Foreign Keys.
1.  **Extreme Locking**: On massive scale, updating a parent row locks child rows. If this contention kills your throughput, you might drop FKs (but you need a cleaner process).
2.  **Distributed Sharding**: If `Users` are on Server A and `Bugs` are on Server B, the database cannot enforce the link. You are forced to do it in the app.
3.  **Temporary Migrations**: Tools like `pt-online-schema-change` often drop constraints temporarily to facilitate massive table rewrites.

## 5.3 The Solution: Declare Constraints (Poka-Yoke)
**Poka-Yoke** (Japanese for "Mistake Proofing") creates a system where errors are impossible.
*   **The Constraint**:
    ```sql
    CREATE TABLE Bugs (
      reported_by BIGINT REFERENCES Accounts(id) 
        ON UPDATE CASCADE 
        ON DELETE RESTRICT -- Can't delete User if they have bugs
    );
    ```
*   **The Benefit**:
    1.  **Impossible to Corrupt**: `INSERT INTO Bugs (reported_by) VALUES (9999)` -> **Rejected DB Error**.
    2.  **Cascading Updates**: `UPDATE Accounts SET id = 2 WHERE id = 1` -> Automatically updates all bugs from 1 to 2. No Catch-22!
    3.  **No Code Required**: You delete the account, the DB tells you "No". You don't write `SELECT count(*)` checks.

### Overhead? Not Really.
Checking an index (`O(log N)`) is cheaper than:
1.  Sending a `SELECT` over the network.
2.  Parsing and Planning that query.
3.  Locking the table explicitly to prevent races.
4.  Cleaning up the mess later with scripts.

> **Takeaway**: Use Foreign Keys. They are the cheapest, fastest, and most reliable way to ensure your data isn't garbage.

# Chapter 22: Pseudokey Neat-Freak

> **"There's a gap between ID 2 and ID 4. Where is 3? I need to renumber these to make them tidy."**

This is the **Pseudokey Neat-Freak** antipattern. It occurs when developers (or managers) try to fill gaps in a sequence of primary keys, treating the IDs as a "count" rather than unique identifiers.

---

## 22.1 The Objective: Tidy Up the Data
Human beings naturally find gaps in a series unnerving.
*   **The Intent**: Make reports look cleaner and ensure no data was "lost."
*   **The assumption**: A gap means a failure. "If ID 3 is missing, did the database crash? Did we lose a sale?"

---

## 22.2 The Antipattern: Filling in the Corners
There are two common ways people try to "fix" gaps:

### 1. Assigning Numbers Out of Sequence
Instead of using the next auto-increment value, you hunt for the lowest unused ID.
```sql
-- Query to find the first "hole" in the sequence
SELECT b1.bug_id + 1
FROM Bugs b1
LEFT OUTER JOIN Bugs AS b2 ON (b1.bug_id + 1 = b2.bug_id)
WHERE b2.bug_id IS NULL
ORDER BY b1.bug_id LIMIT 1;
```
*   **Problem**: This is extremely inefficient and causes **concurrency collisions**. Two users might simultaneously find "3" as the lowest value and both try to insert it, causing one to fail.

### 2. Renumbering Existing Rows
Rewriting the sequence to be contiguous.
```sql
-- Move ID 4 to ID 3
UPDATE Bugs SET bug_id = 3 WHERE bug_id = 4;
```
*   **Problem**: This is a maintenance nightmare. If you don't have `ON UPDATE CASCADE` configured perfectly on all foreign keys, you just orphaned every child record (comments, attachments, logs) belonging to that bug.

---

## 22.3 Why it Fails (The Real Costs)

### 1. Breaking External References
In the real world, IDs are printed on shipping labels, referenced in emails, and saved in accountants' spreadsheets. 
*   If you change Asset ID `100` to `95`, your physical labels no longer match your digital records.
*   Last quarter's reports will no longer match this quarter's, even if the data itself hasn't changed.

### 2. Recycling Corruption (Identity Theft)
Suppose user `789` was banned for harassment. You delete them and "recycle" the ID for a new, polite user. 
*   If old logs or "举报" (reports) reference `789`, the new user will be blamed for the old user's crimes.
*   **Rule**: Never reuse an ID. A gap is a historical record that a row *once existed*.

### 3. Fighting the Engine
Most database engines (MySQL, Postgres) don't look back. Even if you fill the gap at ID 3, the `AUTO_INCREMENT` or `SEQUENCE` counter is still at 5. The next insert will be 5, leaving a gap at 4. You are fighting a losing battle against the database's internal counters.

### 4. Performance
Finding "holes" requires expensive joins or table scans. Renumbering requires massive updates and index rebuilds. 

> **Takeaway**: **Primary Keys are Identifiers, not Row Numbers.** Gaps are normal. They are the fingerprints of deletions and rolled-back transactions. Let them be.

## 22.4 The Solution: Get Over It
The only rules for a primary key are that it must be **unique** and **non-null**. There is no "Contiguous Rule."

### 1. Row Numbers vs. Primary Keys
Don't confuse the two. If you need a sequential list for a report or pagination, use the `ROW_NUMBER()` window function. It calculates a "tidy" sequence on the fly without touching your data.
```sql
SELECT 
    bug_id, 
    summary,
    ROW_NUMBER() OVER (ORDER BY date_reported) AS display_order
FROM Bugs;
```
*   **Result**: If `bug_id` 3 is missing, `display_order` will still be 1, 2, 3... This gives the user the "neatness" they want without breaking the database.

### 2. Using GUIDs (The Ultimate "Fix")
If people can't stop obsessing over gaps in numbers, stop using numbers. Use **GUIDs/UUIDs**.
*   **ID**: `550e8400-e29b-41d4-a716-446655440000`
*   **Benefit**: Nobody complains about "missing" UUIDs because there is no obvious sequence.
*   **Trade-off**: Harder to read and slightly slower/larger than a 4-byte integer.

### 3. Managing the Manager (The Soft Skill)
If your boss asks to "fix the gaps," explain the **Real Cost**:
*   **Cost of Change**: Renumbering takes days of development and testing.
*   **Risk of Outage**: Locking tables to renumber millions of rows will crash production.
*   **Corruption**: External tools (Excel, shipping systems) will lose track of reality.
*   **Truth**: "Gaps aren't a bug; they are a historical log of deleted or failed records."

---

### Mini-Antipattern: Auto-Increment per Group
**"I need each customer to have their own Invoice # starting from 1."**

*   **The Trap**: Logic like `SELECT MAX(id) + 1 WHERE customer_id = X`.
*   **The Fail**: Concurrent inserts will try to grab the same number. One will crash.
*   **The Solution**: Use a global `invoice_id` as the primary key. Use `ROW_NUMBER()` with `PARTITION BY` to display the "Customer Sequence" in reports.
```sql
SELECT 
    customer_id,
    invoice_date,
    ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY invoice_date) AS customer_invoice_num
FROM Invoices;
```

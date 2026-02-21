# Chapter 23: See No Evil

> **"Why would I look at the return value? If my code didn't have bugs, there would be no errors. I shouldn't have to work around my own bugs."**

This is the **See No Evil** antipattern. It occurs when developers write "elegant" (short) code by ignoring error checks from database APIs or by attempting to debug complex SQL-building logic without ever looking at the final SQL string.

---

## 23.1 The Objective: Write Less Code
Programmers love conciseness. 
*   **The Intent**: Fewer lines of code mean faster development, less to document, and a "cleaner" look.
*   **The Problem**: We often sacrifice reliability for the sake of an "elegance ratio"—the ratio of cool functionality to volume of code.

---

## 23.2 The Antipattern: See No Evil
There are two primary ways this manifests:

### 1. Ignoring API Return Values
Developers often assume that because the database *should* be there, it *will* be there.
```python
# The "See No Evil" way
cnx = mysql.connector.connect(user='scottt', database='test') # Typo in username
cursor = cnx.cursor()
query = "SELCET bug_id FROM Bugs" # Typo in SQL
cursor.execute(query) # No error checking!

for row in cursor:
    print(row)
```
In this example:
*   The connection might fail (wrong password, server down).
*   The query might have a syntax error (`SELCET`).
*   The application might just display a **"White Screen of Death"** or a cryptic system error because the developer didn't handle the `ProgrammingError`.

### 2. Debugging Fragmented SQL
Developers spend hours staring at the code *building* the SQL instead of the SQL itself.
```python
# White Space Trap
query = "SELECT * FROM Bugs"
if bug_id > 0:
    query = query + "WHERE bug_id = %s" # Oops, no space before WHERE
```
**Resulting SQL**: `SELECT * FROM BugsWHERE bug_id = 1234`  
This is like trying to solve a jigsaw puzzle without looking at the photo on the box. You see `query = query + ...` but you don't see the broken syntax.

---

## 23.3 Why it Fails

### 1. Silent Failures & Blank Screens
Ignoring return values doesn't make errors go away; it just makes them harder to find. Users see a blank page, and you have no logs to tell you why.

### 2. Inefficient Debugging
Staring at 50 lines of Python/PHP logic that concatenates a query is exhausting. Printing the final string—or looking at the database's error log—shows the syntax error in 2 seconds.

### 3. Concurrency & Network Realities
Errors in database code aren't just about typos. They are about network timeouts, deadlocks, and disk space. These are **runtime realities**, not "bugs in the code" that can be coded away.

> **Takeaway**: **Code elegantly, but check for reality.** A shorter script that crashes silently is not better than a longer script that handles errors gracefully.

## 23.4 The Solution: Recover from Errors Gracefully
Dancing through a database integration requires the ability to recover gracefully from missteps.

### 1. Maintain the Rhythm (Check Everything)
Assume any line of code involving the database **will** fail. Check the return status or catch exceptions for every connection and execution.
```python
try:
    cnx = mysql.connector.connect(user='scott', database='test')
except mysql.connector.Error as err:
    # Log the real error for developers, show a friendly one to users
    log.error(f"Connection failed: {err}")
    return "Service temporarily unavailable"

try:
    cursor.execute(query, params)
except mysql.connector.Error as err:
    log.error(f"SQL Error: {err}")
    log.debug(f"Query was: {query}") # See the actual string!
```

### 2. Retrace Your Steps (Debug the SQL, not the Code)
If a query fails, the code that built it is irrelevant until you see the **final result**.
*   **The Rule**: Print or log the final SQL string.
*   **The Tip**: Build your query in a variable (`sql = "..."`) instead of inside the `execute()` call so you can inspect it.
*   **Security Note**: Never print SQL to the end-user (e.g., in HTML comments). It’s a roadmap for hackers. Log it to a file or a private console.

---

### Mini-Antipattern: Reading Syntax Error Messages
Database error messages are often cryptic, but they contain specific clues if you know where to look.

**1. The "Near" Clue**
MySQL tells you exactly where its brain stopped working.
*   **Error**: `...near 'date_reported' at line 1`
*   **Fix**: Look at the word **immediately before** `date_reported`. In `ORDER date_reported`, the word `BY` is missing.

**2. The "Double Quote" Mystery**
*   **Error**: `...near '' at line 1` (Two single quotes with nothing inside).
*   **Fix**: This means the error is at the **very end** of the query. Usually, this indicates an unclosed parenthesis: `WHERE (status = 'NEW'`. The parser reached the end of the file expecting a `)` but found nothing.

> **Final Thought**: A graceful recovery is the difference between a minor hiccup and a total application crash. Assume the network is hostile and the syntax is fragile—**Verify everything.**

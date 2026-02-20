# Chapter 21: SQL Injection

> **"Bobby Tables says hello."**

This is the **SQL Injection** antipattern. It occurs when developers concatenate untrusted user input directly into SQL query strings, allowing attackers to manipulate the query's structure and execute arbitrary commands.

---

## 21.1 The Objective: Write Dynamic SQL Queries
You need to build queries based on user input.
*   **The Intent**: "The user wants to search for a product named 'O'Hare', so I'll insert that name into my SQL."
*   **The Shortcut**: "I'll just string-concatenate the variable into the SQL."

---

## 21.2 The Antipattern: Execute Unverified Input As Code
You treat user input as safe and paste it directly into your executable SQL string.

### The Classic Example
```python
# UNSAFE!
user_input = "O'Hare"
sql = f"SELECT * FROM Products WHERE name = '{user_input}'"
# Result: SELECT * FROM Products WHERE name = 'O'Hare'
# Syntax Error! (The quote in O'Hare closed the string early)
```

## 21.3 Common Attack Variants
SQL Injection isn't just about deleting tables. It comes in many flavors.

### 1. The Terminator (Destructive)
The attacker adds a new command to destroy data.
*   **Input**: `x'; DROP TABLE Students; --`
*   **Result**: The application runs the search, then immediately deletes the table.
```sql
SELECT * FROM Students WHERE name = 'x';
DROP TABLE Students; --'
```

### 2. The Impostor (Authentication Bypass)
The attacker tricks the login query into always returning "True".
*   **Input**: `admin' OR '1'='1`
*   **Code**:
    ```python
    sql = f"SELECT * FROM Users WHERE user = '{user}' AND pass = '{pass}'"
    ```
*   **Resulting SQL**:
    ```sql
    SELECT * FROM Users WHERE user = 'admin' OR '1'='1' AND pass = '...'
    ```
    *   Since `'1'='1'` is always True, the database logs them in as the first user (usually Admin) without a password.

### 3. The Spy (Data Exfiltration)
The attacker joins your results with results from *another* table to read sensitive data.
*   **Input**: `' UNION SELECT username, password FROM Users --`
*   **Code**:
    ```python
    sql = f"SELECT title, description FROM Products WHERE id = '{id}'"
    ```
*   **Resulting SQL**:
    ```sql
    SELECT title, description FROM Products WHERE id = ''
    UNION SELECT username, password FROM Users --'
    ```
    *   **Effect**: The application displays "Product Title: admin" and "Description: [Hashed Password]" on the screen.

### 4. The Ghost (Blind SQL Injection)
If the application hides errors (good practice), attackers ask "True/False" questions using time.
*   **Input**: `' AND SLEEP(5) --` (MySQL) or `' AND WAITFOR DELAY '0:0:5' --` (SQL Server).
*   **Logic**: "If the first letter of the password is 'A', sleep for 5 seconds."
*   **Effect**: The attacker measures how long the page takes to load. If it hangs, they know they guessed the letter correctly. They can extract the entire database byte-by-byte this way.

### 21.4 The False Cures

Many developers try to invent their own solutions to SQL injection or rely on outdated methods.

**1. Escaping Strings**
The oldest "fix" is to manually escape quote characters (e.g., changing `'` to `''` or `\'`). Libraries often provide functions like `escape()` for this.
*   **Why it fails (Numeric Injection)**: Escaping quotes only works if the value is enclosed in quotes in your SQL. If you are injecting an integer, there are no quotes to escape.
    ```python
    user_input = "1 OR 1=1" # No quotes to escape!
    escaped_input = cnx.converter.escape(user_input) # Still "1 OR 1=1"
    
    # UNSAFE!
    sql = f"SELECT * FROM Users WHERE id = {escaped_input}"
    ```
    The query becomes `SELECT * FROM Users WHERE id = 1 OR 1=1`, bypassing the escape entirely.
*   **Why it fails (Encoding Bypasses)**: In obscure cases with multi-byte character sets (like GBK), an attacker can craft a byte sequence where the escaping function correctly adds a `\`, but the database interprets the preceding byte *plus* the `\` as a valid, non-quote character, leaving the attacker's trailing quote unescaped to break out of the string.
2.  **Stored Procedures**: If you just concatenate strings *inside* the stored procedure (`EXECUTE IMMEDIATE`), you are still vulnerable.
3.  **"My Framework handles it"**: Most frameworks *allow* raw SQL. If you use it, you are on your own.

### 21.5 The Solution: Query Parameters (Prepared Statements)
The only robust solution is to separate the *Code* from the *Data*.

**How it works**
1.  **Prepare**: You send the SQL template to the database with placeholders (`?` or `%s`). The DB parses, compiles, and optimizes the query plan. The structure is now locked.
2.  **Execute**: You send the values separately. The DB treats them strictly as literal values, never as executable SQL.

**Python (MySQL / Postgres)**
```python
# SAFE!
user_input = "O'Hare"
sql = "SELECT * FROM Products WHERE name = %s" # Placeholder

# The library sends the SQL and the Data separately
cursor.execute(sql, (user_input,)) 
```

**Java (JDBC)**
```java
// SAFE!
String query = "SELECT * FROM Users WHERE name = ?";
PreparedStatement pstmt = conn.prepareStatement(query);
pstmt.setString(1, userInput);
ResultSet results = pstmt.executeQuery();
```

**Why it works**
Even if the input is `x'; DROP TABLE Students; --`, the database treats it as searching for a user named literally `"x'; DROP TABLE Students; --"`. No commands are executed.

### 21.5.1 Parameterize Dynamic Values
A query parameter substitutes for a **single value** after the RDBMS has already parsed the SQL statement. No SQL injection attack can change the syntax of a parameterized query.

*   **Attack Attempt**: An attacker provides `123 OR TRUE` for a `userid` parameter.
*   **Resulting Logic**: 
    ```sql
    UPDATE Accounts SET password_hash = '...' 
    WHERE account_id = '123 OR TRUE'
    ```
*   **Safety**: The RDBMS interprets the parameter as the literal string `"123 OR TRUE"`. If `account_id` is a numeric column, the database may attempt to cast the string to 123 (ignoring trailing characters) or simply return no matches. The `OR TRUE` part is never executed as code.

### 21.6 Advanced Protection Strategy (Defense in Depth)
Prepared Statements solve 90% of cases. Here is the other 10%.

**1. Isolate User Input from Code (Whitelisting)**
Prepared Statements handle **Data Values**. For structural parts like Column Names, Table Names, or Keywords (e.g., `ASC`/`DESC`), you must isolate the user's input from the code entirely.

*   **The Technique**: Map user-friendly strings to hard-coded SQL identifiers.
```python
# Whitelists (Mapping user choices to safe SQL pieces)
sort_map = {"price": "price_usd", "date": "created_at"}
dir_map = {"up": "ASC", "down": "DESC"}

# Safe Lookups
u_sort = request.args.get("sort")
u_dir = request.args.get("dir")

safe_col = sort_map.get(u_sort, "id")       # Default to "id"
safe_dir = dir_map.get(u_dir, "ASC")        # Default to "ASC"

# SAFE: Only hard-coded strings ever reach the query
sql = f"SELECT * FROM Products ORDER BY {safe_col} {safe_dir}"
```
*   **Benefits**:
    *   **Decouples UI**: You can change DB column names without breaking external URLs.
    *   **Total Security**: An attacker sending `DROP TABLE` results in a safe default (e.g., `ORDER BY id`).

**2. Input Filtering**
Reject bad data *before* it touches the database.
*   **Casting**: If `id` must be an integer, use `int(user_input)`. It will crash on `1 OR 1=1`.
*   **Regex**: Ensure usernames only contain alphanumeric characters (`^\w+$`).

**3. Least Privilege**
Why does your Web App user have `DROP TABLE` permission?
*   Create a specific database user for the app.
*   Grant only `SELECT, INSERT, UPDATE, DELETE`.
*   **Result**: Even if an attacker injects `DROP TABLE`, the database rejects it.

**4. Parameterizing an IN() Predicate**
You cannot pass a comma-separated string (`"1,2,3"`) as a single parameter for an `IN()` clause. You need one placeholder per value.
*   **The Problem**: `WHERE id IN (%s)` with `input="1,2,3"` results in `WHERE id IN ('1,2,3')`, which checks for a single string, not three numbers.
*   **The Fix**: Build the placeholders dynamically.
    ```python
    ids = [1, 2, 3]
    placeholders = ",".join(["%s"] * len(ids)) # Result: "%s,%s,%s"
    sql = f"SELECT * FROM Bugs WHERE id IN ({placeholders})"
    cursor.execute(sql, ids)
    ```

**5. Quoting Dynamic Values (Edge Case)**
In very rare cases, Prepared Statements can cause the "Query Optimizer" to make poor decisions if data is highly skewed (e.g., 99% of rows are `active=true`). The DB doesn't know the value during "Prepare" phase, so it might use a generic (and slow) plan.
*   **The Fix**: If (and ONLY if) you prove a parameter is causing slowness via `EXPLAIN`, you might interpolate the value.
*   **Safety**: Use a mature, well-tested escaping function from your database driver. Never implement your own.

**6. Rule #31: Check the Back Seat (Second Order Injection)**
"Just because it's in the database doesn't mean it's safe."
*   **The Scenario**:
    1.  User registers as `O'Reilly`.
    2.  You use Prepared Statements to INSERT it. **Safe.** Data is stored as `O'Reilly`.
    3.  Later, an admin script runs:
        ```python
        user = db.get_user(1) # Returns "O'Reilly"
        # UNSAFE!
        sql = f"SELECT * FROM Logs WHERE user = '{user}'"
        ```
    4.  **Boom**. The single quote breaks the generic SQL script.
*   **The Lesson**: Treat data *from* the database as untrusted if it originated *from* a user.

## 21.7 Clean Code = Secure Code
The biggest myth is that security is "extra work." In reality, **Prepared Statements are easier to write.**

**The Messy Way (Literal Hell)**
```php
$sql = "INSERT INTO Accounts (id, name, email) VALUES ("
       .intval($id).", '"
       .mysqli_real_escape_string($conn, $name)."', '"
       .mysqli_real_escape_string($conn, $email)."')";
```
Can you see the missing comma or the extra quote? This is a nightmare to debug.

**The Clean Way (Professional)**
```php
$sql = "INSERT INTO Accounts (id, name, email) VALUES (?, ?, ?)";
$stmt->execute([$id, $name, $email]);
```
There are no quotes to track, no messy dots, and it is **100% immune to SQL Injection.**

### Mini-Antipattern: Query Parameters inside Quotes
**"I used `?` but it still doesn't work!"**

*   **The Mistake**: Putting placeholders in quotes.
    ```sql
    -- WRONG: Treated as a literal string '?'
    SELECT * FROM Bugs WHERE bug_id = '?'
    ```
    The database treats the `?` as a literal character, not a placeholder. It searches for a `bug_id` that equals the character `'?'`. Since `bug_id` is usually an integer, the database casts `'?'` to `0`, resulting in no matches.

*   **Why?**: If SQL allowed placeholders inside quotes, you could never search for an actual question mark (e.g., `WHERE summary = 'Why?'`).

*   **The `LIKE` Problem**:
    ```sql
    -- WRONG: The '?' is hidden inside a string literal
    SELECT * FROM Bugs WHERE summary LIKE '%?%'
    ```
*   **The Solution**:
    1.  **SQL Side**: Use concatenation.
        ```sql
        -- MySQL
        SELECT * FROM Bugs WHERE summary LIKE CONCAT('%', ?, '%')
        ```
    2.  **App Side (Best)**: Add wildcards to the string *before* passing it as a parameter.
        ```python
        # Python
        word = "crash"
        pattern = f"%{word}%"
        # Placeholder is a separate token, NO QUOTES
        cursor.execute("SELECT * FROM Bugs WHERE summary LIKE %s", [pattern])
        ```

> **Takeaway**: **Trust No One.** Not the user, not the database, not even yourself. Parameterize everything you can; Whitelist everything else.

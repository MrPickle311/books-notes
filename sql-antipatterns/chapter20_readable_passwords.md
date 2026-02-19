# Chapter 20: Readable Passwords

> **"I forgot my password. Can you email it to me?"**

This is the **Readable Passwords** antipattern. It occurs when developers store passwords in plain text or reversibly encrypted formats, allowing anyone with database access (or intercepted network traffic) to read user credentials.

---

## 20.1 The Objective: Recover or Reset Passwords
Users forget passwords constantly.
*   **The Intent**: "Create a system to help users get back into their accounts."
*   **The Shortcut**: "Store the password as is. If they forget, I'll just email it to them."

---

## 20.2 The Antipattern: Store Password in Plain Text
You store passwords as standard strings in your database.

### The Implementation
```sql
CREATE TABLE Accounts (
  account_id SERIAL PRIMARY KEY,
  username   VARCHAR(20),
  password   VARCHAR(20) -- PLAIN TEXT
);

-- Authentication
SELECT * FROM Accounts WHERE username = 'admin' AND password = 'password123';
```

### Why it fails (The Security Breach)
1.  **The Insider Threat**:
    *   Any DBA, Developer, or Support Staff with read access to the `Accounts` table can see everyone's password.
    *   **Social Engineering**: "Hi, I'm the CEO. I forgot my password. Read it to me." (See the "Pat Johnson" story).

2.  **The Data Leak**:
    *   If your database is dumped (SQL Injection, Backup theft), **every user account is compromised immediately**.
    *   Since users reuse passwords, their Banking, Email, and Social Media accounts are also compromised.

3.  **The Network Sniffing**:
    *   If you execute `SELECT * FROM Accounts`, the cleartext passwords travel over the wire. Anyone with `tcpdump` or `Wireshark` can harvest them.

4.  **The "Email Me My Password" Feature**:
    *   Sending a password via Email is essentially broadcasting it. Email is stored in plain text on multiple servers (Sender, Receiver, Intermediaries).
    *   If you can *read* the password to email it, your system is already broken.

> **Takeaway**: **If you can read a user's password, you are doing it wrong.** You should only be able to *verify* it.

### Legitimate Uses of the Antipattern
*   **3rd Party Credentials**: If your app logs into an SMTP server, you *must* store that password in a reversible format (Encrypted, not Hashed).
*   **Low Security Apps**: An internal "Office Lunch Order" app might not need military-grade security (but don't be lazy!).

## 20.3 The Solution: Store Salted Hashes
Hash it. Salt it. Never read it.

### 1. Understanding Hashing (One-Way Street)
A hash function turns data into a fixed-length string of gibberish. You cannot reverse it.
*   Input: `xyzzy` -> Hash: `184858a0...`

**Which Algorithm?**
*   **DO NOT USE**: MD5, SHA-1 (Broken). SHA-256 (Too fast, vulnerable to GPU attacks).
*   **USE**: **Argon2id** (Best), **Bcrypt** (Standard), or **PBKDF2**. These are "Slow Hashes" designed to resist brute force.

**How to use in Postgres (`pgcrypto`)**
Postgres has a built-in extension for this.
```sql
CREATE EXTENSION pgcrypto;

-- Insert (Auto-salts with Blowfish)
INSERT INTO Accounts (username, password_hash) 
VALUES ('bill', crypt('secret123', gen_salt('bf')));

-- Authenticate (Returns True/False)
SELECT (password_hash = crypt('secret123', password_hash)) AS is_valid
FROM Accounts WHERE username = 'bill';
```
*   **Note**: `crypt()` extracts the salt *from* the stored hash automatically to verify the input.

### 2. Adding Salt (Defeating Rainbow Tables)
A "Rainbow Table" is a precomputed list of trillions of common password hashes.
*   **The Vulnerability**: If two users have the password `password123`, they have the *same hash*. An attacker who cracks one cracks them all.
*   **The Defense (Salting)**: Add a unique, random string (the Salt) to every password before hashing.

**Step-by-Step Implementation (The "Manual" Way)**
If you aren't using a modern algo like Bcrypt (which handles salt automatically), you must do it manually.

**Step 1: The Table**
Add a column to store the random salt.
```sql
CREATE TABLE Accounts (
  account_id SERIAL PRIMARY KEY,
  password_hash CHAR(64) NOT NULL, -- SHA-256 output
  salt BINARY(16) NOT NULL         -- 16 bytes of random noise
);
```

**Step 2: The Insert (Application Logic)**
1.  Generate 16 random bytes: `0x1a2b...`
2.  Concatenate: `Hash("password123" + 0x1a2b...)` -> `8f43g...`
3.  Store **both** in the DB.
```sql
INSERT INTO Accounts (password_hash, salt) 
VALUES (SHA2(CONCAT('password123', ?), 256), ?);
```

**Step 3: Verification**
1.  Fetch the `salt` for the user.
2.  Hash the *input* with that *salt*.
3.  Compare with `password_hash`.
**Why it works (The Math)**
*   **Without Salt**: `Hash("secret")` always equals `5en...`.
*   **With Salt A**: `Hash("secret" + "SaltA")` = `a94...`
*   **With Salt B**: `Hash("secret" + "SaltB")` = `b72...`
Even though the password is the same, the output is totally different.

**How does the system know?**
You don't need to memorize the salt. You *store it* right next to the hash.
1.  **User Login**: "Hi, I'm Bill. My password is 'secret'."
2.  **System Lookup**: "Okay Bill. Let me get your row." -> Returns `{Hash: a94..., Salt: SaltA}`.
3.  **The Test**: The system effectively asks: "Does `Hash('secret' + 'SaltA')` equal `a94...`?"
4.  **Result**: Yes! Access Granted.

### 3. Hiding the Password from SQL
Calculate the hash *in the Application*, not the Database.
*   **Bad**: `INSERT ... SHA2('password', 256)` (Password is visible in SQL Logs).
*   **Better**": Frontend hashing 
*   **Good**: Application hashes it, sends `8f43g...` to DB. DB never sees the real password.

### 4. Reset, Don't Recover
Since you can't read the password, you can't email it.
*   **The Workflow**:
    1.  User clicks "Forgot Password".
    2.  App generates a unique **Token** (Guid/UUID) with an expiration (1 hour).
    3.  App emails a link: `example.com/reset?token=abc-123...`.
    4.  User clicks link -> App verifies token -> User sets *new* password.

### 5. Use Modern Algorithms
Basic SHA-256 is fast (too fast for GPUs to crack). Use slow algorithms designed for passwords:
*   **Argon2** (Winner of Password Hashing Competition).
*   **Bcrypt** (Standard for years).
*   **PBKDF2** (NIST recommended).

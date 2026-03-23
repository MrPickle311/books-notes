The isolation levels discussed so far (Read Committed, Snapshot Isolation) primarily protect *Read-Only* transactions from concurrent writers. But what happens when two transactions actively try to **write** to the exact same object concurrently? 

The most famous write-write conflict is the **Lost Update Problem**.

**What is a Lost Update?**
It occurs almost exclusively during a **Read-Modify-Write** cycle.
1.  Transaction A reads a counter (value: 42).
2.  Transaction B reads the same counter (value: 42).
3.  Transaction A adds 1 and writes back 43.
4.  Transaction B adds 1 (to its local snapshot of 42) and writes back 43.
*The Result:* The counter is 43 instead of 44. Transaction A's modification was completely "clobbered" (lost). 

This happens constantly when:
*   Incrementing account balances.
*   Updating a value deep inside a complex JSON document.
*   Two users editing the same Wiki page simultaneously.

#### Solutions to the Lost Update Problem
Because this is incredibly common, databases offer several mechanisms to completely eliminate it:

**1. Atomic Write Operations (Let the DB do the math)**
The best solution is to completely remove the Read-Modify-Write logic from your application code.
Many databases provide custom commands that natively perform the math inside the database atomically (by briefly applying an exclusive write-lock while the math executes).
*Example:* `UPDATE counters SET value = value + 1 WHERE key = 'foo';`
*(Warning: ORM frameworks like Django or Ruby on Rails often foolishly abstract this away, silently executing a dangerous Read-Modify-Write cycle behind the scenes instead of using the database's native atomic increment).*

**2. Explicit Locking (`FOR UPDATE`)**
If your application logic is too complex for a standard Atomic Increment (e.g., "Move this game piece, but only after mathematically verifying the move is legal against the rules engine"), your application can explicitly order the database to lock the row.
*Example (SQL):* `SELECT * FROM figures WHERE name = 'robot' FOR UPDATE;`
The `FOR UPDATE` clause places an ironclad lock on the row. If a second player attempts to grab the robot, they are forced to wait patiently until the first player finishes their entire complex Read-Modify-Write cycle.

**3. Automatically Detecting Lost Updates**
Instead of forcing developers to remember to type `FOR UPDATE` manually, some advanced databases allow transactions to execute perfectly in parallel. However, right before they Commit, the Database Transaction Manager rapidly checks the math. 
If it detects that a Lost Update is about to happen, it **Aborts** the offending transaction and forces the application to retry. 
*   *Advantage:* It is vastly less error-prone because developers don't have to write any special locking code; the database just catches the math errors automatically.
*   *Implementation:* PostgreSQL (`Repeatable Read`) and Oracle (`Serializable`) automatically detect and block lost updates. However, **MySQL InnoDB (`Repeatable Read`) does NOT**. (Many authors argue MySQL shouldn't even be allowed to claim it offers Snapshot Isolation because of this).

**4. Conditional Writes (Compare-And-Set)**
If your database doesn't offer true transactions, it might offer a conditional write built to mimic a hardware CPU's "compare-and-swap" (CAS) instruction. This forces an update to ONLY occur if the database row *still matches* the exact state you saw when you first read it.
*Example (SQL):* `UPDATE wiki_pages SET content = 'new' WHERE id = 123 AND content = 'old';`
If another user already changed the content to 'different', the `WHERE` clause mathematically fails, the update has zero effect, and the application must retry.
*(Sidebar: Sometimes, developers use an auto-incrementing `version` column instead of comparing the entire text content. This is commonly known as **Optimistic Locking**).*

#### Conflict Resolution and Replication
Everything discussed so far (Row Locks, Compare-and-Set, Atomic Increments) makes one giant assumption: **That there is only ONE authoritative copy of the data.** 

In Replicated databases (like Multi-Leader or Leaderless architectures), these solutions completely break down. 
Because multiple geographically separated nodes accept writes concurrently and blindly replicate them in the background later, there is no "Single Authority" to hold a lock or instantly verify a `Compare-and-Set`. 

How do Replicated databases handle write-write conflicts then?
Instead of preventing conflicts, they intentionally let them happen. They allow concurrent writes to create multiple conflicting versions of the exact same record simultaneously (called **Siblings**). 
It is then the responsibility of the application (or a special data structure) to merge those siblings after the fact.
*   **Commutative Math (Safe):** If the conflicting writes are mathematically Commutative (like incrementing a counter), they can be merged flawlessly later. E.g., Node A gets `+1`, Node B gets `+2`. When they finally sync over the network, the total is naturally `+3`. (This is the underlying principle behind CRDTs).
*   **Last Write Wins (Dangerous):** If the database simply relies on LWW (using timestamps to blindly pick a "winner" and aggressively delete the other), it is completely prone to Lost Updates. *Unfortunately, LWW is the default setting in many modern replicated databases.*
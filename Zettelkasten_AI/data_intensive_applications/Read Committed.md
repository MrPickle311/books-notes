The most basic, baseline level of isolation that most databases provide is **Read Committed**. 
It guarantees exactly two things:
1.  **No Dirty Reads:** When reading, you will only see committed data.
2.  **No Dirty Writes:** When writing, you will only overwrite committed data.

**What is a Dirty Read?**
Imagine Transaction A modifies a user's balance from $10 to $20, but the transaction hasn't officially *Committed* yet. Can Transaction B "peek" and see that uncommitted $20?
If yes, that is a **Dirty Read**. 

*Read Committed isolation explicitly forbids this.* Transaction B will continue to see $10 until Transaction A fully commits.
![Figure 8-4: No dirty reads: user 2 sees the new value for x only after user 1’s transaction has committed.](data_intensive_applications/figure-8-4.png)
*Why preventing Dirty Reads is vital:*
1.  If Transaction A was updating multiple rows (e.g., adding an email AND updating an unread counter), a Dirty Read means Transaction B might see a halfway state (the email, but an empty counter).
2.  **Cascading Aborts:** If Transaction B reads the $20, makes business decisions based on the $20, and then Transaction A suddenly *Aborts* (crashing back to $10), Transaction B is now hopelessly corrupted because it based its logic on data that mathematically never existed.

**What is a Dirty Write?**
Imagine Transaction A edits a user's shopping cart, adding an "Apple". Before it commits, Transaction B swoops in and maliciously edits the *same* cart, adding a "Banana". Which write wins?

If a database allows Transaction B to blindly overwrite an *uncommitted* value currently held by Transaction A, that is a **Dirty Write**.

*Read Committed isolation explicitly forbids this.* It prevents Dirty Writes by enforcing a Row Lock. Transaction B must sit patiently and wait for Transaction A to either Commit or Abort before it is allowed to touch the object.
![Figure 8-5: With dirty writes, conflicting writes from different transactions can be mixed up.](data_intensive_applications/figure-8-5.png)

*Why preventing Dirty Writes is vital:*
Without it, transactions that modify multiple identical rows interleave incorrectly. Imagine Alice and Bob both click "Buy Car" at the exact same millisecond. 
*   Alice updates the `listings` table.
*   Bob updates the `listings` table (overwriting Alice).
*   Alice updates the `invoices` table.
*   Bob updates the `invoices` table (overwriting Alice).
Without Read Committed locks, you end up with a mix: Bob wins the car listing, but Alice wins the invoice (paying for a car she didn't get). Read Committed safely prevents this by forcing whoever acts second to wait until the first buyer's transaction completely finishes.

#### Implementing Read Committed
How do databases (like Postgres, Oracle, and SQL Server) actually enforce this isolation level under the hood?

**Preventing Dirty Writes (Row-Level Locks)**
Databases almost universally prevent dirty writes by using **Row-Level Locks**. 
If Transaction A wants to modify a row, it must acquire the lock for that specific row. It holds that lock until it officially Commits or Aborts. If Transaction B wants to write to that exact same row, it physically cannot; it must wait in line for the lock to be released.

**Preventing Dirty Reads (Remembering the Old Value)**
You *could* use a read-lock to prevent dirty reads (forcing Transaction B to wait for the write-lock to release before it's allowed to *read* the data). 
However, **Read Locks perform terribly**. One massive, long-running write transaction would freeze every single read in the system, crippling the application's response time.

Instead, almost all modern databases prevent Dirty Reads by **Versioning** the data:
*   When Transaction A acquires the write-lock and modifies the row, the database *remembers* the old, officially committed value.
*   While Transaction A is still working, any other transaction that asks to read the row is simply handed the old value. 
*   The instant Transaction A commits, the database switches over and starts handing out the new value.

*(Sidebar: Some databases offer an ultra-weak isolation level called **Read Uncommitted**. This prevents Dirty Writes using locks, but completely allows Dirty Reads. This avoids storing two versions of a row, making it slightly faster at the cost of exposing users to partially-finished corrupted data).*
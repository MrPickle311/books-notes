---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
So far, protecting against Write-Write Conflicts focused on protecting *a single object* (e.g. locking a specific `counter` row). 
There is an incredibly subtle race condition where two transactions modify *different objects* simultaneously, leading to a catastrophic logic error collectively. This is called **Write Skew**.

**The Write Skew Anomaly (The On-Call Doctors):**
Imagine a hospital rule: "There must always be strictly >= 1 doctor on call." 
Aaliyah and Bryce are currently both on-call. They both get sick, and click "Request Leave" at the exact same millisecond. 
1.  Transaction A completes a `SELECT` count and sees 2 doctors on call. 
2.  Transaction B completes a `SELECT` count and sees 2 doctors on call.
3.  Because "2 > 1", both transactions calculate that it is perfectly legal to drop their respective doctor's status.
4.  Transaction A updates Aaliyah's row to `on_call = false`.
5.  Transaction B updates Bryce's row to `on_call = false`.
*The Result:* The hospital has zero doctors on call. The business logic constraint was completely mathematically circumvented. 
![Figure 8-8: Example of write skew causing an application bug.](data_intensive_applications/figure-8-8.png)

*Why previous defenses fail here:*
*   This is not a "Dirty Write" (they didn't update the same row).
*   This is not a "Lost Update" (no data was clobbered or written over).
*   Automatic Lost-Update Detection does nothing here because two completely different rows were modified.

#### Characterizing Write Skew
Write Skew is a generalization of the lost update problem. It occurs when two conflicting transactions:
1.  Read the same shared dataset (the total count of doctors).
2.  Make a decision based on that data (it's safe to take leave).
3.  Separately write data that fundamentally invalidates the original premise the decision was based on.

Because they touched *different* rows, weak isolation levels (like Snapshot Isolation) mathematically see no conflict and allow both to commit happily. 

#### Solutions to Write Skew
Preventing Write Skew is incredibly difficult:
1.  **Atomic Operations don't work**, because multiple separate rows are involved.
2.  **Database Constraints (e.g., Uniqueness, Foreign Keys) rarely help**, because creating a complex trigger-based constraint like "COUNT(doctors) where shift=123 must be >=1" is very difficult to build correctly in most SQL databases.
3.  **True Serializable Isolation:** Running transactions sequentially perfectly eliminates this, but at a huge performance cost.
4.  **Explicit Locking (The practical workaround):** If you can't use Serializable isolation, the second-best option is explicitly locking the rows the transaction mathematically relied upon:
    ```sql
    BEGIN TRANSACTION;
    -- Explicitly place a lock on ALL doctors currently working this shift
    SELECT * FROM doctors WHERE on_call = true AND shift_id = 1234 FOR UPDATE;
    
    UPDATE doctors SET on_call = false WHERE name = 'Aaliyah' AND shift_id = 1234;
    COMMIT;
    ```

#### More Examples of Write Skew
This sounds esoteric, but it is extremely prevalent in modern web apps:
1.  **Meeting Room Booking System:** 
    *User 1 and User 2 both try to book Room A at 12:00 PM.* 
    Transaction A and B both `SELECT` to check if a conflicting meeting exists. Both return zero rows. Both perfectly insert their own brand-new `booking` rows. (Room is double booked).
2.  **Claiming a Username:** 
    *User 1 and 2 try to register 'Alice' simultaneously.* 
    Transactions A and B both `SELECT` and see the username is free, and both attempt to create the account. (Fortunately, a simple `UNIQUE` constraint perfectly fixes this specific scenario). 
3.  **Preventing Double-Spending:** 
    *User has $10 and buys both Item A ($8) and Item B ($8) at the exact same millisecond.*
    Transaction A and B both SELECT the total balance, see $10, and legally insert their $8 purchases into the `purchases` ledger. The user's account drops to -$6, completely skipping the insufficient funds check.

#### Phantoms Causing Write Skew
In the Doctor example, the transaction successfully bypassed Write Skew by explicitly locking the rows using `SELECT FOR UPDATE` (Step 1). 
However, look at the Meeting Room booking example: 
*   Transaction A searches for overlapping bookings. It finds **zero** rows.
*   Because it found *nothing*, `SELECT FOR UPDATE` literally cannot attach a lock to anything.
*   Transaction B searches, also finds zero rows, and *inserts* a new overlapping meeting.

This effect—where a write in one transaction changes the result of a search query in another transaction—is called a **Phantom**.
Using Snapshot Isolation mathematically prevents Phantoms for *read-only* queries, but in *read-write* transactions, Phantoms are the primary driver of Write Skew anomalies.

#### Materializing Conflicts (The Last Resort)
If the core problem with Phantoms is that "there are no rows to attach a lock to," can we artificially create rows just so we can lock them? 

Yes. In the meeting room example, you could create a brand new table called `time_slots`. You proactively insert a row for every single 15-minute chunk of time for every room for the next 6 months.
Now, when a user wants to book Room A at 12:00 PM:
1.  The transaction runs `SELECT FOR UPDATE` on the generic `time_slots` table for that specific 15-minute slot. 
2.  Even though the meeting doesn't exist yet, it successfully places a physical lock on the *time slot itself*.
3.  Any other transaction trying to book the same room/time is forced to wait in line. 

This technique is called **Materializing Conflicts** (turning a conceptual invisible Phantom into a tangible row that can be physically locked).
*Warning:* Materializing conflicts is ugly, incredibly error-prone, and violently leaks database concurrency mechanisms into the application's clean data model. It should only be used as an absolute last resort if you cannot afford true Serializable Isolation.

---
## Related Concepts
* [[Data Intensive Applications]]

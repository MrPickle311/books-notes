In the harsh reality of distributed data systems, things constantly go wrong:
*   Databases or hardware can fail mid-write.
*   Applications can crash halfway through a complex operation.
*   Network interruptions can suddenly sever connections.
*   Multiple clients can concurrently write to the exact same data, overwriting each other.
*   Clients can read data that is only partially updated and makes no logical sense.

Handling all of these potential failures perfectly in application code is an impossible amount of work. To simplify this, databases offer **Transactions**.

### What Exactly is a Transaction?
A transaction is a way for an application to group several reads and writes together into a single, logical unit. 
Conceptually, all operations in a transaction are executed as *one single operation*:
1.  **Commit:** Everything succeeds perfectly.
2.  **Abort / Rollback:** If *anything* fails, the entire transaction is cancelled, and the database perfectly unwinds any partial writes that occurred. 

Because of this, the application never needs to worry about "partial failures" (where money was deducted from Account A, but the database crashed before adding it to Account B). If it fails, the application simply retries blindly, knowing the database kept things safe. 

*(Note: Transactions are not laws of physics. They are simply an artificial programming model built by database engineers to provide "safety guarantees" so application developers don't have to code around hardware failures).*
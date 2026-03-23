The biggest challenge is ensuring true transactional behavior. Failures often lead to data corruption.

#### 1. Lack of an Explicit Database Transaction
Executing multiple database updates without wrapping them in a transaction is a common mistake.

*   **Problem:** If the second `DB.Execute` call fails, the `Users` table is updated, but the `VisitsLog` is not, leading to an inconsistent state.
```csharp
// BAD: Not transactional
public void Execute(Guid userId, DataTime visitedOn)
{
  DB.Execute(“UPDATE Users SET last_visit=@prm1...”, ...);
  DB.Execute(“INSERT INTO VisitsLog...”, ...);
}
```
*   **Solution:** Wrap the database calls in a `try/catch` block with an explicit transaction and rollback.
```csharp
// GOOD: Transactional
public void Execute(Guid userId, DataTime visitedOn)
{
  try
  {
    DB.StartTransaction();
    DB.Execute(“UPDATE Users...”);
    DB.Execute(“INSERT INTO VisitsLog...”);
    DB.Commit();
  } catch {
    DB.Rollback();
    throw;
  }
}
```

#### 2. Implicit Distributed Transactions
A transaction isn't just about the database; it includes every part of an operation, like publishing to a message bus or even communicating success back to the caller.

*   **Problem 1: DB + Message Bus:** If the message bus publish fails after the database commit, the system is inconsistent. Standard distributed transactions are often avoided due to complexity. (More advanced patterns like Outbox will be covered later).

![Figure 5-2: A diagram showing an operation that involves both updating data and notifying a caller, forming a distributed transaction.](figure-5-2.png)

*   **Problem 2: The "Successful" Call That The Caller Never Hears About:** If a call to increment a counter succeeds but the network fails before the "OK" response reaches the client, the client will likely retry the operation, incrementing the counter a second time and corrupting the data.

*   **Solution (Idempotency):** Design the operation so it can be repeated multiple times with the same result. For example, instead of `visits = visits + 1`, the operation could be `UPDATE Users SET visits = @newValue`. The caller provides the final value, so retries don't change the outcome.

---

### When to Use Transaction Script

*   **Best for:** Supporting subdomains where logic is simple and procedural.
*   **Good for:** ETL (Extract, Transform, Load) operations and as adapters for integrating external systems (like in an Anticorruption Layer).
*   **Advantage:** Simplicity. It has minimal overhead and is easy to understand.
*   **Disadvantage:** Prone to code duplication as logic gets more complex. **Should not be used for core subdomains.**

![Figure 5-3: A diagram of an Extract-Transform-Load (ETL) data flow, a good use case for Transaction Script.](figure-5-3.png)
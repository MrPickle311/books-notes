---
aliases:
tags:
  - domaindrivendesign
source_book: "Domain-Driven Design"
status: pending
---
*   **Entities:** Objects that require explicit IDs and can change over time
*   **Entities are NOT used independently** - only as part of aggregates
*   **Aggregates:** Collections of entities that share transactional consistency

![Figure 6-2: A diagram showing how an explicit ID field allows differentiating between instances even with identical other values.](figure-6-2.png)

#### Commands and State Modification
External processes can only modify an aggregate through its public command interface:

```csharp
public class Ticket  // Aggregate Root
{
    public void Execute(EscalateTicket cmd)
    {
        // Validate business rules
        if (!this.IsEscalated && this.RemainingTimePercentage <= 0)
        {
            this.IsEscalated = true;
            var escalatedEvent = new TicketEscalated(this.Id);
            this.domainEvents.Append(escalatedEvent);
        }
    }
}
```

#### Concurrency Management
Aggregates must implement optimistic concurrency control:

```csharp
class Ticket
{
    TicketId Id;
    int Version;  // Critical for concurrency management
    // ... other fields
}
```

SQL example for atomic updates:
```sql
UPDATE tickets
SET ticket_status = @new_status,
    agg_version = agg_version + 1
WHERE ticket_id = @id AND agg_version = @expected_version;
```

#### Aggregate Boundaries: Keep Them Small
*   **One aggregate per transaction:** You cannot modify multiple aggregates in a single database transaction
*   **Reference by ID:** Other aggregates should be referenced by their IDs, not as direct object references
*   **Strong consistency only:** Only include data that MUST be strongly consistent

![Figure 6-4: A diagram showing aggregates as consistency boundaries, with external references by ID.](figure-6-4.png)

Example of proper boundary design:
```csharp
public class Ticket
{
    // These belong to the aggregate (need strong consistency)
    private List<Message> messages;
    
    // These are external references (eventual consistency is OK)
    private UserId customer;
    private UserId assignedAgent;
    private List<ProductId> products;
}
```
---
## Related Concepts
* [[Domain Driven Design]]

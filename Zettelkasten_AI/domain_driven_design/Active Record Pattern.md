---
aliases:
tags:
  - domaindrivendesign
source_book: "Domain-Driven Design"
status: pending
---
This pattern is an evolution of Transaction Script, designed for simple business logic that operates on more complex data structures or object hierarchies.

*   **Definition:** "An object that wraps a row in a database table or view, encapsulates the database access, and adds domain logic on that data." - Martin Fowler
*   **Core Principle:** The data structure itself becomes an "active" object responsible for its own persistence (Create, Read, Update, Delete operations). It is tightly coupled to a data access framework like an ORM.

![Figure 5-4: A diagram of a complex data model with relationships, which is hard to manage with simple transaction scripts.](figure-5-4.png)

*   **Implementation:** The main business logic is still a transaction script, but instead of writing direct SQL, it manipulates Active Record objects.

```csharp
public class CreateUser 
{
 public void Execute(UserDetails details)
 {
  try 
  {
    DB.StartTransaction();
    var user = new User(); // 'user' is an Active Record object
    user.Name = details.Name;
    user.Email = details.Email;
    user.Save(); // The object saves itself
    DB.Commit();
  } catch {
    DB.Rollback();
    throw;
  }
 }
}
```

---

### When to Use Active Record

*   **Best for:** Supporting subdomains where the main complexity is mapping complex data structures to a database, not complex business rules.
*   **Good for:** Simple CRUD-heavy applications.
*   **Warning (The "Anemic Domain Model" critique):** This pattern is often called an anti-pattern because the business logic lives outside the data objects. While this is true, it's a perfectly valid and useful tool for simple domains. Using a more complex pattern here would be over-engineering.

---
## Related Concepts
* [[Domain Driven Design]]

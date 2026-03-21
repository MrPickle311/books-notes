---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
Use custom annotations (Java) or attributes (C#) to programmatically document which services participate in which sagas. This provides a simple way to query the codebase to understand the scope and impact of a given transactional workflow.

```java
// Example 12-1: Defining a transactional saga annotation in Java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Saga {
   public Transaction[] value();

   public enum Transaction {
      NEW_TICKET,
      CANCEL_TICKET,
      // ...
   }
}
```

```java
// Example 12-3: Applying the saga annotation to a service
@ServiceEntrypoint
@Saga({Transaction.NEW_TICKET, Transaction.CANCEL_TICKET})
public class TicketServiceAPI {
   // ...
}
```
A simple command-line tool can then be built to query this metadata:
`$ ./sagatool.sh NEW_TICKET -services`
`-> Ticket Service`
`-> Assignment Service`
`...`

---
## Related Concepts
* [[Architecture]]

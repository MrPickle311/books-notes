---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

### 1. Code Replication

This technique involves copying shared code into each service's source code repository, avoiding any formal sharing mechanism.

![Figure 8-2: A diagram showing three services, each containing an identical, copied block of code labeled "Shared Functionality."](figure-8-2.png)

While this approach perfectly preserves the bounded context of each service, it is highly risky. A bug fix or functional change requires manually finding and updating every copy of the code, which is error-prone and time-consuming.

#### Example: A Good Candidate for Replication

Simple, static marker annotations or attributes that contain no logic are good candidates for replication. They are unlikely to ever change or contain bugs.

```java
// Example 8-1: Source Code defining a service entry point annotation (Java)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ServiceEntrypoint {}

/* Usage:
@ServiceEntrypoint
public class PaymentServiceAPI {
   ...
}
*/
```

```csharp
// Example 8-2: Source Code defining a service entry point attribute (C#)
[AttributeUsage(AttributeTargets.Class)]
class ServiceEntrypoint : Attribute {}

/* Usage:
[ServiceEntrypoint]
class PaymentServiceAPI {
   ...
}
*/
```

#### Trade-offs for Code Replication

| Advantages                       | Disadvantages                             |
| -------------------------------- | ----------------------------------------- |
| Preserves the bounded context    | Difficult to apply code changes           |
| No code sharing                  | Code inconsistency across services        |
| No versioning complexities       | No versioning capabilities across services |

*   **When to Use:** Use with extreme caution for simple, truly static code (like marker annotations or one-off utilities) that is highly unlikely to ever change.
---
## Related Concepts
* [[Architecture]]

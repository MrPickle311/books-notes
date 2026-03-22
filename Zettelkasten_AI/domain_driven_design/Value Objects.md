---
aliases:
tags:
  - domaindrivendesign
source_book: "Domain-Driven Design"
status: pending
---
Value objects are objects identified by the composition of their values, not by an explicit ID.

#### Core Characteristics
*   **Identity:** Defined by the combination of their field values
*   **Immutability:** Changes create new instances, never modify existing ones
*   **Equality:** Two value objects with the same values are equal
*   **No ID needed:** An ID field would be redundant and create potential for bugs

![Figure 6-1: A diagram showing how a ColorId field is redundant and can lead to bugs with duplicate color values.](figure-6-1.png)

#### Fighting Primitive Obsession
Using primitive types (strings, ints) to represent domain concepts is a code smell called "primitive obsession."

**Bad Example (Primitive Obsession):**
```csharp
class Person
{
  private string firstName;
  private string lastName;
  private string landlinePhone;  // Just a string
  private string email;          // Just a string
  private string countryCode;    // Just a string
}
```

**Good Example (Value Objects):**
```csharp
class Person
{
  private PersonId id;
  private Name name;
  private PhoneNumber landline;   // Rich object with behavior
  private EmailAddress email;     // Rich object with behavior  
  private CountryCode country;    // Rich object with behavior
}
```

#### Rich Behavior Examples
Value objects shine when they encapsulate business logic:

```csharp
var heightMetric = Height.Metric(180);
var heightImperial = Height.Imperial(5, 3);
var firstIsHigher = heightMetric > heightImperial;  // true

var phone = PhoneNumber.Parse("+359877123503");
var country = phone.Country;                        // "BG"
var phoneType = phone.PhoneType;                    // "MOBILE"

var red = Color.FromRGB(255, 0, 0);
var green = Color.Green;
var yellow = red.MixWith(green);                    // New Color instance
```

#### Implementation Guidelines
*   **Immutability:** All operations return new instances
*   **Override Equality:** Implement proper value-based equality checks
*   **Rich API:** Encapsulate all logic that manipulates the values
*   **Thread Safety:** Immutability makes them inherently thread-safe

---
## Related Concepts
* [[Domain Driven Design]]

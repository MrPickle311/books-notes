---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
### The Nature of Contracts

Contracts are a cross-cutting concern that affects all dimensions of dynamic coupling (communication, consistency, and coordination).

![Figure 13-1: A 3D diagram showing that contracts are an orthogonal force affecting the entire space of dynamic coupling.](figure-13-1.png)

> **Contract Definition:** The format used by parts of an architecture to convey information or dependencies. This includes everything from API calls to library dependencies.

---

### Strict versus Loose Contracts

Contracts exist on a spectrum. The architect's job is to choose the appropriate level of strictness for a given interaction.

![Figure 13-2: A spectrum showing contract types ranging from strict (like RMI) to loose (like Name/Value pairs).](figure-13-2.png)

#### Strict Contracts
These require exact adherence to names, types, ordering, and other details, leaving no ambiguity. Examples include RPC frameworks like gRPC and schema-validated JSON.

```json
// Example 13-1: A strict JSON contract using a schema
{
    "$schema": "http://json-schema.org/draft-04/schema#",
    "properties": {
      "acct": {"type": "number"},
      "cusip": {"type": "string"},
      "shares": {"type": "number", "minimum": 100}
   },
    "required": ["acct", "cusip", "shares"]
}
```

| Advantages                     | Disadvantages    |
| ------------------------------ | ---------------- |
| Guaranteed contract fidelity   | Tight coupling   |
| Versioned                      | Versioned (can be complex) |
| Easier to verify at build time |                  |
| Better documentation           |                  |

#### Loose Contracts
These have little to no schema information, often using simple name/value pairs. Examples include basic JSON or YAML.

```json
// Example 13-4: A loose contract using simple name/value pairs in JSON
{
  "name": "Mark",
  "status": "active",
  "joined": "2003"
}
```

Loose contracts are critical for keeping services decoupled, especially when they have different internal data models (bounded contexts).

![Figure 13-4: An illustration of two microservices with different internal models of a "Customer" communicating via a loose name/value pair contract.](figure-13-4.png)

| Advantages         | Disadvantages            |
| ------------------ | ------------------------ |
| Highly decoupled   | Contract management      |
| Easier to evolve   | Requires fitness functions |

---
## Related Concepts
* [[Architecture]]

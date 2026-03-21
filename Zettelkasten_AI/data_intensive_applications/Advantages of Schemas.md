---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 2: Internals"
status: pending
---
While textual formats like JSON and XML are widespread, binary encodings based on simple schemas (like Avro and Protobuf) are incredibly viable and offer massive advantages:
1.  **Compactness:** They omit field names from the encoded bytes, saving significant space over binary JSON variants (like MessagePack).
2.  **Guaranteed Documentation:** The schema serves as a valuable form of documentation. Because the schema is physically *required* to decode the data, you can be 100% sure the documentation is up-to-date and hasn't drifted from reality.
3.  **Compatibility Validation:** Maintaining a schema registry allows your CI/CD pipelines to automatically check forward and backward compatibility *before* you deploy an application change.
4.  **Type Checking:** For statically typed languages, generating domain classes directly from the schema enables compile-time type-checking.

In short, schema evolution gives you the exact same flexibility as schemaless/JSON document databases, but with strictly better guarantees, smaller storage footprints, and better tooling.
---
## Related Concepts
* [[Data Intensive Applications]]

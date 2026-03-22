---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 2: Internals"
status: pending
---
**Apache Avro** is another binary format, distinctly different from Protobuf. It was born out of the Hadoop ecosystem because Protobuf wasn't a good fit. 
*   **Schema Specs:** It uses two flavors of schema: one for human editing (Avro IDL) and one machine-readable (JSON-based). Like Protobuf, it only specifies fields/types, no complex validation rules.
*   **The Difference (No Field Tags):** Unlike Protobuf, Avro's schema **does not use field tags/numbers**. 
*   **Tiny Size:** Compresses the example record into just **32 bytes** (the most compact format). 

![Figure 5-4: Example record encoded using Avro.](data_intensive_applications/figure-5-4.png)

**How it works (and why it's so small):**
Because there are no tags or field identifiers, the encoded Avro binary is literally just the raw concatenated values in a row. It doesn't even tell you the datatype. A string is just a length prefix followed by UTF-8 bytes; it could just as easily be an integer as far as the raw binary is concerned.

To decode it, **you must read through the fields in the exact order they appear in your schema**. This means the binary data can *only* be decoded if the code reading it handles the exact same schema structure as the code that wrote it.

#### The Writer's Schema vs The Reader's Schema
Since Avro binaries lack tags and types, how does it handle schema evolution if the structures must match? It uses two schemas simultaneously during decoding:
1.  **The Writer's Schema:** The exact schema that the authoring application used to encode the byte sequence.
2.  **The Reader's Schema:** The schema the receiving application is expecting to process.

If they are different, Avro performs **Schema Resolution** by looking at them side by side. It matches up fields *by field name*. 
*   If the reader expects a field the writer didn't include, the reader fills it with a **default value** defined in the reader's schema.
*   If the writer includes a field the reader wasn't expecting, the reader simply ignores it.
*   Field order doesn't matter, as long as the names match.

![Figure 5-5: Encoding and decoding can use different versions of a schema in Avro and Protobuf.](data_intensive_applications/figure-5-5.png)
![Figure 5-6: An Avro reader resolves differences between the writer's schema and the reader's schema.](data_intensive_applications/figure-5-6.png)

#### Avro Schema Evolution Rules
Because resolution relies on filling in blanks with default values, the rule for Avro schema evolution is strict:
*   **To maintain compatibility, you may ONLY add or remove a field that has a default value.**
*   *Forward Compatibility:* If you remove a field that has no default, old readers won't be able to read data written by new writers.
*   *Backward Compatibility:* If you add a field that has no default, new readers won't be able to read data written by old writers.

*Note on Nulls:* Unlike some languages, Avro fields aren't inherently nullable. To allow `null`, you use a union type (e.g., `union { null, long, string } field;`). `null` must be the first branch to be used as a default value. This verbosity prevents accidental null-reference bugs. Changing the datatype of a field is possible, provided that Avro can convert the type. Changing the name
of a field is possible but a little tricky: the reader’s schema can contain aliases for field names, so it can
match an old writer’s schema field names against the aliases. This means that changing a field name is
backward compatible but not forward compatible. Similarly, adding a branch to a union type is back-
ward compatible but not forward compatible.

#### Where does the Writer's Schema come from?
Since Avro records are so small, attaching the full schema to every single record would defeat the purpose. So how does the reader get the Writer's Schema?
1.  **Large Files (Hadoop/Object Storage):** Millions of records are packed into one file. The writer's schema is simply included once at the very top of the file (Avro Object Container Files).
2.  **Database Records:** Different records might be written at different times with different schema versions. Systems prefix the binary record with a tiny **version number**. The system maintains a separate "schema registry" database mapping version numbers to schemas. The reader fetches the registry schema based on the version number. (e.g., Confluent Schema Registry for Kafka).
3.  **Network/RPC Connections:** Two processes connecting over a network negotiate the schema version during the connection handshake and use it for the lifetime of that connection.

#### Dynamically Generated Schemas (Avro's Superpower)
You might wonder why Avro's omission of tag numbers is considered an advantage over Protobuf's tags. The answer is that **Avro is vastly superior for dynamically generated schemas.**

Imagine writing a script to dump an entire relational database into a binary file:
*   **With Avro:** You can write a script that looks at the database, generate an Avro JSON schema on the fly (where DB columns = Avro fields), and encode the data automatically. If the DB schema changes tomorrow (a column is dropped), the script runs again, generates a totally new Avro schema on the fly, and exports the data. No human intervention needed. Existing Readers simply map the new writer's schema fields to their expected fields by name.
*   **With Protobuf:** Because field tags are strictly mapped to fields and immutable, you would likely need an administrator to manually assign and track "Database Column X = Protobuf Tag #3" every time the database changed to ensure a tag was never accidentally recycled or mismatched. Dynamically generating schemas simply wasn't a design goal for Protobuf, whereas it was a core goal for Avro.
---
## Related Concepts
* [[Data Intensive Applications]]

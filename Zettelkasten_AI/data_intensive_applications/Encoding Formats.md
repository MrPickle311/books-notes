Programs interact with data in two main representations:
1.  **In-Memory:** Objects, structs, lists, arrays, hash tables. Optimized for CPU access via pointers.
2.  **Network/Disk (Byte Sequence):** Self-contained sequence of bytes (e.g., a JSON document) that any process can understand without relying on memory pointers.

The translation from the in-memory representation to a byte sequence is called **Encoding** (also Serialization or Marshalling). The reverse is **Decoding** (Parsing, Deserialization, Unmarshalling).
*(Note: "Serialization" is a loaded term that also refers to transaction isolation, so "Encoding" is preferred here to avoid confusion).*

#### Language-Specific Formats
Many languages feature built-in encoding (e.g., Java's `java.io.Serializable`, Python's `pickle`, Ruby's `Marshal`). While convenient for quick saves, they suffer from deep flaws:
*   **Language lock-in:** Data is tied strictly to one language, making integration with other systems nearly impossible.
*   **Security risks:** Decoding arbitrary byte sequences requires instantiating arbitrary classes, which attackers exploit to execute arbitrary code.
*   **Versioning neglected:** Forward and backward compatibility are usually an afterthought.
*   **Inefficiency:** E.g., Java's built-in serialization is notorious for bloated encoding size and poor performance.
*   *Conclusion:* Never use language-built-in encoding for anything other than transient, temporary purposes.

#### Textual Formats: JSON, XML, and CSV
When seeking standardized encodings readable by many languages, JSON, XML, and CSV are the primary textual choices. While incredibly widely used (especially as data interchange formats between organizations), they have subtle problems:

*   **Number Parsing Ambiguity:** 
    *   XML/CSV cannot distinguish between a number and a string composed of digits without an external schema.
    *   JSON distinguishes strings vs. numbers, but fails to distinguish integers vs. floating-point numbers or specify precision.
    *   *Real-World Issue:* Integers larger than 2^53 aren't exact in standard IEEE 754 floats. Twitter has to send 64-bit post IDs as both integer *and* decimal-string formats in JSON so languages like JavaScript can parse them correctly avoiding precision loss.
*   **No Native Binary Strings:** JSON/XML don't support raw binary byte sequences. Developers work around this by encoding binary data as text via Base64, inflating file size by 33%.
*   **Schema Complexity:** True JSON/XML Schemas exist but are quite complex to learn/implement, so many apps hardcode encoding logic instead.
*   **CSV Ambiguity:** CSV lacks any schema entirely. Dealing with new columns requires manual app updates, and edge cases (like commas inside values) are often handled poorly by parsers.

Despite these flaws, getting different organizations to agree on a format is harder than dealing with the flaws of JSON or XML, ensuring their continued dominance for data interchange.

#### JSON Schema
**JSON Schema** is widely adopted to model data exchanged between systems or written to storage (found in OpenAPI, Schema Registries, and DB validators like `pg_jsonschema`).
*   **Validation:** Schema includes standard primitives (strings, numbers, booleans) and allows developers to overlay constraints (e.g., `port` between 1 and 65535).
*   **Content Models:**
    *   *Open Content Model (Default):* Permits any field not explicitly defined in the schema to exist with any data type (`additionalProperties: true`). This means JSON schemas typically define *what isn't permitted* rather than what *is* permitted.
    *   *Closed Content Model:* Only allows explicitly defined fields.
*   **Complexity:** Features like open/closed models, conditional if/else logic, and remote references make JSON Schema powerful but unwieldy. Reasoning about schemas and evolving them forward/backward compatibly is notoriously challenging. Example: defining a map from integer IDs to strings requires convoluted syntax since JSON objects only support string keys.

#### Binary Encoding
While JSON is less verbose than XML, textual formats use a lot of space. This led to binary encodings for JSON (e.g., MessagePack, CBOR, BSON) and XML (e.g., WBXML).
*   **How they work:** Some extend the datatypes (distinguishing floats vs integers, or adding native binary arrays), but otherwise maintain the exact JSON/XML data model.
*   **The Flaw (No Schema):** Because they don't prescribe a strict schema, these binary formats still have to include all the object **field names** within the encoded data itself. For example, the literal string "userName" must be embedded in every single binary encoded record.
*   **Verdict:** MessagePack and similar JSON binary encodings generally only save a trivial amount of space compared to the raw text (e.g. 66 bytes binary vs 81 bytes minified text JSON). It's debatable whether this small space reduction is worth losing human-readability.
*   *Note:* The text notes that there are better formats that can compress this same record into just 32 bytes (covered in the next sections).

![Figure 5-2: Example record encoded using MessagePack.](data_intensive_applications/figure-5-2.png)
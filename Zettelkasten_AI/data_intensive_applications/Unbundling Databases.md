---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
At their core, Databases, Stream Processors, and Unix Operating Systems all do the exact same thing: they are "Information Management Systems" that store data and let you query it. 

Historically, Unix and SQL took two completely opposing philosophies to this problem:
*   **The Unix Way:** Provide bare-metal, low-level abstractions (files and pipes). Expose the raw mechanics to the engineer and let them compose small, sharp tools together.
*   **The Relational DB Way:** Hide the entire frightening reality of the hardware. Provide an incredibly high-level, declarative abstraction (`SQL`) so the programmer doesn't have to think about B-Trees, disk concurrency, or crash recovery.

For decades, these two philosophies warred (the NoSQL movement was essentially an attempt to bring Unix-style low-level abstractions to distributed storage). Modern data architectures are finally attempting to reconcile and combine both worlds.

### Composing Data Storage Technologies
When you run `CREATE INDEX` in PostgreSQL, the database scans the table, sorts the data, writes it to disk, and then permanently subscribes to a stream of new writes to keep the index continuously synchronized. 
Notice how this built-in database feature is physically identical to configuring a Kafka CDC stream to derive an external Elasticsearch index!

### The Meta-Database of Everything
When you zoom out, an entire modern enterprise architecture is literally just **One Giant Distributed Database**.
*   Kafka is the Write-Ahead Log.
*   Flink is the internal trigger/stored-procedure engine.
*   Elasticsearch, Redis, and Snowflake are nothing more than specialized secondary indexes maintained by the log!

Instead of buying a single massive "Oracle" monolith that tightly couples all these features under one roof, modern organizations are building the exact same capabilities by composing bespoke, best-in-class software together.

If no single database can do everything, how do we stitch these disparate tools together into a cohesive system? There are two avenues:

#### 1. Federated Databases (Unifying Reads)
If you have data in Postgres, Mongo, and Kafka, you can place a **Federated Query Engine** (like Trino or Presto) over all of them. 
This engine acts like a massive SQL router. The user writes a single SQL query, and the Federated Engine breaks it apart, translates it, sends the pieces to Postgres and Mongo, and merges the results. 
This perfectly follows the SQL Philosophy: it gives the user one beautiful, high-level querying abstraction, hiding the terrifying complexity underneath. However, federation only solves *reading* data; it does absolutely nothing to help you *write* data across systems safely.

#### 2. Unbundled Databases (Unifying Writes)
The alternative is embracing the Unix Philosophy. 
Instead of trying to hide the complexity under a magic federated query engine, you expose the raw event streams using Change Data Capture. You meticulously compose the architecture by having small, sharp components (Stream Processors) "pipe" the data from the source of truth into the various distinct databases. 
This "Unbundled" approach guarantees that all writes confidently sync across the company's entire infrastructure, perfectly preserving fault tolerance.

### Making Unbundling Work
Synchronizing writes across heterogeneous storage systems via distributed transactions (XA protocol) is a notorious infrastructure nightmare because there is no standardized protocol between vastly different databases.
The "Unbundled" solution—an ordered asynchronous event log with idempotent consumers—is a far superior abstraction for cross-system integration.

This log-based **loose coupling** provides two massive advantages:
1. **System-Level Robustness:** If Elasticsearch crashes, the synchronous distributed transaction approach would force Postgres to abort its writes too! With an asynchronous log, the Kafka broker simply buffers the messages. Postgres keeps writing flawlessly, and Elasticsearch just catches up hours later when it comes back online. The fault is perfectly contained locally.
2. **Human-Level Independence:** Unbundling allows different software teams to work entirely independently. The Database team only cares about emitting the CDC log. The Search team only cares about pulling from that log. The strict, durable event stream acts as the perfect, decoupled API boundary between teams.

### Unbundled vs. Integrated Systems
Does the Unix-philosophy of "Unbundled Databases" mean you should never use a single monolithic database again? Absolutely not.
If a single technology (like PostgreSQL) perfectly satisfies all your requirements, **you should just use it.** 

Stitching together Kafka, Flink, Postgres, and Elasticsearch carries immense operational cost. You have to deploy, patch, and monitor four different distributed systems with four different learning curves. Building a massively scalable unbundled architecture when you only have a gigabyte of data is classic *Premature Optimization*. A single integrated Postgres database will give you vastly better and more predictable performance on a single machine than an unbundled rig.

The goal of Unbundling is not to compete with individual databases on pure performance. The goal is entirely about **breadth**. 
When your application requirements grow so diverse that *no single database on Earth can physically handle all your workloads*, unbundling provides the only robust architectural framework to safely stitch specialized tools together.

---
## Related Concepts
* [[Data Intensive Applications]]

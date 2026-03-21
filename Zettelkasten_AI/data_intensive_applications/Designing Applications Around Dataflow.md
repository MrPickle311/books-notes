---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
The concept of automatically updating derived views when the underlying data changes is a concept we've already had since 1979: **Spreadsheets** (like VisiCalc or Excel). 
In Excel, you type a formula in cell `C1` (`=A1+B1`). When you mutate `A1`, `C1` automatically and instantaneously recalculates. You don't have to manually tell `C1` to update, and you don't care *how* it happens—you just trust the reactive dataflow.

Modern stream processors are essentially trying to build a fault-tolerant, planetary-scale version of Excel. When a user updates their profile in the database, the search index and frontend UI cache should react and correctly recalculate on their own.

### Application Code as a Derivation Function
The logic that connects a source dataset to a derived dataset is the **Derivation Function**.
*   **Simple Functions:** For a standard exact-match B-Tree index, the function is just "pluck the column and sort it." This is so mundane that databases natively provide it via the `CREATE INDEX` command.
*   **Complex Functions:** For a Machine Learning model or a recommendation engine, the derivation function requires immense statistical analysis, specific feature extraction, and unique business logic. 

Relational databases have historically attempted to support custom derivation functions using **Stored Procedures and Triggers**. However, embedding complex business logic *inside* the database has proven to be an architectural nightmare. Relational databases are notoriously bad environments for arbitrary code execution (lacking dependency management, version control, modern monitoring, and network call capabilities).

### Separation of Application Code and State
Instead of putting application code inside the database (via Triggers/Stored Procedures), modern engineering relies on a strict separation:
> *"We believe in the separation of Church and state."* 
> — Functional Programming Joke (referencing Alonzo Church, creator of the immutable boolean Lambda calculus).

**The Modern Architecture:**
*   **State Management:** Handled purely by the Databases/Brokers (Kafka, Postgres). Their only job is durability, concurrency, and fault tolerance.
*   **Application Code:** Handled purely by Stateless Services deployed on Kubernetes, Mesos, or Docker. These orchestrators are immensely better at tracking versions, managing packages, and scaling compute horizontally than any database plugin could ever be.

In this model, the database acts as a massive global *Mutable Variable*. The stateless web servers connect via the network to read and write to it. 
However, this exposes a massive shortcoming of modern programming languages. In Java or Python, if another node mutates a variable, you cannot easily mathematically *subscribe* to that change. You are forced to passively **poll** it (querying `SELECT *` every 5 seconds to see if anything changed). 

Databases inherited this passive polling approach from programming languages. Only recently, with the rise of tools like Kafka and CDC streams, are databases finally exposing the ability to actively *subscribe* to changes.

### Dataflow: Interplay Between State Changes and Application Code
Once you have the ability to *subscribe* to state changes, you fundamentally renegotiate how your application runs. 
Instead of treating Postgres as a dumb, passive variable that your Java code manually pushes updates into, your Java code **collaborates** with Postgres. Application code reacts to state changes in one place by deterministically triggering state changes in another place. 

If we unbundle the database and rely on stream processing to do this, we need the log to provide two massive guarantees:
1.  **Stable Ordering:** The exact chronological sequence of events is perfectly preserved so that all downstream views remain mathematically consistent.
2.  **Fault Tolerance (Durable Delivery):** Dropping a single message causes a derived cache to go permanently out of sync with its source.

Fortunately, modern Kafka + Flink setups provide these stringent guarantees effortlessly. It is immensely faster and more practically robust to rely on durable, totally ordered event logs than to attempt synchronous distributed transactions. 

### Stream Processors and Services (REST vs. Dataflow)
Currently, the dominant architectural paradigm is Microservices communicating via **Synchronous REST APIs or RPC Calls**.
Like the Dataflow approach, Microservices achieve great organizational scalability by loosely coupling teams. However, the exact way the servers talk to each other is profoundly different.

Imagine a user is purchasing an item on your eCommerce platform, and your code needs the current foreign exchange rate to complete the checkout.

1.  **The REST / RPC Approach:**
    When the user clicks "Buy", your `Purchase Service` makes a synchronous HTTP network call over the internet to the `Exchange Rate Service`. 
    *The Nightmare:* If the Exchange Rate Service goes down, your Purchase Service crashes. If the network drops the packet, your Purchase Service hangs.

2.  **The Dataflow / Streaming Approach:**
    The `Purchase Service` permanently *subscribes* to a Kafka stream of Exchange Rate updates ahead of time. It stores the latest exchange rates sequentially inside its own isolated, local database on the exact same physical machine. 
    When the user clicks "Buy," the `Purchase Service` simply runs a 0.1-millisecond `SELECT` query against its own local Hard Drive!

Not only is the Dataflow approach infinitely faster (because you physically eliminated the network round-trip), but it is perfectly robust. If the Exchange Rate Service crashes entirely, your users can still successfully check out because the `Purchase Service` simply uses the last known cached rate from its local database!

*The fastest and most reliable network request is no network request at all.* 
By embracing event streams, we completely eliminate devastating synchronous dependencies and move toward a spreadsheet-like model of reactive dataflow.
---
## Related Concepts
* [[Data Intensive Applications]]

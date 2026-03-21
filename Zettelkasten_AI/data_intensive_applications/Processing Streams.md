---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
Once you have an endless stream of real-time events flowing through a message broker, what do you actually do with it? 
Historically, there are three major use cases for stream processing:
1. **Complex Event Processing (CEP):** Looking for specific patterns (e.g., detecting fraud if a credit card is swiped in New York and London within 10 minutes).
2. **Stream Analytics:** Aggregating metrics across time windows.
3. **Maintaining Materialized Views:** Rebuilding caching databases from the stream.

### Stream Analytics
While CEP focuses on finding a specific needle in a haystack, Analytics focuses on the shape of the haystack itself. Stream analytics heavily relies on aggregations and statistical metrics over massive volumes of events:
*   Measuring the rate of a specific event per minute.
*   Calculating a moving 5-minute rolling average.
*   Comparing current real-time statistics against the same time-window from last week to trigger an alert.

Because true Stream Analytics operates on millions of events per second, it often employs **Probabilistic Algorithms**. Algorithms like *HyperLogLog* (for estimating unique visitors) or *Bloom Filters* provide highly accurate mathematical approximations using phenomenally less RAM than exact algorithms. *(Note: Using approximation algorithms is just a performance optimization; stream processors are fully capable of exact, lossless math if configured to do so).*

Popular analytical stream processors include Apache Flink, Apache Storm, Spark Streaming, and hosted solutions like Google Cloud Dataflow.

### Maintaining Materialized Views
As discussed earlier, a massive use case for stream processing is taking a CDC stream from Postgres and maintaining a materialized view inside Elasticsearch or Redis. 
Unlike Stream Analytics (which throws away the data after the 5-minute window closes), maintaining a materialized view mathematically requires a window that stretches back to the beginning of time. 
Tools like **Kafka Streams** and **ksqlDB** exist purely to execute this infinite timeline by aggressively leaning on Kafka's Log Compaction feature.

#### The Rise of Incremental View Maintenance (IVM)
Historically, traditional databases maintain a Materialized View using brute-force batch processing. If you run PostgreSQL's `REFRESH MATERIALIZED VIEW`, the database literally drops the table and re-runs the entire massive SQL query from scratch. 
This is disastrous for two reasons:
1. **Poor Efficiency:** It criminally wastes CPU re-calculating millions of unchanged rows.
2. **Poor Freshness:** You can realistically only run a massive refresh batch job once an hour or once a day, meaning the Materialized View is constantly stale.

The modern evolution of stream processing fixes this using **Incremental View Maintenance (IVM)**. 
Instead of dropping the table, IVM mathematically acts like a continuous derivative. If you have an incredibly complex `GROUP BY` and `JOIN` query maintaining a materialized view, an IVM database will listen to the stream of real-time `INSERT` events, calculate the *exact delta change* that one new row causes to the aggregate totals, and elegantly update the exact cells on disk. 

Databases engineered around IVM (like **Materialize**, **ClickHouse**, **RisingWave**) can magically expose massive data-warehouse-style SQL queries that are mathematically maintained in sub-second real-time via the event stream.

### Searching on Streams (The Flipped Database)
We are used to traditional Search Engines: You insert millions of Documents into Elasticsearch, and then a user executes a specific Query against those documents.

Streaming allows you to perfectly invert this architecture (which is famously used by media monitoring services or real-estate "Alert Me" features).
Instead of storing Documents, **you store the Queries.**
When a real-estate agent registers an alert for *"3 Bedroom houses under $500k"*, that SQL-like query is physically stored in the database. When the real-time stream of new property listings files past, the individual Document is checked against the database of millions of Queries. If it matches, an email alert is fired immediately.

### Event-Driven Architectures and RPC
Earlier in the book, we discussed the "Actor Model" as an alternative to RPC calls for microservice communication. While Actor frameworks (like Akka) use messages, they are conceptually different from Stream Processors:
*   Actors use messaging primarily to manage concurrency and distributed execution. Stream processing is fundamentally a *Data Management* technique.
*   Actor communication is generally ephemeral, one-to-one, and allows cyclic request/response loops. Stream processing relies on durable logs, multi-subscriber fan-outs, and strictly *acyclic* pipelines (DAGs) where data flows purely in one direction.
*   Most Actor frameworks do not guarantee message delivery during node crashes. Stream processors are built entirely around flawless fault-tolerance.
---
## Related Concepts
* [[Data Intensive Applications]]

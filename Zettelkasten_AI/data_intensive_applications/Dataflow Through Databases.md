---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 2: Internals"
status: pending
---

In a database, the process writing the data encodes it, and the process reading it decodes it. 
*   **A message to your future self:** If only one process is accessing the database, writing data is just sending a message to a future version of your own code. Thus, *Backward Compatibility* is absolutely mandatory, otherwise your future code couldn't read your past data.
*   **Rolling Upgrades:** In modern systems with zero-downtime rolling upgrades, multiple versions of your app might be accessing the database simultaneously. An old node might read a record that a newly-upgraded node just wrote. Thus, *Forward Compatibility* is also strictly required.

**Data Outlives Code**
When you deploy a new version of your server application, the old code is entirely replaced within minutes. However, a five-year-old database row will still be sitting there locally in its original 5-year-old encoding format unless you explicitly rewrote it. This is summarized by the phrase: **"Data outlives code."**
*   *Migrations:* Rewriting an entire massive dataset during a schema change is too expensive. Therefore most relational databases allow simple changes (like adding a column with a default `null`) instantly without touching existing rows on disk. When an old row is read, the DB just fills in the missing column with `null` on the fly. 
*   *Compaction:* LSM-trees (like those discussed in Chapter 4) take care of older schema migrations organically; when the engine performs background compaction to merge SSTables, it naturally rewrites the data into the newest format.

**Archival Storage**
When you take a periodic database snapshot for backup or to load into a Data Warehouse, you don't copy the mix of historic schema formats. Instead, the data extraction process is a great time to dump everything consistently using the *latest* schema. Because this archival data is written in one massive go and becomes immutable, formats like **Avro Object Container Files** or analytics-focused columnar formats like **Parquet** are the perfect fit.
---
## Related Concepts
* [[Data Intensive Applications]]

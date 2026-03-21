---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
Modern batch processing was revolutionized by Google's publication of the **MapReduce** algorithm in 2004 (and its open-source offspring, **Hadoop**). MapReduce proved that you could easily write a single script and automatically parallelize it across thousands of cheap commodity servers.

Today, however, the original MapReduce algorithm is largely considered obsolete and is no longer used at Google. The ecosystem has evolved rapidly:
*   **Compute Engines:** Developers now use highly optimized memory-native frameworks like **Apache Spark** and **Apache Flink**, or massively parallel Cloud Data Warehouses like **Snowflake** and **BigQuery**. These systems feature advanced query optimizers, DataFrame APIs, and declarative SQL support.
*   **Orchestration:** The old XML-heavy Hadoop schedulers (Oozie, Azkaban) have been entirely replaced by modern Python-based DAG orchestrators like **Apache Airflow, Dagster,** and **Prefect**.
*   **Storage:** The heavy, complex Distributed File Systems (like HDFS or GlusterFS) have been almost entirely retired in favor of globally scalable **Object Storage** (like Amazon S3 or Google Cloud Storage).
---
## Related Concepts
* [[Data Intensive Applications]]

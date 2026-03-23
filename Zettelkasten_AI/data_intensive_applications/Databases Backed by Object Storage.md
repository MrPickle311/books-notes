Many modern databases are shifting to use cloud object storage (AWS S3, Google Cloud Storage, Azure Blob Storage) not just for backups, but for serving live query data.

**Benefits of Object Storage:**
1.  **Cost:** It is dramatically less expensive than SSDs/NVMe or attached virtual block storage (AWS EBS). This enables "tiered storage" where hot data is kept on fast disks/memory, and cold data sits on cheap S3.
2.  **Built-in High Availability:** Cloud object stores natively provide multi-region replication with near-perfect durability guarantees without inter-zone network fees.
3.  **Transactions/Election via CAS:** Databases can leverage the object store's own Compare-and-Set (CAS) features (like S3 conditional writes) to easily implement split-brain-proof transaction coordination and leader election.
4.  **Data Lake Integration:** Storing database files in open formats (like Apache Parquet or Iceberg) directly on S3 makes integrating data across entirely different systems incredibly easy.

**Trade-offs and Mitigation:**
1.  **Latency & File Structure:** Object storage has much higher read/write latency than local disks. They also charge a per-API-call fee, requiring databases to batch operations aggressively. Furthermore, they are immutable and lack POSIX filesystem features (like non-sequential writes), meaning databases must be entirely re-architected to write in large, immutable chunks rather than modifying in-place.
2.  **Tiered vs. Zero-Disk Architectures (ZDA):** 
    *   *Tiered Storage:* Keep fast, small WALs (Write-Ahead Logs) on low-latency virtual disks (EBS), and periodically flush cold data to Object Storage.
    *   *Zero-Disk Architecture (ZDA):* Systems like WarpStream, Confluent Freight, and Turbopuffer push everything to S3, using local disks and RAM *strictly* as temporary caches. This means the actual compute nodes are completely stateless, making operations and scaling drastically simpler.
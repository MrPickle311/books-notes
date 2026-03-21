---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
status: moc
---

# Data-Intensive Applications

## Chapter 1: Trade-offs in Data Systems Architecture
* [[Data-Intensive Applications Building Blocks]]
* [[Operational Versus Analytical Systems]]
* [[Data Warehousing and Data Lakes]]
* [[Systems of Record and Derived Data]]
* [[Cloud Versus Self-Hosting]]
* [[Cloud-Native System Architecture]]
* [[Distributed Versus Single-Node Systems]]
* [[Microservices and Serverless]]
* [[Data Systems Law and Society]]
* [[Tips for Data Systems Architecture]]

## Chapter 2: Defining Nonfunctional Requirements
* [[Functional vs Nonfunctional Requirements]]
* [[Social Network Timelines Case Study]]
* [[Describing Performance]]
* [[Reliability and Fault Tolerance]]
* [[Scalability]]
* [[Maintainability]]

## Chapter 3: Data Models and Query Languages
* [[Relational Model vs Document Model]]
* [[Schema-on-Read vs Schema-on-Write]]
* [[Normalization vs Denormalization]]
* [[Graph-Like Data Models]]
* [[Query Languages for Data]]
* [[Data Warehousing Schemas]]
* [[Event Sourcing and CQRS]]

## Chapter 4: Storage and Retrieval
* [[Log-Structured Storage]]
* [[B-Trees]]
* [[Comparing B-Trees and LSM-Trees]]
* [[Column-Oriented Storage]]
* [[Secondary Indexes and In-Memory Databases]]
* [[Query Execution and Materialized Views]]
* [[Multidimensional and Full-Text Indexes]]
* [[Vector Embeddings and Semantic Search]]

## Chapter 5: Encoding and Evolution
* [[Encoding Formats]]
* [[Protocol Buffers]]
* [[Apache Avro]]
* [[Dataflow Through Databases]]
* [[Dataflow Through Services]]
* [[Durable Execution and Workflows]]
* [[Event-Driven Architectures]]

## Chapter 6: Replication
* [[Single-Leader Replication]]
* [[Synchronous vs Asynchronous Replication]]
* [[Setting Up New Followers]]
* [[Databases Backed by Object Storage]]
* [[Handling Node Outages]]
* [[Implementation of Replication Logs]]
* [[Problems with Replication Lag]]
* [[Multi-Leader Replication]]
* [[Dealing with Conflicting Writes]]
* [[Leaderless Replication]]

## Chapter 7: Sharding
* [[Sharding Overview]]
* [[Pros and Cons of Sharding]]
* [[Sharding for Multitenancy]]
* [[Sharding by Key Range]]
* [[Sharding by Hash of Key]]
* [[Consistent Hashing]]
* [[Skewed Workloads and Hot Spots]]
* [[Automatic or Manual Rebalancing]]
* [[Request Routing]]
* [[Sharding and Secondary Indexes]]

## Chapter 8: Transactions
* [[What is a Transaction]]
* [[The Meaning of ACID]]
* [[Single-Object and Multi-Object Operations]]
* [[Handling Errors and Aborts]]
* [[Read Committed]]
* [[Snapshot Isolation and Repeatable Read]]
* [[Preventing Lost Updates]]
* [[Write Skew and Phantoms]]
* [[Serializability]]
* [[Distributed Transactions]]

## Chapter 9: The Trouble with Distributed Systems
* [[Faults and Partial Failures]]
* [[Unreliable Networks]]
* [[Timeouts and Unbounded Delays]]
* [[Unreliable Clocks]]
* [[Clock Confidence Intervals]]
* [[Process Pauses]]
* [[Knowledge Truth and Lies]]
* [[Distributed Locks and Fencing]]
* [[Byzantine Faults]]
* [[System Models and Correctness]]

## Chapter 10: Consistency and Consensus
* [[Linearizability]]
* [[Linearizability vs Serializability]]
* [[Relying on Linearizability]]
* [[Implementing Linearizable Systems]]
* [[The Cost of Linearizability]]
* [[Distributed ID Generators]]
* [[Logical Clocks]]
* [[Linearizable ID Generators]]
* [[Consensus]]
* [[The Many Faces of Consensus]]
* [[Coordination Services]]
* [[The True Cost of Consensus]]

## Chapter 11: Batch Processing
* [[The Philosophy of Batch Processing]]
* [[The Evolution of Batch Frameworks]]
* [[Batch Processing with Unix Tools]]
* [[Distributed Filesystems]]
* [[Object Stores]]
* [[Distributed Job Orchestration]]
* [[Scheduling Workflows]]
* [[Handling Faults in Batch]]
* [[MapReduce]]
* [[Dataflow Engines]]
* [[Shuffling Data]]
* [[JOIN and GROUP BY]]
* [[The Evolution of Query Languages]]
* [[Batch Use Cases]]

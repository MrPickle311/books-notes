The Unix tool example chained several commands together using pipes (`awk | sort | uniq`). The exact same pattern happens in distributed processing. It is very rare for a single batch job to compute the entire final answer natively; instead, data is passed through a sequence of dozens of independent jobs. 

This chain of jobs is called a **Workflow**, or a **Directed Acyclic Graph (DAG)** of jobs.

*(Note: In Chapter 9 we discussed "Durable Execution" workflows, which orchestrate complex microservice RPC calls. In the context of Batch Processing, a "Workflow" strictly means a DAG of massive data-crunching pipelines, typically with no external API calls).*

We build Workflows instead of one giant script for several reasons:
1.  **Multiple Consumers:** The output of Job A might be useful to three different teams (e.g., Team B needs it for an AI model, and Team C needs it for billing). Writing it to a shared location prevents redundant computation.
2.  **Tool Bridging:** You might run a massive Spark job to clean raw logs, and then trigger a Trino SQL query to perform the final fast aggregation. A workflow orchestrators safely manages handing the data between two completely different software ecosystems.
3.  **Algorithmic Requirements:** Often, you need to shard data by `user_id` in Job 1 to sort behavior logs, but then re-shard that exact same data by `country_id` in Job 2 to generate geographical metrics.

### Distributed Pipes vs Storage Hand-offs
In a Unix pipeline, data streams seamlessly from one program to the next through tiny in-memory buffers. If the buffer is full, the producing program physically pauses (which naturally creates *backpressure*). Modern streaming batch engines (like Spark or Flink) can emulate this exact in-memory streaming over the network. 

However, in classic Batch Workflows, **in-memory streaming is rarely used.** Instead, the standard architecture is for Job A to compute its entire output, save it permanently to an Object Store (like S3), completely power down, and then flag the Workflow Scheduler to boot up Job B to read that folder.
This brutally decouples the jobs. It's slower, but it prevents the entire 100-step DAG from instantly crashing if one node in the middle experiences a microsecond of network lag. 

### Modern Workflow Schedulers
The cluster orchestrators we just talked about (YARN, Kubernetes) **do not** manage workflows. They only know how to ask for CPU cores and run a single isolated Docker container.

To manage the dependencies between 100 sequential batch jobs (e.g., "Don't run Job C until both Job A and Job B have finished analyzing their partitions"), the industry built a secondary layer of **Workflow Schedulers** (Data Orchestrators).
Modern data engineering teams manage their DAGs almost exclusively using generalized Python orchestrators like **Apache Airflow, Dagster,** or **Prefect**. These tools give data engineers a visual dashboard to monitor exactly where massive chains of dependencies are succeeding, failing, or bottlenecking.
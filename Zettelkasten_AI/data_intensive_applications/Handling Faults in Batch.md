---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
Batch processing jobs are uniquely susceptible to faults simply because they run for so long. If a massive job takes 14 hours to run across 1,000 servers, the statistical probability of at least one server experiencing a hardware failure, network partition, or OS crash during that window approaches 100%.

Furthermore, batch jobs are intentionally executed on cheap, unreliable hardware. Many companies use **Spot Instances** (Preemptible VMs) to save money. These virtual machines are extremely cheap, but the cloud provider (AWS/GCP) actively reserves the right to assassinate the machine with zero warning if they need the capacity back. On spot instances, Preemption (being killed by the cloud provider) is actually a vastly more common fault than standard hardware failure!

### Task-Level Fault Tolerance
If a 14-hour job fails at hour 13 because a single spot instance was terminated, it would be insanely expensive and wasteful to force the entire 1,000-node cluster to start the 14-hour job over from scratch. 

To solve this, frameworks (like MapReduce and Spark) break the massive job down into thousands of tiny, independent **Tasks**. 
If one specific Task crashes:
1.  The Task Executor notifies the Resource Manager that it failed.
2.  The system simply deletes the partial/corrupted output of that *single specific task*.
3.  The Scheduler re-assigns that specific Task to a new, healthy node.
4.  The other 999 nodes continue working completely uninterrupted.

### Preserving Intermediate Data
Fault tolerance becomes much trickier if the output of Task A is being actively streamed into the input of Task B. If Task A is preempted and dies halfway through, what happens to Task B?

Different frameworks solve this intermediate data problem differently:
*   **MapReduce (The Safe/Slow Way):** MapReduce completely eliminated this risk by forcing every single task to fully write its completed output to the Distributed File System (HDFS) *before* the next task was allowed to begin reading it. This guarantees bulletproof fault tolerance but absolutely destroys performance by forcing constant, heavy disk I/O.
*   **Spark (The Fast/Smart Way):** Spark avoids writing to disk whenever possible, keeping data pipelines entirely in RAM. To achieve fault tolerance, Spark tracks the **Lineage** (the exact mathematical steps) of how the data was computed. If a node crashes and loses its chunk of RAM, Spark simply looks at the lineage graph and dynamically recomputes *only* the missing chunk of data on a new node.
*   **Flink:** Takes a completely different approach by periodically taking global snapshot "Checkpoints" of the entire cluster's state. If a node fails, the entire cluster briefly rolls back to the last successful 10-second checkpoint and resumes (more on this in Chapter 12: Stream Processing).
---
## Related Concepts
* [[Data Intensive Applications]]

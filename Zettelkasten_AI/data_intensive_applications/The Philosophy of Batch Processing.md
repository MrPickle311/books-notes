A Batch Processing job operates on a very specific set of rules:
1.  **Read-Only Inputs:** The job takes a massive dataset as input, but it mathematically guarantees it will *never* mutate or alter that input data.
2.  **Generated Outputs:** The job runs its computations and writes the final results to a completely new location generated from scratch.

Because the output is purely derived from the inputs without mutating the original source of truth, batch processing introduces a powerful concept called **Human Fault Tolerance**. 

### Human Fault Tolerance
If you deploy a buggy line of code to an Online OLTP database that accidentally corrupts user data, rolling back the code *does not fix the database*. The data is permanently ruined. 

In a Batch Processing system, if you deploy buggy code, the output file is ruined, but the original input data is perfectly safe. To fix the system, you simply roll back the code and run the batch job again. The new, correct output cleanly replaces the old, buggy output. 
*(Many modern object stores, like Amazon S3 or open table formats, support "Time Travel," allowing you to literally keep the old buggy output and toggle back and forth between versions).*

This principle of **minimizing irreversibility** drastically accelerates feature development, because developers are no longer paralyzed by the fear of permanently destroying the database.

### The Trade-offs of Batch Processing
*   **Pros:** Safe (Human Fault Tolerant), highly efficient use of compute resources, allows the same input files to be used by multiple different analytical jobs concurrently.
*   **Cons:** High Latency (jobs can take minutes, hours, or days to finish). Furthermore, if a single byte in the input data changes, the entire batch job generally has to reprocess the entire dataset from scratch. 
*   **Primary Metric:** Instead of measuring *Response Time* (like online systems), batch systems measure **Throughput** (how many terabytes of data they can churn through per hour).

*(Note: The middle ground between Batch Processing and Online Processing is called **Stream Processing**, which we will cover in Chapter 12).*

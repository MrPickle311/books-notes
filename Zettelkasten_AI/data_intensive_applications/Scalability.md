Scalability is not a label ("X is scalable") but a question: "If load increases, how do we cope?"

*   **Describing Load:** Identify the **load parameter** (e.g., writes/sec, concurrent users, cache hit rate).
*   **Scaling Up (Vertical):** Moving to a more powerful machine. Also known as **Shared-Memory Architecture** because multiple threads/processes share the same RAM. Simple but expensive and has hard limits.
*   **Shared-Disk Architecture:** Using multiple machines with independent CPUs/RAM but sharing an array of disks (NAS/SAN) via a fast network.
*   **Scaling Out (Horizontal/Shared-Nothing):** Distributing load across many smaller machines (nodes), each with its own CPU, RAM, and disk. Cheaper and theoretically limitless but adds complexity (distributed systems issues).
*   **Autoscaling:** Automatically adding or removing computing resources in response to changing demand. Useful for unpredictable load but can introduce operational surprises.

> **Magic Scaling Sauce:** There is no generic scalable architecture. A system designed for 100k small requests/sec is different from one designed for 3 massive requests/min. Architecture must evolve with load (usually every 10x growth).
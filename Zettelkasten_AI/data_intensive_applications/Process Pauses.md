---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Let's look at another hidden trap of clocks. 
Imagine a single-leader database. To prove it is still the leader, it routinely renews a 10-second "lease" (like a lock). The code looks like this:

1.  Check the clock to ensure the lease has at least 10 seconds remaining.
2.  If the lease is valid, process a user's write request.

We already know this is dangerous if we use a Wall Clock, because Wall Clocks can jump around. So we switch to a Monotonic Clock to perfectly measure the 10-second interval. Are we safe now? 
**No. Because of Process Pauses.**

The code above assumes that step 1 and step 2 occur back-to-back instantaneously. 
But what if the entire application thread is paused by the operating system *in between* checking the clock and processing the request? If the thread is paused for 15 seconds, by the time Step 2 resumes, the lease has unknowingly expired. Another node has taken over as leader, but the paused thread awakens and unknowingly writes the data anyway, creating a horrific split-brain scenario.

**Why do threads suddenly pause for 15 seconds?**
In distributed systems, you must assume your execution thread can be completely frozen at any given millisecond for many reasons:
*   **Contention among threads:** Accessing a shared resource, such as a lock or queue, can cause threads to spend a lot of their time waiting.
*   **Garbage Collection (GC):** "Stop-the-world" garbage collectors (like older Java VMs) will literally freeze all running threads to manage memory. This can take tens of seconds or even minutes.
*   **Virtual Machine Suspension:** Hypervisors will often completely pause a VM, save its memory to disk, and migrate it to a completely different physical server without rebooting. During this, the VM is totally frozen.
*   **Context Switching (Steal Time):** If the OS or Hypervisor switches to another thread/tenant, your thread gets put in a queue waiting for CPU time.
*   **Synchronous Disk Access:** If you touch a file—or even if Java triggers an unexpected Lazy Classload—and the disk is a network drive (like AWS EBS), you get blocked by unbounded network I/O latency.
*   **Swapping to Disk:** If memory is full and the OS "page faults", the thread freezes while reading memory from a slow hard drive.
*   **SIGSTOP:** A human admin accidentally hits Ctrl-Z in the terminal, suspending the process.

During all of these pauses, the node has no idea it went to sleep. The rest of the distributed system keeps moving, assumes the frozen node is dead, and reorganizes.

When writing multi-threaded software on a single computer, we use Mutexes and Semaphores to handle this uncertainty. But distributed systems don't have shared memory to place a Mutex in. 
A node in a distributed cluster must assume its thread could be yanked away *at any point*, and when it wakes up, it can no longer trust any assumptions it made before it went to sleep.

#### Response Time Guarantees 
Can we prevent these process pauses? Yes, but only if you build a **Hard Real-Time System**.

In software that controls aircraft, airbags, or pacemakers, a delayed response isn't an annoyance—it's catastrophic. These systems use **Real-Time Operating Systems (RTOS)** that mathematically guarantee a specific CPU allocation at strict intervals. They disable dynamic memory allocation to prevent GC pauses, and they use extremely tight, strictly documented libraries.

Building this is incredibly expensive, slow, and severely limits the tools you can use. 
*(Note: Do not confuse "Real-Time" with "Fast". RTOS prioritizes guaranteed timeliness, which usually means it actually has much lower throughput than a standard OS).* 

For standard server-side web systems, this level of strict CPU partitioning is simply not economical. We use standard operating systems that optimize for dynamic throughput instead. Therefore, server-side data processing must suffer unpredictable pauses, and our distributed algorithms must be designed to survive them.

#### Limiting the Impact of Garbage Collection
Since we cannot eliminate process pauses in standard operating systems, can we at least mitigate the worst offender (Garbage Collection)?
Modern GC algorithms (like Java's ZGC or Shenandoah) have improved massively, usually keeping pauses under a few milliseconds. But if we want to aggressively limit GC pauses, there are a few strategies:

1.  **Avoid GC entirely:** Use a language that tracks memory lifetimes at compile-time (like Rust or C++) or uses automatic reference counting (like Swift), completely eliminating the need for a runtime Garbage Collector.
2.  **Object Pools / Off-heap memory:** If using a GC language, allocate memory manually off the heap or reuse objects from a pre-allocated pool to prevent the GC from ever needing to clean them up.
3.  **Treat GC like a planned outage:** If the language runtime can warn the application that it is about to run a massive "Stop-the-world" GC pause, the application can route all new incoming traffic to other nodes in the cluster, finish its current requests, and *then* run the GC without dropping any active user traffic. This completely hides the GC pause from the end-user.
4.  **Restart instead of Full GC:** Use the GC only for fast, short-lived objects. Before the slow, long-lived object heap fills up (which requires a massive pause to clean), proactively reboot the entire node one at a time using a rolling upgrade strategy.

---
## Related Concepts
* [[Data Intensive Applications]]

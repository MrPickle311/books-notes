---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Modern distributed databases use a "shared-nothing" architecture. Dozens of computers have their own CPU, memory, and disk. They share absolutely nothing except a network cable.

The internet and datacenter networks are **Asynchronous Packet Networks**. They offer absolutely zero guarantees about when a packet will arrive, or if it will fundamentally arrive at all.

If you send a request over the network and do not receive a response, here is everything that could have happened:
1.  **Request Lost:** The packet was dropped (someone tripped over a cable).
2.  **Request Delayed:** The packet is still sitting in a queue waiting to be processed.
3.  **Remote Node Dead:** The server crashed or lost power.
4.  **Remote Node Suspended:** The server is temporarily frozen (e.g., executing a massive Garbage Collection pause) and will respond eventually.
5.  **Response Lost:** The server successfully did the work, but the network switch dropped its reply packet.
6.  **Response Delayed:** The server did the work, but its reply is stuck in a queue.

![Figure 9-1: If you send a request and don’t get a response, it’s not possible to distinguish whether (a) the request was lost, (b) the remote node is down, or (c) the response was lost.](figure-9-1.png)

**The Crux:** It is mathematically impossible to distinguish between these 6 scenarios. The sender only knows one thing: "I have not received a response yet." 
The only practical way to handle this is by implementing a **Timeout**, though even when a timeout triggers, you still have no idea *why* it triggered or if the remote node successfully processed your request before timing out.

### The Limitations of TCP
Doesn't the Transmission Control Protocol (TCP) promise "reliable delivery"? Yes, but only to a certain extent. 
TCP breaks large data strings into packets, puts them in the correct order, detects corruption, and handles network congestion (backpressure). 

When you “send” some data by writing it to a socket, it actually doesn’t get sent immediately, but it’s only placed in a buffer managed by your operating system. The congestion control algorithm decides that it has capacity to send a packet

However, TCP does not change the physical reality of the network:
*   TCP will retry lost packets, but if the network cable is actually unplugged, TCP will eventually just throw an error. TCP’s deduplication and retransmission capabilities only apply to a single connection, so if the application reconnects and retransmits, data could be duplicated.
*   If a TCP connection drops, you have absolutely no idea how much of your data the receiving application actually processed. 
*   Even if TCP successfully delivered the packet to the remote node's operating system, the remote application itself might have crashed a millisecond before looking at it. 

*Conclusion:* TCP guarantees packets reach the remote machine's kernel network buffers. But if you want to know if an application actually successfully processed your business logic, the *only* way is to receive a positive application-level response back from the remote server.

### Network Faults in Practice
We’ve been building networks for decades; haven't we solved this? No.
Even in modern, tightly controlled datacenters, network faults are surprisingly common:
*   Human error is a massive cause of outages (misconfigured switches). Adding redundant networking gear often doesn't help because human misconfiguration can take down both paths.
*   Hardware fails constantly: power distribution unit failures, switch failures, accidental power loops.
*   Submarine cables get severed by sharks, cows, or backhoes.
*   **Asymmetric faults:** Node A can reach Node B, but Node B cannot reach Node A. (Often a switch drops outbound packets but passes inbound ones).
*   **Arbitrary delays:** During a switch software upgrade, packet routing can be delayed by more than a minute while topology is re-computed.

Because networks will fail, your software *must* be able to handle it. If you don't define the error handling for network faults, the software may lock up, deadlock the cluster, or silently delete user data when an unexpected interrupt happens.

Testing is vital here. You should deliberately sever network links in your testing environments to observe how your system reacts (known as **Fault Injection**). 

*(Note: When a network fault completely cuts off one part of the network from another, it is called a **Network Partition** or "netsplit".)*

### Detecting Faults
Because networks are totally asynchronous, it is incredibly difficult to reliably detect if another node has actually died. Many systems (like load balancers routing traffic, or a database needing to elect a new leader) desperately need to know if a remote node is actually dead.

Sometimes, the network or OS will give you a helpful hint:
*   **Connection Refused:** If the machine is alive but the application process crashed, the OS will aggressively reject your TCP connection (sending an `RST` or `FIN` packet).
*   **Process Notifications:** If a database node crashes (like HBase) but the OS survives, a background script can quickly ping the rest of the cluster to tell them the node died.
*   **Switch Management Interfaces:** In a private datacenter, you can query the hardware switches themselves to see if the link to a specific server is powered down.
*   **ICMP Unreachable:** If a router knows an IP address is dead, it *might* send back an "ICMP Destination Unreachable" packet.

However, you *cannot rely on any of these hints.* If a switch is misconfigured or a server loses power entirely, you won't get an explicit rejection. You will get *nothing*. You will just sit there waiting for a response that will never arrive.

Because of this, the universal gold standard for detecting faults is the **Timeout**:
You try to ping the remote node. If it doesn't respond within X seconds, you declare it dead.
This creates a critical balancing act:
*   **Too Short:** You get false positives. You declare a perfectly healthy (but temporarily slow) node dead. If it was the Leader, you force an expensive, unnecessary Leader Election that slows down your whole system. Worse, if the node is actually alive and executing an action (like sending an email), declaring it dead and re-assigning its work might cause the action to happen twice.
*   **Too Long:** You get false negatives. Your system sits completely paralyzed for minutes waiting on a server that is literally unplugged.

### Timeouts and Unbounded Delays
If you must use a timeout, exactly how long should it be? 
In a perfect universe, we could mathematically calculate it. If we knew the absolute maximum time it takes a packet to cross the network ($d$), and we knew the maximum time the server takes to process the request ($r$), a perfect timeout would be $2d + r$. 
If it took any longer than $2d + r$, we would absolutely guarantee the node was dead.

Unfortunately, our universe has **unbounded delays**. 
1. Asynchronous networks have no maximum limit on how long a packet can take. 
2. Servers usually cannot guarantee a maximum processing time limit.

#### Network Congestion and Queueing (Why Delays are Unbounded)
Why do packets take varying amounts of time to travel? Just like driving a car, it mostly comes down to traffic jams—or in networking terms, **Queueing**. 
*   **Switch Queues:** If multiple servers all try to send data to the same destination simultaneously, the network switch becomes a bottleneck. It must put the packets in a memory queue and feed them to the destination link one by one. If the queue gets completely full, the switch just drops the newly arriving packets entirely.
![Figure 9-2: If several machines send network traffic to the same destination, its switch queue can fill up.](figure-9-2.png)
*   **OS Queues:** When the packet finally arrives at the server, but all CPU cores are busy, the operating system puts the packet in a queue until the application has time to read it.
*   **VM Pauses:** If the server is a Virtual Machine on a public cloud, the hypervisor might literally pause the VM for tens of milliseconds to let another tenant use the CPU. While paused, incoming packets just sit buffered. 
*   **TCP Congestion Control:** Before the packet even leaves the sender, TCP intentionally throttles your send rate to avoid congesting the network further, keeping the data buffered on your own OS.

*(Note: **TCP vs. UDP**. TCP forces reliable delivery, so if a packet drops, it silently re-transmits it, hiding the loss from the application but massively increasing the delay variance. Applications like VoIP videoconferencing use UDP instead because they prioritize speed over reliability; if a packet drops, UDP ignores it, creating a brief glitch in audio, rather than pausing the entire video call while waiting for a retransmission.)*

Because of "noisy neighbors" in public cloud datacenters maxing out shared router links, you have virtually zero control over network queueing. So how do you pick a timeout?

You cannot just guess a number. The best strategy is continuous experimentation. **Dynamic Timeouts** (like the *Phi Accrual failure detector* used in Akka and Cassandra) automatically measure the jitter and response times of the network in real-time. By observing the distribution dynamically, the system constantly adjusts its own timeout threshold to find the perfect mathematical balance between false positives and false negatives for your actual current network weather.

### Synchronous vs. Asynchronous Networks
Why is the internet so flaky? Old-school fixed-line telephone networks are incredibly reliable. When you place a voice call, you don't experience random buffering or dropped audio packets. Why can't datacenter networking be built like telephone networks?

The difference lies in how the networks are structured: **Synchronous** vs **Asynchronous**.

#### Telephone Networks: Circuit Switching (Synchronous)
When you dial a phone, the network establishes a **Circuit**. 
A circuit physically reserves a fixed, guaranteed amount of bandwidth along the *entire* route between the two callers for the entire duration of the call.
*   Because the space along the wire is perfectly reserved just for you, nobody else can use it.
*   Because nobody else can use it, your data **never enters a queue**.
*   Because there are zero queues, the network has a mathematically **Bounded Delay**. The maximum end-to-end latency is fixed and guaranteed.

#### The Internet: Packet Switching (Asynchronous)
The internet and datacenters do *not* use circuits; they use **Packet Switching**. 
TCP doesn't reserve bandwidth; it opportunistically grabs whatever bandwidth happens to be free that exact millisecond. If the wire is busy, TCP data is forced to wait in a queue.

Why did we design the internet this way? **Burstiness**. 
A voice call needs a constant, exact stream of data (e.g., exactly 16 bits every 250 microseconds).
But internet traffic is incredibly "bursty." When you request a web page, you need zero bandwidth for a while, and then suddenly you need an intensive burst of bandwidth to download an image file as quickly as possible. 
If you tried to download a file over a Synchronous Circuit, you'd have to guess how much bandwidth to reserve. Guess too low, and it's slowly bottlenecked. Guess too high, and you permanently lock up network capacity that someone else could have used.

### Latency vs. Resource Utilization
The fundamental reason we suffer from unbounded delays and variable network latency is because we prioritized **Cheap Resource Utilization**.

There is a direct mathematical trade-off:
*   **Static Resource Partitioning (Circuits):** Achieves perfectly stable latency (no queueing), but suffers from terrible utilization. If you reserve a circuit and remain silent on the phone, that bandwidth is entirely wasted. Because it wastes resources, it is **expensive**.
*   **Dynamic Resource Partitioning (Packets):** Suffers from unpredictable latency (queueing), but achieves incredible utilization. Senders jostle and shove each other dynamically to cram as many packets onto the wire as possible at every given nanosecond. Because it maximizes exactly how much data fits on the wire, each byte sent is vastly **cheaper**.

The same trade-off applies to CPU Cores (Static allocation vs Dynamic OS Thread Scheduling) and Cloud Computing (Dedicated Hardware vs Multi-tenant Virtual Machines).
We *could* build computer networks with latency guarantees if we statically partitioned them with exclusive bandwidth. But the industry has unanimously decided that the immense cost savings of multitenancy and dynamic packet switching are worth the headache of variable delays. 

Therefore, variable network delays are not a law of physics—they are a deliberate, system-wide cost/benefit trade-off. We chose cheap and bursty over reliable and stable. As software engineers, it is now our job to handle the resulting chaos.
---
## Related Concepts
* [[Data Intensive Applications]]

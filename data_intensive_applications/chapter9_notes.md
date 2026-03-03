# Chapter 9: The Trouble with Distributed Systems

## 1. Introduction: The Pessimistic Mindset
Building reliable distributed systems requires a radical shift in mindset. As developers, we typically focus on the "happy path" because things usually work perfectly. 

However, in large distributed systems, **"anything that can go wrong, will go wrong."** Even a fault with a one-in-a-million probability will happen every single day when operating at scale. To build reliable systems, you must embrace pessimism and paranoia, anticipating unpredictable failures at all layers.

## 2. Faults and Partial Failures
When you write software for a single computer, the operating system goes out of its way to present an idealized, mathematically perfect reality. **Single-node computers are deterministic:** they either work perfectly, or they crash completely (kernel panic, blue screen of death). They are specifically designed *not* to operate in a halfway-broken state because that causes confusing errors.

**Distributed systems are fundamentally different.**
Because they span multiple machines connected by cables in the physical world, they are subject to **Partial Failures**. 
In a partial failure, one part of your system breaks in an unpredictable way while the rest of the system continues functioning fine. 

Because partial failures are entirely **nondeterministic**, a request might work, it might fail, or—worst of all—you might not even know if it succeeded or not. However, if we accept and design around partial failures, we can achieve something incredible: **Fault Tolerance**. We can build a perfectly reliable system constructed entirely out of inherently unreliable physical hardware.

## 3. Unreliable Networks
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

## 4. Unreliable Clocks
Clocks and time are critically important in distributed systems. We rely on them to determine if a timeout has expired, measure the 99th percentile response time, or figure out the exact date and time an article was published.

However, time is a tricky business across multiple machines for two reasons:
1.  **Communication is not instantaneous:** A message is always received *after* it was sent, but because of unbounded network delays, we have no idea exactly *how much* later. This makes reasoning about the order of events incredibly difficult.
2.  **Hardware is imperfect:** Every machine has its own physical quartz crystal oscillator clock. Because crystals vibrate at slightly different frequencies depending on temperature and manufacturing, their clocks inevitably drift apart. Even if synchronized via the Network Time Protocol (NTP), machines will always possess slightly different notions of what time it actually is.

Because of this, modern computers expose two completely different types of clocks to developers:

### 1. Time-of-Day Clocks (Wall-Clock Time)
A Time-of-Day clock answers the question: **"What is the exact date and time right now?"** 
*(e.g., `CLOCK_REALTIME` in Linux or `System.currentTimeMillis()` in Java).*
It returns the number of milliseconds since the UNIX epoch (Jan 1, 1970 UTC).

These clocks are synchronized globally via NTP, making them useful for recording timestamps across different machines. However, they have a massive, fatal flaw for software engineers: **They can jump backwards.**
*   If your local clock drifts too far ahead of the global NTP server, NTP will forcibly reset your local clock.
*   To the application, time will suddenly jump backwards.
*   Leap seconds also cause time-of-day clocks to jump or repeat a second.

*Conclusion:* Because Time-of-Day clocks can jump backwards in time, you must **never** use them to measure elapsed durations or calculate timeouts! If the clock jumps backwards while you are measuring a timeout, the math will yield a negative elapsed time, breaking your application logic.

### 2. Monotonic Clocks (Stopwatches)
A Monotonic clock answers the question: **"Exactly how much time has elapsed between Point A and Point B?"**
*(e.g., `CLOCK_MONOTONIC` in Linux or `System.nanoTime()` in Java).*

Monotonic clocks act exactly like a stopwatch. You check the clock before an event, check the clock after the event, and calculate the difference.
Unlike a Wall-Clock, Monotonic clocks mathematically guarantee they **always move forward**. They will never jump backwards, even if NTP realizes the local time is completely wrong. (NTP is allowed to slightly tweak the *speed* of the monotonic clock—slewing it by up to 0.05%—but it can never force it to jump backwards).

However, the absolute value of a monotonic clock is completely meaningless. It might just be the number of nanoseconds since the server recently booted. 
*Conclusion:* You should always use Monotonic clocks for measuring timeouts and response times. But because the starting point is arbitrary, you cannot compare a Monotonic clock value on Server A with a Monotonic clock value on Server B.

### Clock Synchronization and Accuracy
While Monotonic Clocks don't need synchronization, Time-of-Day Clocks are useless unless they are synchronized globally. We use the Network Time Protocol (NTP) to sync computers to external servers (which themselves sync to GPS receivers or atomic clocks).

Unfortunately, hardware clocks are surprisingly bad at keeping time, and NTP synchronization is incredibly fragile:
*   **Hardware Drift:** The quartz crystal inside a computer naturally drifts depending on its physical temperature. Even with perfect conditions, a standard server drifts about 17 seconds per day without synchronization.
*   **Forced Resets:** If a local clock drifts too far (because of drift, or a broken internal battery), NTP will forcibly snap the clock back, making time jump backward.
*   **Network Delays:** NTP relies on calculating the network round-trip time to figure out the exact global time. However, as we covered earlier, internet delays are unbounded. If the network experiences massive congestion, NTP's calculations become inaccurate by up to a full second (or the client might just give up entirely).
*   **Leap Seconds:** When a leap second is added, an artificial minute occurs that is 61 seconds long. Historically, this has crashed massive global systems (like Reddit and airline booking systems) because developers' code made incorrect assumptions about time. Many major companies now "Smear" the leap second—lying to the clock by running it slightly slower over the entire day to absorb the extra second, rather than forcing a violent jump. 
*   **Virtual Machine Jumps:** In a VM environment, when the hypervisor pauses a VM to give CPU time to a noisy neighbor, the time-of-day clock appears to freeze. When the VM wakes up 100ms later, the clock "jumps" forward instantaneously.
*   **Malicious Users:** If you are building mobile apps or edge devices, you can never trust the client's clock. End-users deliberately change the clocks on their smartphones all the time (e.g., to cheat in mobile games like Candy Crush).

**Can we get perfect accuracy?**
Yes, but it requires insane amounts of money. High-Frequency Trading (HFT) firms are legally required by financial regulations (like MiFID II) to sync all servers to within 100 *microseconds* of UTC to detect "flash crashes" and market manipulation. They achieve this using dedicated physical hardware (GPS antennas bolted to the roof of the datacenter, local Atomic Clocks), Precision Time Protocol (PTP), and intense monitoring. For regular software engineering, this is usually entirely out of reach.

### Relying on Synchronized Clocks
Because networks drop packets, we write robust error handling for them. Surprisingly, developers rarely write error handling for incorrect clocks. 

The problem with bad clocks is that they **fail silently**. If a CPU breaks or a network cable is unplugged, the system loudly crashes. But if an NTP client is misconfigured and the clock slowly drifts into the future, the server will continue operating perfectly fine—it will just silently corrupt data that relies on the time. 
*Conclusion:* If your software requires synchronized clocks to function, you must aggressively monitor the clock offsets between all machines. If any node drifts too far from the rest of the cluster, you must deliberately crash it (declare it dead) to prevent data corruption.

#### Timestamps for Ordering Events (The Danger of LWW)
It is incredibly tempting to use Wall Clocks to answer queries like: *"Two users updated the same record simultaneously, which one happened last?"*

![](figure-9-3.png)

Many systems (like Cassandra) use **Last Write Wins (LWW)** to resolve editing conflicts. When a write occurs, it is tagged with the timestamp of the client's local clock. If two writes conflict, the database simply keeps the one with the highest timestamp and drops the other.

This is a dangerous trap:
1.  **Silent Data Dropping:** Imagine Client A writes $x=1$, but their local clock is accidentally 50ms slow. Client B then comes along 10ms later and writes $x=2$, but their clock is accurate. Even though $x=2$ happened *after* $x=1$ in reality, Client B's timestamp will mathematically be smaller. The database will drop the new write entirely without throwing any errors.
2.  **Violating Causality:** It is entirely possible to send a packet from Server A (Timestamp: 100ms) and have it arrive at Server B (Timestamp: 99ms). Did the packet arrive before it was sent? No, the clocks are just misaligned, but LWW logic will become hopelessly confused.

You cannot just "make NTP better" to fix this. To guarantee perfect event ordering with physical clocks, your NTP synchronization error must be mathematically smaller than your network delay. Because internet delays are unbounded, this is physically impossible.

**The Solution: Logical Clocks**
Instead of using physical quartz oscillators to order events, distributed systems use **Logical Clocks** (like Version Vectors or Lamport Timestamps). 
A Logical Clock is simply an incrementing integer counter. It doesn't care about the time of day or how many seconds have elapsed. It only tracks the relative ordering of events (e.g., Event 45 happened before Event 46). If you need to establish a strict ordering of causality ("Did A happen before B?"), Logical Clocks are the mathematically safe alternative to Wall Clocks.

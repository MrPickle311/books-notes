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

* **Hardware Drift:** The quartz crystal inside a computer naturally drifts depending on its physical temperature. Even with perfect conditions, a standard server drifts about 17 seconds per day without synchronization.

* **Forced Resets:** If a local clock drifts too far (because of drift, or a broken internal battery), NTP will forcibly snap the clock back, making time jump backward.

* **Network Delays:** NTP relies on calculating the network round-trip time to figure out the exact global time. However, as we covered earlier, internet delays are unbounded. If the network experiences massive congestion, NTP's calculations become inaccurate by up to a full second (or the client might just give up entirely).

* **Leap Seconds:** When a leap second is added, an artificial minute occurs that is 61 seconds long. Historically, this has crashed massive global systems (like Reddit and airline booking systems) because developers' code made incorrect assumptions about time. Many major companies now "Smear" the leap second—lying to the clock by running it slightly slower over the entire day to absorb the extra second, rather than forcing a violent jump.

* **Virtual Machine Jumps:** In a VM environment, when the hypervisor pauses a VM to give CPU time to a noisy neighbor, the time-of-day clock appears to freeze. When the VM wakes up 100ms later, the clock "jumps" forward instantaneously.

* **Malicious Users:** If you are building mobile apps or edge devices, you can never trust the client's clock. End-users deliberately change the clocks on their smartphones all the time (e.g., to cheat in mobile games like Candy Crush).

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

1. **Silent Data Dropping:** Imagine Client A writes $x=1$, but their local clock is accidentally 50ms slow. Client B then comes along 10ms later and writes $x=2$, but their clock is accurate. Even though $x=2$ happened *after* $x=1$ in reality, Client B's timestamp will mathematically be smaller. The database will drop the new write entirely without throwing any errors.

2. **Violating Causality:** It is entirely possible to send a packet from Server A (Timestamp: 100ms) and have it arrive at Server B (Timestamp: 99ms). Did the packet arrive before it was sent? No, the clocks are just misaligned, but LWW logic will become hopelessly confused.

You cannot just "make NTP better" to fix this. To guarantee perfect event ordering with physical clocks, your NTP synchronization error must be mathematically smaller than your network delay. Because internet delays are unbounded, this is physically impossible.

**The Solution: Logical Clocks**

Instead of using physical quartz oscillators to order events, distributed systems use **Logical Clocks** (like Version Vectors or Lamport Timestamps).

A Logical Clock is simply an incrementing integer counter. It doesn't care about the time of day or how many seconds have elapsed. It only tracks the relative ordering of events (e.g., Event 45 happened before Event 46). If you need to establish a strict ordering of causality ("Did A happen before B?"), Logical Clocks are the mathematically safe alternative to Wall Clocks.

### Clock Readings with a Confidence Interval

Even if an API lets you read the time down to the nanosecond, that does not mean the time is actually accurate to the nanosecond.

Because of quartz drift and network delays, a server's time-of-day clock is always slightly wrong.

Therefore, it makes no sense to think of a clock reading as a single point in time. You must think of a clock reading as a **Confidence Interval**.

A system shouldn't say *"It is exactly 10.300 seconds."* It should say *"I am 95% confident that the time right now is somewhere between 10.3 and 10.5 seconds."*

Unfortunately, standard APIs (like `System.currentTimeMillis()`) do not expose this uncertainty to the developer. It just hands you a single number and hides the fact that its confidence interval might be as wide as 5 seconds.

However, Google's Spanner database uses a custom API called **TrueTime**, which explicitly exposes the confidence interval.

When you ask TrueTime what time it is, it returns an array of two values: `[earliest, latest]`. Spanner calculates its exact clock drift since its last NTP sync and mathematically guarantees the true current time is somewhere within that interval.

#### Synchronized Clocks for Global Snapshots

Why did Google invent the TrueTime API for Spanner? To generate global Transaction IDs.

To provide Serializable Snapshot Isolation (SSI), databases need to generate monotonically increasing Transaction IDs. In a single-node database, you just use an auto-incrementing counter. But across a distributed database spanning multiple datacenters, coordinating a single global integer counter becomes a massive bottleneck.

Google wanted to use Spanner's local Wall Clocks to generate Transaction IDs instead, but ran into the exact problem described above: what if Server A's clock is 5ms faster than Server B's clock?

Spanner solved this brilliantly by leveraging TrueTime's confidence intervals.

If Transaction 1 produces timestamp interval $A = [A_{earliest}, A_{latest}]$ and Transaction 2 produces $B = [B_{earliest}, B_{latest}]$, Spanner can compare the intervals:

* **No Overlap:** If $A_{latest} < B_{earliest}$, then Spanner mathematically guarantees that Transaction 1 definitively happened before Transaction 2.

* **Overlap:** If the intervals overlap, Spanner is unsure.

To ensure intervals *never* overlap, Spanner does something radical: **It intentionally sleeps.**

Before Spanner commits a write, it calculates the raw TrueTime confidence interval (say, 7 milliseconds). Spanner then forces the write to wait for exactly 7 milliseconds before finally committing.

By deliberately waiting out the uncertainty, Spanner ensures that no future read transaction could ever possibly have an overlapping interval.

*Conclusion:* In order to keep this mandatory waiting time as short as possible, Google installed GPS receivers and Atomic Clocks directly into every Spanner datacenter. This hardware isn't strictly necessary—you could run Spanner on the public internet—but the Atomic Clocks keep the confidence interval under 7ms, which means Spanner transactions only have to pause for 7ms instead of pausing for an entire second.
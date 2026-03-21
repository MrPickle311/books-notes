---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 3: Distributed"
status: pending
---
Everything we have discussed so far assumes that nodes are unreliable, but **Honest**. 
We assume a node might crash, or pause, or experience network drops. But we absolutely trust that when a node finally does send a message, it is telling the truth. It is playing by the rules of the protocol to the best of its knowledge.

But what if a node deliberately lies? What if it intentionally sends a fake fencing token to subvert the system?
This brings us to the **Byzantine Generals Problem**.

A **Byzantine Fault** occurs when a node maliciously malfunctions or deliberately tries to deceive the rest of the cluster. A system is considered "Byzantine Fault-Tolerant" (BFT) if it can mathematically guarantee consensus even when active traitors are infiltrating the network.

**Do we need to worry about Byzantine Faults?**
In standard server-side software engineering: **No.**
In this book, we assume your datacenter is filled with mutually trusting nodes running the same software. Achieving Byzantine Fault Tolerance is extraordinarily expensive and complicated. It typically requires a supermajority of nodes to function correctly (e.g., you would need 4 identical copies of an entire system just to tolerate 1 traitor). 

We only require actual Byzantine Fault Tolerance in two incredibly specific domains:
1.  **Aerospace/Hardware Embedded Systems:** In space, radiation literally flips bits in the RAM, causing the software to behave insanely and unpredictably (which mathematically looks exactly like a lying traitor).
2.  **Cryptocurrencies (Blockchains):** A global network composed entirely of mutually untrusting, anonymous clients who actively want to defraud the system to steal money. Bitcoin is essentially just an algorithm solving the Byzantine Generals problem.

For normal software bugs or hackers compromising your servers, BFT will not save you (because if an attacker hacks Node A, they will just use the same exploit to hack Nodes B, C, and D simultaneously). We rely on standard firewalls, TLS, and access control instead.

#### Weak Forms of Lying
Even though we don't build full Byzantine Fault-Tolerant systems, we do still protect our applications against "weak" forms of lying (hardware glitches, driver bugs, or user errors):
*   **Checksums:** Network packets can get corrupted by bad routers along the way. We use Application-level checksums, TCP checksums, and TLS to detect physically corrupted data.
*   **Input Sanitization:** We assume end-users are malicious liars. We heavily sanitize web inputs to prevent SQL Injection or XSS attacks.
*   **NTP Outlier Detection:** We configure our NTP client to talk to multiple servers. If 4 servers tell us it's 3:00 PM, and 1 server tells us it's 8:00 AM, the NTP client realizes the 5th server is "lying" (misconfigured) and safely ignores it as an outlier.
---
## Related Concepts
* [[Data Intensive Applications]]

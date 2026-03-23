So far, we've established that distributed systems are plagued by partial failures, unreliable networks, wildly inaccurate clocks, and arbitrary process pauses.
Because of these issues, a node in a network can never actually *know* anything for sure. It can only make educated guesses based on the messages it receives.

If a remote node doesn't respond to a ping, there is no physical way to distinguish between "the network cable is unplugged," "the remote node crashed," or "the remote node is currently paused by its garbage collector."
The resulting system borders on the philosophical: What is the "truth" in a system where perception and measurement are entirely unreliable? 

### The Majority Rules (Quorums)
Because a node cannot even trust its own local clock or its own execution thread, a distributed system must never rely on a single node's judgment. 

Imagine an asymmetric network fault where a node can receive messages but cannot send any outgoing messages. The node feels perfectly fine and continues processing work, but from the outside perspective, it is completely silent. After a timeout, the rest of the cluster declares the node dead. The node protests ("I'm still alive!"), but nobody hears it. 
In a distributed system, individual nodes must surrender their autonomy to the cluster. If a **Quorum** (usually an absolute majority of nodes) votes that a node is dead, then that node is legally dead, even if it feels alive inside. The node itself must abide by the quorum's decision and step down.

Voting guarantees safety because there can only ever be one absolute majority in a cluster at any given time, inherently preventing split-brain scenarios.
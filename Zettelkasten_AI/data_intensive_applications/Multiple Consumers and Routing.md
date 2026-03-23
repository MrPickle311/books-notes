If millions of messages are firing into a single topic, a single Consumer server physically cannot process them fast enough.
When you attach *multiple* Consumer servers to the same Topic, you must mathematically decide how the messages are mathematically routed. There are two primary patterns:

![Figure 12-1: Two main patterns for delivering messages to multiple consumers.](data_intensive_applications/figure-12-1.png)
*Figure 12-1: (a) Load balancing routes each message to purely one worker. (b) Fan-out duplicates every message to every receiver.*

1.  **Load Balancing (Shared Subscription):** The broker acts as a round-robin dealer. Message 1 goes to Consumer A. Message 2 goes to Consumer B. Message 3 goes to Consumer C. This is crucial for horizontally scaling out computationally expensive tasks (like video encoding) because each message is delivered to *exactly one* consumer.
2.  **Fan-Out (Broadcast):** The broker acts as a loudspeaker. Message 1 is completely duplicated and delivered to Consumer A, Consumer B, and Consumer C simultaneously. This is the streaming equivalent to having three different Batch Jobs read the exact same input file.

*(Note: Modern systems like Kafka elegantly combine these two. A Single "Consumer Group" acts as a Load Balancer, while multiple different "Consumer Groups" attached to the exact same topic act as a massive Fan-Out).*
Because Consumers can crash in the exact middle of processing a message, how do we guarantee data isn't lost?
Message brokers utilize **Acknowledgments (ACKs)**. The rule is simple: The broker simply refuses to permanently delete the message from the queue until the Consumer sends back an explicit `ACK` network request stating *"I have fully processed this message"*.

If the Consumer severs its TCP connection or times out without sending an `ACK`, the broker assumes the Consumer crashed. To save the data, the broker takes the unacknowledged message and **Redelivers** it to another healthy Consumer server.

### The Reordering Danger
When you combine *Load Balancing* with *Redelivery*, you introduce a massive streaming hazard: **Message Reordering**.

![Figure 12-2: Redelivery causes messages to process out of order.](data_intensive_applications/figure-12-2.png)
*Figure 12-2: Consumer 2 crashes while processing message m3. By the time m3 is redelivered to Consumer 1, Consumer 1 is already processing m4. The final order is scrambled.*

Even if the broker algorithmically tries to guarantee strict ordering, if Consumer 2 crashes while processing `m3`, the broker will immediately pass `m3` to Consumer 1. However, Consumer 1 might have already processed `m4` in the meantime! The order is permanently ruined. If your messages have strict causal dependencies (e.g. `m3` was an "Account Created" event, and `m4` was an "Account Deleted" event), reordering them will crash your system!

### Poison Pills and Dead Letter Queues (DLQ)
What if the Consumer didn't crash because of a hardware fault? What if the message itself is a "Poison Pill" (e.g. a badly malformed JSON file)?
1. Consumer 1 grabs the bad JSON, fails to parse it, and crashes (no ACK sent).
2. The Broker generously redelivers the JSON to Consumer 2.
3. Consumer 2 crashes (no ACK).
4. Redeliver to Consumer 3... and so on forever.

This is a permanent blockage. To solve this, queuing systems implement a **Dead Letter Queue (DLQ)**. If a piece of data is retried too many times and fails, the broker stops trying. It removes the Poison Pill from the active stream and dumps it into a special DLQ database, setting off an alarm so a human engineer can manually inspect the bad code.
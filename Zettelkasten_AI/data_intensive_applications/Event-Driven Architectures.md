Another way encoded data flows between processes is via an *Event-Driven Architecture*. Here, a process sends a "message" or "event", but unlike RPC, the sender **does not wait for the recipient to process it**. 
Furthermore, instead of a direct network connection, the message is sent through an intermediary called a **Message Broker** (or event broker / message queue).

**Advantages of Message Brokers over direct RPC:**
1.  **Buffering:** Acts as a buffer if the recipient is unavailable, overloaded, or dead, improving total system reliability.
2.  **Redelivery:** Automatically redelivers messages to a crashed process to prevent data loss.
3.  **No Service Discovery:** Senders don't need to know the IP address or port of the recipients.
4.  **Fan-out:** Allows the exact same message to be delivered to multiple separate recipients.
5.  **Decoupling:** Logically separates the sender from the recipient. The sender publishes a message and does not care who consumes it.

#### Message Brokers
Historically dominated by commercial suites (TIBCO, IBM WebSphere), the landscape is now dominated by open-source implementations (RabbitMQ, Apache Kafka, NATS, Redpanda) and cloud services (AWS Kinesis, Google Cloud Pub/Sub).

**Message Distribution Patterns:**
1.  **Named Queue:** Multiple consumers listen to a single queue. *One* of them receives the message.
2.  **Named Topic (Pub/Sub):** Multiple subscribers listen to a topic. *All* of them receive a copy of the message.

**Data Encoding in Brokers:**
Message brokers don't enforce data models; a message is just a blob of bytes. Therefore, it's common to use JSON, Protobuf, or Avro and deploy a **Schema Registry** right alongside the broker to manage schema versions. 
*(Note: If a consumer reads a message, mutates it, and republishes it to a new topic, it must be careful to preserve any unknown fields to maintain Forward Compatibility, exactly as warned in the Database section).*

#### Distributed Actor Frameworks
The **Actor Model** is a programming paradigm designed to solve concurrency in a single process without dealing directly with threads, race conditions, or deadlocks.
*   Logic is grouped into "Actors" (e.g. representing a single client or entity). Every actor has its own private, isolated local state.
*   Actors communicate with each other exclusively by sending and receiving asynchronous messages.
*   Since each actor processes strictly one message at a time sequentially, you never have to worry about thread safety or locks.

**Distributed Actors (Akka, Orleans, Erlang/OTP):**
In these frameworks, this exact same message-passing model is used to scale across *multiple machines*.
*   If Actor A and Actor B are on different servers, the framework transparently intercepts the message, encodes it into bytes, sends it over the network, and decodes it on the other side.
*   *Why this works better than RPC:* RPC tries to pretend network calls are perfectly safe local functions (which is a lie). The Actor model natively assumes that messages can be lost anyway, even within a local process. Thus, the fundamental mismatch between local and remote communication is drastically minimized.
*   **Compatibility:** Because distributed actors essentially integrate a message broker directly into the runtime, you still have to worry about Forward and Backward Compatibility (using schemas like Avro/Protobuf) during rolling upgrades when Old-Actor-Nodes are sending messages to New-Actor-Nodes.
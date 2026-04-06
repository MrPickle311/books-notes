### Pattern: Request Pipeline (Patterns of Distributed Systems)

A **Request Pipeline** improves throughput and reduces latency by sending multiple requests on a single connection without waiting for individual responses.

#### Problem: Blocked Capacities in Single-Socket Channels
When using a **Single-Socket Channel**, throughput is often limited because the sender is blocked until a response arrives. If the server uses a **Singular Update Queue**, it can accept multiple requests while processing one, but if the client only sends one-by-one, most of the server's capacity is wasted in network wait-times.

#### Solution: Asynchronous Send and Receive
Clients send multiple requests asynchronously and use a separate thread/channel to process responses as they come in. (Figure 32.1)

```java
// Main sending logic
class SingleSocketChannel {
    public void sendOneWay(RequestOrResponse request) throws IOException {
        var dataStream = new DataOutputStream(socketOutputStream);
        byte[] messageBytes = serialize(request);
        dataStream.writeInt(messageBytes.length);
        dataStream.write(messageBytes);
    }
}

// Background monitoring thread
class ResponseThread extends Thread {
    public void run() {
        while (isRunning) {
            doWork();
        }
    }

    public void doWork() throws IOException {
        RequestOrResponse response = socketChannel.read();
        processResponse(response);
    }
}
```

#### Challenges: Managing In-Flight Requests
Continuously sending requests without feedback can overwhelm the receiver. To mitigate this, a limit on the number of "in-flight" requests is applied.

**Request Limiting Strategy:**
Use a **Blocking Queue** (e.g., with a capacity of 5) to track sent but unacknowledged requests.

```java
class RequestLimitingPipelinedConnection {
    private int maxInflightRequests = 5;
    private Map<InetAddressAndPort, ArrayBlockingQueue<RequestOrResponse>> inflightRequests = new ConcurrentHashMap<>();

    public void send(InetAddressAndPort to, RequestOrResponse request) throws InterruptedException {
        var requestsForAddress = inflightRequests.computeIfAbsent(to, 
            k -> new ArrayBlockingQueue<>(maxInflightRequests));
        
        requestsForAddress.put(request); // Blocks if the queue is full
    }

    private void consume(SocketRequestOrResponse response) {
        var requestsForAddress = inflightRequests.get(response.getAddress());
        var first = requestsForAddress.peek();
        
        // Ensure responses match requests in order
        if (response.getRequest().getCorrelationId() != first.getCorrelationId()) {
            throw new RuntimeException("Responses must be in the same order as requests.");
        }
        requestsForAddress.remove(first); // Make room for more requests
    }
}
```

#### Challenges: Maintaining Ordering
When failures and retries occur, requests may arrive out-of-order at the server.
*   **Raft Solution:** Every request includes the `prevLogIndex`. If it doesn't match the server's current index, the out-of-order request is rejected.
*   **Kafka Solution:** Producers assign a unique identifier and sequence numbers to message batches. The broker rejects any request that violates the expected sequence.

*(Awaiting the final sections of the chapter...)*

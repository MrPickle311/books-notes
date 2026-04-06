### Pattern: Idempotent Receiver (Patterns of Distributed Systems)

An **Idempotent Receiver** uniquely identifies client requests to safely ignore duplicates sent by a client during retries (e.g., after a network timeout or server crash).

#### Problem: Indistinguishable Crashes vs. Network Errors
When a client sends a request and fails to receive a response, it is impossible to determine whether:
1.  The request never reached the server.
2.  The server processed the request but crashed before sending the response.
3.  The response was lost in the network.

If the client retries, a "non-idempotent" server might process the same operation twice (e.g., a bank withdrawal resulting in a double debit).

#### Solution: Client Registration & Caching Responses
The server assigns a unique ID to each client during a **Registration** phase. The server then maintains a "Session" for each client to cache recent responses.

**1. Client Registration**
Before sending data, a client registers itself with the server (typically a **Consistent Core** like Raft). The server can use the current Write-Ahead Log (WAL) index as a unique `clientId`.

```java
// Server-side registration logic
class ReplicatedKVStore {
    private Map<Long, Session> clientSessions = new ConcurrentHashMap<>();
    
    private RegisterClientResponse registerClient(WALEntry walEntry) {
        Long clientId = walEntry.getEntryIndex(); // WAL index as unique ID
        clientSessions.put(clientId, new Session(clock.nanoTime()));
        return new RegisterClientResponse(clientId);
    }
}
```

**2. Request Handling with Sessions**
The **Session** object tracks the last access time and holds a bounded queue of responses for recent client requests.
*   **Duplicate Recognition:** If a request arrives with a `requestNumber` already present in the session's `clientResponses` queue, the server simply returns the cached response.
*   **Safety:** In a Consistent Core, the registration request is replicated via consensus so that session information is not lost during a leader failover.

```java
public class Session {
    private long lastAccessTimestamp;
    private Queue<Response> clientResponses = new ArrayDeque<>();
    private static final int MAX_SAVED_RESPONSES = 5;

    public Session(long lastAccessTimestamp) {
        this.lastAccessTimestamp = lastAccessTimestamp;
    }

    public void addResponse(Response response) {
        if (clientResponses.size() == MAX_SAVED_RESPONSES) {
            clientResponses.remove(); // Remove oldest
        }
        clientResponses.add(response);
    }

    public Optional<Response> getResponse(int requestNumber) {
        return clientResponses.stream()
            .filter(r -> requestNumber == r.getRequestNumber())
            .findFirst();
    }

    public void refresh(long nanoTime) {
        this.lastAccessTimestamp = nanoTime;
    }
}
```

#### Why Sessions are Necessary
By storing the results of the $N$ most recent requests, the server ensures that even if the client retries several times, it will always receive the original outcome of its command. This effectively makes any operation idempotent at the receiver level.

#### Idempotent vs. Non-Idempotent Requests
*   **Naturally Idempotent:** Operations like `Set(key, value)` can be safely repeated without changing the system state beyond the first call.
*   **Non-Idempotent:** Operations like `CreateLease(name)` are problematic. If a server crashes after successfully creating a lease but before responding, a client retry would normally fail (e.g., "Lease already exists"), causing the client to incorrectly believe the operation failed.

**The Fix:** By including a **Request Number** with the `clientId`, the server can detect the retry and return the previously cached success response without attempting to re-execute the logic.

#### Implementation: Registering a Lease
The server stores the response in the client's session immediately after a successful execution.

```java
class ReplicatedKVStore {
    private Response applyRegisterLeaseCommand(WALEntry entry, RegisterLeaseCommand command) {
        try {
            leaseTracker.addLease(command.getName(), command.getTimeout());
            Response success = Response.success(RequestId.RegisterLeaseResponse, entry.getEntryIndex());
            if (command.hasClientId()) {
                Session session = clientSessions.get(command.getClientId());
                session.addResponse(success.withRequestNumber(command.getRequestNumber()));
            }
            return success;
        } catch (DuplicateLeaseException e) {
            // Error handling...
        }
    }
}
```

#### Client-Side: Request Numbering and Retries
The client maintains an incrementing `nextRequestNumber` and implements a robust retry mechanism.

```java
class ConsistentCoreClient {
    AtomicInteger nextRequestNumber = new AtomicInteger(1);
    private static final int MAX_RETRIES = 3;

    public void registerLease(String name, Duration ttl) {
        var request = new RegisterLeaseRequest(clientId, 
            nextRequestNumber.getAndIncrement(), name, ttl.toNanos());
        var serializedRequest = new RequestOrResponse(request, correlationId.getAndIncrement());
        
        var response = sendWithRetries(serializedRequest);
    }

    private RequestOrResponse blockingSendWithRetries(RequestOrResponse request) {
        for (int i = 0; i <= MAX_RETRIES; i++) {
            try {
                return blockingSend(request);
            } catch (NetworkException e) {
                resetConnectionToLeader();
                // Log retry attempt...
            }
        }
        throw new NetworkException("Timed out after " + MAX_RETRIES + " retries");
    }
}
```

#### Server-Side: Early Exit for Duplicate Requests
Before applying any command from the Write-Ahead Log, the server checks the session for a pre-existing response.

```java
class ReplicatedKVStore {
    private Response applyWalEntry(WALEntry walEntry) {
        Command command = deserialize(walEntry);
        if (command.hasClientId()) {
            var session = clientSessions.get(command.getClientId());
            var savedResponse = session.getResponse(command.getRequestNumber());
            if (savedResponse.isPresent()) {
                return savedResponse.get(); // Return cached result
            }
        }
        // Proceed with normal command execution...
    }
}
```

#### Expiring Saved Client Requests
Servers cannot store response caches indefinitely. Multiple strategies exist to prune them:
1.  **Client-Led Pruning:** The client includes the highest request number for which it has successfully received a response. The server then discards all cached responses older than that index.
2.  **In-Flight Limits:** For systems using a **Request Pipeline** (like Apache Kafka), the server only caches a fixed number of recent responses (e.g., `MAX_SAVED_RESPONSES = 5`). Once the limit is hit, the oldest response is removed to make room for the new one.

#### Removing Registered Clients (Session Lifecycle)
Client sessions are maintained to detect duplicate retries during connection failures. If a client process fails and restarts, it typically registers as a *new* client, meaning deduplication is not guaranteed across process restarts.

To prevent memory leaks from inactive clients, the server uses a **Time-To-Live (TTL)** for sessions:
*   **Heartbeats:** Clients must send periodic heartbeats to refresh their session's `lastAccessTimestamp`.
*   **Session Checker:** A background task periodically scans all active sessions and removes those that have exceeded the timeout window.

```java
class ReplicatedKVStore {
    private long sessionCheckingIntervalMs = 10_000; // 10 seconds
    private long sessionTimeoutNanos = 30_000_000_000L; // 30 seconds

    private void startSessionCheckerTask() {
        executor.scheduleWithFixedDelay(this::removeExpiredSession, 
            sessionCheckingIntervalMs, sessionCheckingIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void removeExpiredSession() {
        long now = System.nanoTime();
        for (Long clientId : clientSessions.keySet()) {
            Session session = clientSessions.get(clientId);
            if (now - session.getLastAccessTimestamp() > sessionTimeoutNanos) {
                clientSessions.remove(clientId); // Session expired
            }
        }
    }
}
```

#### At-Most-Once, At-Least-Once, and Exactly-Once Actions
The interaction between client retries and server idempotency determines the processing guarantee:

| Guarantee | Client Behavior | Server Behavior | Result |
| :--- | :--- | :--- | :--- |
| **At-Most-Once** | No Retry | Default | Request may fail or happen once. |
| **At-Least-Once**| Retries | Default | Request happens at least once, potentially many. |
| **Exactly-Once** | Retries | **Idempotent Receiver** | Request happens exactly once despite retries. |

To achieve **Exactly-Once** semantics, both client retries and idempotent receivers are required.

*(Awaiting the final sections of the chapter...)*

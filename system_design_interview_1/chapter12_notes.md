### Chapter 12: Design a Chat System - Summary

This chapter details the design of a scalable chat system supporting both one-on-one and group messaging, targeting 50 million daily active users (DAU). The design process starts with defining the core features: low-latency 1-on-1 and small group chats (up to 100 members), online presence indicators, multi-device support, and push notifications, with a requirement to store chat history forever.

The high-level architecture centers on a persistent connection model. After evaluating polling, long polling, and WebSockets, **WebSocket** is chosen as the primary communication protocol for its bidirectional and efficient nature. The system is logically divided into **stateless services** (for user management, login, etc.), a **stateful chat service** (managing persistent WebSocket connections), and third-party integrations like push notifications.

For storage, a **key-value store** (like Cassandra or HBase) is selected for the massive volume of chat history data due to its horizontal scalability and low latency. The data model is designed with a unique, time-sortable `message_id` to ensure message ordering.

The deep dive explores critical components:
1.  **Service Discovery** (using Zookeeper) to intelligently assign users to the best chat server.
2.  **Message Flows**, detailing how messages are sent, stored, and synchronized across multiple devices using a `cur_max_message_id` tracker on each client. For group chat, a fanout-on-write approach is used where a message is copied to each group member's message queue (inbox).
3.  **Online Presence**, managed by dedicated presence servers. A heartbeat mechanism handles temporary user disconnections gracefully, and a pub-sub model efficiently fans out status updates to a user's friends.

The chapter concludes by touching upon further enhancements like handling media files, end-to-end encryption, and error handling strategies.

---

### 1. Requirements and Scope

*   **Chat Types:** 1-on-1 and group chat.
*   **Group Size:** Max 100 members.
*   **Scale:** 50 million Daily Active Users (DAU).
*   **Core Features:**
    *   Text messages only (< 100,000 characters).
    *   Online presence indicator.
    *   Multi-device support.
    *   Push notifications.
*   **Data Retention:** Store chat history forever.
*   **Security:** End-to-end encryption is not a hard requirement initially.

![Figure 12-1: Popular chat apps](figure-12-1.png)

---

### 2. High-Level Design & Communication Protocols

The core of a chat system is the communication between clients and the server. Clients do not talk to each other directly.

![Figure 12-2: Client-server communication](figure-12-2.png)

#### Choosing a Protocol

While the sender can use standard HTTP, the receiver needs a way to get messages from the server in real-time.

*   **Polling:** Client repeatedly asks the server for new messages. Highly inefficient.
    ![Figure 12-3: Polling](figure-12-3.png)
*   **Long Polling:** Client holds a connection open until the server has a message, then immediately reconnects. Better, but still has issues with server state and efficiency.
    ![Figure 12-4: Long polling](figure-12-4.png)
*   **WebSocket (Chosen Solution):** A persistent, bidirectional connection initiated by the client. It allows the server to push messages to the client at any time with low latency. It's the standard for modern real-time applications.
    ![Figure 12-5: WebSocket](figure-12-5.png)

Using WebSockets for both sending and receiving simplifies the overall design.

![Figure 12-6: WebSocket for sender and receiver](figure-12-6.png)

#### System Architecture

The system is broken down into three main categories:

![Figure 12-7: High-level architecture components](figure-12-7.png)

1.  **Stateless Services:** Standard HTTP-based microservices for login, user profiles, etc. Sit behind a load balancer.
2.  **Stateful Service (Chat Service):** The core of the system. Manages persistent WebSocket connections from clients. A client stays connected to a single chat server.
3.  **Third-Party Integration:** Primarily for push notifications (e.g., APNS, FCM) to alert offline users.

This leads to a more detailed high-level design:

![Figure 12-8: High-level design](figure-12-8.png)

---

### 3. Storage Layer

*   **Generic Data (User profiles, settings):** Stored in a standard relational database.
*   **Chat History Data:** The volume is enormous (e.g., 60 billion messages/day for Facebook/WhatsApp).
    *   **Access Pattern:** Recent chats are read frequently; older chats are accessed infrequently but require random access for features like search.
    *   **Chosen Storage:** **Key-Value Store** (like Cassandra, HBase).
        *   **Why?** Easy horizontal scaling, low latency, handles "long tail" data better than SQL.

#### Data Models

*   **1-on-1 Chat Table:**
    *   `primary_key`: `message_id` (must be unique and time-sortable).
    *   Other columns: `message_from_id`, `message_to_id`, `content`, `created_at`.
    ![Figure 12-9: Message table for 1-on-1 chat](figure-12-9.png)

*   **Group Chat Table:**
    *   `primary_key`: `(channel_id, message_id)`.
    *   `channel_id` is the partition key, as all queries are scoped to a channel.
    ![Figure 12-10: Message table for group chat](figure-12-10.png)

*   **`message_id` Generation:** Must be unique and time-sortable. Can be generated by a global service like Snowflake or a local generator (unique only within a chat/channel).

---

### 4. Deep Dive

#### Service Discovery

*   **Goal:** To find the best chat server for a client to connect to, based on location, server load, etc.
*   **How it works:**
    1.  Client logs in to a stateless API server.
    2.  After authentication, the API server queries the Service Discovery service (e.g., Zookeeper).
    3.  Service Discovery returns the hostname of the least-loaded chat server.
    4.  The client then establishes a WebSocket connection directly to that chat server.

![Figure 12-11: Service discovery flow](figure-12-11.png)

#### Message Flows

*   **1-on-1 Chat Flow:**
    1.  User A sends a message to Chat Server 1.
    2.  Server 1 gets a new `message_id`.
    3.  The message is sent to a message sync queue and persisted in the KV store.
    4.  The system determines which server User B is connected to (Chat Server 2).
    5.  The message is forwarded to Chat Server 2, which pushes it to User B via WebSocket.
    6.  If User B is offline, a push notification is sent instead.
    ![Figure 12-12: 1-on-1 chat flow](figure-12-12.png)

*   **Multi-Device Message Synchronization:**
    *   Each client device (phone, laptop) maintains a `cur_max_message_id` variable.
    *   When a client comes online, it queries the KV store for any messages where its `user_id` is the recipient and the `message_id` is greater than its `cur_max_message_id`.
    ![Figure 12-13: Message synchronization](figure-12-13.png)

*   **Group Chat Flow:**
    *   **Write Path:** When User A sends a message to a group, the server retrieves the member list and writes a copy of the message to the message sync queue (inbox) for **each member**.
    *   **Read Path:** Each user simply fetches messages from their own inbox, which contains a mix of 1-on-1 and group messages. This design is simple and works well for small groups.
    ![Figure 12-14: Group chat flow - sender side](figure-12-14.png)
    ![Figure 12-15: Group chat flow - recipient side](figure-12-15.png)

#### Online Presence

*   **User Login/Logout:** When a user connects/disconnects their WebSocket, their status (`online`/`offline`) and `last_active_at` timestamp are updated in the KV store.
    ![Figure 12-16: User login and presence](figure-12-16.png)
    ![Figure 12-17: User logout and presence](figure-12-17.png)

*   **Handling Disconnections (Heartbeating):**
    *   A client periodically sends a lightweight heartbeat signal to the presence server.
    *   If the server doesn't receive a heartbeat for a certain period (e.g., 30 seconds), it marks the user as offline. This prevents flicker from brief, temporary network drops.
    ![Figure 12-18: Disconnection handling with heartbeat](figure-12-18.png)

*   **Status Fanout:**
    *   A **publish-subscribe** model is used.
    *   When User A's status changes, the presence server publishes this event to channels subscribed to by A's friends (e.g., `channel-A-B`, `channel-A-C`).
    *   Friends receive the status update in real-time over their WebSocket connection.
    ![Figure 12-19: Online status fanout](figure-12-19.png)

---

### 5. Wrap-up and Further Considerations

*   **Media Files:** To support photos/videos, you would need:
    *   Compression on the client-side.
    *   A cloud storage service like Amazon S3 to store the files.
    *   Thumbnails for previews.
*   **End-to-End Encryption:** Requires client-side logic to encrypt/decrypt messages. Only the sender and receiver can read the content.
*   **Error Handling:** If a chat server fails, Service Discovery will route its connected users to new servers upon reconnection. A message resend mechanism (retries with a queue) is needed to handle transient failures.
*   **Caching:** Caching messages and user data on the client device is crucial to reduce network traffic and improve perceived performance.

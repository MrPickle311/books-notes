---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 4: Derived Data"
status: pending
---
At a high level, the dataflow systems we've explored provide a mechanism to constantly compute and update derived datasets. The entire journey of a piece of data can be split into two fundamental halves:

1.  **The Write Path (Eager Evaluation):** The journey data takes from the moment the user makes a change until it is fully processed through the streams and mathematically materialized into various derived caches ready to be served. This work happens *eagerly*, ahead of time, regardless of whether anyone ever actually asks to see it.
2.  **The Read Path (Lazy Evaluation):** The journey the data takes from the derived cache to the screen of the final end-user who requested it. This work happens *lazily*, occurring exactly at the millisecond the user asks for it.

The **Derived Dataset** (e.g., the Search Index or the Redis Cache) is specifically the exact border wall where the Write Path finally collides with the Read Path!

![Figure 13-1: In a search index, the Write Path meets the Read Path.](data_intensive_applications/figure-13-1.png)
*Figure 13-1: The search index acts as the exact boundary where background precomputation (Write Path) meets on-demand user queries (Read Path).*

### Materialized Views and Caching (Shifting the Boundary)
Architecting a database system simply involves negotiating *where* to draw the boundary between the Write Path and the Read Path. 
Consider a Search Engine:
*   **Maximum Read-Path Work:** You do not build an index at all. When a user searches for a term, your system must literally `grep` scan scanning every document on earth to find it. The Write Path is instantly fast (you just save the file), but the Read Path is brutally slow.
*   **Maximum Write-Path Work:** You attempt to pre-compute the full search results for every single possible theoretical keyword search on earth. The Read Path is instantly fast, but the Write Path is mathematically impossible (exponential permutations).
*   **The Cache / Materialized View Compromise:** You pre-calculate the exact search results for the Top 1,000 most common searches (the Write Path) and store them in a Cache. You let the other unique searches hit the standard index (the Read Path). 

The entire purpose of any cache, index, or materialized view is to intentionally force your backend systems to work vastly harder on the Write Path in order to save precious milliseconds on the Read Path when the user finally requests it. We saw this previously in the Twitter Home Timeline cache. 

### Stateful, Offline-Capable Clients
We can extend this philosophy of "Shifting the Boundary" all the way to the user's physical cell phone!

Historically, web browsers were completely stateless clients. If you lost WiFi, the website completely stopped working because every click required a network round-trip. 
Today, modern Single-Page Applications (SPAs) and highly robust Mobile Apps rely on a radically different architecture: **Local-First Software**.
Instead of forcing the app to wait for a 500-millisecond REST API call to fetch data, we literally shift a massive replica of the Cloud Database directly onto the phone's internal storage! 

This achieves two massive paradigm shifts:
1.  **Offline Capability:** The user interface reacts instantly to local taps, perfectly functioning inside a subway with zero network connection. It seamlessly syncs the data stream to the cloud lazily in the background when the phone catches a WiFi signal.
2.  **The Ultimate Cache:** The local SQLite database running physically inside the user's iPhone is effectively the absolute furthest edge of the *Derived Dataset* boundary. The App's UI pixels on the screen are merely a materialized view reacting to the phone's local database replica!

### Pushing State Changes to Clients
Historically, browsers only ever downloaded data once. If a user was looking at an article and the server updated it, the browser would never know unless the user explicitly hit "Refresh". 
The browser's state was essentially a *Stale Cache* that had to be manually polled.

Modern protocols like **WebSockets** and **Server-Sent Events (SSE)** completely change this. They hold an open TCP connection to the browser, allowing the server to proactively *push* database changes directly into the user's screen without them clicking refresh.
By doing this, we extend the "Write Path" all the way out of the datacenter and directly into the end-user's physical device!

If the user's phone temporarily loses cellular connection, it simply behaves identically to a Kafka Consumer that crashed: when the phone reconnects to WiFi, it simply tells the server its last known "Offset" and safely downloads all the events it missed while in the tunnel.

### End-to-End Event Streams
Modern frontend Javascript frameworks (like React, Vue, and Elm) are natively built to react to state changes. 
Combining "Server-Sent Events" with "React" creates an absolutely beautiful **End-to-End Event Pipeline**:
1. User A clicks a button.
2. The Database writes it to the Log.
3. The Stream Processor derives the new Cache.
4. The WebSocket pushes the new Cache to User B's laptop.
5. User B's React framework automatically updates the DOM pixels on their screen.

This entire pipeline happens in less than a second, creating a spreadsheet-like dataflow across the entire planet. Real-time apps (like Slack or Multiplayer Games) already do this, so why don't we build *all* applications this way?

Because the legacy "Stateless Request/Response" paradigm is still deeply baked into our libraries and languages. Transitioning the entire global software industry from synchronous RPC calls to asynchronous Publish/Subscribe dataflows is a massive paradigm shift that will take years of effort.

### Reads are Events Too
Throughout the book, we've treated **Writes** as immutable events that flow through a stream, while treating **Reads** as ephemeral network requests that hit a database and are immediately forgotten.

But what if we treated *Reads* as events too?
Imagine routing both your Write Stream and your Read Stream into the same stream processor! The processor simply performs a **Stream-Table Join**: it takes the Read Event, joins it with the current State, and emits the query result.
In this paradigm, a one-off `SELECT` query is just a transient stream-join that forgets the user immediately. A `Subscribe` query is simply a persistent stream-join that remembers the user and keeps feeding them results.

Recording every single user "Read" into an immutable disk log provides immense analytical value. In an eCommerce store, the only way to know *why* a customer abandoned their cart is to look at exactly what Inventory Status and Shipping Date was rendering on their screen at the exact second they clicked away. Capturing causality requires logging both what the user did (writes) and what the user saw (reads).

### Multi-Shard Data Processing
Treating Reads as a stream of events seems like overkill for a standard single-database query. However, it becomes incredibly powerful when performing massively distributed joins.
Imagine a Fraud Prevention system: to determine if a checkout event is fraudulent, the system must check the user's IP reputation, Email reputation, and Billing Address reputation. Each of these three datasets is massively sharded across different clusters.
Instead of making synchronous RPC calls to three different sharded databases, you can simply feed the "Checkout Event" into a Stream Processor, which automatically routes and joins the event across the differently sharded reputation streams! 
---
## Related Concepts
* [[Data Intensive Applications]]

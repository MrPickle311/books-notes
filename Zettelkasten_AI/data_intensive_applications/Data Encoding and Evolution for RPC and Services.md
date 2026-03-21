---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 2: Internals"
status: pending
---
Unlike databases (where data outlives code and you need massive forward & backward compatibility), dataflow across services allows for a simplifying assumption: **You can assume servers will be updated first, and clients will be updated second.**

Therefore, in an RPC environment:
*   You only need **Backward Compatibility** on requests (Server can accept old client requests).
*   You only need **Forward Compatibility** on responses (Client can gracefully ignore new data from the server's response).

The exact rules follow whatever encoding method is used (Protobuf, Avro, JSON).

*   **REST/JSON API Evolution:** Adding optional request parameters, or adding new fields to a response object, usually maintains compatibility perfectly.

**The Public API Versioning Problem:**
If your RPC/API crosses organizational boundaries (e.g. a public API), you simply cannot force your clients to upgrade. Compatibility must be maintained indefinitely. If a breaking change is inevitable, the provider must host multiple versions side-by-side. 
There is no universal agreement on how REST API versioning should occur. The three most common approaches are:
1.  Version number strictly embedded in the URL (e.g. `/v1/users`).
2.  Version passed in the HTTP `Accept` header.
3.  Configured via API Key: A client's API key locks them into the version they had when they signed up, until they manually upgrade via an admin dashboard.
---
## Related Concepts
* [[Data Intensive Applications]]

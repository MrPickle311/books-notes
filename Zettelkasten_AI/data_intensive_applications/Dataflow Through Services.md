---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 2: Internals"
status: pending
---
When processes communicate over a network, the most common arrangement is **clients and servers**. The servers expose an API, known as a **service**.
*   **Encapsulation:** Unlike databases which allow arbitrary SQL queries, services expose predetermined APIs that lock down inputs and outputs based on business logic. This allows fine-grained restriction on what clients can do.
*   **Microservices Architecture:** A key goal is to make services independently deployable and evolvable by separate teams. This implies that old and new versions of client and server code will be running simultaneously across the network. Thus, data encodings across service APIs must be heavily backward and forward compatible.

#### Web Services
When HTTP is the underlying transport protocol for a service, it is called a "web service". Web services are not just for the public web; they are used in:
1.  **Client-to-Server:** User devices (mobile/web apps) making requests over the public internet.
2.  **Service-to-Service (Intra-organization):** Microservices talking to each other within the same private datacenter.
3.  **Service-to-Service (Inter-organization):** Public APIs exchanging data between different companies (e.g., Stripe, OAuth).

**REST vs RPC**
Two main design philosophies dominate web services: **REST** and **RPC**.

**REST (Representational State Transfer)**
*   A design philosophy built intensely upon the native principles of HTTP.
*   It emphasizes simple data formats (like JSON), using URLs to identify resources, and leveraging built-in HTTP features for cache control, authentication, and content-type negotiation. An API following these rules is *RESTful*.

**Interface Definition Languages (IDLs) & Frameworks**
To document and evolve APIs, developers use IDLs:
*   **OpenAPI (Swagger):** The standard IDL for RESTful web services sending/receiving JSON.
*   **gRPC:** The standard IDL for services sending/receiving Protocol Buffers.
Using an IDL allows you to automatically generate API documentation, client SDKs in various languages, and base server scaffolding (which you then fill in using frameworks like Spring Boot or FastAPI).

#### The Problems with Remote Procedure Calls (RPCs)
RPC is an older model (originating in the 1970s) that attempts to make a network request look *exactly* like calling a local function in your programming language (location transparency). Successors included EJB, RMI, DCOM, CORBA, and SOAP.

However, trying to make a network call look like a local function is **fundamentally flawed** because they are radically different things:
1.  **Unpredictability:** Local functions succeed or fail predictably. Network requests are entirely unpredictable (responses drop, remote machines crash, switches fail).
2.  **Ambiguity on Timeout:** If a local function hangs, you know. If a network request times out, you have no idea if the remote service processed your request before the connection died, or if it never received the request at all.
3.  **Retries and Idempotence:** If you retry a local function, it just runs. If you retry a network request because the *response* was dropped, the server will execute the action multiple times (e.g., double-charging a credit card) unless the API is built with a deduplication mechanism (*idempotence*).
4.  **Variable Latency:** A local function is uniformly fast. A network request fluctuates wildly depending on internet congestion.
5.  **Pass by Reference:** You can pass a large object pointer to a local function efficiently. Over a network, all parameters must be painstakingly encoded into a byte sequence.
6.  **Data Type Translation:** Since client and server might be written in completely different languages, the RPC framework has to translate types under the hood (which breaks down on edge cases, like JS 64-bit integers and floats).

*Conclusion:* A remote service is a fundamentally different beast than a local function. REST's appeal is largely that its design inherently acknowledges that network state transfer is a distinct, explicit process, rather than trying to hide it behind a fake local function call.

---
## Related Concepts
* [[Data Intensive Applications]]

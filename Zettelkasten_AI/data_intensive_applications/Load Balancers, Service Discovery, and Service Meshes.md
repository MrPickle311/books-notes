---
aliases:
tags:
  - dataintensive
  - dataintensiveapplications
source_book: "Designing Data-Intensive Applications"
topic_layer: "Layer 2: Internals"
status: pending
---
For a client to communicate with a server, it must know the server's IP address and port (**Service Discovery**). Hardcoding this is fragile (servers crash, move, or get overloaded). To guarantee high availability, multiple instances of a service are run, and requests are spread across them via **Load Balancing**:

1.  **Hardware Load Balancers:** Physical data-center appliances. Clients connect to a single IP, and the appliance routes it to healthy downstream servers.
2.  **Software Load Balancers:** Applications (like Nginx or HAProxy) running on standard commodity machines, functioning similarly to hardware balancers.
3.  **DNS Load Balancing:** The client's network layer resolves a domain name into a list of multiple IPs and picks one. *Drawback:* DNS is heavily cached and propagates slowly, meaning clients might try to hit old/dead IP addresses if instances change too frequently.
4.  **Service Discovery Systems:** Central registry databases (like ZooKeeper or etcd). New service instances register their IP/port on startup and send continuous "heartbeats." If they crash, the registry delists them. Clients query the registry dynamically before connecting, making it far superior to DNS for highly dynamic environments.
5.  **Service Meshes:** A hyper-sophisticated combination of load balancing and service discovery (e.g., Istio, Linkerd). Often deployed as "sidecar" containers sitting directly next to both the client and the server. The client talks to its local sidecar -> which intelligently routes and encrypts the traffic to the server's sidecar -> which passes it to the server. This abstracts away TLS/SSL logic entirely and provides massive real-time network observability.
---
## Related Concepts
* [[Data Intensive Applications]]

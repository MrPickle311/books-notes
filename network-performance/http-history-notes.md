# HTTP: Brief History — Quick Reference Notes

*Source: High Performance Browser Networking, Ch.9 (Ilya Grigorik)*

## HTTP/0.9 (1991) — "The One-Line Protocol"

- Created by **Tim Berners-Lee**, designed with extreme simplicity to drive adoption of the Web.
- Key traits:
  - Request = single line: `GET /path`
  - No headers, no status codes, no metadata.
  - Response = raw HTML only.
  - **New TCP connection per request**, closed immediately after transfer.
- No versioning, no content-type — it could only serve hypertext.

**Interview soundbite:** "HTTP/0.9 was a bare-bones request/response protocol — just a GET line and an HTML response, one connection per request."

---

## HTTP/1.0 (1996) — "Rapid Growth, Informational RFC"

- Published as **RFC 1945** (May 1996) — *informational only*, not a real standard; it documented existing common practice from many independent implementations (1991–1995 "Internet boom": Mosaic, Netscape, W3C/HTTP-WG formed).
- New capabilities:
  - **Status line** in responses (e.g., `200 OK`)
  - **Headers** for both request and response (metadata)
  - Support for **any content type**, not just HTML → HTTP became a "hypermedia transport," not just "hypertext transfer" (name stuck anyway)
  - Basics of content encoding, charsets, multi-part types, auth, caching, proxying
- Still **closes the TCP connection after every request** → major performance cost (new handshake + slow-start per request).

**Interview soundbite:** "HTTP/1.0 added headers, status codes, and support for arbitrary content types, but still required a new TCP connection per request — expensive due to handshake + slow start."

---

## HTTP/1.1 (1997/1999) — First Official Internet Standard

- Standardized by IETF: **RFC 2068** (Jan 1997) → revised as **RFC 2616** (June 1999).
- Major performance features introduced:
  - **Persistent connections (keepalive) by default** — connection stays open unless `Connection: close` is sent. (Keepalive was backported to 1.0 via `Connection: Keep-Alive` header.)
  - **Chunked transfer encoding** — send response in chunks without knowing total length upfront.
  - **Byte-range requests** — request partial content (e.g., resume downloads).
  - **Additional caching mechanisms** (e.g., `Cache-Control`, `Expires`, `ETag`).
  - **Request pipelining** (sending multiple requests without waiting for each response — though rarely used in practice due to head-of-line blocking issues).
  - Content/language/charset negotiation, cookies, and many more headers.
- This is essentially the **HTTP/1.1 still widely used today**.

**Interview soundbite:** "HTTP/1.1 is the real standard (RFC 2616) — its headline feature is persistent/keepalive connections, plus chunked encoding, byte-range requests, caching headers, and pipelining."

---

## HTTP/2 — "Improving Transport Performance"

- Motivated by growing demands (real-time responsiveness, heavier web apps) that HTTP/1.1 couldn't meet without changes.
- **HTTPbis working group chartered HTTP/2 effort in early 2012.**
- Critical point: **HTTP/2 changes the framing/transport layer only — NOT the semantics.**
  - Same headers, same methods, same status codes, same use cases.
  - Goal: lower latency + higher throughput via a new binary framing in ordered, bidirectional streams over (primarily) TCP.
  - Existing sites/apps work over HTTP/2 **without any application changes** — it's a transparent upgrade at the server/protocol level.

**Interview soundbite:** "HTTP/2 doesn't change HTTP semantics — same headers/methods/status codes — it changes the wire format/framing to fix HTTP/1.1's performance problems (e.g., head-of-line blocking from one request per connection round-trip)."

---

## Quick Comparison Table

| Version | Year | Status | Connection Model | Headers/Status | Content Type |
|---|---|---|---|---|---|
| 0.9 | 1991 | Prototype (unofficial) | New TCP conn per request | None | HTML only |
| 1.0 | 1996 | RFC 1945 (informational) | New TCP conn per request | Yes | Any |
| 1.1 | 1997/1999 | RFC 2068 → RFC 2616 (standard) | Persistent/keepalive by default | Yes, extensive | Any |
| 2 | ~2015 (spec work started 2012) | Standard | Multiplexed streams over single conn | Same as 1.1 (semantics unchanged) | Any |

---

## Things worth remembering for interviews

1. **"Hypertext Transfer Protocol" is a misnomer** — since HTTP/1.0 it transfers any content type, so it's really a "hypermedia transport."
2. HTTP/1.0 was never a *formal* standard (just documented common practice); HTTP/1.1 was the first real IETF standard.
3. Persistent connections vs. pipelining vs. multiplexing — know the distinction:
   - **Persistent (1.1):** reuse one TCP connection sequentially for multiple requests.
   - **Pipelining (1.1):** send multiple requests without waiting for responses, but responses must still return in order (head-of-line blocking).
   - **Multiplexing (2.0):** multiple requests/responses interleaved concurrently over one connection, solving head-of-line blocking at the HTTP layer.
4. HTTP/2 is a transport-layer/framing change, not a semantic one — this is a common interview trick question ("does HTTP/2 add new headers/methods?" → No).

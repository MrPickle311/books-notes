# Primer on Web Performance — Quick Reference Notes

*Source: High Performance Browser Networking, Ch.10 (Ilya Grigorik)*

## 1. Three Eras of the Web (and why it matters for perf)

| Era | What it is | Perf metric |
|---|---|---|
| **Hypertext document** | Plain text + hyperlinks (original Web). Matches HTTP/0.9: 1 doc, 1 TCP conn, close. | Single request time |
| **Web page** | HTML + images/audio + richer layout (HTTP/1.0 added headers; HTTP/1.1 added keepalive/caching). Multiple TCP connections now involved. | **PLT — Page Load Time** ("time to `onload` event") |
| **Web application** | JS + DHTML + AJAX → interactive, complex dependency graph of scripts/styles/markup. | Custom, app-specific milestones: time-to-first-interaction, engagement/conversion, etc. |

**Key point:** PLT is no longer sufficient for modern apps — you need app-specific benchmarks (first interaction, key user actions, conversion rate) tied to business goals.

> In plain terms, PLT is often described simply as "the time until the loading spinner stops spinning."

---

## 2. DOM, CSSOM & JavaScript — the rendering pipeline

The parsing of the HTML document constructs the Document Object Model (DOM), while the CSS Object Model (CSSOM) is built in parallel from stylesheet rules; the two combine into the "render tree," which the browser uses to perform layout and paint.

![Figure 10-1. Browser processing pipeline: HTML, CSS, and JavaScript|562](https://hpbn.co/assets/diagrams/084666d979c7b1507df8c538f5557ac9.svg)

**The critical dependency chain (interview gold):**
- A synchronous `doc.write` from a script can block DOM parsing and construction.
- Scripts querying computed styles mean JavaScript can also block on CSS.
- As a result, DOM construction cannot proceed until JavaScript executes, and JavaScript execution cannot proceed until CSSOM is available.

**→ This is *why* "CSS at top, JS at bottom" is best practice:** rendering and script execution are blocked on stylesheets, so get CSS down early.

---

## 3. Anatomy of a Modern Web App (HTTP Archive data, early 2013)

An average web application was composed of 90 requests, fetched from 15 hosts, with 1,311 KB total transfer size — broken down as HTML: 10 requests/52 KB, Images: 55 requests/812 KB, JavaScript: 15 requests/216 KB, CSS: 5 requests/36 KB, Other: 5 requests/195 KB.

![Figure 10-2. Average transfer size and number of requests (HTTP Archive)|682](https://hpbn.co/assets/diagrams/0b2435f40a21288f26b55208d5a8c5b1.svg)

Trend: these numbers only ever grow. Unlike desktop apps, which pay an install cost once, web apps effectively re-run their "installation" (resource downloads, DOM/CSSOM construction, JS execution) on every single visit.

---

## 4. Human Perception & Performance Budgets

Reaction-time constants stay the same regardless of device — this is the basis for perf budgets:

| Delay | User perception |
|---|---|
| 0–100 ms | Instant |
| 100–300 ms | Small perceptible delay |
| 300–1,000 ms | "Machine is working" |
| 1,000+ ms | Likely mental context switch |
| 10,000+ ms | Task abandoned |

**Rule of thumb:** render pages, or at minimum provide visual feedback, in under 250 milliseconds to keep users engaged.

### Dollars and cents (great stats to quote in interviews)
- A 2,000 ms delay on Bing search pages decreased per-user revenue by 4.3%.
- An Aberdeen study of 160+ organizations found that an extra one-second delay in page load time led to a 7% loss in conversions, 11% fewer page views, and a 16% decrease in customer satisfaction.

---

## 5. Resource Waterfall & Connection View

Every HTTP request breaks into distinct stages: **DNS resolution → TCP handshake → TLS negotiation (if any) → request dispatch → content download.**

*(Tool note: the examples below use **WebPageTest** — a free, open-source tool that runs real browser tests from multiple geographic locations and produces waterfall/connection views like the ones below.)*

![Figure 10-3. Components of an HTTP request (WebPageTest)](https://hpbn.co/assets/diagrams/adaebeaa2cbc89c09666a0171e7344f9.png)

**Yahoo! homepage case study (WebPageTest, 2013):**
- The Yahoo! homepage took 683 ms to download, and over 200 ms — about 30% of total latency — was spent waiting on the network.
- Loading the full homepage required 52 resources from 30 different hosts, totaling 486 KB.

![Figure 10-4. Yahoo.com resource waterfall (WebPageTest, March 2013)](https://hpbn.co/assets/diagrams/f9504a4dede2eb3d3781ec4baab96e93.png)

**Key waterfall insights:**
- HTML parsing is incremental, so the browser discovers and dispatches new resource requests in parallel while the document is still downloading — this creates the "waterfall effect," and page markup structure largely determines request scheduling.
- "Start Render" and "Document Complete" typically fire well before all resources finish loading — the user can interact while secondary content (ads, widgets) fills in later.

**Connection view** (per-TCP-connection, not per-request):

![Figure 10-5. Yahoo.com connection view (WebPageTest, March 2013)](https://hpbn.co/assets/diagrams/8696009a007572bf2da042cb6cef3ec1.png)

- The Yahoo! example required 15 DNS lookups and 30 TCP handshakes, with most connection time spent waiting for the first byte rather than downloading data.
- Bandwidth utilization stayed very low throughout — bandwidth is not the limiting factor for most web apps; network round-trip latency is the real bottleneck.

> **Note:** "Front-end performance" (waterfall optimization) is a misleading name — back-end/server response time and network latency matter just as much, since nothing can be parsed/executed while blocked on the network.

---

## 6. Performance Pillars: Computing, Rendering, Networking

Every web app is doing three things, continuously:
1. **Fetching resources** (networking)
2. **Page layout & rendering**
3. **JavaScript execution**

**Critical fact (great interview detail):** rendering and JS execution are **single-threaded and interleaved** — the browser cannot render and run script at the same time, and it cannot concurrently modify the DOM from multiple places. That's *why* long-running JavaScript blocks the page from rendering/responding.

**The punchline:** even a perfectly optimized rendering pipeline and lightning-fast JS are worthless if the browser is stuck waiting on the network — which is exactly the setup for the next finding.

---

## 7. Bandwidth vs. Latency — the big finding

- Streaming HD video is bandwidth-limited, but loading and rendering a typical page like Yahoo!'s homepage is latency-limited.

![Figure 10-6. Page load time vs. bandwidth and latency|670](https://hpbn.co/assets/diagrams/940cb8cfbb433a04b05e15b4868cb8e3.svg)

Mike Belshe's study (this became the motivation for **SPDY → HTTP/2**):
- Increasing bandwidth from 1 to 2 Mbps nearly halved page load time, but gains diminished quickly afterward — going from 5 Mbps to 10 Mbps yielded only about a 5% improvement.
- By contrast, every 20 ms improvement in latency produced a roughly linear improvement in page load time.

**Interview soundbite:** "Past ~5 Mbps, more bandwidth barely helps page load time — RTT/latency is the real bottleneck, which is exactly why SPDY/HTTP/2 focused on reducing round trips (multiplexing, header compression) instead of raw throughput."

---

## 8. Synthetic Testing vs. Real User Monitoring (RUM)

| | Synthetic Testing | RUM |
|---|---|---|
| What | Controlled, scripted tests (local builds, staging load tests, geo-distributed monitors) | Actual measurements from real users' browsers |
| Strength | Reproducible; great for catching regressions; can set a perf "budget" and alarm on it | Captures real-world diversity |
| Weakness | Doesn't capture real-world variance | Needs instrumentation & scale |

Sources of real-world variance synthetic testing misses: scenario/navigation pattern selection, browser cache state, intermediary proxies/caches, hardware diversity, browser version diversity, and constantly changing connectivity.

### Navigation Timing API
The W3C Navigation Timing API exposes high-precision (microsecond) timers like DNS and TCP connect time via a standardized `performance.timing` object in the browser — supported in IE9+, Chrome 6+, and Firefox 7+ as of early 2013 (notably not Safari/Opera at the time).

![Figure 10-7. User-specific performance timers exposed by Navigation Timing|691](https://hpbn.co/assets/diagrams/54af0f14aaabe6664274d81d60e38d40.svg)

### Analyzing RUM data
**Rule: never trust averages on skewed/multimodal distributions — use histograms, medians, quantiles.**

![Figure 10-8. Page load time (skewed) and response time (multimodal) distributions for igvita.com](https://hpbn.co/assets/diagrams/ea05cfc71c5cd7777026101bface157e.svg)

Example: page load time was skewed while server response time was multimodal — the two modes corresponded to cached vs. uncached page generation by the application server.

### Three complementary W3C timing APIs
1. **Navigation Timing** — root document only.
2. **Resource Timing** — same data, per sub-resource.
3. **User Timing** — custom app-defined marks/measures via JS.

**User Timing code example:**
```js
function init() {
  performance.mark("startTask1"); 
  applicationCode1(); 
  performance.mark("endTask1");

  logPerformance();
}

function logPerformance() {
  var perfEntries = performance.getEntriesByType("mark");
  for (var i = 0; i < perfEntries.length; i++) { 
    console.log("Name: " + perfEntries[i].name +
                " Entry Type: " + perfEntries[i].entryType +
                " Start Time: " + perfEntries[i].startTime +
                " Duration: "   + perfEntries[i].duration  + "\n");
  }
  console.log(performance.timing); 
}
```
- `performance.mark("startTask1")` → stores a named timestamp.
- Run app code.
- Iterate `getEntriesByType("mark")` to log custom marks.
- `performance.timing` → logs the full Navigation Timing object.

---

## 9. Browser-level Optimizations

Two broad classes:
1. **Document-aware optimization** — networking stack integrated with HTML/CSS/JS parsing to prioritize and dispatch critical assets early (resource priority, lookahead parsing).
2. **Speculative optimization** — browser predicts likely next actions (pre-resolve DNS, pre-connect, etc.) based on learned navigation patterns.

### Four concrete browser techniques
1. **Resource pre-fetching & prioritization** — blocking/critical resources get high priority; low-priority ones are queued.
2. **DNS pre-resolve** — resolve likely hostnames ahead of time.
3. **TCP pre-connect** — speculatively open TCP connections after DNS resolves, potentially saving a full RTT.
4. **Page pre-rendering** — fully render the likely next page in a hidden tab for instant swap-in.

### Things you can do to help the browser
- Put critical CSS/JS early & discoverable in the document.
- Deliver CSS ASAP (unblocks rendering + JS execution).
- Defer non-critical JavaScript.
- **Flush the HTML document periodically** — it's parsed incrementally, so early flushes let the client start fetching resources sooner.

### Resource hints (code listing)
```html
<link rel="dns-prefetch" href="//hostname_to_resolve.com"> 
<link rel="subresource"  href="/javascript/myapp.js"> 
<link rel="prefetch"     href="/images/big.jpeg"> 
<link rel="prerender"    href="//example.org/next_page.html">
```
1. `dns-prefetch` → pre-resolve specified hostname.
2. `subresource` → prefetch a critical resource needed later on *this* page.
3. `prefetch` → prefetch a resource for this or a future navigation.
4. `prerender` → prerender the specified page, anticipating next navigation.

**Note:** these are *hints* — browsers may ignore them; unsupported hints are harmless no-ops.

### Browser support matrix (as of ~2013 — good to know support is a real consideration, exact versions likely outdated now)
| Browser | dns-prefetch | subresource | prefetch | prerender |
|---|---|---|---|---|
| Firefox | 3.5+ | n/a | 3.5+ | n/a |
| Chrome | 1.0+ | 1.0+ | 1.0+ | 13+ |
| Safari | 5.01+ | n/a | n/a | n/a |
| IE | 9+ (called "prefetch") | n/a | 10+ | 11+ |

### Real-world example: Google Search TTFB trick
When a search request arrives, the server immediately flushes the static page header before even processing the query, since the header is identical for every search results page; while the client parses that header, the query runs against the search index, and the results are streamed in once ready — dynamic bits like the logged-in user's name are then filled in via JavaScript.

---

## Interview soundbites — summary

1. **"Bandwidth doesn't matter much, latency does"** — past ~5 Mbps, more bandwidth gives diminishing returns; RTT reduction gives near-linear improvement. This is literally why SPDY/HTTP2 exist.
2. **PLT is an outdated single metric** for modern apps — use app-specific milestones (time-to-interactive, key engagement events).
3. **CSS blocks JS blocks DOM** — that's the technical reason behind "CSS top, JS bottom."
4. **Rendering and JS execution are single-threaded and interleaved** — the browser can't render while running script, which is why long-running JS freezes the page.
5. **Never trust averages** on perf data — use percentiles/histograms; distributions are often skewed or multimodal (e.g., cache hit vs. miss).
6. **Synthetic testing + RUM are complementary**, not either/or — synthetic catches regressions in controlled conditions, RUM captures real-world variance.
7. **Resource hints (`dns-prefetch`, `preconnect`/`preconnect`-era `subresource`, `prefetch`, `prerender`)** are speculative, best-effort browser hints, not guarantees.

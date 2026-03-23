#### Throughput vs. Response Time
*   **Throughput:** Requests per second (RPS) or data volume per second.
*   **Response Time:** The time between a client sending a request and receiving the full response.

*   **Description**: This graph shows the relationship between throughput and response time. As the system load (throughput) increases towards its maximum capacity, the response time stays relatively flat initially but then shoots up exponentially due to queueing effects once the system becomes saturated.
![Figure 2-3: As the throughput of a service approaches its capacity, the response time increases dramatically due to queueing.](data_intensive_applications/figure-2-3.png)

> **Metastable Failure:** An overloaded system can get stuck in a degraded state even after load decreases, often due to retry storms. Strategies to avoid this include **exponential backoff**, **circuit breakers**, **load shedding**, and **backpressure**.

#### Latency vs. Response Time
*   **Latency:** The time a request is waiting to be handled (latent).
*   **Response Time:** The client's view: Service Time + Network Latency + Queueing Delays.

*   **Description**: This timeline diagram distinguishes between different time metrics. "Response time" is the total duration seen by the client. It is composed of "Network latency" (travel time), "Queueing delay" (waiting to be processed), and "Service time" (actual processing).
![Figure 2-4: Response time, service time, network latency, and queueing delay.](data_intensive_applications/figure-2-4.png)

#### Percentiles (p50, p95, p99)
Averages (means) are often misleading because they hide outliers. **Percentiles** are better:
*   **Median (p50):** Half of users experience a faster time, half slower. Good for "typical" experience.
*   **p95, p99, p999 (Tail Latencies):** The threshold that 95%, 99%, or 99.9% of requests are faster than. These outliers are critical because they often represent the most data-heavy (and profitable) users.

*   **Description**: This bar chart visualizes a distribution of response times. While the "mean" (average) is shown, the chart highlights the "median" (p50) where 50% of requests are faster, and the "95th percentile" (p95) which captures the slower tail of requests.
![Figure 2-5: Illustrating mean and percentiles: response times for a sample of 100 requests to a service.](data_intensive_applications/figure-2-5.png)

*   **Tail Latency Amplification:** If a user request requires calling multiple backend services, the chance of the whole request being slow increases dramatically, as it is limited by the *slowest* backend call.

*   **Description**: This diagram shows a single end-user request triggering multiple parallel calls to backend services. Even if most backend calls are fast, one slow call (the bottom one) delays the entire response to the user, illustrating tail latency amplification.
![Figure 2-6: When several backend calls are needed to serve a request, it takes just a single slow backend request to slow down the entire end-user request.](data_intensive_applications/figure-2-6.png)
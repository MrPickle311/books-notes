---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---
*   **Problem:** The team needs to become more data-driven in planning for expert supply, which requires analyzing skill set demand in different locations over time. This requires aggregating data from multiple domains.
*   **Analysis:** They determine that the required data exists across three existing DPQs:
    1.  `Tickets DPQ`: Long-term view of all tickets.
    2.  `User Maintenance DPQ`: Daily snapshots of expert profiles.
    3.  `Survey DPQ`: Log of all customer survey results.
*   **Decision:** They decide to create a new **aggregate DPQ** called `Experts Supply DPQ`. This new quantum will take asynchronous inputs from the three source DPQs and use an ML model to generate daily supply recommendations.

![Figure 14-3: Ticket Management domain, including two services with their own DPQs, with a Ticket DPQ](figure-14-3.png)
![Figure 14-4: Implementing the Experts Supply DPQ](figure-14-4.png)

*   **Risk:** The trend analysis is sensitive to incomplete data. Receiving partial data for a given day would skew the results more than receiving no data at all. This leads to a critical ADR.

*   **ADR: Ensure that Expert Supply DPQ sources supply an entire day’s data or none**
    *   **Context:** The Expert Supply DPQ performs trend analysis. Incomplete data for a particular day will skew trend results and should be avoided.
    *   **Decision:** We will ensure that each data source for the Expert Supply DPQ receives complete snapshots for daily trends or no data for that day, allowing data scientists to exempt that day. The contracts should be loosely coupled.
    *   **Consequences:** If too many days become exempt due to availability issues, the accuracy of trends will be negatively impacted.
    *   **Fitness functions:**
        *   **Complete daily snapshot:** Check timestamps on incoming messages. A gap of more than one minute indicates a processing issue, marking that day's data as exempt.
        *   **Consumer-driven contract fitness function for Ticket DPQ and Expert Supply DPQ:** To ensure that internal evolution of the Ticket Domain doesn’t break the Experts Supply DPQ.

---
## Related Concepts
* [[Architecture]]

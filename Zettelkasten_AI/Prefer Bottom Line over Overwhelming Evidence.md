Avoid drowning non-technical stakeholders in a sea of technical details. Reduce the complex analysis to a few key points and a clear "bottom line" decision that focuses on business outcomes.

![Figure 15-9: Deciding between communication types](figure-15-9.png)

For a choice between synchronous and asynchronous communication, the bottom line for stakeholders is: *which is more important, a guarantee that a process starts immediately, or responsiveness and fault-tolerance?*

**Table 15-3. Trade-offs between synchronous and asynchronous communication for credit card processing.**

| Synchronous advantage                                           | Synchronous disadvantage                                  | Asynchronous advantage                              | Asynchronous disadvantage                 |
| --------------------------------------------------------------- | --------------------------------------------------------- | --------------------------------------------------- | ----------------------------------------- |
| Credit approval is guaranteed to start before customer request ends | Customer must wait for credit card approval process to start | No wait for process to start                        | No guarantee that the process has started |
|                                                                 | Customer application rejected if orchestrator is down     | Application submission not dependent on orchestrator |                                           |
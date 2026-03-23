Architects should be objective arbiters of trade-offs, not evangelists for a particular tool or technique. Be wary of any solution that promises to be all good with no downsides. Use scenario analysis to cut through the hype and uncover the real trade-offs.

![Figure 15-10: An architect evangelist who thinks they have found a silver bullet!](figure-15-10.png)

When faced with a decision like using a single topic vs. individual queues, modeling the scenarios reveals the deeper trade-offs related to contracts, security, and operational coupling.

![Figure 15-11: Scenario 1: Adding bid history to the existing topic](figure-15-11.png)
![Figure 15-12: Using individual queues to capture bid information](figure-15-12.png)

**Table 15-4. Trade-offs between Point-to-Point versus Publish-and-Subscribe Messaging**

| Point-to-Point                                 | Publish-and-Subscribe                     |
| ---------------------------------------------- | ----------------------------------------- |
| Allows heterogeneous contracts                 | Extensibility (easy to add new consumers) |
| More granular security access and data control |                                           |
| Individual operational profiles per consumer   |                                           |

> **Forced evangelism:** Don’t allow others to force you into evangelizing something—bring it back to trade-offs.
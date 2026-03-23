Aggregates communicate with the outside world through domain events.

*   **Purpose:** Describe significant business events that have occurred
*   **Naming:** Past tense (TicketEscalated, MessageReceived)
*   **Content:** All data needed by interested subscribers

![Figure 6-6: A diagram showing the domain events publishing flow from aggregates to subscribers.](figure-6-6.png)

Example domain event:
```json
{
  "ticket-id": "c9d286ff-3bca-4f57-94d4-4d4e490867d1",
  "event-id": 146,
  "event-type": "ticket-escalated",
  "escalation-reason": "missed-sla",
  "escalation-time": 1628970815
}
```
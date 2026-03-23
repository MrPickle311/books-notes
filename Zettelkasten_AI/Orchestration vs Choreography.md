
These are the two fundamental patterns for coordinating workflows.

![Figure 11-2: A side-by-side comparison. Orchestration shows services communicating through a central "Orchestrator." Choreography shows services communicating directly with each other.](figure-11-2.png)

### Orchestration Communication Style

This pattern uses a central **orchestrator** (or mediator) component to manage the workflow, including state, error handling, and alternate paths. It's like a conductor leading an orchestra.

![Figure 11-3: A generic illustration of orchestration, with a central orchestrator directing communication between services A, B, C, and D.](figure-11-3.png)

#### Happy Path Example
Consider a customer placing an order. The orchestrator directs the flow between the `Order Placement`, `Payment`, `Fulfillment`, and `Email` services. Time-sensitive calls (like payment) are synchronous, while less critical calls (like fulfillment and email) can be asynchronous.

![Figure 11-4: The "happy path" for an order placement workflow, managed by an orchestrator.](figure-11-4.png)

#### Error Handling Examples

The real value of an orchestrator becomes apparent when handling errors.
1.  **Payment Rejected:** The orchestrator receives the failure from the `Payment Service`, tells the `Email Service` to notify the customer, and updates the `Order Placement Service` to reflect the failed order status.
    ![Figure 11-5: The workflow for a rejected payment. The orchestrator coordinates the error response.](figure-11-5.png)

2.  **Item Backordered:** The `Fulfillment Service` reports a backorder. The orchestrator manages the compensating transactions, telling the `Payment Service` to refund the charge and updating the `Order Placement Service`.
    ![Figure 11-6: The workflow for a backordered item. The orchestrator coordinates refunding the payment and updating the order status.](figure-11-6.png)

#### Trade-offs for Orchestration

| Advantage          | Disadvantage     |
| ------------------ | --------------- |
| Centralized workflow | Responsiveness  |
| Error handling     | Fault tolerance |
| Recoverability     | Scalability     |
| State management   | Service coupling|

---

### Choreography Communication Style

In this pattern, there is no central coordinator. Services communicate directly with each other, like dance partners who have practiced their moves beforehand.

#### Happy Path Example
The same order placement workflow is modeled as a chain of events. The `Order Placement Service` triggers the `Payment Service`, which triggers the `Fulfillment Service`, and so on.

![Figure 11-7: The "happy path" for an order placement workflow using choreography. Services trigger each other in a sequence.](figure-11-7.png)

#### Error Handling Examples
Error handling is where choreography becomes complex.
1.  **Payment Rejected:** The `Payment Service` must now know to communicate directly with both the `Email Service` and the `Order Placement Service` to handle the failure. This adds a new communication link.
    ![Figure 11-8: Handling a payment error in choreography requires the Payment Service to have new communication paths to other services.](figure-11-8.png)

2.  **Item Backordered:** This is much more complex. The `Fulfillment Service` discovers the error late in the process. It must now broadcast an event or send individual messages to the `Email`, `Payment`, and `Order Placement` services so they can perform compensating actions. This adds many new communication links and distributes the workflow logic across multiple services.
    ![Figure 11-9: Handling a backorder in choreography is complex, requiring the Fulfillment Service to coordinate compensating actions with three other services.](figure-11-9.png)
    ![Figure 11-10: An illustration showing that each error condition in choreography tends to add more direct communication links between services.](figure-11-10.png)

### Trade-Offs: Orchestration vs. Choreography

The decision depends on the complexity of the workflow. As semantic complexity (number of steps, error conditions, alternate paths) increases, the utility of an orchestrator rises proportionally.

![Figure 11-14: A graph showing that as workflow semantic complexity increases, the usefulness of orchestration also increases.](figure-11-14.png)

*   **Use Choreography for:** Simple workflows that need high responsiveness and scalability, with few and simple error conditions.
*   **Use Orchestration for:** Complex workflows with significant error handling, boundary conditions, and the need for a queryable state.

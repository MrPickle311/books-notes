Stamp coupling describes passing a large data structure between services where each service only uses a small portion of that data.

![Figure 13-6: An illustration of stamp coupling, where a large data structure is passed between four services, but each only uses a small part of it.](figure-13-6.png)

#### Stamp Coupling as an Anti-Pattern
1.  **Over-Coupling:** An architect might couple a consumer to a provider's entire data structure "just in case" it needs more fields later. This is a fragile design. If the provider changes a field the consumer doesn't even use, the contract still breaks, forcing unnecessary coordinated changes.
    ![Figure 13-7: The Wishlist service only needs the customer's name but is unnecessarily coupled to the entire Profile data structure.](figure-13-7.png)
2.  **Bandwidth Waste:** Passing large, unnecessary data payloads over the network can consume significant bandwidth, violating one of the fallacies of distributed computing ("bandwidth is infinite").

#### Stamp Coupling as a Useful Pattern
In complex **choreographed** workflows, stamp coupling can be used to manage workflow state without a central orchestrator.
*   **How it works:** The message contract is designed to include both the domain data and the workflow state (e.g., status, transaction ID). Each service in the chain reads the state, performs its action, updates the state in the contract, and passes the enriched contract to the next service.
*   **Benefit:** This allows for highly scalable, choreographed workflows to manage complex state, trading off higher data coupling for better throughput.

![Figure 13-8: A diagram showing a contract with both domain and workflow data being passed between services in a choreographed workflow.](figure-13-8.png)

| Advantages                                       | Disadvantages                                  |
| ------------------------------------------------ | ---------------------------------------------- |
| Allows complex workflows within choreographed solutions | Creates (sometimes artificially) high coupling between collaborators |
|                                                  | Can create bandwidth issues at high scale      |

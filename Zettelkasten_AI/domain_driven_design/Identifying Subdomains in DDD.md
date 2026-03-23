### How to Identify Subdomains

1.  **Start with Business Strategy:** Look at the company's organizational structure (departments) and business capabilities.
2.  **Analyze and Refine:** Break down coarse-grained areas into finer components. A customer service department might seem "supporting," but it could contain a "core" case routing algorithm.

    ![Figure 1-2: An example of distilling the 'Customer Service' subdomain into finer-grained core, generic, and supporting subdomains.](figure-1-2.png)
    
    ![Figure 1-4: An example showing that further distilling a generic subdomain like 'Help Desk System' may not provide additional strategic value.](figure-1-4.png)

3.  **Look for Coherent Use Cases:** A subdomain often represents a set of related use cases involving the same actors and data (e.g., everything related to processing a credit card payment).

    ![Figure 1-3: A use case diagram for a credit card payment gateway, illustrating a coherent set of use cases that form a subdomain.](figure-1-3.png)

4.  **Focus on Essentials:** Concentrate on the subdomains relevant to the software system you are building.

---

### Detailed Examples

To illustrate how to apply these concepts, let's look at the two fictitious companies from the text.

#### Gigmaster
A ticket sales and distribution company with a mobile app that recommends nearby shows.

*   **Business Domain:** Ticket Sales
*   **Core Subdomains:**
    *   **Recommendation Engine:** The algorithm that analyzes user data to suggest shows is their key competitive advantage.
    *   **Data Anonymization:** A crucial feature to protect user privacy.
    *   **Mobile App UX:** A great user experience is vital for a consumer-facing app.
*   **Generic Subdomains:**
    *   **Encryption, Accounting, Clearing, Authentication:** Standard functionalities that can be solved with off-the-shelf solutions.
*   **Supporting Subdomains:**
    *   **Integrations:** Connecting to music streaming services and social networks.
    *   **Attended-gigs Module:** A simple CRUD feature for users to log past gigs.

#### BusVNext
A public transportation company that provides optimized, on-demand bus rides.

*   **Business Domain:** Public Transportation
*   **Core Subdomains:**
    *   **Routing:** The complex algorithm to adjust bus routes on the fly is their main differentiator.
    *   **Analysis:** Continuously analyzing ride data to optimize the routing algorithm.
    *   **Fleet Management:** Managing the fleet of buses is critical to the operation.
    *   **Mobile App UX:** Ensuring the app is easy to use for customers and drivers.
*   **Generic Subdomains:**
    *   **Traffic Conditions:** Using third-party data for traffic.
    *   **Accounting, Billing, Authorization:** Standard financial and user management tasks.
*   **Supporting Subdomains:**
    *   **Promotions Management:** A simple module for managing discounts and promo codes.

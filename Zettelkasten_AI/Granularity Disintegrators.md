
### Granularity Disintegrators (Reasons to Break a Service Apart)

These drivers help answer the question: "When should I consider breaking apart a service into smaller parts?"

#### 1. Service Scope and Function
This is about cohesion. Is the service doing one thing well, or is it a collection of loosely related functions?
*   **Good Candidate for Disintegration (Weak Cohesion):** A "Customer" service that handles profiles, preferences, and website comments. These are all related to the customer but are functionally distinct.
    ![Figure 7-3: A "Customer Service" box is shown breaking apart into three smaller services: "Profile Service," "Preferences Service," and "Comments Service."](figure-7-3.png)
*   **Poor Candidate (Strong Cohesion):** A "Notification" service that sends SMS, email, and postal letters. While the methods differ, the core responsibility—notifying a customer—is a single, cohesive concept.
    ![Figure 7-2: A "Notification Service" box with three functions inside (SMS, Email, Letter) is shown staying as a single service.](figure-7-2.png)

#### 2. Code Volatility
How often does the code for a specific function change? If one part of a service changes frequently while other parts are stable, it's a good candidate for separation.
*   **Example:** In the Notification service, if the postal letter logic changes weekly (due to evolving postal regulations) but the SMS/email logic rarely changes, the whole service must be re-tested and re-deployed for every small change.
*   **Solution:** Split the service. By creating a separate "Postal Letter Notification" service, changes are isolated, reducing testing scope and deployment risk for the stable electronic notification parts.
    ![Figure 7-4: A "Notification Service" is split into two: a stable "Electronic Notification" service (containing SMS and Email) and a volatile "Postal Letter Notification" service.](architecture_the_hard_parts/figure-7-4.png)

#### 3. Scalability and Throughput
Do different parts of the service have vastly different performance requirements?
*   **Example:** An SMS notification function might need to handle 220,000 requests/minute, while the postal letter function handles 1 request/minute. As a single service, the postal letter code must be deployed on infrastructure capable of handling the massive SMS load, which is inefficient and costly.
*   **Solution:** Split the service by function. This allows the "SMS Service" to scale independently on robust infrastructure, while the "Email Service" and "Letter Service" can run on smaller, less expensive infrastructure.
    ![Figure 7-5: The "Notification Service" is split into three separate services (SMS, Email, Letter), each with a different arrow size indicating its required scale.](architecture_the_hard_parts/figure-7-5.png)

#### 4. Fault Tolerance
Can a failure in a non-critical part of the service bring down critical functionality?
*   **Example:** If the email functionality in the Notification service has a memory leak and crashes, the entire service fails, meaning critical SMS notifications also stop.
*   **Solution:** Split the service. By separating the functions, a crash in the "Email Service" does not impact the operation of the "SMS Service" or "Letter Service."
    ![Figure 7-6: The "Notification Service" is split into three. An "X" is placed on the "Email Service," but the "SMS" and "Letter" services are shown as still running.](architecture_the_hard_parts/figure-7-6.png)

#### 5. Security
Does one part of the service handle highly sensitive data while other parts do not?
*   **Example:** A single "Customer Profile" service handles both basic profile information (name, address) and sensitive credit card data. Although API endpoints might differ, the code for both functions coexists in the same deployment unit, creating a larger attack surface and increasing risk.
*   **Solution:** Split the service. Creating a separate, highly secured "Credit Card Service" isolates the sensitive data and functionality, allowing for stricter access controls.
    ![Figure 7-7: A "Customer Profile Service" is split into a "Profile Service" and a more heavily secured "Credit Card Service."](architecture_the_hard_parts/figure-7-7.png)

#### 6. Extensibility
Is it known that a service's context will grow over time?
*   **Example:** A "Payment" service initially handles Credit Cards, Gift Cards, and PayPal. The business plans to add ApplePay, reward points, and store credit in the future. Adding each new method to a single service requires re-testing and re-deploying all existing payment methods.
*   **Solution:** Split the service by payment method. Now, adding "ApplePay" is a matter of creating a new, independent "ApplePay Service," which can be developed, tested, and deployed with zero risk to the existing payment services.
    ![Figure 7-8: A "Payment Service" is split into separate services for "Credit Card," "Gift Card," and "PayPal," with space to easily add a new "Reward Points" service.](architecture_the_hard_parts/figure-7-8.png)

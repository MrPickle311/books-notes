To ground abstract concepts, the book uses a running saga about modernizing a legacy system for **Penultimate Electronics**.

*   **The Business:** Customers buy support plans for their electronics. When a problem occurs, a "Sysops Squad" expert is dispatched to fix it.
*   **The Problem:** The current system is a large, unreliable monolith. Tickets are lost, the wrong experts are sent, and the system is frequently unavailable. Changes are slow and risky.
*   **The Goal:** Evolve the architecture to solve these problems without disrupting the lucrative support business.

#### Sysops Squad Architectural Components

The existing monolith is composed of several tightly coupled components.

![Figure 1-3: A component diagram of the existing Sysops Squad monolith, showing components for login, billing, customer management, knowledge base, reporting, ticketing, etc.](figure-1-3.png)

| Component             | Namespace                | Responsibility                                    |
| --------------------- | ------------------------ | ------------------------------------------------- |
| Login                 | `ss.login`               | Internal user and customer login and security logic |
| Billing Payment       | `ss.billing.payment`     | Customer monthly billing and credit card info     |
| Billing History       | `ss.billing.history`     | Payment history and prior billing statements      |
| Customer Notification | `ss.customer.notification` | Notify customer of billing, general info          |
| Customer Profile      | `ss.customer.profile`    | Maintain customer profile, customer registration  |
| Expert Profile        | `ss.expert.profile`      | Maintain expert profile (name, location, skills)  |
| KB Maint              | `ss.kb.maintenance`      | Maintain and view items in the knowledge base     |
| KB Search             | `ss.kb.search`           | Query engine for searching the knowledge base     |
| Reporting             | `ss.reporting`           | All reporting (experts, tickets, financial)       |
| Ticket                | `ss.ticket`              | Ticket creation, maintenance, completion          |
| Ticket Assign         | `ss.ticket.assign`       | Find an expert and assign the ticket              |
| Ticket Notify         | `ss.ticket.notify`       | Notify customer that the expert is on their way   |
| Ticket Route          | `ss.ticket.route`        | Sends the ticket to the expert's mobile device    |
| Support Contract      | `ss.supportcontract`     | Support contracts for customers, products in plan |
| Survey                | `ss.survey`              | Maintain surveys, capture and record results      |
| Survey Notify         | `ss.survey.notify`       | Send survey email to customer                     |
| Survey Templates      | `ss.survey.templates`    | Maintain various surveys based on service type    |
| User Maintenance      | `ss.users`               | Maintain internal users and roles                 |

#### Sysops Squad Data Model

All components share a single, third-normal-form database schema.

![Figure 1-4: The Entity-Relationship diagram for the Sysops Squad database, showing tables for Customer, Ticket, Billing, Survey, etc., all interconnected.](figure-1-4.png)
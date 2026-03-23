This is a fundamental pattern that organizes business logic into procedures, where each procedure handles a single, atomic request from a consumer.

*   **Definition:** "Organizes business logic by procedures where each procedure handles a single request from the presentation." - Martin Fowler
*   **Core Principle:** The system's public operations are treated as a collection of transactions. Each script implementing an operation **must be transactional**—it either succeeds completely or fails completely, leaving the system in a consistent state.

![Figure 5-1: A diagram showing a system's public interface as a collection of business transactions.](figure-5-1.png)
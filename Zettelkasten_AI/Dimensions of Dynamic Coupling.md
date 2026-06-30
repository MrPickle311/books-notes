
In a distributed architecture, services must interact. This interaction, or dynamic coupling, has three key dimensions that architects must manage:
1.  **Communication:** (Synchronous vs. Asynchronous)
2.  **Consistency:** (Transactional vs. Eventual)
3.  **Coordination:** (Orchestration vs. Choreography)

![Figure 11-1: A diagram showing the three dimensions of dynamic quantum coupling: Communication, Consistency, and Coordination.](architecture_the_hard_parts/figure-11-1.png)

This chapter focuses on **coordination**, which involves combining two or more services to perform a unit of work.

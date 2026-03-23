Architects can't justify expensive refactoring efforts by saying, "nothing else we try seems to work." A solid business case is required, and it starts with explaining the "why" behind modularity.

#### The Water Glass Analogy

This analogy is a powerful tool for explaining the limitations of a monolith to business stakeholders.

*   **The Monolith at Capacity:** A monolithic application is like a single, large glass of water. As the application grows to handle more features and load, the glass fills up. Once the glass is full, you cannot add more water. Adding another identical glass (server) doesn't help because it's just another full glass.

    ![Figure 3-1: A single, full glass of water representing a large monolithic application that is close to its resource capacity.](figure-3-1.png)

*   **Modularity Creates Capacity:** Breaking the monolith apart is like getting a second, empty glass and pouring half the water into it. You now have two half-full glasses, creating 50% more capacity for growth and scalability across your existing resources.

    ![Figure 3-2: Two half-full glasses, representing how breaking an application apart creates more capacity for growth.](figure-3-2.png)
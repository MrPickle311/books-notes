Bounded Contexts define clear lines of separation in your system.

1.  **Model Boundary:** It defines the scope where a Ubiquitous Language is consistent.
2.  **Physical Boundary:** Each context should be an independent, deployable unit (e.g., a microservice, project, or library), allowing it to have its own architecture and technology stack.
3.  **Ownership Boundary:** A Bounded Context must be owned by **one team only**. This enforces clear responsibility. However, a single team can own multiple Bounded Contexts.

![Figure 3-8: A diagram showing Team 1 owning two bounded contexts (Marketing, Optimization) and Team 2 owning one (Sales).](figure-3-8.png)

### Real-Life Examples of Bounded Contexts

The concept of using different models in different contexts is everywhere.

*   **The Tomato:** Is it a fruit or a vegetable?
    *   In the **Botany Context**, it's a fruit (contains seeds).
    *   In the **Culinary Context**, it's a vegetable (based on flavor profile).
    *   In the **US Taxation Context**, it's legally a vegetable.
*   **The Refrigerator Model:** To check if a fridge will fit in a kitchen:
    *   A simple **cardboard cutout** of the fridge's base is a perfect model for the context of "Will it fit through the door?"
    *   A **tape measure** is a perfect model for the context of "Is it too tall?"
    *   Each model is simple and perfectly suited for its specific, *bounded* problem.

![Figure 3-9: A picture of a flat piece of cardboard.](figure-3-9.png)
![Figure 3-10: The cardboard cutout being used to check if it fits through a doorway.](figure-3-10.png)
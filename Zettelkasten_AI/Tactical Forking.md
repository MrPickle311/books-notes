### Tactical Forking

This approach is best suited for a "Big Ball of Mud" architecture where extracting components is nearly impossible due to high coupling. Instead of extracting what you want, you clone the monolith and **delete what you don't want**.

![Figure 4-6: An illustration of extraction, showing a single piece being pulled out from a tangled mass, trailing many dependency strands.](figure-4-6.png)

![Figure 4-7: An illustration of deletion, showing the tangled mass with unwanted parts being erased, leaving the desired part and its existing internal dependencies intact.](figure-4-7.png)

#### The Process

1.  **Start:** A single monolithic application with multiple domains mixed together.
    ![Figure 4-8: A block representing a monolith containing various shapes (hexagons, squares, circles) mixed together.](figure-4-8.png)

2.  **Clone:** Create multiple copies of the entire monolithic codebase, one for each target service/team.
    ![Figure 4-9: Two identical copies of the monolith block are shown side-by-side.](figure-4-9.png)

3.  **Delete:** Each team begins deleting the code that is not relevant to their target service. This is often easier than extraction because you don't have to untangle dependencies; you just delete code until the service compiles and runs.
    ![Figure 4-10: The two blocks are shown with parts being faded out, as each team gradually eliminates unwanted code.](figure-4-10.png)

4.  **Finish:** The result is two (or more) coarse-grained services, each containing the code for their specific domain.
    ![Figure 4-11: The final state showing two separate services, one containing the hexagon and square, the other containing the circle.](figure-4-11.png)

#### Trade-offs

*   **Benefits:**
    *   Teams can start immediately with little up-front analysis.
    *   Deleting code is often easier and faster than extracting it from a highly coupled system.
*   **Shortcomings:**
    *   Resulting services will likely contain dead, latent code.
    *   The internal code quality of the new services is no better than the original monolith.
    *   Can lead to inconsistencies in shared code and components across services.
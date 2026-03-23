This chapter introduces a foundational framework for analyzing the difficult trade-offs in distributed architectures. It argues that generic advice like "embrace decoupling" is insufficient because architectural problems are **entangled**, with multiple competing forces that are hard to analyze independently. 

Architectural problems are like a braid: multiple concerns are woven together, making it hard to see and analyze the individual strands. Before an architect can perform a trade-off analysis, they must first untangle these concerns.

![Figure 2-1: A picture of a braid, illustrating how individual strands of hair are entangled and hard to separate.](figure-2-1.png)

The proposed framework for analysis is:
1.  **Find** what parts are entangled together.
2.  **Analyze** how they are coupled to one another.
3.  **Assess** trade-offs by determining the impact of change to these interdependent parts.
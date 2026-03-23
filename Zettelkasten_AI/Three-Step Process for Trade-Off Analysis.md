The book proposes a three-step process for conducting custom trade-off analysis.

1.  **Find what parts are entangled together:** Discover the unique dimensions that are braided together within a specific architecture.
2.  **Analyze how they are coupled to one another:** Determine how a change in one part of the architecture will force a change in another.
3.  **Assess trade-offs by determining the impact of change to interdependent systems:** Use the understanding of coupling to evaluate the pros and cons of different architectural decisions.
---

### Finding Entangled Dimensions

The first step is to discover which architectural dimensions are intertwined. This is unique to each ecosystem and requires experience.

#### Coupling
The core of the analysis is understanding coupling: if someone changes X, will it possibly force Y to change? Architects should build static coupling diagrams for their architecture quanta, detailing dependencies like:
*   Operating systems/container dependencies
*   Transitive dependencies (frameworks, libraries)
*   Persistence dependencies (databases, search engines)
*   Architecture integration points (e.g., service mesh)
*   Messaging infrastructure

This analysis reveals the forces that require trade-off analysis. For the book's analysis, the authors identified **communication, consistency, and coordination** as the key entangled dimensions in distributed architectures.

---

### Analyze Coupling Points

Once coupling points are identified, the next step is to model the possible combinations in a lightweight way to understand the forces at play. This allows the creation of ratings tables and comparative matrices.

**Table 15-1. Ratings for the Parallel Saga Pattern**

| Parallel Saga               | Ratings     |
| --------------------------- | ----------- |
| Communication               | Asynchronous|
| Consistency                 | Eventual    |
| Coordination                | Centralized |
| Coupling                    | Low         |
| Complexity                  | Low         |
| Responsiveness/availability | High        |
| Scale/elasticity            | High        |

By analyzing each pattern in isolation and then comparing them, important correlations can be discovered.

**Table 15-2. Consolidated comparison of dynamic coupling patterns**

| Pattern               | Coupling Level | Complexity | Responsiveness/availability | Scale/elasticity |
| --------------------- | -------------- | ---------- | --------------------------- | ---------------- |
| Epic Saga             | Very high      | Low        | Low                         | Very Low         |
| Phone Tag Saga        | High           | High       | Low                         | Low              |
| Fairy Tale Saga       | High           | Very low   | Medium                      | High             |
| Time Travel Saga      | Medium         | Low        | Medium                      | High             |
| Fantasy Fiction Saga  | High           | High       | Low                         | Low              |
| Horror Story          | Medium         | Very high  | Low                         | Medium           |
| Parallel Saga         | Low            | Low        | High                        | High             |
| Anthology Saga        | Very low       | High       | High                        | Very high        |

This analysis revealed two key observations:
1.  There is a direct inverse correlation between **coupling level** and **scale/elasticity**.
2.  Higher **coupling** generally leads to lower **responsiveness/availability**.

This iterative process of building a matrix and modeling possibilities is key to understanding the nuanced impacts of architectural decisions.

---

### Assess Trade-Offs

With the analysis in place, architects can focus on the fundamental trade-offs. By fixing one dimension (e.g., choosing asynchronous communication), subsequent choices become limited, simplifying the decision-making process. What’s left after the hard, entangled decisions are made is design.
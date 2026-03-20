---
aliases:
tags:
  - layer4strategy
  - architecturethehardparts
source_book: "Architecture: The Hard Parts"
topic_layer: "Layer 4: Strategy"
status: pending
---

### Is the Code Base Decomposable?

Before starting, an architect must determine if the codebase has enough internal structure to be salvageable. This involves analyzing coupling metrics.

#### Afferent and Efferent Coupling

*   **Afferent Coupling (Ca):** Measures the number of **incoming** connections to a code artifact. High afferent coupling means many other parts of the system depend on this component.
*   **Efferent Coupling (Ce):** Measures the number of **outgoing** connections to other code artifacts. High efferent coupling means this component depends on many other parts of the system.

Tools like JDepend can visualize these relationships, helping architects understand the web of dependencies.

![Figure 4-2: A screenshot of the JDepend Eclipse plug-in showing a matrix view of coupling relationships between Java packages.](figure-4-2.png)

#### Abstractness, Instability, and Distance from the Main Sequence

These derived metrics, created by Robert Martin, provide a more holistic view of a codebase's health.

1.  **Abstractness (A):** The ratio of abstract artifacts (interfaces, abstract classes) to concrete artifacts (implementation classes). A value near 1 is highly abstract; a value near 0 is highly concrete.

    \[ A = \frac{\sum m_a}{\sum m_c + \sum m_a} \]

2.  **Instability (I):** The ratio of efferent coupling (Ce) to total coupling (Ce + Ca). It measures the volatility of a codebase. A value near 1 is highly unstable (many outgoing dependencies); a value near 0 is highly stable (many incoming dependencies).

    \[ I = \frac{C_e}{C_e + C_a} \]

3.  **Distance from the Main Sequence (D):** This metric measures the balance between abstractness and instability. The "main sequence" is an idealized line where A + I = 1. The goal is for components to be as close to this line as possible.

    \[ D = |A + I - 1| \]

Components far from this line fall into two undesirable zones:
*   **Zone of Pain:** Low abstractness, low instability. The code is concrete, stable, and rigid, making it very difficult to change.
*   **Zone of Uselessness:** High abstractness, high instability. The code is highly abstract but has many outgoing dependencies, making it difficult to use.

![Figure 4-3: A graph with Abstractness on the Y-axis and Instability on the X-axis, showing the idealized "main sequence" line from (0,1) to (1,0).](figure-4-3.png)

![Figure 4-4: The same graph as above, but with the "Zone of Pain" highlighted in the lower-left corner and the "Zone of Uselessness" in the upper-right corner.](figure-4-4.png)
---
## Related Concepts
* [[Architecture]]

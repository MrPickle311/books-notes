
The chapter presents a sequential roadmap for applying the decomposition patterns. This flow provides a structured, iterative approach to breaking apart a monolith.

![Figure 5-1: A flowchart illustrating the sequence of component-based decomposition patterns. It starts with "Identify and Size Components," flows to "Gather Common," then to "Flatten," then to "Determine Dependencies," then to "Create Domains," and finally to "Create Domain Services."](figure-5-1.png)

The six patterns are:
1.  **Identify and Size Components:** Catalog all logical components and ensure they are of a relatively consistent size.
2.  **Gather Common Domain Components:** Consolidate duplicated business logic to reduce redundancy and the number of potential services.
3.  **Flatten Components:** Refactor component hierarchies to ensure source code only resides in leaf-node namespaces, eliminating ambiguity.
4.  **Determine Component Dependencies:** Analyze the coupling between components to determine migration feasibility and effort.
5.  **Create Component Domains:** Logically group related components into domains that will eventually become services.
6.  **Create Domain Services:** Physically extract the defined domains from the monolith into separately deployed services.
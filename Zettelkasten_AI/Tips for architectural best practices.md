> **1. Embrace Trade-Offs, Don't Seek "Best Practices".** Your primary job as an architect is to analyze the trade-offs between competing concerns and find the least worst balance for your specific context.
>
> **2. Document Your Decisions with ADRs.** Use a lightweight format like an ADR to record what you decided, why you decided it, and what the consequences are. This creates an invaluable historical record for the team. Here is repo for ADRs: https://adr.github.io/
>
> **3. Automate Your Architecture with Fitness Functions.** Don't let your architectural principles live only in diagrams. Codify them as executable fitness functions that run as part of your continuous integration build. This prevents architectural drift and technical debt.
>
> **4. Treat Data as a First-Class Architectural Concern.** Data lasts longer than code. Understand the difference between operational (OLTP) and analytical data, and recognize that many hard architectural problems stem from managing data in a distributed environment.
>
> **5. Use Fitness Functions as an Executable Checklist.** Like pilots and surgeons, architects can use fitness functions as a checklist to ensure important-but-not-urgent principles (like modularity and code quality) are not skipped under pressure.
If two transactions are completely independent, the database runs them safely in parallel. But what if one transaction modifies data exactly while another one reads or modifies it? That is a **Race Condition**.

In a perfect world, databases would simply hide these race conditions by relying on **Serializable Isolation** (the database mathematically ensures that transactions have the exact same effect as if they were forced to run sequentially: one at a time, completely eliminating race conditions). 

However, forcing a global distributed database to run everything sequentially is agonizingly slow. Because of the massive performance penalty, almost all databases (even "ACID" relational ones like Oracle) use **Weak Isolation Levels** by default. These weaker levels provide *some* protection, but intentionally allow certain bugs to leak through in exchange for speed.

*(Note: Weak isolation causes catastrophic real-world bugs, including bankrupting a Bitcoin exchange in 2014 when attackers mathematically exploited a race condition allowing them to overdraw their balances).*
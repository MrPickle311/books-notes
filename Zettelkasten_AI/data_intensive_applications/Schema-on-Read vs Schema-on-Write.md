*   **"Schemaless" is Misleading:** Document databases are often called schemaless, but there is usually an implicit schema assumed by the application code.
*   **Schema-on-Read (Document):** The structure is interpreted only when data is read (like dynamic typing).
    *   **Pros:** Great for heterogeneous data (where items have different structures) or when data structure is determined by external systems. Easy to evolve: just write new fields; handle old documents in code.
    *   **Use Case:** Ideal when there are many different types of objects, and it is not practicable to put each type of object in its own table, or when the data structure is determined by external systems over which you have no control.
    *   **Cons:** Application code becomes more complex to handle multiple versions of data.
    *   **Migration Example:** To split `name` into `first_name` and `last_name`:
        ```javascript
        if (user && user.name && !user.first_name) {
            // Handle old documents on read
            user.first_name = user.name.split(" ")[0];
        }
        ```
*   **Schema-on-Write (Relational):** The schema is explicit and enforced by the database (like static typing).
    *   **Pros:** Documents and enforces structure, ensuring all data conforms.
    *   **Cons:** Schema changes (migrations) can be slow and operationally challenging on large tables. Better use `DEFAULT` SQL keyowrd with some default valuw
    *   **Migration Example:** Requires an explicit migration.
        ```sql
        ALTER TABLE users ADD COLUMN first_name text DEFAULT NULL;
        UPDATE users SET first_name = split_part(name, ' ', 1); -- Slow on large tables!
*   **Normalization (IDs):** Storing `region_id` (e.g., `us:91`) instead of the plain string "Washington, DC".
    *   **Benefits:**
        *   **Consistent Style:** Eliminates spelling variations.
        *   **Avoids Ambiguity:** Distinguishes between cities with the same name.
        *   **Ease of Updating:** If a name changes, you only update one record in the `regions` table, not millions of user profiles.
        *   **Localization:** The ID can be translated into the user's language at runtime.
        *   **Better Search:** Hierarchical data (Washington is in the USA) can be encoded in the region table.
    *   **Cons:** Requires **joins** to resolve IDs into names, which can be slower for reads.
*   **Denormalization (Strings):** Storing "Washington, DC" directly in the user record.
    *   **Pros:** Faster reads (no joins required). Better **data locality** (all info is in one document).
    *   **Cons:** Harder to update (must update every record), potential for data inconsistency and duplication

*   **Hydrating IDs:** In large-scale systems (like Twitter's timeline), joins can be too expensive. Instead, the application fetches a list of IDs (only IDs e.g., Post IDs) and then "hydrates" them by fetching the content in parallel from a separate cache or service. This effectively moves the join from the database to the application layer.
    *   **Example Query:** Fetching only IDs for a timeline.
        ```sql
        SELECT posts.id, posts.sender_id FROM posts
        JOIN follows ON posts.sender_id = follows.followee_id
        WHERE follows.follower_id = current_user
        ORDER BY posts.timestamp DESC LIMIT 1000
        ```
    *   The application then looks up the full post content (from posts table) and user profiles (user table) for these IDs in a second step.

> **Rule of Thumb:** Normalized data is faster to write (one copy). Denormalized data is faster to read (no joins). Document databases are good for 1-to-many. Relational databases are better for many-to-many.
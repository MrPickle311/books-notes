If your data is highly interconnected (many-to-many relationships are the norm), a **Graph Model** is best. Graphs are good for evolvability: as you add features to your application, a graph can easily be extended to accommodate changes in your application’s data structures.
*   **Examples:** Social graphs, fraud detection, road networks.
*   **Components:** Vertices (nodes/entities) and Edges (relationships/arcs).

*   **Description**: This is an example graph dataset showing two people, "Lucy" and "Alain", and various locations. Vertices represent people and places (e.g., "Idaho", "United States"). Edges represent relationships like "BORN_IN", "LIVES_IN", "MARRIED_TO", and "WITHIN".
![Figure 3-6: Example of graph-structured data.](data_intensive_applications/figure-3-6.png)

#### Property Graphs (e.g., Neo4j, Cypher)
*   Vertices and edges both have properties (key-value pairs).
*   **Cypher Query Language:** Uses ASCII art to represent patterns.
    ```cypher
    (person) -[:BORN_IN]-> () -[:WITHIN*0..]-> (us:Location {name:'United States'})
    ```

#### Triple-Stores (e.g., SPARQL, RDF)
*   **Data Model:** All information is stored as simple three-part statements: `(Subject, Predicate, Object)`.
    *   **Subject:** Equivalent to a vertex in a graph.
    *   **Object:** Can be a primitive value (string, number) or another vertex.
    *   **Predicate:** If the object is a primitive, the predicate is like a property key (e.g., `(lucy, age, 33)`). If the object is a vertex, the predicate is like an edge (e.g., `(lucy, marriedTo, alain)`).
*   **Turtle Format:** A concise format for RDF data. Allows grouping multiple predicates for the same subject using semicolons.
    ```turtle
    @prefix : <urn:example:>.
    _:lucy a :Person; :name "Lucy"; :bornIn _:idaho.
    ```
*   **RDF (Resource Description Framework):** Uses URIs (e.g., `http://my-company.com/namespace#within`) for subjects, predicates, and objects to avoid naming conflicts when combining data from different sources.
*   **SPARQL:** Standard query language for RDF data (Semantic Web).
    *   **Example Query:** Find people who moved from the US to Europe.
        ```sparql
        PREFIX : <urn:example:>
        SELECT ?personName WHERE {
          ?person :name ?personName.
          ?person :bornIn / :within* / :name "United States".
          ?person :livesIn / :within* / :name "Europe".
        }
        ```

#### Datalog
*   An older, logic-based language (subset of Prolog).
*   **Data Model:** Built on facts (like rows in a relational table). E.g., `location(2, "United States", "country").`
*   **Rules:** Queries are built by defining rules that derive new facts (virtual tables) from existing ones. Great for complex recursive queries.
*   **Example:** Recursively finding locations within North America.
    ```prolog
        location(1, "North America", "continent").
        location(2, "United States", "country").
        location(3, "Idaho", "state").
        within(2, 1)./* US is in North America */
        within(3, 2)./* Idaho is in the US
        */
        person(100, "Lucy").
        born_in(100, 3). /* Lucy was born in Idaho */
    ```
    ```prolog
    within_recursive(LocID, PlaceName) :- location(LocID, PlaceName, _). /* Rule 1 */
    within_recursive(LocID, PlaceName) :- within(LocID, ViaID), /* Rule 2 */
                                          within_recursive(ViaID, PlaceName).
    migrated(PName, BornIn, LivingIn) :- person(PersonID, PName),
                                         born_in(PersonID, BornID), /* Rule 3 */
                                         within_recursive(BornID, BornIn),
                                         lives_in(PersonID, LivingID),
                                         within_recursive(LivingID, LivingIn).
    us_to_europe(Person) :- migrated(Person, "United States", "Europe"). /* Rule 4 */
    /* us_to_europe contains the row "Lucy". */
    ```
    *   **Explanation:** Rule 1 says "If a location exists, it is within itself." Rule 2 says "If location A is within B, and B is within C, then A is within C." By applying these rules repeatedly, the database can traverse the entire hierarchy.

*   **Description**: This diagram illustrates how Datalog rules derive new facts. Rule 1 infers `within_recursive(1, "North America")`. Rule 2 combines `within(2,1)` with the previous fact to infer `within_recursive(2, "North America")` (US is in North America). This recursion continues to infer that Idaho is also in North America.
![Figure 3-7: Determining that Idaho is in North America, using Datalog rules.](data_intensive_applications/figure-3-7.png)
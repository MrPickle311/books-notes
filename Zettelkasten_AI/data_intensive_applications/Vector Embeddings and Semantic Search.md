Semantic search is a massive leap beyond traditional full-text search. Instead of looking for identical words or substrings, semantic search tries to match **concepts and meaning**. If a user searches "terminate contract", they should find the page titled "cancelling your subscription".

#### Vector Embeddings
To match concepts, semantic search models (like BERT, GPT, or Word2Vec) translate sentences, documents, images, or audio into abstract floating-point arrays called **vector embeddings**.
*   A vector represents a distinct point in a multi-dimensional space (often consisting of over 1,000 dimensions).
*   If two documents are semantically similar (in human meaning), the machine learning model will generate vectors that reside very close to each other in this abstract space.

*Note: Do not confuse this with "vectorized processing". In execution engines, vectors are batches of bits processed via SIMD. In semantic search, vectors are mathematical coordinates representing concepts.*

#### Distance/Similarity Metrics
Search engines determine how similar two items are by mathematically measuring the distance between their vectors using calculating techniques:
*   **Cosine Similarity:** Measures the angle between two vectors.
*   **Euclidean Distance:** Measures the straight-line physical distance between two points in space.

#### Vector Indexes
When a user provides a search query, the system generates a vector embedding for the query string on the fly, and must find the "nearest neighbor" vectors inside the database. Because standard R-trees cannot handle 1,000+ dimensions, databases use specialized vector indexes:

1.  **Flat Indexes:** Performs an exact, brute-force comparison of the query vector against every single vector in the database. 100% accurate, but incredibly slow.
2.  **Inverted File (IVF) Indexes:** Clusters the vector space into partitions (centroids). The system only measures vectors inside nearby partitions. Faster, but approximate (might miss a match sitting right on the border of a partition).
3.  **Hierarchical Navigable Small World (HNSW):** An approximate algorithm representing the vector space as a multi-layered graph. The top layer has few nodes, while bottom layers are dense. The query rapidly descends the layers, following proximity edges to hone in on the closest match.

*   **Description:** This figure visualizes the HNSW algorithm. It shows a query vector dropping down through progressively denser graph layers to locate the nearest matching entry without needing to scan every node.
![Figure 4-11: Searching for the database entry that is closest to a given query vector in a HNSW index.](data_intensive_applications/figure-4-11.png)

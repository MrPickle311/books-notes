### Chapter 7: Indexing for query performance - Summary

**Indexes** are special data structures storing a small portion of data in an easily traversable form (B-tree). They allow efficient equality matches, range-based queries, and sorted results. Without indexes, MongoDB performs a **COLLSCAN** (full collection scan).

**Trade-off:** Indexes improve Read performance but negatively impact Write performance (insert/update/delete).

---

The query planner in MongoDB is a component that analyzes various ways to execute a query and selects
the most efficient execution plan. It uses indexes and other available data to minimize response times
and resource usage. If there is no index available for a given query, the query planner will perform a full collection scan.

### 7.1 MongoDB query planner

The query planner selects the most efficient plan.
*   **Trial Phase:** Tests strategies/indexes. Query planner temporarily tests all available op- tions. This phase is part of MongoDB’s plan caching mechanism, where the database evaluates the efficiency of different execution strategies under actual query conditions. The plan that demonstrates the best balance of speed and resource usage during this trial is selected and cached for future use. Generally, the winning plan is the one that retrieves the most results with the least effort.
*   **Plan Cache:** Stores the winning plan. Each query shape is categorized into one of three states in the plan cache
*   **Cache States:**
    *   *Missing*: No hierarchy.
    *   *Inactive*: Placeholder state. The query shape is recognized, and the work required is noted. If queried again, MongoDB reevaluates plans. A new, equally or more efficient plan replaces the Inactive entry and becomes Active. If less efficient, the entry remains Inactive, updating the work value.
    *   *Active*: The entry is a winning plan currently used. If performance degrades or work increases it may be reassessed and moved to Inactive if it no longer meets efficiency criteria.

To access the query plan details in mongosh for a specific query, you can use either db.collection.explain() or cursor.explain().

**Query Engines:** - MongoDB uses two query engines to find and return results. The engine is chosen automatically by MongoDB, and you can't select it manually:
1.  **Classic Query Engine**: Legacy.
2.  **Slot-Based Query Engine (SBE)**: Modern, faster, lower CPU/RAM. Used for aggregations with `$group`/`$lookup`.
    *   Log identifier: `queryFramework: "sbe"`.

If a query doesn't meet the criteria for the slot-based engine, MongoDB will use the classic engine instead. To determine which query engine was used, you can examine the explain results under `queryFramework` field.

#### 7.1.1 Viewing query plan (`explain()`)
Supported modes:
*   `"queryPlanner"` (Default): Details about the selected plan.
*   `"executionStats"`: Runs the query and returns stats (time, docs scanned).
*   `"allPlansExecution"`: Runs all plans and returns stats.

```javascript
/* Listing 7.1 Using explain() */
db.collection.find({ field: "value" }).explain("executionStats")
```

sample output:
```json
{
    "winningPlan": {
        "stage": "FETCH",
        "inputStage": {
            "stage": "IXSCAN",
            "indexName": "yourField_1",
            "keyPattern": {
                "yourField": 1
            },
            "indexBounds": {
                "yourField": [
                "[\"value\", \"value\"]"
                ]
            }
        }
    },
    "executionStats": {
    "nReturned": 1,
    "executionTimeMillis": 3,
    "totalKeysExamined": 1,
    "totalDocsExamined": 1
    }
}
```
In this specific execution plan, the IXSCAN stage scans the index yourField_1, efficiently locating the relevant keys that match the query condition { yourField: "value" }. These index keys are passed to the FETCH stage, which retrieves the actual documents from the collection.

totalKeysExamined and totalDocsExamined reveal how many index keys and documents were scanned during the query process. This indicates efficient use of the index, minimizing the need to scan unnecessary documents.

The queryHash helps MongoDB identify if the same query structure has been exe-
cuted before, allowing for the reuse of execution plans, and the planCacheKey considers the current state
and environment of the database to determine if a cached plan is appropriate

> **TIP** An index fully supports a query when it includes all the fields the query needs to scan. Instead of scanning the entire collection, the query scans the index. By creating indexes that align with your queries, you can significantly enhance query performance. If the index does not include all the fields the query needs, the query will scan the index and then access the collection for the remaining fields, which can still improve performance but not as much as a fully covering index.If the index does not include all the fields the query needs, the query will scan the index and then access the collection for the remaining fields, which can still improve performance but not as much as a fully covering index.

**Key Explain Output Fields:**
*   `COLLSCAN`: Scanning the whole collection. BAD
*   `IXSCAN`: Scanning index keys. GOOD
*   `FETCH`: Fetching documents.
*   `GROUP`: Grouping documents.
*   `SHARD_MERGE`: Combining results from different shards.
*   `SHARDING_FILTER`: Removing orphan documents from shards.
*   `BATCHED_DELETE`: Deleting multiple documents in batches, a feature starting in MongoDB 6.1.
*   `EXPRESS` specialized stages introduced in MongoDB version 8.0. They are execution phases that allow
specific queries to bypass the traditional query planning and directly utilize highly optimized index
scan strategies. This approach is designed to enhance performance for targeted query patterns, offer-
ing faster query execution by focusing on indexed fields. The available EXPRESS stages include:
*   `EXPRESS_CLUSTERED_IXSCAN`: Provides optimized index scanning for queries targeting clustered indexes, enabling faster data retrieval.
*   `EXPRESS_DELETE`: Delivers high-performance execution for delete operations by leveraging index
optimizations.
*   `EXPRESS_IXSCAN`: Accelerates index scans for non-clustered indexes, improving query speed in
relevant operations.
*   `EXPRESS_UPDATE`: Optimizes the execution of update operations, making them more efficient
when interacting with indexed data.

> **TIP** To see the list of operations supported by db.collection.explain(), run: db.collection.explain().help()



#### 7.1.2 Plan Cache Purges
Occurs on:
- restart
- index/collection drops
- LRU eviction

Manual clear: `PlanCache.clear()`. 

> **TIP** `$planCacheStats` returns the approximate size of a plan cache entry in bytes.



---

### 7.2 Supported index types

MongoDB automatically creates a unique index on the _id field when a collection is created. This index cannot be dropped.

#### 7.2.1 Single Field Indexes
Indexes a single field. Supports sorting in both directions (1 or -1).
```javascript
db.movies.createIndex({ "runtime": 1 })
```
> **Tip:** Max 64 indexes per collection.

How to get index:
```javascript
db.collection.getIndexes()
```

### Using indexes for sorting:

Because indexes hold ordered records, MongoDB can derive sorted results directly from an index that en-
compasses the fields used in the sort operation. If the sort operation aligns with the indexes used in the query predicate, MongoDB can use the index efficiently to perform the sorting.

If MongoDB cannot use indexes for sorting, it uses a blocking sort operation, processing all documents in
memory before returning results.

Starting with MongoDB 6.0, if a pipeline execution stage requires more than 100 megabytes of memory, MongoDB will automatically write temporary files to disk, unless the query explicitly sets `{ allowDiskUse: false }`.

```javascript
db.collection.find({ field: "value" }).sort({ sortField: 1 })
```

#### CONVERTING AN EXISTING INDEX TO UNIQUE

If a collection already has a non-unique index and you want to convert it to a unique index, you can use
the `collMod` command. First we need to use `prepareUnique`.

```javascript
    db.runCommand({
        collMod: "users",
        index: {
            keyPattern: { email: 1 },
            uniqueprepareUnique:: true
        }
    })
```

After setting prepareUnique to true, MongoDB will prevent any new insertions or updates that would result in a duplicate email value in the users collection. If you try to insert a document with an email that already exists in the database, MongoDB will throw a duplicate key error.

Next, check for existing violations of what will become the unique constraint by running the collMod command with the unique and dryRun options:

```javascript
    db.runCommand({
        collMod: "users",
        index: {
            keyPattern: { email: 1 },
            unique: true
        },
        dryRun: true
    })
```

And finally:

```javascript
    db.runCommand({
        collMod: "users",
        index: {
            keyPattern: { email: 1 },
            unique: true
        }
    })
```

#### 7.2.2 Compound Indexes
Index on multiple fields.
*   **Prefixes:** Supports queries on the beginning subset of fields. 
    *   Index `{ a: 1, b: 1, c: 1 }` supports only queries on `{a}`, `{a, b}`, and `{a, b, c}`. Here `a` is the prefix and it's mandatory to be used in a query if the index is used.
*   **Not supported:** Queries on `{b}` or `{c}` alone.

**TIP:** If your collection includes both a compound index and an index on its prefix (for example, { a: 1, b: 1 } and { a: 1 }), you can safely delete the index on the prefix ({ a: 1 }). MongoDB will use the compound index in all of the situations that it would have used the prefix index.

**The ESR (Equality, Sort, Range) Rule:**
Best practice for ordering fields in a compound index. It dictates the order in which the elements of the query should be indexed: first by fields used in equality conditions, followed by fields used for sorting, and finally by fields used in range conditions
1.  **Equality**: Fields matched exactly (e.g., `year: 1914`).
2.  **Sort**: Fields used for sorting (e.g., `title: 1`).
3.  **Range**: Fields with operators like `$gt`, `$lt` (e.g., `rating: { $gte: 7 }`).

*   **Example:** Query `{ year: 1914, rating: { $gt: 7 } }.sort({ title: 1 })`
    *   **Optimal Index:** `{ year: 1, title: 1, rating: 1 }`

EQUALITY:
Index searches using exact matches reduce the number of documents MongoDB must review to complete a query.

SORT:
This configuration allows MongoDB to perform a non-blocking sort. An index supports sorting when the fields in the query match a subset of the index keys, but only if there are equality conditions for all preceding prefix keys in the index before the sort keys.

RANGE:
MongoDB cannot use an index to sort results if a range filter is applied to a different field. To enable MongoDB to perform an index-based sort, ensure that the range filter is placed after the sorting condition.


#### 7.2.3 Multikey Indexes
Indexes array fields. They are B-tree indexes. Creates an index entry for *each* element. This is index is used when query arrays elements. MongoDB is capable of generating multikey indexes for arrays containing both scalar values (like strings  and numbers) and embedded documents. When an array includes several occurrences of the same value, the index will only record one entry for that value.

*   **Constraint:** Only *one* field in a compound index can be an array.

```javascript
db.customers.createIndex({ accounts: 1 }) // 'accounts' is an array
```

Sample usage:
```javascript
db.customers.find({ accounts: 371138 })
```

#### MULTIKEY INDEXES WITH EMBEDDED FIELDS IN ARRAYS
```javascript
db.grades.createIndex({ "scores.score": 1 })
```

Sample usage:
```javascript
db.grades.find({ "scores.score": { $gt: 80 } })
```

MongoDB can quickly locate all documents where any score field within the scores array is greater than 70. This avoids a full collection scan, significantly improving query performance.

The index also supports sort operations on the scores.score field.
```javascript
db.grades.find().sort({ "scores.score": -1 })
```


#### 7.2.4 Text Indexes
Classic full-text search (case-insensitive, stemming). This type of index does not support searches for partial values, such as those conducted with regular expressions. In such cases, MongoDB bypasses the index and performs a full collection scan.  Conversely, a search index, available in Atlas, requires more data storage but enables partial value searches, commonly referred to as full-text searches.
*   **Operator:** `$text`.
*   **Recommendation:** Use **Atlas Search** instead for production full-text needs.

This index is used for `$text` operator for text search. Text operator has the following syntax:
```javascript
{
    $text: {
        $search: <string>,
        $language: <string>,
        $caseSensitive: <boolean>,
        $diacriticSensitive: <boolean>
    }   
}
```

Sample usage:
```javascript
db.movies.createIndex({
    title: "text",
    fullplot: "text"
})
```

How to use search:
```javascript
db.movies.find(
    { $text: { $search: "Zone drinks" } },
    { score: { $meta: "textScore" } }
).sort(
    { score: { $meta: "textScore" } }
).limit(3)
```


#### 7.2.5 Wildcard Indexes
Indexes *all* fields or sub-fields. Useful for unstructured/unpredictable schemas (e.g., product attributes). Targeted indexes on specific fields usually perform better.


Suppose your application frequently queries various subfields within the tomatoes field in the movies collection of the sample_mflix database, but the exact subfields are unpredictable or may change over time.
```javascript
db.movies.createIndex({ "tomatoes.$**": 1 })
```

The wildcard index on the tomatoes field allows efficient querying on any subfield within tomatoes with-
out knowing the specific subfields in advance.

Use wildcard indexes in these scenarios:
*   If field names vary between documents, a wildcard index supports queries on all possible field names.
*   If embedded document fields have inconsistent subfields, a wildcard index supports queries on all
subfields.
*   For documents with shared common characteristics, a compound wildcard index efficiently covers
many queries for those common fields.


#### 7.2.6 Geospatial Indexes
Geospatial indexing allows these applications to quickly query and analyze data by location, avoiding the need to scan through extensive data sets to find relevant information.

*   **2dsphere**: Earth-like sphere. Supports GeoJSON. For `$near`, `$geoNear`.
2dsphere indexes support geospatial queries on an earth-like sphere, such as determining points within
an area, calculating proximity to a point, and exact matches on coordinate queries. Indexed field values
must be GeoJSON objects (a format for encoding a variety of geographic data structures) or legacy coordi-
nate pairs (older formats stored as [longitude, latitude]), which MongoDB converts to GeoJSON points.

Sample:
```javascript
db.places.createIndex({ coordinates: "2dsphere" })
```

sample search:
```javascript
db.shipwrecks.find({
    coordinates: {
        $near: {
            $geometry: { type: "Point", coordinates: [-79.9081268, 9.3547792] },
            $maxDistance: 5000
        }
    }
})
```

sample result:
```javascript
{
    "_id": ObjectId("578f6fa2df35c7fbdbaed8c4"),
    "feature_type": "Wrecks - Visible",
    "coordinates": [-79.9081268, 9.3547792]
}
```


*   **2d**: Flat plane. Legacy coordinate pairs.

#### 7.2.7 Hashed Indexes
Uses MD5 hash of field value.
*   **Primary Use:** **Sharding** - Hashed sharding helps distribute the writes more evenly across shards. Indexes support sharding using hashed shard keys, which means they use a hashed index of a field as the shard key to partition data across your sharded cluster.
*   **Limitations:** No range queries, no unique constraint, no arrays.

Hashed indexing is ideal for shard keys with fields that change monotonically, such as ObjectId values or timestamps. In traditional ranged sharding, a monotonically increasing shard key value can lead to an issue where the chunk with an upper bound of MaxKey receives the majority of incoming writes. This behavior restricts insert operations to a single shard, which negates the advantage of distributed writes in a sharded cluster. Hashed sharding helps distribute the writes more evenly across shards, solving this issue.

Sample:
```javascript
db.orders.createIndex({ "field": "hashed" })
```

**TIP** When MongoDB uses a hashed index to resolve a query, it automatically computes the hash values using an internal hashing function. Applications do not need to calculate these hashes themselves.

Hashed indexes convert floating-point numbers to 64-bit integers before hashing.
For instance, the values 2.3, 2.2, and 2.9 will share the same hash key due to this conversion, causing a collision where multiple values are assigned to a single hash key. These collisions can negatively affect query performance.
To avoid collisions, do not use a hashed index for floating-point numbers that cannot be reliably converted to 64-bit integers and then back to floating-point numbers.
Additionally, hashed indexes do not support floating-point numbers larger than 2^53.

The hashing function does not support multi-key indexes, which means you cannot create a hashed index on a field that contains an array, nor can you insert an array into a hashed indexed field.

You cannot specify a unique constraint on a hashed index. Instead, to enforce uniqueness on a field, you
need to create an additional non-hashed index with the unique constraint. MongoDB will use this non-
hashed index to ensure the uniqueness of the field.

---

### 7.3 Managing Indexes

*   **Dropping:** `db.collection.dropIndex("name")`.
*   **Hiding:** `db.collection.hideIndex("name")`. Great for testing if an index is needed without deleting it.
*   **Hinting:** Force usage of a specific index. `find().hint({ field: 1 })`.
*   **Modifying:** - you need to recreate index. TTL indexes are exception and may be modified by `collMod`

#### Monitoring Usage (`$indexStats`)
Check if indexes are actually being used.
```javascript
db.movies.aggregate([ { $indexStats: {} } ])
```
*   Returns `accesses.ops` (number of times used). Reset on restart.

#### HINTING

Sometimes, you may want to compel MongoDB to use a specific index.  The `hint()` method tells MongoDB to use the specific compound index.  The hint() method tells MongoDB to use the specific compound index. 

This can be useful if the index you want to use is not being selected by the query planner. For instance, if you know a particular index will provide better performance for a specific query, using `hint()` ensures MongoDB uses that index. Additionally, if you are diagnosing query performance issues or want to test the impact of different indexes, `hint()` allows you to force the use of a specific index for precise control and analysis.

Sample:
```javascript
db.movies.find(
    { year: 1914, "imdb.rating": { $gte: 7 } }
).sort(
    { title: 1 }
).hint(
    { year: 1, type: 1, "imdb.rating": 1 }
)
```

---

### 7.4 Index Attributes

Index attributes influence how the query planner uses an index and how indexed documents are stored.
These attributes can be set as optional parameters when creating an index.

#### 7.4.1 Partial Indexes
Index only documents meeting a filter expression. Saves size/write overhead.
```javascript
db.movies.createIndex(
  { year: 1 },
  { partialFilterExpression: { type: { $eq: "movie" } } } // Only index movies
)
```

#### 7.4.2 Sparse Indexes
Only index documents where the field *exists*.
> **Tip:** Partial indexes are generally preferred over sparse indexes.

Sample:
```javascript
db.movies.createIndex(
  { year: 1 },
  { sparse: true } // Only index documents with a year field
)
```

#### 7.4.3 TTL (Time To Live) Indexes
Automatically delete documents after a period.
*   **Field:** Must be a Date or Array of Dates.
*   **Usage:** Logs, Sessions.
```javascript
db.sessions.createIndex(
  { "createdAt": 1 },
  { expireAfterSeconds: 3600 } // Delete after 1 hour
)
```
*   **0 Seconds Trick:** Set `expireAfterSeconds: 0` to delete exactly at the timestamp specified in the field.

sample:
```javascript
db.getSiblingDB('sample_analytics').runCommand(
{
    collMod: "transactions",
    index: {
        keyPattern: { date: 1 },
        expireAfterSeconds: 0
    }
}
)
```

#### CONVERTING A NON-TTL INDEX INTO A TTL INDEX

```javascript
use sample_analytics
db.getSiblingDB('sample_analytics').runCommand(
{
    collMod: "transactions",
    index: {
        keyPattern: { date: 1 },
        expireAfterSeconds: 31536000
    }
}
)
```

WARNING: After creating a TTL index, you might find that there are a large number of qualifying documents to delete at once.

**TTL indexes restrictions:**
*   TTL indexes are restricted to single-field indexes; compound indexes do not support TTL and will ig-
nore the expireAfterSeconds option.
*   The _id field does not support TTL indexes.
*   You cannot create a TTL index on a capped collection.
*   For a time series collection, you can only create TTL indexes on the collection's timeField.
*   You cannot use the `createIndex()` method to change the expireAfterSeconds value of an existing index Instead, use the `collMod` database command.

#### 7.4.4 Using hidden indexes

By hiding an index, you can test the effects of its absence without permanently removing it. If the results
are unfavorable, you can unhide the index instead of recreating it.
Hidden indexes are not visible to the query planner and are not used to support queries.

Hide:
```javascript
db.getSiblingDB('sample_mflix').movies.hideIndex(
{ runtime: 1 } // Specify the index key specification document
)
```

Unhide:
```javascript
db.collection.unhideIndex()
db.getSiblingDB('sample_mflix').movies.unhideIndex(
    { runtime: 1 } // Specify the index key specification document
)
```

The hidden index that is a unique index still enforces its unique constraint on the documents. Similarly, if a hidden index is a TTL index, it will still expire documents as expected. Hidden indexes are updated with write operations to the collection and continue to consume disk space and memory. Hiding an unhidden index or unhiding a hidden index resets its $indexStats.

---

### 7.5 Index Building

*   **Process:** Optimized build locks collection exclusively only at start and end. Interleaves read/writes during build. Index builds across a replica set or sharded cluster occur simultaneously on all data-bearing members of the replica set. The primary node mandates that a minimum number of data-bearing, voting members, including itself, complete the build. Only then is the index marked as ready for use.
*   **MongoDB 7.1+:**
    *   Better error reporting (immediate fail on duplicate key).
    *   Resilience (secondary can maintain build if primary fails).
    *   `indexBuildMinAvailableDiskSpaceMB` - specify the minimum required disk space for index builds. This parameter halts index builds if the available disk space falls below the set threshold.
*   **Rolling Builds:** For zero-downtime on replica sets (take node offline -> build -> rejoin).

Currently, MongoDB locks exclusively only the collection being indexed at the beginning and end of the
build to safeguard metadata changes. The rest of the build process uses the yielding behavior of back-
ground builds, enhancing read-write access to the collection during construction. This approach main-
tains efficient index structures while allowing more flexible access.

The index build process unfolds as follows:
1. Upon receiving the createIndexes command, the primary immediately logs a `startIndexBuild` oplog
entry tied to the index build.
2. The secondary members initiate the index build upon replicating the `startIndexBuild` oplog entry.
3. Each member casts a vote to commit the build after completing the indexing of the collection's data.
4. If no violations occur:
1. While waiting for the primary to confirm a quorum of votes, secondary members integrate any
new write operations into the index.
2. Once a quorum is confirmed, the primary checks for key constraint violations, such as duplicate
keys.
3. If no violations are found, the primary finalizes the index build, marks the index as ready, and logs
a `commitIndexBuild` oplog entry.
5. If key constraint violations occur, the index build is deemed a failure.
1. The primary then logs an `abortIndexBuild` oplog entry and halts the build.
2. Secondaries that replicate the `commitIndexBuild` oplog entry complete the index build.
3. If secondaries replicate an `abortIndexBuild` oplog entry, they terminate the index build and dis-
card the build task.

> **NOTE** Index builds can affect the performance of a replica set. For workloads that cannot afford a decrease in performance during index builds, consider using a rolling index build process. This method involves taking one replica set member offline at a time, beginning with the secondary members, and building the index on that member while it operates as a standalone. Rolling index builds necessitate at least one replica set election.

> **TIP** If you invoke `db.collection.createIndex()` on an index that already exists, MongoDB will not recreate the index.

To monitor the status of an index build operation, you can use the `db.currentOp()` method.

To terminate an index build operation, you can use the `db.dropIndex()` method.

Don't use `killOp` to terminate an index build operation.

---

### 7.6 Optimization & Covers

#### 7.6.4 Using indexes with $OR queries

When evaluating the clauses in a `$or` expression, MongoDB either performs a collection scan or, if all
clauses are supported by indexes, performs index scans.

Sample query:
```javascript
db.movies.find({
    $or: [
        { year: 1914 },
        { "imdb.rating": { $gt: 7 } }
    ]
}).explain("executionStats")
```

The movies collection has this index created earlier: `{ year: 1, type: 1, "imdb.rating": 1}` that does not fully support both conditions in the `$or` clause so here we have full collection scan. You should create an additional index for the `imdb.rating` condition:

```javascript
db.movies.createIndex({ "imdb.rating": 1 })
```

### 7.6.5 Using indexes with $NE, $NIN and $NOT operators

The impact on performance of the `$ne` (not equal), `$nin` (not in), and `$not` operators depends on the index structure. While single-field indexes may offer limited benefits, multi-field indexes can still be effectively utilized by the query planner.

#### 7.6.6 RAM Sizing
Indexes should ideally fit in RAM ("Working Set"). This prevents the system from reading the index from disk.
*   Check size: `db.collection.totalIndexSize()`.

#### 7.6.8 Covered Queries
Query where **all** requested fields are in the index. This allows MongoDB to retrieve the results directly from the index without scanning the documents in the collection, leading to more efficient query performance.
*   **Performance:** Lightning fast (0 docs examined).
*   **Requirement:** Must explicitly exclude `_id` unless it's in the index.
    ```javascript
    // Index: { name: 1 }
    db.coll.find({ name: "Alice" }, { name: 1, _id: 0 }).explain("executionStats")
    ```

Result:
```javascript
winningPlan: {
    stage: 'PROJECTION_COVERED',
    transformBy: { title: 1, year: 1, _id: 0 },
    inputStage: {
        stage: 'IXSCAN',
        keyPattern: { year: 1, title: 1, 'imdb.rating': 1 },
        indexName: 'year_1_title_1_imdb.rating_1'
    }
}
executionStats: {
    executionSuccess: true,
    nReturned: 2,
    executionTimeMillis: 0,
    totalKeysExamined: 4,
    totalDocsExamined: 0
```

It was a covered query (PROJECTION_COVERED), meaning all the required fields were retrieved directly
from the index without scanning the documents in the collection. The index scan (IXSCAN) used the speci-
fied index to filter the results.


> **WARNING** Multikey indexes cannot provide a covered query plan if any of the returned fields contain arrays.

#### 7.7 When NOT to use an index?
*   **Large Result Sets:** If query returns >30% of collection, a full scan is often faster (avoiding double-lookup of Index + Document).
*   **Low Selectivity:** Fields like `gender` (boolean) are often poor candidates unless part of a compound index.

---

### Key Tips
*   **$or Queries:** All clauses must be indexed, or it falls back to COLLSCAN.
*   **Negation:** `$ne`, `$nin` usually cannot use indexes efficiently.
*   **Sort:** Use indexes for sorting to avoid in-memory blocking sorts (limit 100MB unless `allowDiskUse` is true).

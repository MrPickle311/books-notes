### Chapter 6: Building aggregation pipelines - Summary

The **MongoDB aggregation framework** is a powerful tool for processing, transforming, and analyzing data. It works like a factory production line where documents pass through a sequence of stages (a pipeline).

**Key Capabilities:**
*   Filtering, grouping, sorting, reshaping data.
*   Joining collections ($lookup).
*   Full-text and Vector Search.
*   Reporting and Real-time analytics.

---

### 6.1 Understanding the aggregation framework

A pipeline is an array of stages: `[ { <stage> }, { <stage> }, ... ]`.

**Table 6.1 SQL vs MongoDB Aggregation Map**

| SQL Term | MongoDB Operator |
| :--- | :--- |
| WHERE / HAVING | `$match` |
| GROUP BY | `$group` |
| SELECT | `$project`, `$set`, `$unset` |
| ORDER BY | `$sort` |
| LIMIT | `$limit` |
| OFFSET | `$skip` |
| JOIN | `$lookup` |
| UNION ALL | `$unionWith` |

#### 6.1.1 Writing an aggregation pipeline
Use `db.collection.aggregate([])`.

```javascript
/* Listing 6.1 Aggregation Pipeline Example */
db.routes.aggregate([
  { $match: { airplane: "CR2" } },     // 1. Filter
  {
    $group: {                          // 2. Group
      _id: "$src_airport",
      totalRoutes: { $sum: 1 }
    }
  },
  { $sort: { totalRoutes: -1 } },      // 3. Sort Descending
  { $limit: 5 }                        // 4. Top 5
])
```

#### 6.1.2 Aggregation pipeline stages
**Most Popular Stages:**
*   `$match`: Filtering (Place early for performance).
*   `$group`: Aggregation (sum, avg, count).
*   `$set` / `$unset`: Add/Remove fields.
*   `$sort`: Ordering (Use indexes).
*   `$limit`: Restrict output count.
*   `$unwind`: Flatten arrays.
*   `$lookup`: Join collections.

**Table 6.2 Key Stages in MongoDB 8.0**
Includes `$bucket`, `$count`, `$documents`, `$fill`, `$geoNear`, `$merge`, `$out`, `$replaceRoot`, `$unionWith`, `$vectorSearch`, and more.

**Best Practices:**
1.  **Place `$match` early**: Reduce document set immediately.
2.  **Use `$set` / `$unset` over `$project`**: Unless reshaping entirely.
3.  **Index support for `$sort`**: Essential for performance.
4.  **$limit before potentially expensive stages**: Reduce working set.
5.  **Filter arrays before $unwind**: Apply filtering conditions to arrays before using the $unwind stage to minimize the increase in document count and avoid excessive processing.
6.  **Optimize $lookup operations**: Ensure that the foreign field used in the $lookup stage is indexed in the joined collection.
7.  **Use $addFields sparingly**: Apply the $addFields stage judiciously to create or modify fields, avoiding overly complex expressions that can slow down the pipeline.
8.  **Streamline your pipeline**: Regularly review and optimize your pipeline by removing unnecessary
stages and combining stages where possible to streamline processing.
9.  **Monitor pipeline performance**: Utilize MongoDB’s explain() method (introduced in Chapter 7) and
other performance monitoring tools to analyze and optimize pipeline performance, identifying and
addressing bottlenecks.

#### 6.1.3 Using $set and $unset instead of $project
*   **$project**: "All or nothing" (except `_id`). Verbose. Best for final output reshaping.
*   **$set / $unset**: Modify specific fields, pass others through. more intuitive and more flexible.

```javascript
/* Listing 6.2 Using $unset */
db.routes.aggregate([
  { $unset: ["codeshare", "stops"] } // Removes only these fields
])

// Using $set with conditional logic
db.routes.aggregate([
  {
    $set: {
      isDirect: { $eq: ["$stops", 0] },
      codeshare: "$$REMOVE" // Removes field inside $set
    }
  }
])
```

**Non-intuitive usage:** With $project, you can either include or exclude fields in a single stage, but not both, except for the special case of the _id field, which can be excluded while including other fields. This exception makes $project somewhat confusing and counterintuitive.

**Verbosity and inflexibility:** The $project stage tends to be verbose. To add just one field, you must explicitly list all other fields to include. This requirement leads to redundant and lengthy code, complicating maintenance and making it difficult to adapt to changes in the data model.

#### 6.1.4 Saving results ($out and $merge)
*   **`$out`**:
    *   Writes to a new/existing collection.
    *   **Replaces** the entire collection atomically if it exists.
    *   Must be the last stage.
    ```javascript
    { $out: { db: "output_db", coll: "projected_routes" } }
    ```

*   **`$merge`**:
    *   More flexible: Insert, Update, Merge, Replace, Keep.
    *   Can write to sharded collections.
    *   Can run on secondaries (reads).
    ```javascript
    /* Listing 6.5 $merge Example */
    {
      $merge: {
        into: "routes",
        on: "_id",
        whenMatched: "merge",
        whenNotMatched: "insert"
      }
    }
    ```

---

### 6.2 Joining collections ($lookup)

Performs a **Left Outer Join**.
*   **Performance:** Can be slow on large datasets; prefer embedding if possible. ensure foreign fields are indexed.

**Syntax:**
```javascript
{
  $lookup: {
    from: "joined_collection",
    localField: "input_doc_field",
    foreignField: "from_doc_field",
    as: "output_array_field"
  }
}
```

**Creating Views with $lookup:**
You can create a "read-only view" that virtually joins collections.
```javascript
/* Listing 6.6 Create View */
db.createView("enriched_transactions", "transactions", [
  {
    $lookup: {
      from: "customers",
      localField: "account_id",
      foreignField: "accounts",
      as: "customer_details"
    }
  },
  // Flatten result
  { $set: { "Customer Name": { $arrayElemAt: ["$customer_details.name", 0] } } },
  { $unset: "customer_details" }
])
```

**TIP:** The view definition pipeline is restricted from including the $out or $merge stage. This limitation extends to embedded pipelines used within stages like $lookup or $facet.

**Flattening Joined Data ($mergeObjects):**
Combine the joined sub-document with the root document.
```javascript
db.transactions.aggregate([
    {
        $lookup: {
            from: "accounts",
            localField: "account_id",
            foreignField: "account_id",
            as: "account_details"
        }
    },
    {
        $unwind: "$account_details"
    },
    {
        $replaceRoot: {
            newRoot: {
                $mergeObjects: ["$account_details", "$$ROOT"]
            }
        }
    },
    {
        $unset: "account_details"
    }
])
```

**NOTE:** Excessive use of $lookup can lead to overly complex and slow queries, complicating code management
and maintenance. In such scenarios, consider alternative approaches like data denormalization instead
of relying heavily on collection joins. Thoughtful schema design is essential for optimizing database per-
formance.

> **Tip:** Use `$unionWith` to merge streams (UNION) instead of joining documents.

---

### 6.3 Deconstructing arrays with $unwind

Flattens an array. Creates a new document for *every element* in the array.
*   **Input:** `{ accounts: [1, 2] }`
*   **Output:** `{ accounts: 1 }, { accounts: 2 }`

For example this document:
```json
{
    "_id": ObjectId("5ca4bbcea2dd94ee58162a76"),
    "username": "portermichael",
    "name": "Lauren Clark",
    "address": "1579 Young Trail\nJessechester, OH 88328",
    "birthdate": ISODate("1980-10-28T16:25:59.000Z"),
    "email": "briannafrost@yahoo.com",
    "accounts": [883283, 980867, 164836, 200611, 528224, 931483],
    "tier_and_details": {
    "b0d8ebd346824edc890898b0b2ad6e2d": {
        "tier": "Silver",
            "benefits": ["concert tickets", "sports tickets"],
            "active": true,
            "id": "b0d8ebd346824edc890898b0b2ad6e2d"
    }
}
}
```

```javascript
/* Listing 6.7 Using $unwind */
db.customers.aggregate([
    {
        $match: {_id: ObjectId("5ca4bbcea2dd94ee58162a76")}
    },
    {
        $unwind: "$accounts"
    },
    {
        $project: {
            _id: 0,
            username: 1,
            accounts: 1
        }
    }
])
```

Sample output:
```json
[
{ username: 'portermichael', accounts: 883283 },
{ username: 'portermichael', accounts: 980867 },
{ username: 'portermichael', accounts: 164836 },
{ username: 'portermichael', accounts: 200611 },
{ username: 'portermichael', accounts: 528224 },
{ username: 'portermichael', accounts: 931483 }
]
```

**Common Use Case:** Counting items across arrays in all documents.
```javascript
/* Listing 6.8 Grouping unwound data */
db.customers.aggregate([
    {$unwind: "$accounts"},
    {
        $group: {
            _id: "$accounts",
            count: {$sum: 1}
        }
    },
    {$sort: {count: -1}}
])
```

Sample output:
```json
[
    { _id: 883283, count: 1 },
    { _id: 980867, count: 1 },
    { _id: 164836, count: 1 },
    { _id: 200611, count: 1 },
    { _id: 528224, count: 1 },
    { _id: 931483, count: 1 }
]
```

---

### 6.4 Working with accumulators

Used in `$group` and `$project`.
*   **`$sum`**: Total.
*   **`$avg`**: Average.
*   **`$max` / `$min`**: Extremes.
*   **`$push` / `$addToSet`**: Collect arrays.

```javascript
/* Listing 6.9 Max Accumulator */
{
  $group: {
    _id: { username: "$username" },
    maxAccountNumber: { $max: "$accounts" }
  }
}
```

---

### 6.5 Using MongoDB Atlas aggregation pipeline builder

A UI tool in Atlas to visually build, debug, and export pipelines.
*   **Features:** Drag-and-drop stages, real-time preview of data flow.
*   **Export:** Generates code for Node.js, Python, Java, etc.


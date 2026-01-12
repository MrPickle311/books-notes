### Chapter 4: Executing CRUD operations - Summary

This chapter covers the fundamental specific actions known as **CRUD** (Create, Read, Update, Delete). It details how to execute these operations using `mongosh`, explore the behavior of various operators, optimize performance (e.g., bulk operations), and understand the **MongoDB Stable API**.

**Key New Features in MongoDB 8.0 covered:**
*   `defaultMaxTimeMS` cluster parameter for read timeouts.
*   New `bulkWrite` command supporting cross-collection operations.
*   Full-text search availability in Community Edition.

---

### 4.1 Connecting to mongosh for CRUD operations

When connecting, using the `--apiVersion` parameter ensures your application targets the **MongoDB Stable API**. This prevents breaking changes when the server upgrades.

```bash
mongosh "mongodb+srv://YOUR_CLUSTER.YOUR_HASH.mongodb.net/" \
--apiVersion 1 --username USERNAME --password PASSWD
```

---

### 4.2 Inserting documents

#### Inserting a Single Document
Use `insertOne()`. Returns an `insertedId` (auto-generated `ObjectId` if not provided).

```javascript
/* Listing 4.1 Inserting a single document */
db.routes.insertOne({
  airline: { id: 410, name: 'Lufthansa', alias: 'LH', iata: 'DLH' },
  src_airport: 'MUC',
  dst_airport: 'JFK',
  codeshare: '',
  stops: 0,
  airplane: 'A380'
})
// Result: { acknowledged: true, insertedId: ObjectId('...') }
```

#### Inserting Multiple Documents
Use `insertMany()`. Takes an array of documents.
*   **Efficiency:** Much faster than loop-inserting; reduces round trips.
*   **Limit:** Batch size limited by 16MB BSON limit (technically split by driver if needed, max operations per batch is 100,000).

```javascript
/* Listing 4.2 Inserting many documents */
db.routes.insertMany([
  { ... },
  { ... },
  { ... }
])
```

To check which document failed in an insertMany() operation in MongoDB, look at the error details pro-
vided in the operation's response. The error message will include the index of the failed document within
your array of documents.

MongoDB limits the number of operations in each batch to the maxWriteBatchSize, which defaults to
100,000. This limit helps avoid issues with large error messages. If a batch exceeds this limit, the client
driver splits it into smaller batches.


**Ordering Behavior:**
*   **Default (`ordered: true`):** Stops on first error.
*   **`ordered: false`:** Continues inserting remaining documents even if one fails. Useful for bulk loads where some dupes might exist.

> **Performance Tip:** For large bulk inserts into indexed fields (e.g., hashed indexes), performance may drop due to random index entries causing cache eviction.
> *   **Strategy:** Drop index -> Bulk Insert -> Recreate Index **OR** insert the data into an unindexed collection and create the index post-insertion, allowing for an organized, memory-sorted index creation.
> *   **Warning:** Dropping an index before large bulk inserts can significantly boost write performance by reducing insertion overhead. However, this comes at the cost of slower reads and potential query disruptions.  Only do this during low activity/downtime as it impacts read performance.

---

### 4.3 Updating documents

Methods: `updateOne()`, `updateMany()`, `replaceOne()`.
*   **Upsert (`upsert: true`):** Creates a new document if no match is found. Ensure filter fields have unique indexes to avoid duplicates.

#### 4.3.1 Update Operators
Use operators to modify specific fields without rewriting the whole document.

*   **`$set`:** Updates value of a field.
    ```javascript
    /* Listing 4.3 Using $set */
    db.routes.updateOne(
      { "airline.id": 411, "src_airport": "LHR" },
      { $set: { "airplane": "A380" } }
    )
    ```

*   **`$inc`:** Increments a numeric value. Efficient low-overhead.
    ```javascript
    /* Listing 4.4 Using $inc */
    db.routes.updateOne(
      { "airline.id": 413, "src_airport": "DFW" },
      { $inc: { "stops": 1 } }
    )
    ```

> **NOTE:** The updateOne method in MongoDB updates an arbitrary one of the matching documents if multiple documents fit the query criteria and no specific sort order is applied. This behavior can be unintuitive because it does not specify which document will be updated when there are multiple matches. To accurately target a specific document, it is advisable to use unique identifiers in your query, ensuring a predictable update.

> **TIP:** The $inc operator modifies the value of an existing key or creates a new one if the key does not exist. When choosing between MongoDB's $inc and $set operators, select $inc for numeric updates to benefit
from its fast and low-overhead operations.

**Table 4.1 MongoDB update operators**

| Name | Description |
| :--- | :--- |
| **`$currentDate`** | Sets a field to the current date (Date or Timestamp). |
| **`$inc`** | Increments/decrements a field. |
| **`$min`** / **`$max`** | Updates only if value is less/greater than current. |
| **`$mul`** | Multiplies value. |
| **`$rename`** | Renames a field. |
| **`$set`** | Sets specific field value. |
| **`$setOnInsert`** | Sets field only during an insert (upsert). |
| **`$unset`** | Removes a field. |

#### 4.3.2 Updating many documents
`updateMany()` modifies *all* matching documents. Before using it, first execute a `find()` method and verify whether the filter accurately selects
the desired documents.
> **Note:** If an update fails mid-operation, previous updates are *not* rolled back (partial updates possible). Any matching documents after failure are not updated.

---

### 4.4 Updating arrays

#### Adding Elements
*   **`$push`:** APPend element to array.
    ```javascript
    /* Listing 4.5 Using $push */
    db.routes.updateOne(
      { "airline.id": 413 },
      { $push: { "prices": { class: "business", price: 2500 } } }
    )
    ```
*   **Modifiers:** `$each` (add multiple), `$sort` (order array after add), `$slice` (keep only top N items).
    ```javascript
    { $push: { prices: {
        $each: [ ... ],
        $sort: { price: 1 },
        $slice: -3 // Keep last 3
    }}}
    ```
*   **`$addToSet`:** Adds element only if it doesn't already exist (unique).

#### Removing Elements
*   **`$pull`:** Remove elements matching a query.
    ```javascript
    /* Listing 4.8 Using $pull */
    db.routes.updateOne(
      { "airline.id": 413 },
      { $pull: { prices: { class: 'first', price: 2000 } } }
    )
    ```
*   **`$pop`:** Remove first (`-1`) or last (`1`) element.

#### Updating Specific Elements
*   **Direct Index:** Update element by array index.
    ```javascript
    // Update price at index 0
    db.routes.updateOne(
      { "airline.id": 413 },
      { $set: { "prices.0.price": 2600 } }
    )
    ```

*   **Positional Operator (`$`):** Identify element by query condition. This is recommended for updating specific elements in an array.
    ```javascript
    // Update price where class is "luxury"
    db.routes.updateOne(
      { "prices.class": "luxury" },
      { $set: { "prices.$.price": 3500 } }
    )
    ```

> **NOTE**
If the specified field price does not exist within an array element that matches the condition (such as
"prices.class": "luxury"), MongoDB will automatically add the price field to that element and set its
value to 3500. This action occurs because the $set operator in MongoDB not only updates existing fields
but can also create new fields in document elements where they are missing, ensuring that the specified
update is applied correctly.

*   **Filtered Positional Operator (`$[<identifier>]`):** Update elements matching `arrayFilters`. Update operators process fields in documents with string-based names in lexicographic order, while fields with numeric names are processed in numeric order.
    ```javascript
    /* Listing 4.10 Using arrayFilters */
    db.routes.updateOne(
      { "airline.id": 413 },
      { $set: { "prices.$[elem].price": 2600 } },
      { arrayFilters: [ { "elem.class": "business" } ] }
    )
    ```

**Table 4.2 Array Update Operators**

| Name | Description |
| :--- | :--- |
| **`$`** | Update first matching element. |
| **`$[]`** | Update all elements. |
| **`$[<identifier>]`** | Update elements matching `arrayFilters`. |
| **`$addToSet`** | Add unique. |
| **`$pop`** | Remove first/last. |
| **`$pull`** | Remove by query. |
| **`$pullAll`** | Remove by value list. |
| **`$push`** | Add item. |

---

### 4.5 Replacing documents

`replaceOne()` replaces the **entire** document content (except immutable `_id`).
*   **Warning:** Do not use if you only want to change a few fields (use `$set` instead). Sending full documents wastes bandwidth and bloats oplog.
*   **Upsert:** Creates new document if not found.

```javascript
/* Listing 4.11 Replacing a document */
db.routes.replaceOne(
  { "airline.id": 412 },
  { flight_info: { ... }, status: "Scheduled" },
  { upsert: true }
)
```

> **NOTE:** If a different _id is supplied, the replacement operation will fail. MongoDB enforces the immutability of
the _id field, and any attempt to change it will result in an error.

---

### 4.6 Reading documents

`find(<filter>, <projection>)` returns a cursor. When you execute db.collection.find() in mongosh, it automatically iterates the cursor to display up to
the first 20 documents. To continue viewing more documents, you can type it to iterate further.

*   **Default Timeout (v8.0):** MongoDB 8.0 introduces `defaultMaxTimeMS`.
    ```javascript
    // Set default timeout for read operations to 5 seconds
    db.adminCommand({ setClusterParameter: { defaultMaxTimeMS: { readOperations: 5000 } } })
    ```

#### Logical Operators (Table 4.3)
`$and` (implicit with comma), `$or`, `$nor`, `$not`.
```javascript
/* Listing 4.12 Compound Query */
db.routes.find({ "src_airport": "CDG", "dst_airport": "JFK" })
```

#### Comparison Operators (Table 4.4)
`$eq`, `$gt`, `$gte`, `$lt`, `$lte`, `$ne`, `$in`, `$nin`.
```javascript
/* Listing 4.13 Using $in */
db.routes.find({ src_airport: { $in: ['MUC', 'JFK'] } })
```

#### Projections
Include (`1`) or Exclude (`0`) fields. `_id` is included by default unless excluded. Projections are used for fetch less data than is needed.
```javascript
db.routes.find({}, { "airline.name": 1, "_id": 0 })
```

#### Nulls and Existence (Table 4.5)
*   **`{ field: null }`**: Matches if field is null OR field does not exist.
*   **`{ field: { $exists: true } }`**: Matches if field exists (even if null).
*   **`{ field: { $type: "null" } }`**: Matches only explicit BSON Null type.

---

### 4.7 Performing regular expression searches

Use `$regex`.
*   **Options:**
    *   i: Case insensitivity.
    *   m: Multiline matching, treating start ^ and end $ anchors to match line beginnings and endings.
    *   x: Extended regex to ignore white space within the regex pattern.
    *   s: Allows the dot . to match newline characters.
    *   u: Unicode support, though redundant as MongoDB's $regex defaults to UTF.
```javascript
// Find airlines with "air" (case insensitive)
db.routes.find({ "airline.name": { $regex: "air", $options: "i" } })
```
> **Tip:** MongoDB 8.0 includes full-text search in Community Edition (Atlas Search equivalent).

---

### 4.8 Querying arrays

*   **Exact Match:** `{ accounts: [1, 2] }` (Must match strictly order and content).
*   **`$all`:** `{ accounts: { $all: [1, 2] } }` (Contains 1 AND 2, any order).
*   **Single Element condition:** `{ accounts: { $gt: 300 } }` (At least one element > 300).
*   **`$elemMatch`:** `{ accounts: { $elemMatch: { $gt: 300, $lt: 400 } } }` (At least one element satisfies BOTH conditions).
*   **`$size`:** Exact size match. Does not support ranges.

## 4.9 Querying embedded/nested documents

1.  **Dot Notation:** `"airline.name": "American Airlines"`
    *   **Recommended.** Flexible.
2.  **Full Document Match:** `{ airline: { id: 413, name: ... } }`
    *   **Warning:** Requires exact match of fields AND order. Fragile.

> **WARNING:** MongoDB cautions against exact matches for embedded documents because such queries demand a complete match, down to the order of fields. If even one field is omitted, removed from the document, or reordered, the query won't work. It's generally better to use the dot notation approach for more flexibility and reliability.

**TIP:** When using dot notation in queries, make sure to enclose both the field and nested field in quotation marks.

**Querying Array of Objects:**
*   `"prices.price": { $lte: 1000 }`: Matches if *any* price in array is <= 1000.
*   `"prices.0.price"`: Matches first element.

---

### 4.10 Sorting, skipping, and limiting
Chaining methods for pagination:
```javascript
db.routes.find()
  .sort({ "prices.0.price": 1 }) // 1 = Ascending, -1 = Descending
  .skip(10)                      // Skip first 10
  .limit(5)                      // Return next 5
```

---

### 4.11 Deleting documents
*   `deleteOne()`, `deleteMany()`.
*   `findOneAndDelete()`: Atomically deletes and returns the document.
> **Tip:** Use "Logical Deletes" (soft deletes) with a flag for critical data.

---

### 4.12 Using `bulkWrite()`

Performs batch operations.
*   **Ordered (`ordered: true` - default):** Sequential. Stops on error. Slower on sharded clusters. This is because with an ordered list, each operation must wait for
the preceding operation to complete.
*   **Unordered (`ordered: false`):** Parallel processing possible. Continues on error.

#### New in MongoDB 8.0: `bulkWrite` Command
Unlike `db.collection.bulkWrite()` which is single-collection, the new **command** supports multi-collection writes.

```javascript
/* Listing 4.22 Syntax of bulkWrite command in MongoDB 8.0 */
db.adminCommand({
  bulkWrite: 1,
  ops: [
    { insert: 0, document: { ... } }, // Operation for nsInfo index 0
    { insert: 1, document: { ... } }  // Operation for nsInfo index 1
  ],
  nsInfo: [
    { ns: "sample_training.routes" },
    { ns: "sample_analytics.customers" }
  ]
})
```

---

### 4.13 Understanding cursors

Cursors fetch data in batches (lazy loading).

Example:
```javascript
const cursor = db.routes.find()
while (await cursor.hasNext()) { console.log(await cursor.next()); }
```

#### Iteration methods:
1.  **Manual:** `hasNext()` and `next()`.
    ```javascript
    while (await cursor.hasNext()) { console.log(await cursor.next()); }
    ```
2.  **Array:** `toArray()` (Loads all into RAM - caution with large sets).

> **WARNING:** Mixing different cursor paradigms, like using hasNext() and toArray() together, can lead to unexpected results.

---

### 4.14 MongoDB Stable API

Ensures backward compatibility regardless of server upgrades.
*   **Version 1** is currently supported.
*   **Targeting:**
    ```bash
    mongosh ... --apiVersion 1
    ```

**Table 4.7 Key Stable API Commands**
Includes `count`, `find`, `aggregate`, `insert`, `update`, `delete`, `explain`, `start/commit/abortTransaction`, etc.

# Chapter 18: Querying MongoDB Atlas using SQL

This chapter covers the **Atlas SQL Interface**, enabling the use of standard SQL (SELECT, WHERE) to query, analyze, and visualize data stored in MongoDB collections without complex ETL processes.

## 18.1 Introducing MongoDB Atlas SQL Interface
This feature bridges the gap between MongoDB's document model and traditional SQL-based Business Intelligence (BI) tools (e.g., PowerBI, Tableau).

### Key Concepts
*   **Read-Only:** The interface is exclusively for *querying*. You cannot write data back to Atlas using SQL.
*   **Data Federation:** It runs on the Atlas Data Federation engine, creating a virtual layer over your data.
*   **MongoSQL Dialect:** Uses a SQL-92 compatible dialect.
*   **Schema Generation:** Automatically creates JSON schemas by analyzing sample documents to map BSON data types to SQL types (inferring fields like `bsonType`, `properties`, etc.).

**(Description of Figure 18.1):** The architecture shows User SQL tools (Tableau, PowerBI) connecting via SQL Drivers to the "Atlas SQL Interface" layer. This layer translates SQL into internal queries against the Federated Database, which then pulls data from the actual Atlas Storage.

> **NOTE:** Querying involves data transfer, which incurs charges based on the volume processed.

---

## 18.2 Connecting to the Atlas SQL Interface

### 18.2.1 Enabling via Quick Start
**Steps:**
1.  **Navigate:** Go to your Cluster view in Atlas UI.
2.  **Connect:** Click the **Connect** button (Figure 18.2) > Select **Atlas SQL**.
3.  **Quick Start:** Choose **Quick Start** (Figure 18.3) to automatically set up the Data Federation and enable the interface.

### 18.2.2 Accessing via `mongosh`
Once enabled, you get a connection string. You can use this with `mongosh` just like a standard connection, but it routes to the SQL interface.

**Connection Example:**
```bash
mongosh "mongodb://atlas-sql-URI..." --tls --username <user>
```
*   **Note:** The connection string often includes parameters like `?directConnection=true`.

---

## 18.3 Querying MongoDB using SQL

Atlas provides two primary ways to run SQL: the `$sql` aggregation stage and the `db.sql()` helper.

### 18.3.1 Aggregation pipeline (`$sql`)
Integration of SQL directly into MQL pipelines.

**Syntax:**
```javascript
{
  $sql: {
    statement: "<SELECT ...>",
    excludeNamespaces: true // optional boolean
  }
}
```

**Example (Listing 18.1):**
Retrieve specific customer data.
```javascript
db.aggregate([
  {
    $sql: {
      statement: "SELECT * FROM customers WHERE username = 'valenciajennifer' AND email = 'cooperalexis@hotmail.com'",
      format: "jdbc",
      dialect: "mongosql"
    }
  }
])
```
*   **Constraints:** Must be the *first* stage. Only supports `SELECT` and `UNION`.

### 18.3.2 Short Form Syntax (`db.sql`)
A cleaner, experimental helper for running SQL without the pipeline wrapper.

**Example (Listing 18.2):**
```javascript
db.sql(`
  SELECT username, name, address
  FROM customers
  WHERE username = 'valenciajennifer'
`);
```

> **Review:** This streamlined syntax is excellent for quick ad-hoc analysis but is currently considered **experimental**.

### 18.3.3 Advanced Functions: UNWIND and FLATTEN
Handling the flexible document model (arrays, nested objects) in rigid SQL tables requires special operators.

#### UNWIND
Expands an array into individual rows (similar to `$unwind` in MQL).

**Syntax:**
```sql
SELECT * FROM UNWIND(<source> WITH PATH => <array_field>) ...
```

**Example:**
Splitting a `products` array in the `accounts` collection.
```javascript
db.sql(`
  SELECT account_id, products AS product
  FROM UNWIND(accounts WITH PATH => products)
  WHERE account_id = 198100
`);
```
*   **Result:** One row per product, repeating the `account_id` for each.

#### FLATTEN
Converts nested objects (sub-documents) into top-level columns.

**Syntax:**
```sql
SELECT * FROM FLATTEN(<source> WITH DEPTH => <int>, SEPARATOR => <char>) ...
```

**Example:**
Flattening the `airline` nested object in the `routes` collection.
```javascript
db.sql(`
  SELECT * FROM FLATTEN(routes)
  WHERE src_airport = 'KZN'
`);
```
*   **Result:** Nested fields like `airline.name` become columns like `airline_name`.

---

## 18.4 Limitations of Atlas SQL Interface (Critical)

Although SQL-92 compatible, specific features are restricted:
*   **Unsupported Operators:** `UNION` (use `UNION ALL`), `SELECT DISTINCT`.
*   **Unsupported Types:** No `Date` type support (use `Timestamp`).
*   **Arithmetic:** No Interval or Date Interval arithmetic.
*   **Search Integration:** Does **not** support Atlas Vector Search or Atlas Search.
*   **State:** The interface is strictly **Read-Only**.

---

## 18.5 Summary
*   **Purpose:** Enables SQL-native querying (SELECT, WHERE, JOINs) on MongoDB data, facilitating integration with BI tools like Tableau/PowerBI.
*   **Architecture:** Relying on Data Federation, it creates a virtual read-only layer with schema inference.
*   **Access:** Can be accessed via standard SQL Drivers (JDBC/ODBC) or `mongosh` using the `$sql` stage or `db.sql()` helper.
*   **Handling Complexity:** `UNWIND` handles arrays, and `FLATTEN` handles nested documents, mapping NoSQL flexibility to SQL tabular formats.
*   **Constraints:** Requires awareness of limitations like no `DISTINCT` or Write capabilities.

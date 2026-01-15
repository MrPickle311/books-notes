### Chapter 8: Executing multi-document ACID transactions - Summary

Transactions are discrete units of operation (read/write) that must either all succeed or all fail (ACID). While MongoDB is non-relational, it supports **multi-document ACID transactions** to handle complex scenarios across documents, collections, databases, and shards.

---

### 8.1 WiredTiger Storage Engine

MongoDB's default storage engine, acquired and integrated for high performance and scalability.

*   **Concurrency:** Uses **Multiversion Concurrency Control (MVCC)**. Allows non-blocking reads/writes and minimizes contention.
*   **Locking:** optimized row-level (document-level) locking.
*   **Architecture:** Supports both Row-oriented and Column-oriented storage.
*   **Compression:** Uses **Snappy** by default for collections/journals and prefix compression for indexes. Options: `zlib`, `zstd`.

#### 8.1.1 Snapshots and checkpoints
*   **MVCC:** Provides consistent point-in-time snapshots for readers.
*   **Checkpoints:** Data is flushed to disk every **60 seconds** to create a consistency point.
*   **Recovery:** If a crash occurs, MongoDB recovers from the last valid checkpoint + journal replays.
*   **Parameter:** `minSnapshotHistoryWindowInSeconds` (MongoDB 5.0+) controls how long to keep history (stored in `WiredTigerHS.wt`).

#### 8.1.2 Journaling
*   **Write-ahead logging (WAL):** Records modifications between checkpoints.
*   **Durability:** Ensures no data loss if the system crashes between the 60s checkpoints. If MongoDB exits unexpectedly between checkpoints, the journal is used to replay data modifications since the last checkpoint.

#### 8.1.4 Memory utilization
WiredTiger manages both internal cache and filesystem cache.
**Default Cache Size Calculation:**
`MAX(50% of (RAM - 1GB), 256 MB)`

*   *Example (8GB RAM):* `0.5 * (8 - 1) = 3.5 GB`.
*   *Example (1GB RAM):* Default to `256 MB`.

---

### 8.2 Single vs Multi-Document Transactions

1.  **Single Document:** Always atomic. Updates to embedded docs/arrays happen fully or not at all. No explicit transaction needed.
2.  **Multi-Document:** Required when updates span multiple documents/collections.

#### 8.2.1 Optimistic Concurrency Control (OCC)
WiredTiger uses OCC.
*   **Mechanism:** Transaction takes a snapshot at start. If data changes before write, a **Write Conflict** occurs.
*   **Handling:** The transaction aborts/retries depending on configuration.
*   **Impact:** Performance is high when conflicts are rare (no heavy locking on read). frequent conflicts degrade performance.

---

### 8.3 Defining ACID

*   **Atomicity:** All operations succeed or fail as a unit.
*   **Consistency:** Moves database from one valid state to another.
*   **Isolation:** Concurrent transactions don't interfere (visibility rules).
*   **Durability:** Committed changes persist despite crashes.

---

### 8.4 Multi-document transactions APIs

By using embedded documents and arrays to represent relationships within a single document rather than normalizing data across multiple documents and collections, MongoDB's single-document atomicity often eliminates the need for distributed transactions in many common scenarios.

MongoDB provides two APIs:

**Table 8.1 Core API vs Callback API**

| Feature | Core API | Callback API (Recommended) |
| :--- | :--- | :--- |
| **Control** | Explicit `startTransaction` and `commitTransaction`. | Encapsulated function handles entire transaction lifecycle. |
| **Error Handling** | Manual. Developer must write retry logic. | **Automatic retry** for `TransientTransactionError` and `UnknownTransactionCommitResult`. |
| **Session** | Explicitly pass session to every op. | Explicitly pass session to every op. |

Each operation within a transaction must be associated with this logical session. MongoDB uses logical sessions to track the timing and sequence of operations across the entire deployment. The logical or server sessions are fundamental for supporting retriable writes and causal consistency. These sessions ensure that a sequence of related read and write operations maintains their causal relationship through their order, known as causally consistent client sessions. A client session, started by an application, engages with a server session for these purposes.

#### 8.4.2 Using transactions with `mongosh` (Manual/Core Logic)
*Demonstrates manual retry logic found in the Core API approach.*

```javascript
/* Listing 8.1 Manual Transaction Logic */
const session = db.getMongo().startSession();
session.startTransaction({ readConcern: { level: 'snapshot' }, writeConcern: { w: 'majority' } });

try {
  const accounts = session.getDatabase('sample_analytics').getCollection('accounts');
  // Pass session to ALL operations
  accounts.updateOne({ id: 1 }, { $inc: { count: 1 } }, { session });
  
  session.commitTransaction();
} catch (error) {
  session.abortTransaction();
  // Manual retry logic would go here, just do the same one more time
} finally {
  session.endSession();
}
```

#### 8.4.3 Using Callback API (Node.js)

In most cases, distributed transactions come with higher performance costs compared to single-document writes, and their availability should not replace proper schema design. For many use cases, a de-normalized data model using embedded documents and arrays remains the best choice.

The **Callback API** is preferred because it handles the specific error labels automatically.

**Node.js Example:**
Uses `session.withTransaction(callback)`.

```javascript
const {MongoClient} = require('mongodb')
const uri = "your_mongodb_connection_string"

async function run(accountId) {
    const client = new MongoClient(uri)
    try {
        await client.connect()
        const session = client.startSession()
        const transactionOptions = {
            readConcern: {level: 'snapshot'},
            writeConcern: {w: 'majority'},
            readPreference: 'primary'
        };
        session.startTransaction(transactionOptions)
        const accounts = client.db('sample_analytics').collection('accounts')
        const customers = client.db('sample_analytics').collection('customers')
        const transactions = client.db('sample_analytics').collection('transactions')
        const currentDate = new Date()
        try {
            const account = await accounts.findOne({
                    account_id: parseInt(accountId)
                },
                {
                    session
                }
            )
            if (!account) throw new Error('Account not found')
            // Atomically increment transaction_count in accounts collection
            const accountsUpdateResult = await accounts.updateOne(
                {account_id: parseInt(accountId)},
                {
                    $set: {limit: 12000, last_transaction_date: currentDate}, $inc: {
                        transaction_count: 1
                    }
                },
                {
                    session
                }
            )
            // Update all customers who have this account in their accounts array
            const customersUpdateResult = await customers.updateMany(
                {accounts: {$in: [parseInt(accountId)]}},
                {
                    $inc: {transaction_count: 1}, $set: {
                        last_transaction_date: currentDate
                    }
                },
                {
                    session
                }
            )
            // Insert a new transaction in transactions collection
            const transactionsInsertResult = await transactions.insertOne({
                account_id: parseInt(accountId),
                transaction_count: account.transaction_count + 1,
                bucket_start_date: currentDate,
                bucket_end_date: currentDate,
                transactions: [{
                    date: currentDate,
                    amount: 1500,
                    transaction_code: 'buy',
                    symbol: 'amzn',
                    price: '125.00',
                    total: '187500.00'
                }]
            }, {session})
            await session.commitTransaction();
            console.log("Transaction committed.")
            console.log("Accounts updated:", accountsUpdateResult.modifiedCount);
            console.log("Customers updated:", customersUpdateResult.modifiedCount
            )
            ;
            console.log("New transaction inserted:", transactionsInsertResult.insertedId
            )
            ;
        } catch (error) {
            await session.abortTransaction();
            console.error("Transaction aborted due to error: " + error)
        } finally {
            session.endSession()
        }
    } catch (error) {
        console.error("Error connecting to MongoDB: ", error)
    } finally {
        await client.close()
    }
}

// Get accountId from command line arguments
const accountId = process.argv[2]
if (!accountId) {
    console.error("Please provide an account ID as an argument.")
    process.exit(1)
}
run(accountId).catch(console.dir)
```

---

### 8.5 MongoDB transactions considerations

MongoDB supports multi-document distributed transactions on sharded clusters, enabling multi-docu-
ment transactions across multiple shards. This ensures consistency across distributed data while preserving the ACID properties, even in complex sharded environments.

**Best Practices:**
1.  **Data Modeling First:** Prefer embedding related data to avoid transactions.
2.  **Short Duration:** Keep transactions under **60 seconds** (default timeout).
3.  **Indexing:** Ensure operations use indexes to prevent scanning large datasets inside a transaction.
4.  **Size Limit:** Modify fewer than **1,000 documents** per transaction.
5.  **Performance:** Transactions incur overhead (coordinator, locking). Don't use them for every single write. Transactions involving multiple shards will have a performance overhead.

**Limitations:**
*   Cannot create new collections in cross-shard write transactions (implicit creation). If you write to an existing collection in one shard and implicitly create a collection in another shard, MongoDB cannot handle both operations in the same transaction.
*   Cannot run `listCollections` or `listIndexes` inside a transaction.
*   Cannot run non-CRUD commands like `createUser` or `count` (use aggregation `$count` instead).
*   Explicitly creating collections (e.g., using the db.createCollection() method) and indexes (e.g., using the db.collection.createIndexes() and db.collection.createIndex() methods) when using a read concern level other than "local".
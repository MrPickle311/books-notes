# Chapter 17: Archiving online with Atlas Online Archive

This chapter covers archiving infrequently accessed data to lower-cost storage, setting archiving rules, and restoring data back to the live cluster.

## 17.0 Introduction
**Atlas Online Archive** manages data storage costs by automatically moving cold data to cheaper cloud object storage (S3/Azure). It creates a seamless, queryable ecosystem where you can access both hot (live) and cold (archived) data via a single endpoint.

> **WARNING:** Online Archive is **NOT** a backup solution. It is for cost-optimization of active data. Always maintain separate backups for disaster recovery.

## 17.1 Archiving your data
Atlas transfers data from your primary cluster to a read-only **Federated Database Instance**.

### Archiving Criteria (Table 17.1)
How you select data depends on the collection type:
| Criteria Type | Description | Instance Options |
| :--- | :--- | :--- |
| **Standard Collection** | **Date-Based:** Date field + age limit (e.g., older than 365 days). <br> **Custom Query:** MQL query to select documents. | *Archive-Only* or *Combined* (Live + Archive). |
| **Time Series** | **Time-Based:** Time field + age limit. | *Archive-Only* or *Combined*. |

> **Requirement:** Cluster must be **M10 or higher**.

### 17.1.1 How Archiving Works (The "Job")
1.  **Job Frequency:** Runs every **5 minutes**.
2.  **Capacity:** Writes up to **2GB** per run.
3.  **Indexing:** Requires an "Index Sufficiency" check. If the query to find candidates is inefficient (scan/return ratio > 10), it triggers a warning. **Always index your date fields.**
4.  **Resource Usage:** Consumes cluster IOPS. Ensure your cluster isn't maxed out before enabling.

> **TIP:** Data encryption is handled via Amazon's server-side encryption with S3-managed keys (SSE-S3).

### Limitations
*   **No Direct Writes:** You cannot insert data directly into the archive.
*   **No Capped Collections:** Cannot archive from fixed-size collections.
*   **Small Data:** Data < 5 MiB after 7 days is skipped (prioritizes larger datasets).

---

## 17.2 Initializing Online Archive

### Step 1: Define the Rule
Example: Archive documents in `sample_supplies.sales` that are older than 5 days based on `saleDate`.

**Target Document:**
```javascript
{
  "_id": ObjectId("..."),
  "saleDate": ISODate("2015-08-25T10:01:02.918Z"),
  "storeLocation": "Seattle"
}
```

**Prerequisite:** Create an index on the date field.
```javascript
db.sales.createIndex({ "saleDate": 1 })
```

### Step 2: Initialize via Atlas CLI
```bash
atlas clusters onlineArchive create \
  --clusterName MongoDB-in-Action-M10 \
  --db sample_supplies --collection sales \
  --dateField saleDate --archiveAfter 5 \
  --partition saleDate,customer --output json
```

**Configuration Output (JSON):**
```json
{
  "criteria": {
    "type": "DATE",
    "dateField": "saleDate",
    "expireAfterDays": 5
  },
  "partitionFields": [
    { "fieldName": "saleDate", "order": 0 },
    { "fieldName": "customer", "order": 1 }
  ],
  "state": "PENDING"
}
```

### Step 3: Manage the Archive
*   **List Archives:** `atlas clusters onlineArchive list --clusterName ...`
*   **Start/Pause:** `atlas clusters onlineArchive start <ID> ...`

You can also rely on the **Atlas UI** (Sidebar -> Online Archive) to manage these rules visually.

---

## 17.3 Connecting and querying Online Archive

When you enable Online Archive, Atlas creates a **Federated Database Instance**.

### Connection Strings (Figure 17.1)
You receive three distinct connection options:
1.  **Federated (Combined):** Queries *both* Live Cluster + Archive.
2.  **Live Only:** Standard connection to your cluster.
3.  **Archive Only:** Connection solely to the cold storage.

**Example Connection:**
```bash
mongosh "mongodb://atlas-online-archive-URI..." --tls ...
```

### Performance & Cost
*   **Performance:**
    *   **Blocking Queries (Sorts):** Slow. Must wait for all data from slow object storage.
    *   **Streaming Queries (Find):** Faster. Atlas returns results as soon as they are found.
*   **Cost factors:**
    *   **Data Scanned:** Processing data from the archive is more expensive.
    *   **Data Access:** Charged per *partition* accessed.
    *   **Data Transfer:** Standard cloud transfer fees apply.

---

## 17.4 Restoring archived data

To move cold data *back* to your hot cluster (Reverse Archiving), use the `$merge` aggregation stage.

**Procedure:**
1.  **Retrieve Archive ID:** `atlas clusters onlineArchive list`
2.  **Pause the Archive:**
    ```bash
    atlas clusters onlineArchive pause <ID> --clusterName <NAME>
    ```
3.  **Verify Pause:** Ensure state is `PAUSED`.
4.  **Prepare Destination:** Connect to the **Live Cluster** and ensure a unique index exists to prevent duplicates.
    ```javascript
    db.sales.createIndex({ saleDate:1, customer:1}, {unique: true })
    ```
5.  **Connect to Archive:** Use the *Archive Only* connection string.
6.  **Run Restore Pipeline:**
    ```javascript
    db.sales.aggregate([
      {
        "$merge": {
          "into": {
            "atlas": {
              "clusterName": "MongoDB-in-Action-M10",
              "db": "sample_supplies",
              "coll": "sales"
            }
          },
          "on": [ "saleDate", "customer" ],
          "whenMatched": "keepExisting",
          "whenNotMatched": "insert"
        }
      }
    ])
    ```

> **WARNING:** Not recommended for massive datasets (>1TB) with many partitions due to performance overhead.

---

## 17.5 Summary
*   **Purpose:** Archiving cold data to S3/Azure to reduce reliable storage costs while keeping data queryable.
*   **Architecture:** Moves data based on rules (Date/Custom) via a 5-minute background job.
*   **Access:** Unified endpoint allows querying Live + Archive data simultaneously.
*   **Cost:** Pay for storage + query processing (scan/transfer).
*   **Restoration:** Possible via `$merge` but requires pausing the archive process first.

# Chapter 10: Delving into database as a service

In the first part of the book, we focused on the MongoDB server. Now, we delve into **MongoDB Atlas**, a managed Database-as-a-Service (DBaaS) that simplifies operations, allowing developers to focus on the application usage rather than administration.

Atlas automates:
*   Deployment
*   Scaling
*   Upgrades
*   Backups
*   Security
*   Monitoring & Real-time Analytics

It extends the core server with features like **Atlas Search**, **Vector Search**, **SQL Interface**, **Stream Processing**, **Data Federation**, and **Online Archive**.

---

### 10.1 Shared M0 and Flex clusters

These clusters run in a shared environment (resources shared with others) and are designed for learning, development, and low-throughput apps.

**Cluster Types:**
*   **M0 (Free Tier):**
    *   **Use Case:** Learning, small dev projects.
    *   **Specs:** 512 MB Storage, Shared RAM/vCPU.
    *   **Limit:** One per project. No backups (use `mongodump`).
*   **Flex:**
    *   **Use Case:** Small apps, development.
    *   **Specs:** 5GB Storage, 100 ops/sec included. Auto-scales.
    *   **Cost:** Pay for usage above limits.

> **Tip:** Scaling up/down M0, Flex, or M10 clusters requires **7–10 minutes of downtime**.

**Table 10.1: Configuration Limits (M0 & Flex)**
| Configuration Option | Limit |
| :--- | :--- |
| **Cloud/Region** | Subset of regions on AWS, GCP, Azure. |
| **Version Upgrade** | Auto-managed. Manual upgrade not allowed. |
| **Cluster Tier** | Only 1 M0 per project. |
| **Configuration** | Cannot config Memory or Storage size. |
| **Replication** | Fixed at 3 nodes. No tags. |
| **Sharding** | Not supported. |
| **Backups** | **M0:** None. **Flex:** Snapshots (no point-in-time restore). |
| **Advanced** | No Peering, Private Endpoints, Auditing, or Encryption Key Management. |

**Table 10.2: Operational Limits (M0 & Flex)**
| Operation | Limit |
| :--- | :--- |
| **Aggregation** | No `allowDiskUse`. Max 50 stages. Restricted stages (`$currentOp`, `$listLocalSessions`). |
| **Connections** | Max 500. |
| **Namespace** | Max 100 DBs, 500 Collections. |
| **Throughput** | **M0:** 100 ops/sec. **Flex:** 500 ops/sec. Throttling applies if exceeded. |
| **Storage** | **M0:** 0.5 GB. **Flex:** 5 GB. |
| **Monitoring** | Limited metrics (Connections, Logical Size, Network, Opscounter). |
| **Idle** | M0 paused after 60 days inactivity. |

---

### 10.2 Dedicated clusters

Dedicated clusters provide isolated resources and full Atlas features.

**Tiers:**
*   **M10 - M40 (Medium):** Dev/Test to Production.
    *   **M10/M20:** Burstable performance (good for steady low traffic).
    *   **M30+:** Production standard. Supports Global Clusters & Sharding.
*   **M50 - M200 (Large):** High performance, large storage (up to 4TB).
*   **M300+:** Enterprise pricing/scale.

**Table 10.3: Shared vs Dedicated Comparison**
| Feature | Free (M0) | Flex | Dedicated (M10+) |
| :--- | :--- | :--- | :--- |
| **Storage** | 512 MB | 5 GB | 10 GB – 4 TB |
| **Metrics** | Limited | Limited | Full Real-Time Performance Panel & Alerts |
| **Network** | Public IP only | Public IP only | VPC Peering, PrivateLink available |
| **Regions** | Limited subset | Limited subset | All AWS, GCP, Azure regions |
| **Backups** | None | Daily Snapshots | Point-in-time, Queryable Snapshots |
| **Sharding** | No | No | Yes (M30+) |
| **Advisor** | No | No | Performance Advisor included |

> **Tip:** Use Atlas CLI to scale: `atlas cluster update <your cluster> --tier M30`

#### 10.2.3 Auto scaling clusters and storage
Atlas can automatically adjust tier and storage based on usage.

**1. Cluster Auto-Scaling:**
*   **Metrics:** CPU & Memory Utilization.
    *   *Memory Util Formula:* `(Total - (Free + Buffers + Cached)) / Total * 100`
*   **Rules:**
    *   Scales within same class (General -> General).
    *   **Rolling process** (No downtime).
    *   Does NOT auto-scale by adding nodes/shards (Horizontal scaling is manual).
    *   **Max/Min:** You define the bounds (e.g., Min M30, Max M50).

> **Tip:** Enabled by default in UI, disabled by default via API/CLI.

**2. Storage Auto-Scaling:**
*   **Trigger:** When disk usage reaches **90%**.
*   **Behavior by Provider:**
    *   **AWS/GCP:** Increases to achieve 70% usage.
    *   **Azure:** Doubles storage.
*   **Constraint:** Atlas only scales storage **UP**, never down.

#### 10.2.4 Customizing Atlas Cluster storage
**Cluster Classes (M40+):**
1.  **Low CPU:** Cost-effective. More RAM, fewer vCPUs.
2.  **General:** Balanced standard.
3.  **Local NVMe SSD:** High-speed, low-latency I/O (AWS/Azure).

**Oplog Management:**
*   **Auto-scaling ON (Default):** Managed via `oplogMinRetentionHours` (Default 24h).
*   **Auto-scaling OFF:**
    *   General/Low-CPU: 5% of disk size.
    *   NVMe: 10% of disk size.

**IOPS (AWS M30+):**
*   **Standard:** Minimum 3000 IOPS.
*   **Provisioned:** Customize IOPS rate for consistent performance (lower p90 latency). 99.9% consistency vs 99% for standard.

---

### 10.3 Global Clusters
**Global Clusters** are sharded clusters distributed across multiple regions/countries for low latency (data locality) and compliance (data residency). Supported on M30+.

**Key Features:**
*   **Geo-Partitioning:** Assign data to specific Zones (shards) based on location fields.
*   **Read/Write Anywhere:** Local writes in specific zones.

**Table 10.4: Region Types**
| Region Type | Description |
| :--- | :--- |
| **Highest Priority** | Primary resides here. Accepts writes. |
| **Electable** | Secondaries eligible to become Primary. Provides HA. |
| **Read-only** | Non-electable secondaries. Used for low-latency local reads. |

> **Tip:** Add a Read-only node in the Highest Priority region of each zone to ensure local reads.

---

### 10.4 Going multi-region with workload isolation
Deploy clusters spanning AWS, Google Cloud, and Azure simultaneously.

**Benefits:**
*   Resilience against full cloud provider outages.
*   Workload isolation using specialized nodes.

> **Tip:** Large distances between electable nodes can increase election times and replication lag.

#### Node Types for Isolation
1.  **Electable Nodes:** Standard HA nodes. Participating in elections.
2.  **Read-only Nodes:** Enhance local read performance. No election rights.
3.  **Analytics Nodes:** (M10+) Specialized read-only nodes.
    *   **Use Case:** ETL processes, BI reporting.
    *   **Benefit:** Operational workload is isolated from heavy analytical queries.
    *   **Limit:** Up to 50 total nodes allowed per cluster.

---

### 10.5 Using predefined replica set tags for querying
Atlas provides built-in tags to route queries to specific nodes (Analytics, specific regions, etc.) in your connection string.

**Table 10.5: Predefined Tags**
| Tag Name | Application | Example |
| :--- | :--- | :--- |
| `nodeType` | Target specific node roles. | `{"nodeType": "ANALYTICS"}` |
| `region` | Target specific geographic regions. | `{"region": "US_EAST_2"}` |
| `provider` | Target cloud provider. | `{"provider": "GCP"}` |
| `workloadType` | Target operational vs analytics. | `{"workloadType": "OPERATIONAL"}` |

**Connection String Examples:**

**1. Routing to Analytics Nodes:**
Ideal for long-running reports.
```bash
mongodb+srv://user:pass@host/test?readPreference=secondary&readPreferenceTags=nodeType:ANALYTICS&readConcernLevel=local
```

**2. Isolating Operational Workload:**
Protect analytics nodes from standard app traffic.
```bash
mongodb+srv://user:pass@host/test?readPreference=secondary&readPreferenceTags=workloadType:OPERATIONAL&readConcernLevel=local
```

**3. Geo-Targeting (Nearest Region):**
Connect to nearest region, falling back to others.
```bash
mongodb+srv://.../test?readPreference=nearest&readPreferenceTags=provider:GCP,region:us-east1&readPreferenceTags=provider:GCP,region:us-east4&readPreferenceTags=&readConcernLevel=local
```
*Logic:* Try `us-east1` -> Then `us-east4` -> Then Any node (empty tag fallback).

---

### 10.6 Understanding Atlas custom write concern
For multi-region clusters, specific write concerns ensure data is durable across regions/providers before acknowledgment.

**Table 10.7: Custom Write Concerns**
| Write Concern | Description |
| :--- | :--- |
| `{ w: "twoRegions" }` | Ack from at least **2 regions**. |
| `{ w: "threeRegions" }` | Ack from at least **3 regions**. |
| `{ w: "twoProviders" }` | Ack from at least **2 cloud providers**. |

**Example Usage:**
```javascript
db.routes.insertOne(
    {
       airline: { id: 410, name: 'Lufthansa', alias: 'LH', iata: 'DLH' },
       src_airport: 'MUC',
       dst_airport: 'JFK'
    },
    { writeConcern: { w: "threeRegions" } }
)
```

---

### 10.7 Summary
*   **Atlas** is a comprehensive DBaaS handling scaling, backups, and security.
*   **M0/Flex** are for learning; **M10+** dedicated clusters are for production.
*   **Auto-scaling** handles storage (up only) and tier sizing without downtime.
*   **Global Clusters** allow geo-partitioning for compliance and speed.
*   **Multi-Cloud** support enables resilience across AWS, Azure, and GCP.
*   **Analytics Nodes** isolate heavy reporting loads from operational traffic.
*   **Replica Set Tags** and **Custom Write Concerns** give granular control over query routing and data durability in distributed environments.

### Pattern: Low-Water Mark (Patterns of Distributed Systems)

A **Low-Water Mark** is an index in the Write-Ahead Log (WAL) that indicates which portion of the log is no longer needed and can be safely discarded (deleted from disk).

#### Problem: Indefinite Log Growth
Even with **Segmented Logs**, total disk storage will grow indefinitely if not periodically checked. A mechanism is required to prune older, redundant data to prevent disk exhaustion.

#### Solution: Automated Log Cleaning
A background component, the **Log Cleaner**, continuously monitors the log's state and deletes files older than the current "low-water mark."

**Implementation (Pseudo-Java):**
```java
class WriteAheadLog {
    public WriteAheadLog(Config config) {
        this.logCleaner = new LogCleaner(config, this);
        this.logCleaner.startup();
    }
}

class LogCleaner {
    private void scheduleLogCleaning() {
        singleThreadedExecutor.schedule(() -> {
            cleanLogs();
        }, config.getCleanTaskIntervalMs(), TimeUnit.MILLISECONDS);
    }

    public void cleanLogs() {
        List<WALSegment> segmentsTobeDeleted = getSegmentsToBeDeleted();
        for (WALSegment walSegment : segmentsTobeDeleted) {
            wal.removeAndDeleteSegment(walSegment);
        }
        scheduleLogCleaning(); // Reschedule next run
    }
}
```

#### Log Pruning Strategies

##### 1. Snapshot-Based Low-Water Mark
Common in consensus implementations like **ZooKeeper** and **etcd (Raft)**. 
*   **Process:** The storage engine periodically takes a **Snapshot** of its current state and records the highest WAL index included in that snapshot (`snapShotTakenAtLogIndex`).
*   **Pruning:** All log segments with indexes lower than that snapshot index become redundant and are marked for deletion.

```java
class SnapshotBasedLogCleaner extends LogCleaner {
    @Override
    List<WALSegment> getSegmentsToBeDeleted() {
        return getSegmentsBefore(this.snapshotIndex);
    }

    List<WALSegment> getSegmentsBefore(Long snapshotIndex) {
        List<WALSegment> markedForDeletion = new ArrayList<>();
        List<WALSegment> sortedSavedSegments = wal.sortedSavedSegments;
        for (WALSegment segment : sortedSavedSegments) {
            if (segment.getLastLogEntryIndex() < snapshotIndex) {
                markedForDeletion.add(segment);
            }
        }
        return markedForDeletion;
    }
}
```

##### 2. Time-Based Low-Water Mark
Used in systems like **Kafka**, where logs represent a stream of messages intended for consumption over a fixed duration (e.g., 7 days or weeks).
*   **Process:** Each log entry includes a timestamp. The cleaner discards entire segments once all messages within that segment are older than a configured threshold (`logMaxDurationMs`).

```java
class TimeBasedLogCleaner extends LogCleaner {
    private List<WALSegment> getSegmentsPast(Long logMaxDurationMs) {
        long now = System.currentTimeMillis();
        List<WALSegment> markedForDeletion = new ArrayList<>();
        List<WALSegment> sortedSavedSegments = wal.sortedSavedSegments;
        for (WALSegment segment : sortedSavedSegments) {
            Long lastTimestamp = segment.getLastLogEntryTimestamp();
            if (now - lastTimestamp > logMaxDurationMs) {
                markedForDeletion.add(segment);
            }
        }
        return markedForDeletion;
    }
}
```

##### 3. Size-Based Low-Water Mark
The simplest strategy: once the total log size exceeds a maximum configured threshold, the oldest segments are deleted until the system is back within limits.

*(Awaiting the next parts of the chapter for further details...)*

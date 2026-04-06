### Pattern: Segmented Log (Patterns of Distributed Systems)

A **Segmented Log** splits a single large log file (like a Write-Ahead Log) into multiple smaller files (segments) to improve operational efficiency and system performance.

#### Problem: The Single Log Bottleneck
*   **Startup Latency:** Reading a single massive log file during system startup leads to slow recovery times.
*   **Maintenance Difficulty:** Periodically cleaning up (purging) older logs from a single large file is complex to implement and resource-intensive.

#### Solution: Log Segmentation
Instead of a single file, the log is broken into segments of a fixed size (e.g., 64MB or 128MB). Once the current "open" segment reaches its limit, it is flushed to disk and a new "open" segment is rolled over.

**Core Implemention (Pseudo-Java):**
```java
class WriteAheadLog {
    public Long writeEntry(WALEntry entry) {
        maybeRoll(); // Check if size exceeds limit
        return openSegment.writeEntry(entry);
    }

    private void maybeRoll() {
        if (openSegment.size() >= config.getMaxLogSize()) {
            openSegment.flush();
            sortedSavedSegments.add(openSegment);
            long lastId = openSegment.getLastLogEntryIndex();
            openSegment = WALSegment.open(lastId, config.getWalDir());
        }
    }
}

class WALSegment {
    private static final String logPrefix = "log";
    private static final String logSuffix = ".log"; 
    
    public static String createFileName(Long startIndex) {
        return logPrefix + "_" + startIndex + logSuffix;
    }
    public static Long getBaseOffsetFromFileName(String fileName) {
        String[] nameAndSuffix = fileName.split(logSuffix);
        String[] prefixAndOffset = nameAndSuffix[0].split("_");
        if (prefixAndOffset[0].equals(logPrefix))
            return Long.parseLong(prefixAndOffset[1]);
        return -1l;
    }
}
```

#### Mapping Logical Offsets to Segments
There must be an efficient way to find which physical log file contains a specific logical log offset (or sequence number):
1.  **Offset-based Naming:** Generating the segment name using a known prefix and its **base offset** (e.g., `log_1000.log`, `log_2500.log`).
2.  **Two-part Addressing:** Dividing the sequence number into the filename and the transaction offset within that file.

#### Efficient Read Operations
Reading from the log involves two steps:
1.  **Identify the Segment:** Use the base offsets of the files to find the segment containing the `startIndex`. This is typically done by scanning the sorted list of segments from newest to oldest.
2.  **Sequential Read:** Read from the identified segment and all subsequent segments until completion.

**Complete Logic for identifying and reading segments:**
```java
class WriteAheadLog {
    public List<WALEntry> readFrom(Long startIndex) {
        List<WALSegment> segments = getAllSegmentsContainingLogGreaterThan(startIndex);
        return readWalEntriesFrom(startIndex, segments);
    }

    private List<WALSegment> getAllSegmentsContainingLogGreaterThan(Long startIndex) {
        List<WALSegment> segments = new ArrayList<>();
        // Start from the last (newest) segment and go backwards
        for (int i = sortedSavedSegments.size() - 1; i >= 0; i--) {
            WALSegment walSegment = sortedSavedSegments.get(i);
            segments.add(walSegment);
            if (walSegment.getBaseOffset() <= startIndex) {
                // Break for the first segment found with a baseOffset less than the startIndex
                break;
            }
        }
        
        // Add the current open segment if it holds indices greater than the startIndex
        if (openSegment.getBaseOffset() <= startIndex) {
            segments.add(openSegment);
        }
        return segments;
    }
}
```

*(Awaiting the next parts of the chapter for further details...)*

When increasing the number of replicas or replacing a dead node, how do you ensure the new follower gets an accurate copy of the leader's data?
You cannot simply copy the data files from disk. The database is constantly in flux, so a standard file copy would get different parts of the database at different points in time, resulting in a corrupted, nonsensical file. You *could* lock the database to do this, but that would cause massive downtime.

The standard zero-downtime process is:
1.  **Consistent Snapshot:** Take a consistent snapshot of the leader's database without locking the entire system.
2.  **Copy Dataset:** Copy the snapshot over to the new follower node.
3.  **Request Backlog:** The new follower connects to the leader and requests all data changes that happened *since the exact moment* the snapshot was taken. (This requires the snapshot to be associated with an exact position in the leader's replication log, e.g., PostgreSQL's log sequence number, or MySQL's binlog coordinates).
4.  **Catch Up:** Once the follower processes this backlog, it has "caught up" and can begin processing live streams of changes like a normal follower.
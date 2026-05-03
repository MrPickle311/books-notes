# Chapter 9: High Availability Bad Practices

PostgreSQL is famous for its resilience, but this resilience requires following established High Availability (HA) best practices. Treating HA and backups as afterthoughts will inevitably lead to downtime and data loss.

## 1. Believing RAID or Replication is a Backup
Many administrators believe they don't need backups because their disks are configured in a RAID 1 mirror, or they have a standby replica.

*   **RAID is not a backup:** RAID protects against a single hard drive dying. If filesystem corruption occurs, or if a user accidentally drops a table, RAID will perfectly and instantly duplicate that corruption or deletion to the mirrored drive.
*   **Replication is not a backup:** If someone executes `DROP TABLE patient_data;` on the primary server, that destructive command is instantly streamed to the replica, deleting the data there as well.
*   **VM/Filesystem Snapshots are not backups:** Taking a snapshot of a running PostgreSQL data directory is highly dangerous. Unless the database is cleanly shut down or quiesced using `pg_backup_start()`, a snapshot captures an inconsistent state that requires crash recovery and risks total data corruption.

**Mitigation:** You must take actual, binary backups using tools built specifically for PostgreSQL, like `pg_basebackup`.

---

## 2. No Point-in-Time Recovery (PITR)
Many environments rely on `pg_dump` to create nightly backups. This is a massive mistake for production databases.

If you take a `pg_dump` at 2:00 AM, and an employee accidentally deletes critical data at 2:00 PM, restoring the `pg_dump` means you permanently lose 12 hours of business data. Furthermore, `pg_dump` is a logical backup; restoring it requires PostgreSQL to rebuild every single index from scratch, which can take hours or days for large databases.

**Mitigation:** Implement **Point-in-Time Recovery (PITR)**. PITR uses a `pg_basebackup` combined with continuous WAL archiving. Because every transaction is logged in the WAL, PITR allows you to roll your database back to the precise millisecond before a disaster occurred, ensuring zero data loss.

---

## 3. Backing Up Manually
Relying on a human to manually run a backup script is a recipe for disaster. Humans go on vacation, get sick, forget, or make typos. A common outcome of manual backups is accidentally storing the backup file on the exact same physical hard drive as the database itself.

*Rule: "A backup that is not automated is no backup at all."*

**Mitigation:** Use dedicated, automated open-source backup tools like **Barman** or **pgBackRest**. They handle WAL archiving, retention policies, remote transfers, and PITR automatically.

---

## 4. Not Testing Backups
Countless companies have experienced catastrophic server failures, only to discover their nightly backup script silently broke six months ago, or the resulting backup files are corrupted and unreadable.

If you do not test your backups, you do not have backups.

**Mitigation:** Run automated "fire drills." Regularly restore a backup to an isolated test server.
1. Verify base backup checksums using `pg_verifybackup`.
2. Apply the WAL files.
3. Start the database and verify it reaches a consistent state.
4. (Optional) Use extensions like `pageinspect` to verify block-level integrity.

---

## 5. Not Having Redundancy (Ignoring RTO)
Even with perfect backups, relying solely on backups means your **Recovery Time Objective (RTO)** will be terrible. If a primary server catches fire, provisioning a new server, downloading a multi-terabyte backup, and extracting it can take days.

**Mitigation:** Implement physical redundancy. Maintain at least one hot standby server using streaming replication. If the primary dies, you can failover to the standby in seconds, saving your organization from massive revenue and reputational loss.

---

## 6. Using Custom HA Scripts (Reinventing the Wheel)
When administrators set up a standby server, they often write custom bash scripts to monitor the primary and automatically promote the standby if the primary goes offline. **Do not do this.**

Custom scripts almost always fail to handle two critical edge cases:
1.  **Split-Brain:** If the network link between the primary and standby fails (a network partition), the script assumes the primary is dead and promotes the standby. But the primary is actually still alive and accepting writes from users! You now have two primary databases with diverging data, leading to a catastrophic data reconciliation nightmare.
2.  **Replication Lag & Timeline Forks:** If a script promotes a lagging standby, the new primary will be missing transactions. The other replicas will refuse to connect to it because their WAL timelines are now incompatible, requiring complex manual interventions with `pg_rewind`.

**Mitigation:** Use battle-tested, community-trusted HA tools like **Patroni** or **RepMgr** (or **CloudNativePG** for Kubernetes). These tools use distributed consensus (quorum) and fencing to guarantee split-brain never happens and handle timeline divergence automatically.

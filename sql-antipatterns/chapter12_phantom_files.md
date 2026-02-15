# Chapter 12: Phantom Files

> **"Images are too big for the database. I'll just store the file path and keep the images on disk."**

This is the **Phantom Files** antipattern. It occurs when developers store media (Images, PDFs) on the filesystem and only store the string path (e.g., `/var/www/uploads/img_123.jpg`) in the database. While popular, this approach breaks transaction integrity and backup consistency.

---

## 12.1 The Objective: Store Images or Bulky Media
You need to associate media with your data.
*   User Avatars.
*   Bug Screenshots.
*   Product Photos.

---

## 12.2 The Antipattern: Assume You Must Use Files
You decide that databases are only for "text and numbers," so you store images on the disk.

```sql
CREATE TABLE Screenshots (
  id      SERIAL PRIMARY KEY,
  bug_id  BIGINT,
  path    VARCHAR(255) -- '/images/screen_01.png'
);
```

### Why it fails
1.  **No Transaction Isolation (ACID is broken)**:
    *   **Delete**: You `DELETE` the row. The transaction commits. But the file deletion crashes. Now you have an orphaned file.
    *   **Rollback**: You upload a file, then the database `INSERT` fails. You roll back. The file is still on disk (orphaned).
    *   **Read Consistency**: User A is updating the image. User B reads the *old* path but sees the *new* image content because files don't respect MVCC (Multi-Version Concurrency Control).

2.  **Backups are Inconsistent**:
    *   Database Backup runs at 12:00.
    *   Filesystem Backup runs at 12:10.
    *   A user uploads an image at 12:05.
    *   **Restore**: The database says the image exists (it was backed up), but the file is missing (it wasn't backed up yet). Or vice versa. **Phantom Files**.

3.  **Security Gaps**:
    *   Database Permissions (`GRANT SELECT`) don't protect files. If a hacker gets shell access, they can just reading `/var/www/uploads`.

### Legitimate Uses of the Antipattern
*   **CDN Delivery**: If you serve 10,000 images per second, a File System (Nginx/Apache) or S3 is much faster than a Database.
*   **Huge Files**: Storing 4GB videos in a DB is unwieldy.
*   **Editability**: If you need to open files in Photoshop directly from the server.

## 12.3 The Solution: Use BLOB Data Types As Needed
If you need integrity (e.g., Medical Records, Legal Documents, Small Avatars), store them in the DB.

```sql
CREATE TABLE Screenshots (
  id      SERIAL PRIMARY KEY,
  content BLOB -- Binary Large Object
);
```
*   **MySQL**: `BLOB`, `MEDIUMBLOB` (16MB), `LONGBLOB` (4GB).
*   **Postgres**: `BYTEA`.
*   **Oracle**: `BLOB`.

### Working with BLOBs in SQL
You don't always need an app to manage files. The Database can do it.

**1. Loading Images (MySQL)**
```sql
UPDATE Screenshots
SET content = LOAD_FILE('/tmp/image.jpg')
WHERE id = 1;
```

**2. Exporting Images (MySQL)**
```sql
SELECT content
INTO DUMPFILE '/tmp/export_image.jpg'
FROM Screenshots
WHERE id = 1;
```

### Why it wins
1.  **ACID Guaranteed**:
    *   `ROLLBACK` deletes the image data automatically.
    *   `DELETE FROM Screenshots` reclaims space immediately.

2.  **Backup Peace of Mind**:
    *   `mysqldump` includes the images. Your backup is **Self-Contained**.

3.  **Serving Images (Code Example)**:
    *   You don't need a file on disk to serve an image to a browser.
    ```python
    @app.route('/image/<id>')
    def get_image(id):
        row = db.execute("SELECT content FROM Screenshots WHERE id=%s", id)
        if row:
            # Serve binary directly with correct MIME type
            return Response(row['content'], mimetype='image/jpeg')
        return Response(status=404)
    ```

> **Takeaway**: 
> *   **Filesystem**: For Web Scale (CDNs), huge videos.
> *   **Database (BLOB)**: For Integrity, Security, and Atomic Backups.

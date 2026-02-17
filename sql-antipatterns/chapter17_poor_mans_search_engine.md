# Chapter 17: Poor Man’s Search Engine

> **"I need search functionality. I'll just use `LIKE '%keyword%'`."**

This is the **Poor Man's Search Engine** antipattern. It occurs when developers try to implement full-text search using simple SQL pattern matching operators like `LIKE` or regular expressions, leading to terrible performance and inaccurate results.

---

## 17.1 The Objective: Full-Text Search
You need to search for words or phrases within text columns.
*   **Keywords**: "Find all bugs about 'crash'".
*   **Relevance**: "Show me the most relevant bugs first."
*   **Word Forms**: "crash" should also match "crashes", "crashed", "crashing".

---

## 17.2 The Antipattern: Pattern Matching Predicates
You use SQL's standard string comparison tools.

### 1. The `LIKE` Predicate
```sql
SELECT * FROM Bugs WHERE description LIKE '%crash%';
```
*   **The Wildcard (`%`)**: Matches any sequence of characters.
    *   `%` at the start means the database cannot use a standard B-Tree index (because the index is sorted by the *beginning* of the string).
    *   **Result**: Full Table Scan (O(N)).

### 2. Regular Expressions (`REGEXP`)
```sql
-- MySQL
SELECT * FROM Bugs WHERE description REGEXP 'crash';
```
*   **Cost**: Even more expensive than `LIKE`. The DB must load every row, compile the Regex, and execute it against the text.

### Why it fails
1.  **Performance Checkmate**:
    *   Comparing integers is fast.
    *   Comparing strings is slow.
    *   Scanning *every* string in a table for a substring pattern is **excruciatingly** slow. As the table grows, the search becomes unusable.

2.  **The Scunthorpe Problem (False Positives)**:
    *   Query: `LIKE '%one%'`
    *   Matches: "money", "phone", "lonely", "stone".
    *   You wanted the number "one", but you got "money".

3.  **No Logic**:
    *   It doesn't handle **Synonyms** ("crash" vs "error").
    *   It doesn't handle **Plurals** ("bug" vs "bugs").
    *   It doesn't provide **Ranking** (Relevance).

### Legitimate Uses of the Antipattern
*   **Small Data**: If you have 100 rows, a Full Table Scan is instantaneous. Don't overengineer.
*   **Complex Patterns**: If you need to find SKU codes like `ABC-99-%`, `LIKE` is the correct tool.

## 17.3 The Solution: Use the Right Tool
If you need a search engine, use a search engine (or the one built into your DB).

### Solution 1: Postgres Full-Text Search
Postgres offers industrial-strength text search with `TSVECTOR` and `TSQUERY`.

**1. The Setup (Lexemes & Stemming)**
Postgres breaks text into "Lexemes" (normalized roots).
*   "Crash", "Crashes", "Crashing" -> all become `'crash'`.
```sql
-- Create a Generated Column that auto-updates
ALTER TABLE Bugs ADD COLUMN ts_bug_text TSVECTOR 
GENERATED ALWAYS AS (to_tsvector('english', summary || ' ' || description)) STORED;
```

**2. The Index (GIN)**
A **Generalized Inverted Index (GIN)** maps words to rows. It is blindingly fast.
```sql
CREATE INDEX idx_gin ON Bugs USING GIN(ts_bug_text);
```

**3. The Search & Ranking**
Use `@@` to match and `ts_rank` to sort by relevance.
```sql
SELECT summary, ts_rank(ts_bug_text, query) as rank
FROM Bugs, to_tsquery('english', 'crash & !save') query
WHERE ts_bug_text @@ query
ORDER BY rank DESC;
```
*   **Pros**: Supports complex logic (`&`, `|`, `!`), stemming, stop words, and relevance ranking. Built-in to the DB.

### Solution 2: External Search Engines
For massive scale, offload search to **Elasticsearch**, **Solr**, or **Sphinx**.
*   **Pros**: Features like "Did you mean?", Faceted Search, and distributed scaling.
*   **Cons**: You must keep the external index in sync with your DB (Change Data Capture).

### Solution 3: Roll Your Own (Inverted Index)
If you are strictly limited to standard SQL (no vendor extensions), you can build the index yourself.

Basically, an inverted index is a list of all words one might search for. In a many-to-many relationship, the index associates these words with the text entries that contain the respective word. That is, a word like crash can appear in many bugs, and each bug may match many other keywords.

**1. The Schema (Inverted Index)**
Create a Many-to-Many relationship between "Keywords" and "Bugs".
```sql
CREATE TABLE Keywords (
  keyword_id SERIAL PRIMARY KEY,
  word       VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE BugsKeywords (
  keyword_id INT NOT NULL,
  bug_id     INT NOT NULL,
  PRIMARY KEY (keyword_id, bug_id),
  FOREIGN KEY (keyword_id) REFERENCES Keywords(keyword_id),
  FOREIGN KEY (bug_id) REFERENCES Bugs(bug_id)
);
```

**2. The Logic (Lazy Indexing Stored Procedure)**
```sql
CREATE PROCEDURE BugsSearch(IN p_keyword VARCHAR(40))
BEGIN
  DECLARE v_keyword_id INT;

  -- 1. Check if we've searched for this before
  SELECT keyword_id INTO v_keyword_id FROM Keywords WHERE word = p_keyword;

  -- 2. If NOT found, pay the penalty once
  IF (v_keyword_id IS NULL) THEN
    INSERT INTO Keywords (word) VALUES (p_keyword);
    SET v_keyword_id = LAST_INSERT_ID();

    -- Populate the detailed index using the slow scan
    INSERT INTO BugsKeywords (bug_id, keyword_id)
      SELECT bug_id, v_keyword_id FROM Bugs
      WHERE summary LIKE CONCAT('%', p_keyword, '%')
         OR description LIKE CONCAT('%', p_keyword, '%');
  END IF;

  -- 3. Return the results using the fast index
  SELECT b.* FROM Bugs b
  JOIN BugsKeywords k USING (bug_id)
  WHERE k.keyword_id = v_keyword_id;
END
```

**3. The Trigger (Keep it Fresh)**
When a new Bug is inserted, check it against *all known keywords*.
```sql
CREATE TRIGGER Bugs_Insert AFTER INSERT ON Bugs
FOR EACH ROW
BEGIN
  INSERT INTO BugsKeywords (bug_id, keyword_id)
    SELECT NEW.bug_id, k.keyword_id FROM Keywords k
    WHERE NEW.description LIKE CONCAT('%', k.word, '%')
       OR NEW.summary LIKE CONCAT('%', k.word, '%');
END
```

**4. The Search Query (Fast!)**
```sql
CALL BugsSearch('crash');
```
*   **Pros**: Database Agnostic. Works on any SQL version.
*   **Cons**:
    *   **High Maintenance**: Requires Triggers to update `BugsKeywords` when a Bug is updated.
    *   **Storage**: Consumes significant space.
    *   **First Search Penalty**: The first person to search for a word waits for the Table Scan.

> **Takeaway**: SQL is for atomic values. If you need to search *inside* the text, use **Full-Text Indexing technology**.

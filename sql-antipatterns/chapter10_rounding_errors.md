# Chapter 10: Rounding Errors (FLOAT vs NUMERIC)

> **"I need to store prices like $19.95. I'll use `FLOAT` because it has decimal points."**

This is the **Rounding Errors** antipattern. It occurs when developers use `FLOAT` or `DOUBLE` data types to store exact values like currency. These types are designed for scientific calculations (approximate values), not financial ones.

---

## 10.1 The Objective: Use Fractional Numbers
You need to store non-integers.
*   Hourly Rate: `$59.95`
*   Hours Worked: `2.5`
*   Calculation: `Rate * Hours = Cost`.

---

## 10.2 The Antipattern: Use FLOAT Data Type
You choose `FLOAT` because it sounds like "Floating Point".

```sql
CREATE TABLE Accounts (
  account_id  SERIAL PRIMARY KEY,
  hourly_rate FLOAT
);

INSERT INTO Accounts (hourly_rate) VALUES (59.95);
```

### Why it fails
1.  **Imprecise Storage (IEEE 754)**:
    *   Computers use binary (Base-2).
    *   `59.95` in decimal cannot be exactly represented in binary (just like `1/3` in decimal is `0.3333...`).
    *   The DB actually stores: `59.950000762939`.

2.  **Equality Checks Fail**:
    *   `SELECT * FROM Accounts WHERE hourly_rate = 59.95;` -> **No Results!**
    *   Because `59.950000762939 != 59.95`.

3.  **Aggregate Drift**:
    *   `SUM(hourly_rate)` amplifies the tiny errors.
    *   Adding millions of rows results in "cents drifting", making accounting books unbalanced.
    *   Multiplying (Compound Interest) makes errors explode exponentially.

### Legitimate Uses of the Antipattern
*   **Scientific Data**: Temperatures, Distances, Speeds.
*   **Why**: Values are naturally imprecise (22.5 C might actually be 22.500001).
*   **Operations**: You usually do `AVG()`, `MIN()`, or `> 20`. You almost never do `= 22.500`.

## 10.3 The Solution: Use NUMERIC Data Type
Use fixed-precision types: `NUMERIC` or `DECIMAL`.

```sql
ALTER TABLE Accounts ADD COLUMN hourly_rate NUMERIC(9,2); 
```
*   **Precision (9)**: Total digits (e.g., 1234567.89).
*   **Scale (2)**: Digits after decimal (e.g., .89).

### Why it wins
1.  **Exact Storage**:
    *   `59.95` is stored exactly as `59.95`.
    *   `SELECT * FROM Accounts WHERE hourly_rate = 59.95` -> **Returns the row!**

2.  **Predictable Math**:
    *   `59.95 * 1000000000` = `59950000000` (Exactly).
    *   No drifting cents in your Accounting reports.

> **Takeaway**: 
> *   **Float**: Scientific, Approximate, Large Range (10^308).
> *   **Numeric**: Financial, Exact, Fixed Precision.
> *   **Rule**: If it involves money, use `NUMERIC`.

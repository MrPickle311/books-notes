# Chapter 8: Security Bad Practices

PostgreSQL is highly secure by default, but operator error and sloppy administrative habits are the primary causes of data breaches and privilege escalation. This chapter details the most common ways database administrators inadvertently expose their systems to attackers.

## 1. Using `psql -W` or `--password`
It is a common habit to use the `-W` flag when connecting via the command line to force a password prompt. This is a notorious "footgun."

Using `-W` forces the *client* to prompt for a password, completely regardless of whether the *server* actually requires one. If a server is misconfigured to allow passwordless connections, the client will still ask for a password, you will type it, and you will be connected. You might even mistype the password and still connect! This lulls the administrator into a false sense of security, making them believe the server is protected when it is actually wide open.

**Mitigation:** Never use `-W`. If the server is correctly configured to require a password, it will automatically prompt you for one.

---

## 2. Setting `listen_addresses = '*'`
When developers struggle to connect to a new database server over a network, they often resort to changing `listen_addresses = 'localhost'` to `'*'`.

This entirely defeats PostgreSQL's "security by default" posture. Setting this to `*` tells the database to accept connection requests on *every* network interface available on the machine. If the server has a public-facing IP address, your database is now exposed to the open internet, vulnerable to port scanners and brute-force attacks.

**Mitigation:** Explicitly specify the IP addresses of the specific private networks (e.g., VPNs or intranets) that should be allowed to reach the database, and utilize firewall rules.

---

## 3. Using `trust` in `pg_hba.conf`
The `pg_hba.conf` file controls Host-Based Authentication. Because its syntax can be frustrating, administrators sometimes use the `trust` method for an entire subnet (e.g., `10.10.10.0/24`) to quickly get applications connected.

`trust` is **not** an authentication method. It literally means "let anyone from this IP in with zero authentication." If any single application, device, or machine on that trusted subnet gets compromised, the attacker immediately gains full, passwordless access to your entire database.

**Mitigation:** Never use `trust` over a network. Always use modern challenge-and-response authentication, such as `scram-sha-256`, and strictly limit entries to specific hosts.

---

## 4. Databases Owned by a Superuser
Following bad online tutorials often leads to creating production databases, schemas, and tables owned by the `postgres` superuser account.

PostgreSQL superusers bypass almost all internal security checks, including Row-Level Security (RLS) policies. If a superuser creates a stored procedure (like a script to reset the schema) and an unprivileged application user accidentally executes it, the procedure will run with superuser privileges and can instantly destroy production data.

**Mitigation:** Adhere strictly to the Principle of Least Privilege. Restrict superuser accounts to database administration only. Create dedicated, unprivileged roles to own application databases and objects.

---

## 5. Setting `SECURITY DEFINER` Carelessly
By default, PostgreSQL functions execute with the privileges of the user calling them (`SECURITY INVOKER`). 
If you declare a function as `SECURITY DEFINER`, it acts like the UNIX `setuid` bit—it executes with the privileges of the user who *owns* the function.

If a developer writes a `SECURITY DEFINER` function to calculate privileged financial data and forgets to restrict execution rights, *anyone* in the database can execute it and view that restricted data. Worse, if the function is poorly written, attackers can use it to inject code and escalate their privileges to match the function owner.

**Mitigation:** 
1. Keep `SECURITY DEFINER` functions as simple and single-purpose as possible.
2. Explicitly `REVOKE EXECUTE ON FUNCTION ... FROM PUBLIC`.
3. Selectively `GRANT EXECUTE` only to the specific roles that need it.
4. Hardcode a safe `search_path` inside the function to prevent object hijacking.

---

## 6. Choosing an Insecure Search Path
The `search_path` determines the order in which PostgreSQL looks for unqualified object names (e.g., typing `customers` instead of `erp.customers`). The default path is `"$user", public`.

If untrusted users have the right to create objects in the `public` schema, they can perform **Query Hijacking**. An attacker can create a malicious function or table in the `public` schema with the exact same name as a trusted object. If their schema is checked before the legitimate schema, your query will silently execute the attacker's code or write to the attacker's table.

**Mitigation:**
1. Revoke creation rights on the public schema: `REVOKE CREATE ON SCHEMA public FROM PUBLIC;` (PostgreSQL 15+ does this by default, but older upgraded databases may still be vulnerable).
2. Use explicit schema qualification (`erp.customers`) in application code.
3. Remove untrusted schemas from the `search_path`.

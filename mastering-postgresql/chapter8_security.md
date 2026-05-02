# Chapter 6: Managing PostgreSQL Security

Security in PostgreSQL is a multi-layered concept. Because applications are exposed to increasing internal and external threats, properly configuring permissions is vital.

## The Mental Model for Security

To debug security or connection issues effectively, you must understand the "chain of permissions". To read a single value in a table, every single link in this chain must permit the action:

1.  **Bind Addresses:** `listen_addresses` in `postgresql.conf`
2.  **Host-based Access Control:** `pg_hba.conf`
3.  **Instance-Level Permissions:** Users, roles, login capability, and database creation.
4.  **Database-Level Permissions:** Connecting to a specific database, creating schemas.
5.  **Schema-Level Permissions:** `USAGE` on a schema, creating objects inside it.
6.  **Table-Level Permissions:** `SELECT`, `INSERT`, `UPDATE`, `DELETE`.
7.  **Column-Level Permissions:** Restricting access to specific columns.
8.  **Row-Level Security (RLS):** Restricting access to specific rows based on the user's identity.

If *any* of these levels deny access, the operation fails. 

---

## Managing Network Security

The absolute first line of defense is network security. If a connection cannot physically reach the database process, no authentication or permissions checks even occur.

### Understanding Bind Addresses (`listen_addresses`)

By default, PostgreSQL **does not accept remote connections**. It only listens on `localhost`. If you attempt to connect from a remote machine (e.g., via `telnet`), the connection is instantly rejected by the operating system because the PostgreSQL port is essentially closed to the outside world:

```bash
[hs@test ~]$ telnet 192.168.0.123 5432
Trying 192.168.0.123...
telnet: connect to address 192.168.0.123: Connection refused
```

To fix this, you must edit `postgresql.conf`:

```ini
# - Connection Settings -
listen_addresses = 'localhost'  # defaults to 'localhost'; use '*' for all
```

*   **How it works:** `listen_addresses` specifies which Network Interface Cards (NICs) the database binds to. If your server has 4 network cards (4 IPs), you can configure PostgreSQL to listen on 1, 2, 3, or all of them.
*   **Common Mistake:** You must enter the **server's IP address** here, NOT the client's IP. To listen on all available interfaces, use `*`.
*   **Restart Required:** Changing `listen_addresses` requires a full PostgreSQL service restart.

### Crucial Connection Settings

In addition to the bind address, several other connection parameters reside in `postgresql.conf`:

```ini
port = 5432
max_connections = 100
superuser_reserved_connections = 3
unix_socket_directories = '/tmp'
```

*   **`max_connections` & `superuser_reserved_connections`:** By default, PostgreSQL allows 100 concurrent connections. However, 3 are explicitly reserved for superusers. This means normal applications will start seeing "too many clients" errors once they hit 97 connections. The final 3 slots ensure a database administrator can always log in to fix a broken system.
*   **Why Restarts are Mandatory:** Changing connection limits (`max_connections`) requires a full server restart. This is because PostgreSQL calculates and allocates a static block of shared memory at startup based directly on these connection limits. This memory cannot be dynamically resized on the fly.

### Inspecting Connections and Performance

A common question in PostgreSQL administration is whether increasing the connection limit (`max_connections`) inherently degrades system performance.

The short answer is **not significantly**. While each connection introduces some overhead due to context switching, the database can generally handle a large number of connections cheaply. 

However, the real performance bottleneck is not the connection count itself, but the **number of open snapshots**. Each active transaction or query maintains a "snapshot" of the database state to ensure consistency (MVCC). The more open snapshots the system must manage, the greater the internal overhead and complexity for the database engine.

> [!NOTE]
> For detailed real-world benchmarks on this topic, refer to: [Performance Impact of max_connections](https://www.cybertec-postgresql.com/max_connections-performance-impacts/).

In the next section, we will look at how to further enhance security by restricting connections to Unix domain sockets instead of TCP.

---

## Living in a World without TCP (Unix Sockets)

In many deployments, the database application and the PostgreSQL server reside on the exact same machine. In this scenario, you can bypass the network entirely by communicating via **Unix domain sockets**. 

This approach provides two massive benefits:
1.  **Security:** Your database is completely isolated. The application connects locally without exposing any ports to the outside world.
2.  **Performance:** Bypassing the TCP/IP stack (even the `127.0.0.1` loopback device) removes significant network latency overhead.

By default, PostgreSQL creates Unix sockets in the `/tmp` directory. If you run multiple database instances on a single machine, each instance requires its own separate socket directory.

### Benchmark: Unix Sockets vs. TCP Loopback

The performance difference is often surprising. Let's look at a simple `pgbench` benchmark that repeatedly executes the simplest possible query: `SELECT 1;`.

**Test 1: Connecting via Unix Socket (No host specified)**
```bash
[hs@linux ~]$ pgbench -f /tmp/script.sql -c 10 -T 5 -U postgres postgres 2> /dev/null
...
number of transactions actually processed: 871407
tps = 174377.935625 (excluding connections establishing)
```
*Result: ~174,000 transactions per second (TPS).*

**Test 2: Connecting via TCP Loopback (`-h localhost`)**
```bash
[hs@linux ~]$ pgbench -f /tmp/script.sql -h localhost -c 10 -T 5 -U postgres postgres 2> /dev/null
...
number of transactions actually processed: 535251
tps = 107046.943632 (excluding connections establishing)
```
*Result: ~107,000 transactions per second (TPS).*

Adding `-h localhost` forces the traffic through the TCP stack. In this example, the throughput drops "like a stone" by nearly 40%. For workloads processing thousands of tiny queries, networking overhead is a genuine bottleneck. 

> [!TIP]
> While you can use the `-j` flag in `pgbench` to assign more threads and squeeze out more TPS, the overall percentage penalty imposed by TCP routing remains consistent.

With network access properly restricted or bypassed, the next layer of security is controlling *who* is allowed to connect using `pg_hba.conf`.

---

## Host-Based Access Control (`pg_hba.conf`)

Once a connection physically reaches the server (via `listen_addresses`), PostgreSQL consults the `pg_hba.conf` file to determine exactly how to authenticate the user. 

The file relies on a rigid columnar layout:
```ini
# TYPE  DATABASE        USER            ADDRESS                 METHOD  [OPTIONS]
local   all             all                                     peer
host    all             all             127.0.0.1/32            scram-sha-256
host    all             all             ::1/128                 scram-sha-256
```

### 1. Connection Types
The first column defines the protocol allowed for the connection:
*   **`local`**: Used exclusively for Unix domain socket connections.
*   **`host`**: Standard TCP/IP connections (both SSL and non-SSL).
*   **`hostssl` / `hostnossl`**: Forces the connection to explicitly use (or not use) SSL. Using `hostssl` requires `ssl = on` in `postgresql.conf`.
*   **`hostgssenc` / `hostnogssenc`**: Enforces GSSAPI encryption requirements.

### 2. Authentication Methods
PostgreSQL supports a massive variety of authentication methods to integrate into almost any enterprise environment. 

#### Recommended Core Methods
*   **`scram-sha-256`**: The modern, highly secure standard for password authentication. **Always use this instead of `md5` or `password`** on modern (PostgreSQL 10+) systems.
*   **`peer`**: The ultimate local security mechanism. Valid only for `local` (Unix socket) connections. It checks the operating system user you are logged in as and only allows you to connect if the OS user matches the database user. (E.g., OS user `postgres` can log in without a password as DB user `postgres`).
*   **`cert`**: Uses SSL client certificates for password-less authentication. It matches the `CN` attribute of the client certificate against the requested database username.
*   **`reject`**: Instantly drops the connection. Useful for explicitly blocking bad actors or deprecated IP ranges.

#### Dangerous / Deprecated Methods
*   **`trust`**: Logs the user in without checking any password or certificate. Only use this for extreme debugging or over strictly isolated `local` sockets. **Never use `trust` over a `host` connection.**
*   **`md5`**: Deprecated in favor of `scram-sha-256`.
*   **`password`**: Sends the password across the network in plaintext. Highly dangerous unless forced over a `hostssl` connection.

#### Enterprise Integration Methods
*   **`ldap` / `radius` / `pam` / `gss` / `sspi`**: Allows PostgreSQL to delegate authentication out to external directory services like Active Directory or OpenLDAP. *Note: For LDAP, the user must still technically be created via `CREATE USER` inside PostgreSQL, even if the password is managed externally.*

### CRITICAL RULE: Order Matters!
When evaluating a login attempt, PostgreSQL scans `pg_hba.conf` from top to bottom and **applies the very first rule that matches the connection signature**. It ignores everything below it.

**The Mistake:**
```ini
# TYPE  DATABASE  USER   ADDRESS           METHOD
host    all       all    192.168.1.0/24    scram-sha-256   <-- Matches first!
host    all       all    192.168.1.54/32   reject          <-- IGNORED
```
If an attacker connects from `192.168.1.54`, the `/24` subnet rule catches them first, prompting them for a password and ignoring the `reject` rule entirely.

**The Fix:**
*Always place specific exclusions and constraints above broad catch-all rules.*
```ini
# TYPE  DATABASE  USER   ADDRESS           METHOD
host    all       all    192.168.1.54/32   reject          <-- Blocks attacker instantly
host    all       all    192.168.1.0/24    scram-sha-256   <-- Allows rest of subnet
```

---

## Inspecting `pg_hba.conf` via SQL

In modern, containerized, or fully virtualized environments (like Kubernetes or AWS RDS), you rarely have direct SSH access to the underlying filesystem to run commands like `cat pg_hba.conf`. To solve this, PostgreSQL exposes the live, active contents of `pg_hba.conf` directly via SQL.

### `pg_hba_file_rules`

You can query the `pg_hba_file_rules` system view to see exactly how your authentication rules are configured and evaluated.

```sql
test=# \d pg_hba_file_rules
                      View "pg_catalog.pg_hba_file_rules"
   Column    |  Type   | Collation | Nullable | Default 
-------------+---------+-----------+----------+---------
 rule_number | integer |           |          | 
 file_name   | text    |           |          | 
 line_number | integer |           |          | 
 type        | text    |           |          | 
 database    | text[]  |           |          | 
 user_name   | text[]  |           |          | 
 address     | text    |           |          | 
 netmask     | text    |           |          | 
 auth_method | text    |           |          | 
 options     | text[]  |           |          | 
 error       | text    |           |          | 
```

**Best Practice:**
Use the `\x` (Expanded display) command in `psql` to read the rules clearly. It will show you exactly what file is being read, the precise line number of each rule, and crucially, any syntax errors (`error` column) that prevent a rule from loading.

```sql
test=# \x
Expanded display is on.
test=# SELECT * FROM pg_hba_file_rules;

-[ RECORD 1 ]----------------------------------------
rule_number | 1
file_name   | /Users/hs/db17/pg_hba.conf
line_number | 117
type        | local
database    | {all}
user_name   | {all}
address     | 
netmask     | 
auth_method | trust
options     | 
error       | 
-[ RECORD 2 ]----------------------------------------
rule_number | 2
file_name   | /Users/hs/db17/pg_hba.conf
line_number | 119
type        | host
database    | {all}
user_name   | {all}
address     | 127.0.0.1
netmask     | 255.255.255.255
auth_method | trust
options     | 
error       | 
```
This is a highly comprehensive, easy-to-digest method for verifying host-based access configurations without leaving your SQL client.

---

## Handling SSL

Encrypting the transfer layer between the server and the client is highly beneficial, particularly when communicating over long distances or untrusted networks. SSL provides a simple and secure way to ensure nobody intercepts your communication.

### Setting Up SSL

1.  **Enable SSL:** Open `postgresql.conf` and set `ssl = on`.
2.  **Locate Certificates:** By default, PostgreSQL looks for certificates in the `$PGDATA` directory. If you place them elsewhere, you must configure the following variables (requires restart):
    ```ini
    ssl_cert_file = 'server.crt'
    ssl_key_file = 'server.key'
    # Optional:
    # ssl_ca_file = ''
    # ssl_crl_file = ''
    ```

#### Creating Self-Signed Certificates
If you don't have certificates from a trusted Certificate Authority, you can easily generate self-signed ones for internal use.

1.  **Generate a Request:**
    ```bash
    openssl req -new -text -out server.req
    ```
    Answer the prompts. Make sure to enter your local hostname as the **Common Name**. This generates a passphrase-protected key (typically `privkey.pem`).
2.  **Remove the Passphrase:** (Required if you want the database to start automatically without human intervention).
    ```bash
    openssl rsa -in privkey.pem -out server.key
    rm privkey.pem
    ```
3.  **Generate the Certificate:** 
    Use the unlocked key to create the self-signed certificate:
    ```bash
    openssl req -x509 -in server.req -text -key server.key -out server.crt
    ```
4.  **Set File Permissions:** PostgreSQL will refuse to start if the private key permissions are too open.
    ```bash
    chmod og-rwx server.key
    ```

### Verifying SSL Connections

Once your certificates are in place and your `pg_hba.conf` is updated with `hostssl` rules, you can verify encryption status from inside the database using the `pg_stat_ssl` view.

```sql
postgres=# SELECT * FROM pg_stat_ssl WHERE pid = pg_backend_pid();
-[ RECORD 1 ]-+----------------------------
pid           | 20075
ssl           | t
version       | TLSv1.2
cipher        | ECDHE-RSA-AES256-GCM-SHA384
bits          | 256
client_dn     | 
client_serial | 
issuer_dn     | 
```
If the `ssl` field reads `true` (`t`), your connection is successfully encrypted.

Once you have configured SSL and host-based access, it is time to move up the chain and look at **instance-level security**.

---

## Instance-Level Security (Roles & Users)

After passing through the network layer and authenticating via `pg_hba.conf`, the next layer of security evaluates **Instance-Level Permissions**. 

The most fundamental concept to grasp is that **users are global at the instance level**. When you create a user, they exist across the entire PostgreSQL cluster. They can be seen by all databases, even if they only have permission to access a single specific database.

### Users vs. Roles
In modern PostgreSQL, **Users and Roles are the exact same thing**.
The `CREATE USER` command is merely a legacy alias for `CREATE ROLE`. The only difference is that `CREATE USER` grants the `LOGIN` attribute by default, whereas `CREATE ROLE` does not. Because they are identical under the hood, they share the exact same syntax and attributes.

### Core Role Attributes

When defining a role, you assign attributes that dictate their instance-wide capabilities:

*   **`SUPERUSER`**: The ultimate override. A superuser bypasses all permission checks and can read, write, drop, or alter absolutely anything.
*   **`CREATEDB`**: Allows the user to create new databases. 
    *   *Rule of thumb:* The creator of an object is automatically its owner. If a user creates a database, they own it, and therefore have the right to drop it later.
*   **`CREATEROLE`**: Permits the user to create, alter, and drop other roles.
*   **`INHERIT`**: (Default is `ON`). Allows a role to automatically inherit the privileges of any groups it is a member of. This is the cornerstone of Role-Based Access Control (RBAC).
*   **`LOGIN` / `NOLOGIN`**: Determines if the role can initiate a database session.
    *   *Tip:* While `LOGIN` allows you to connect to the instance, it is not enough on its own to connect to a specific *database*. You still need the `CONNECT` privilege on that database.
*   **`REPLICATION`**: Allows the user to initiate streaming replication. **Security Best Practice:** Never use a `SUPERUSER` for replication. Always create a dedicated user with just the `REPLICATION` attribute.
*   **`BYPASSRLS`**: Allows the role to bypass Row-Level Security policies.
*   **`CONNECTION LIMIT`**: Restricts the maximum number of concurrent connections this specific user can open.
*   **`VALID UNTIL`**: Sets a timestamp for the account to automatically expire and lock. Extremely useful for temporary contractors or interns.

### Designing a Role-Based Hierarchy
Instead of granting permissions directly to individuals (e.g., granting table access to `joe` and `jane`), you should build a hierarchy using the `NOLOGIN` and `INHERIT` attributes.

1.  Create an abstract group role that cannot log in: 
    `CREATE ROLE accounting NOLOGIN;`
2.  Grant the necessary schema/table permissions to `accounting`.
3.  Create individual user roles that can log in, and add them to the group: 
    `CREATE USER joe LOGIN IN ROLE accounting;`

Because `INHERIT` is active by default, `joe` immediately gains all the permissions of `accounting`, but his actions are logged strictly under his individual identity.

### Modifying Users and Parameters

Once users are in place, you will inevitably need to modify them. This is done using the `ALTER ROLE` (or `ALTER USER`) command.

#### The Danger of `ALTER ROLE ... PASSWORD`
In PostgreSQL, users are permitted to change their own passwords. However, you must be extremely careful how you do this.

```sql
test=> ALTER ROLE joe PASSWORD 'abc';
```

> [!WARNING]
> **Handling Passwords Carefully:** If Data Definition Language (DDL) logging is enabled (e.g., `log_statement = 'ddl'`), executing the statement above will literally write the plaintext password `'abc'` straight into your PostgreSQL server logs! 
> 
> *Solution:* Never use `ALTER ROLE` to change passwords manually in SQL. Instead, use the `\password` command in `psql` or use visual administration tools. These tools utilize client-side library support to ensure the password is encrypted *before* it travels over the network, completely avoiding plaintext exposure in the server logs.

#### Setting Per-User Configurations

`ALTER ROLE` is incredibly powerful because it allows you to bind PostgreSQL configuration parameters directly to a specific user.

For example, imagine your database server is in Europe (UTC+2), but Joe lives in Mauritius (UTC+4). You can set his time zone globally for his specific account:

```sql
test=# ALTER ROLE joe SET TimeZone = 'UTC-4';
```

Whenever Joe logs in, his session will automatically inherit that timezone.

```bash
[hs@linux ~]$ psql test -U joe
test=> SELECT now();
              now              
-------------------------------
 2024-05-06 19:09:02.492889+04
```
*(Notice the `test=>` prompt instead of `test=#`. The `>` character indicates you are logged in as a normal user, not a superuser).*

**Important Rules for Role Configurations:**
1.  **Not Immediate:** If a user is currently logged in, `ALTER ROLE ... SET` does not immediately affect their active session. They must reconnect, or run `SET <parameter> TO DEFAULT`.
2.  **Performance Tuning:** This feature is frequently used to allocate higher performance limits to specific users. For instance, you could grant an ETL reporting user a massive memory allowance (`ALTER ROLE etl_user SET work_mem = '4GB';`) without affecting the global default memory limits for normal web traffic.

---

## Database-Level Security

After configuring users at the instance level, we must explicitly grant them permission to access the data inside a specific database. Just because Joe has the `LOGIN` attribute does not mean he should have access to every database in the cluster.

Permissions at the database level are assigned using the `GRANT` command:
```sql
GRANT { CREATE | CONNECT | TEMPORARY } ON DATABASE database_name TO role_name;
```

There are two major permissions to understand here:
*   **`CONNECT`**: Allows a role to initiate a session and connect to the specified database.
*   **`CREATE`**: Allows a role to create a new *schema* inside the database. (Note: This does *not* allow the creation of tables directly. Tables reside inside schemas, so a user must first have access to a schema to build tables within it).

### The "Public" Problem
If you create a brand new database and a brand new user (`joe`), you might be surprised to find that Joe can instantly log into the new database without you granting any explicit permissions. 

Where did he get this permission? From the `public` pseudo-role.
`public` is the equivalent of "everybody on the system." By default in older PostgreSQL versions, the `public` role is automatically granted `CONNECT` and `TEMPORARY` rights on new databases. Because Joe is part of the general public, he inherits this access.

**Security Best Practice:**
To ensure strict security and "default deny" behavior, you should manually revoke all default access from the `public` role. It is highly recommended to do this even on the default `postgres` database.

```sql
test=# REVOKE ALL ON DATABASE test FROM public;
```

If Joe tries to connect now, he will be correctly rejected by the database-level security check:
```bash
[hs@linux ~]$ psql test -U joe
psql: FATAL: permission denied for database "test"
DETAIL: User does not have CONNECT privilege.
```

### Re-Granting Access via RBAC
Now that the database is locked down, access must be explicitly granted. Rather than granting it to Joe directly, we grant it to the abstract group we created earlier:

```sql
test=# GRANT CONNECT ON DATABASE test TO bookkeeper;
```
Because Joe was created with `IN ROLE bookkeeper` (which has `INHERIT` turned on), he instantly regains access, while any new users created outside of the `bookkeeper` group will remain strictly locked out.

---

## Schema-Level Security

Once a user has successfully connected to the database, they encounter the next barrier: **schema permissions**.

Permissions at the schema level are assigned using the `GRANT` command:
```sql
GRANT { CREATE | USAGE } ON SCHEMA schema_name TO role_name;
```

*   **`USAGE`**: Allows a user to "enter" the schema and look up objects within the catalog namespace. *(Note: This does not mean they can query the tables inside it. They still need table-level permissions for that).*
*   **`CREATE`**: Allows a user to create objects (like tables, views, or functions) inside the schema. 

### The `public` Schema Security Flaw (Pre-PG 15)

The default behavior of the `public` schema changed radically depending on your PostgreSQL version.

**PostgreSQL 14 and Older:**
Historically, the `public` pseudo-role was automatically granted `CREATE` and `USAGE` privileges on the `public` schema. This was a massive security flaw. Any user who could connect to the database could freely spam the `public` schema with tables and objects, potentially harming the setup.

*Fix for PG 14 and older:* You must explicitly revoke this access as a superuser:
```sql
test=# REVOKE ALL ON SCHEMA public FROM public;
```

**PostgreSQL 15 and Newer:**
The PostgreSQL community finally closed this loophole. In PostgreSQL 15+, the `public` role no longer has `CREATE` privilege on the `public` schema by default. If a normal user tries to create a table, they will safely hit a wall:
```bash
test=> CREATE TABLE public.t_data (id int);
ERROR: permission denied for schema public
```

### Table Ownership vs. Schema DDLs
A common point of confusion occurs when a user *owns* a table but tries to alter it. 

If Joe owns a table `t_broken`, he can freely `SELECT`, `INSERT`, and `UPDATE` data within it (assuming he has `USAGE` on the schema). 
However, if he attempts to execute a DDL command like renaming the table:
```sql
test=> ALTER TABLE t_broken RENAME TO t_useful;
ERROR: permission denied for schema public
```
Why did it fail? Because renaming a table modifies the schema's catalog structure. **To execute DDL commands on a table you own, you must also have `CREATE` permissions on the schema where the table resides.**

```sql
test=# GRANT CREATE ON SCHEMA public TO bookkeeper;
```
Once `CREATE` is granted at the schema level, the table rename will succeed.

---

## Table-Level Security

After configuring bind addresses, network authentication, user roles, databases, and schemas, we have finally reached the table layer.

Permissions at the table level are assigned using the `GRANT` command:
```sql
GRANT { SELECT | INSERT | UPDATE | DELETE | TRUNCATE | REFERENCES | TRIGGER | MAINTAIN }
ON TABLE table_name 
TO role_name [ WITH GRANT OPTION ];
```

### Table Privileges Explained
*   **`SELECT`**: Read data from the table.
*   **`INSERT`**: Add new rows (applies to `INSERT`, `COPY`, etc.).
    *   *Warning:* Being able to `INSERT` does **not** automatically grant you the ability to `SELECT`. A user with only `INSERT` privileges can write data blindly but cannot read back what they just wrote.
*   **`UPDATE`**: Modify existing rows.
*   **`DELETE`**: Remove rows.
*   **`TRUNCATE`**: Completely empty the table.
    *   *Note:* `TRUNCATE` is explicitly separated from `DELETE` because `TRUNCATE` takes an exclusive lock on the entire table and bypasses per-row operations, making it much more destructive.
*   **`REFERENCES`**: Create foreign keys. You must have this privilege on **both** the referencing column and the referenced column to create the constraint.
*   **`TRIGGER`**: Create triggers on the table.
*   **`MAINTAIN`**: Run administrative maintenance commands like `VACUUM`, `ANALYZE`, `CLUSTER`, `REFRESH MATERIALIZED VIEW`, `REINDEX`, and `LOCK TABLE`. This privilege is fantastic for allowing normal users to maintain performance without giving them full `SUPERUSER` or table ownership rights.

### Granting in Bulk and Delegating
If a schema contains hundreds of tables, granting permissions one by one is tedious. You can grant access to everything at once:

```sql
GRANT SELECT ON ALL TABLES IN SCHEMA public TO bookkeeper;
```

**Delegation via `WITH GRANT OPTION`**
If you manage a massive system with hundreds of users, the DBA can become a bottleneck. You can use `WITH GRANT OPTION` to delegate administrative power to a team lead:

```sql
GRANT SELECT, INSERT ON TABLE financials TO head_accountant WITH GRANT OPTION;
```
Now, `head_accountant` can independently grant `SELECT` and `INSERT` on the `financials` table to their team members, significantly reducing the primary administrator's workload.

---

## Column-Level Security

Often, table-level security is still too broad. In real-world enterprise applications (like banking or healthcare), a user might be allowed to view an account profile, but they should absolutely not be able to read the specific account balance or Social Security Number column.

PostgreSQL handles this by allowing you to restrict `SELECT`, `INSERT`, and `UPDATE` permissions down to the individual column level.

### Granting Column Permissions
You can grant access to specific columns by providing a comma-separated list of column names in parentheses immediately after the permission type.

For example, assume we have a table `t_useful` with two columns: `id` and `name`. We want to allow the user `paul` to read the `id`, but we want the `name` column kept completely hidden from him.

```sql
test=# GRANT SELECT (id) ON t_useful TO paul;
```

If Paul connects to the database, he can successfully query the column he has been granted access to:

```bash
[hs@linux ~]$ psql test -U paul
test=> SELECT id FROM t_useful;
 id 
----
(0 rows)
```

### The Death of `SELECT *`
There is a massive operational caveat when using column-level security: **Applications and users must completely stop using `SELECT *`.**

If Paul attempts to use the wildcard operator, the database will instantly throw an error:

```sql
test=> SELECT * FROM t_useful;
ERROR: permission denied for relation t_useful
```

Because `SELECT *` attempts to fetch every single column in the relation, and Paul does not explicitly have permissions for the `name` column, the entire query is strictly rejected. When building applications on top of column-level security, developers must write explicit `SELECT` statements detailing exactly which columns they intend to fetch.

---

## Configuring Default Privileges

Up to this point, all permissions have been granted manually on existing objects. But what happens when a developer deploys a migration that adds 10 new tables to the system? 

Manually tracking and applying `GRANT` statements for every new object is tedious, highly error-prone, and a massive security risk (if an administrator forgets, it leads to either broken applications or unsecured data).

To solve this, PostgreSQL provides the `ALTER DEFAULT PRIVILEGES` clause. This allows you to define a template of permissions that PostgreSQL will automatically apply the exact moment a new object comes into existence.

### Syntax for Default Privileges
The syntax is highly intuitive and mirrors the standard `GRANT` clause:
```sql
ALTER DEFAULT PRIVILEGES
    [ FOR ROLE target_role ]
    [ IN SCHEMA schema_name ]
    GRANT { SELECT | INSERT | UPDATE | DELETE | ALL } 
    ON TABLES 
    TO role_name;
```

### Automation Example

Imagine a scenario where we want the `paul` role to automatically have full access to any new table created by the `joe` role. 

**1. Set the Default Privilege (as Superuser):**
```sql
test=# ALTER DEFAULT PRIVILEGES FOR ROLE joe IN SCHEMA public GRANT ALL ON TABLES TO paul;
```

**2. Create a New Table (as Joe):**
When Joe connects and creates a new table, he doesn't need to do anything special.
```sql
[hs@linux ~]$ psql test -U joe
test=> CREATE TABLE t_user (
    id      serial,
    name    text,
    passwd  text
);
```

**3. Verify Automatic Access (as Paul):**
Without any DBA intervention, Paul can immediately connect and query the newly created table:
```bash
[hs@linux ~]$ psql test -U paul
test=> SELECT * FROM t_user;
 id | name | passwd 
----+------+--------
(0 rows)
```
The table was read successfully. Implementing default privileges guarantees that your security model scales seamlessly as your database schema evolves.

---

## Row-Level Security (RLS)

Even with table and column security in place, users can still see every single row inside a table. This is insufficient for modern multi-tenant architectures (e.g., SaaS platforms where users should only see their own accounts, or HR systems where an employee can only see their own payslips).

**Row-Level Security (RLS)** solves this by dynamically appending invisible filters to every query a user executes, ensuring they only interact with the rows they are authorized to see.

### 1. Enabling RLS
Before policies can take effect, you must explicitly enable RLS on the target table:
```sql
test=# ALTER TABLE t_person ENABLE ROW LEVEL SECURITY;
```

> [!IMPORTANT]
> **Default Deny:** The moment RLS is enabled on a table, a strict "default deny" policy goes into effect. If Joe queries the table right now, he will receive an empty result set (`0 rows`), even if he has table-level `SELECT` permissions and the table contains millions of rows.

### 2. Creating Policies
Once RLS is enabled, you write policies to explicitly punch holes in the default deny state.

Policies evaluate expressions using two main clauses:
*   **`USING (expression)`**: Filters rows that already exist in the database. Applies to `SELECT`, `UPDATE`, and `DELETE`.
*   **`WITH CHECK (expression)`**: Validates new data attempting to enter the database. Applies to `INSERT` and `UPDATE`.

**Example: Restricting SELECT access**
```sql
test=# CREATE POLICY joe_pol_1 ON t_person 
       FOR SELECT TO joe 
       USING (gender = 'male');
```
When Joe executes `SELECT * FROM t_person`, PostgreSQL intercepts the query and automatically rewrites it behind the scenes to: `SELECT * FROM t_person WHERE gender = 'male'`.

### 3. Multiple Policies (`OR` vs `AND`)
You can assign multiple policies to a single table. By default, PostgreSQL evaluates policies as **`PERMISSIVE`**, meaning multiple rules are combined using an `OR` condition.

If we add a second policy for Joe:
```sql
test=# CREATE POLICY joe_pol_2 ON t_person 
       FOR SELECT TO joe 
       USING (gender IS NULL);
```
Joe's new effective filter becomes: `WHERE (gender = 'male') OR (gender IS NULL)`. The more permissive policies you add, the more data the user sees.
*(Note: If you need conditions to be strictly enforced across the board with an `AND` connection, you can define the policy `AS RESTRICTIVE`).*

### 4. Handling `INSERT` and `RETURNING` Caveats
Because our previous policies were explicitly `FOR SELECT`, Joe is currently entirely blocked from inserting data. To allow him to add rows, we must define a `WITH CHECK` policy:

```sql
test=# CREATE POLICY joe_pol_3 ON t_person 
       FOR INSERT TO joe 
       WITH CHECK (gender IN ('male', 'female'));
```
Joe can now insert a row: `INSERT INTO t_person VALUES ('female', 'maria');`

**The `RETURNING *` Caveat:**
If Joe runs the exact same valid insert, but appends `RETURNING *`, the query will inexplicably fail!
```sql
test=> INSERT INTO t_person VALUES ('female', 'maria') RETURNING *;
ERROR: new row violates row-level security policy for table "t_person"
```
Why? Because `RETURNING *` executes a `SELECT` operation to fetch the newly created row. The `INSERT` itself succeeds against the `WITH CHECK` constraint, but returning a 'female' violates Joe's `SELECT` `USING` constraint (which only allows 'male' and 'NULL'). When designing RLS rules, you must ensure your `SELECT` and `INSERT` logic align cleanly.

---

## Inspecting and Handling Permissions

Once all table, column, and row-level permissions have been set, administrators need a way to audit the system and verify who is allowed to do what.

In the `psql` command line, information about table permissions and RLS policies can be retrieved using the `\z` (or `\dp`) command:

```sql
test=# \z t_person
                               Access privileges
-[ RECORD 1 ]-----+---------------------------------------
Schema            | public
Name              | t_person
Type              | table
Access privileges | postgres=arwdDxtm/postgres
                  | joe=arwdDxtm/postgres
Column privileges | 
Policies          | joe_pol_1 (r):
                  |   (u): (gender = 'male'::text)
                  |   to: joe
                  | ...
```

### Decoding Privilege Shortcuts
If you look closely at the `Access privileges` column, you will see a cryptic string: `joe=arwdDxtm/postgres`. This string represents the exact privileges granted to Joe. 

Here is the key to decoding these shortcuts:
*   **`a`**: Append (`INSERT`)
*   **`r`**: Read (`SELECT`)
*   **`w`**: Write (`UPDATE`)
*   **`d`**: Delete (`DELETE`)
*   **`D`**: Truncate (`TRUNCATE` - uppercase because 't' was taken)
*   **`x`**: References
*   **`t`**: Triggers
*   **`m`**: Maintain (`VACUUM`, `ANALYZE`, etc.)

### Making Permissions Readable (`aclexplode`)
Memorizing shortcut letters is cumbersome. Fortunately, PostgreSQL provides a built-in function to parse these Access Control Lists (ACLs) into a highly readable table format:

```sql
test=# SELECT * FROM aclexplode('{joe=arwdDxtm/postgres}');
 grantor | grantee | privilege_type | is_grantable 
---------+---------+----------------+--------------
      10 |   18481 | INSERT         | f
      10 |   18481 | SELECT         | f
      10 |   18481 | UPDATE         | f
      10 |   18481 | DELETE         | f
      10 |   18481 | TRUNCATE       | f
      10 |   18481 | REFERENCES     | f
      10 |   18481 | TRIGGER        | f
      10 |   18481 | MAINTAIN       | f
```

### The `pg_permission` Extension
If you want an even more robust solution for inspecting the security system, the team at Cybertec maintains an open-source extension called `pg_permission`. 

You can clone and install it from source:
```bash
git clone https://github.com/cybertec-postgresql/pg_permission.git
cd pg_permission
make install
```
Then enable it inside the database:
```sql
test=# CREATE EXTENSION pg_permissions;
```

This extension deploys several structured views, the most important being `all_permissions`. It gives you a crystal-clear overview of all object permissions. 

**Bonus Feature:** The `all_permissions` view has an `INSTEAD OF UPDATE` trigger attached to it. This means you can actively change permissions across your system simply by writing an `UPDATE` statement against the view itself, and the extension will handle the underlying `GRANT`/`REVOKE` commands for you automatically!

---

## Reassigning Objects and Dropping Users

Eventually, users will leave an organization, and their accounts must be removed from the system. Unsurprisingly, the command to do this is `DROP ROLE` (or `DROP USER`).

However, dropping a user in a mature database is rarely a one-step process.

### The Dependency Problem
If you attempt to drop a user who has actively been working in the database, you will almost certainly encounter this error:

```sql
test=# DROP ROLE joe;
ERROR: role "joe" cannot be dropped because some objects depend on it
DETAIL: target of policy joe_pol_3 on table t_person
        target of policy joe_pol_1 on table t_person
        privileges for table t_person
        owner of table t_user
        owner of default privileges on new relations belonging to role joe in schema public
```

PostgreSQL will aggressively block the drop. Why? Because every single object in a PostgreSQL database *must* have an owner. If PostgreSQL allowed you to drop Joe, his tables (`t_user`) would be left orphaned. 

### Step 1: Reassigning Ownership
To solve the orphan problem, you must hand Joe's objects over to someone else (usually an administrative account or a service account) using the `REASSIGN OWNED` command:

```sql
test=# REASSIGN OWNED BY joe TO postgres;
REASSIGN OWNED
```
This transfers ownership of all tables, sequences, and schemas from Joe to `postgres`.

### Step 2: Revoking Lingering Privileges
Even after reassigning ownership, you *still* cannot drop Joe. 
```sql
test=# DROP ROLE joe;
ERROR: role "joe" cannot be dropped because some objects depend on it
DETAIL: target of policy joe_pol_3 on table t_person
        privileges for table t_person
        owner of default privileges...
```
Reassigning ownership does not automatically delete explicit `GRANT` statements, RLS policies, or Default Privileges tied to the user. There is no shortcut here—an administrator must manually `REVOKE` or `DROP` these lingering dependencies one by one until the list is clear.

### The Golden Rule of RBAC
Because offboarding a user with direct privileges is so incredibly tedious, it reinforces the absolute golden rule of PostgreSQL security:

**Never assign permissions directly to human users.**

Try to abstract as much as you can into functional roles (e.g., `accounting`, `reporting`). Assign all explicit privileges to those abstract group roles, and simply grant the human user membership in the group (`GRANT accounting TO joe`). 

When Joe leaves the company, you just drop his membership. Because he owns no objects and holds no direct grants, `DROP ROLE joe;` will execute instantly without error.

# Chapter 8: Configuring Endpoint-Level Authorization: Applying Restrictions

## Core Concepts
- Endpoint-level authorization allows you to apply different security constraints to specific groups of requests based on paths or HTTP methods.

> [!NOTE]
> **Unauthenticated vs. Failed Authentication**
> Remember that **authentication always precedes authorization**. If you configure an endpoint with `.permitAll()`, calling it without any credentials will succeed (`200 OK`) because Spring Security skips authentication. 
> However, if you *do* provide credentials (like an `Authorization` header) and they are **invalid**, the authentication filter will reject the request and return `401 Unauthorized` *before* it ever reaches the authorization filter, even though the endpoint was marked as permit all.

## Selecting Requests: Strategies and Configurations

### 1. Request Matchers with Path Expressions (`requestMatchers()`)
**How it works:**
Uses standard ANT-style syntax to match paths and optionally HTTP methods. It maps specific endpoint paths to authorization rules. 
- `requestMatchers(String... patterns)`: Applies rules based on path patterns, regardless of the HTTP method.
- `requestMatchers(HttpMethod method, String... patterns)`: Applies rules to specific paths and HTTP methods.

**When to use:**
This is the default and most common approach for endpoint authorization. Use it when endpoints have clear, predictable paths or when different HTTP methods on the same path require different permissions.

**Important Rule Order:** 
When declaring multiple matchers, order them from **most specific to most general**. More general matchers like `anyRequest()` must be placed at the end of the chain, as Spring Security evaluates rules in the order they are defined.

**Common ANT-Style Expressions:**
| Expression | Description |
|---|---|
| `/a` | Exact match for path `/a`. |
| `/a/*` | The `*` operator replaces exactly one pathname (e.g., matches `/a/b` or `/a/c`, but not `/a/b/c`). |
| `/a/**` | The `**` operator replaces multiple pathnames (e.g., matches `/a`, `/a/b`, and `/a/b/c`). |
| `/a/{param}` | Matches path `/a` with a single path parameter. |
| `/a/{param:regex}` | Matches path `/a` with a parameter that fits the specified regular expression. |

**Code Example:**
```java
http.authorizeHttpRequests(c -> c
    .requestMatchers(HttpMethod.GET, "/a").authenticated() // GET requests to /a require authentication
    .requestMatchers(HttpMethod.POST, "/a").permitAll()    // POST requests to /a are allowed for everyone
    .requestMatchers("/hello").hasRole("ADMIN")            // Any method to /hello requires ADMIN role
    .requestMatchers("/a/b/**").authenticated()            // Any method to paths starting with /a/b/ requires authentication
    .anyRequest().denyAll()                                // All other requests are denied
);
```

### 2. Regular Expression Matchers (`regexMatchers()`)
**How it works:**
Uses standard Regular Expressions to match request paths via the `regexMatchers()` method.

**When to use:**
Use only when requirements are too complex for ANT-style path expressions (e.g., matching paths containing specific symbols, applying rules to specific phone number or email formats in path variables). It should be a last resort due to readability and maintainability concerns.

**Code Example:**
```java
http.authorizeHttpRequests(c -> c
    .regexMatchers(".*/(us|uk|ca)+/(en|fr).*").authenticated()
    .anyRequest().hasAuthority("premium")
);
```

## Disabling CSRF for Non-GET Requests
By default, Spring Security applies Cross-Site Request Forgery (CSRF) protection, which blocks `POST`, `PUT`, and `DELETE` requests unless a valid CSRF token is provided. For testing endpoint authorization on non-GET methods without a token, you must temporarily disable it:
```java
http.csrf(c -> c.disable());
```

## Figures
![Figure 8.1](figures/figure-8-1.png)

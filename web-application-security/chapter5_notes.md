# Chapter 5: API Analysis

## Endpoint Discovery
API analysis follows subdomain discovery to understand structure, endpoints, and business purpose. Modern APIs typically follow REST or SOAP formats, with REST being more prevalent.

**Indicators of REST APIs:**
- **Resource-Oriented:** Endpoints specify resources, not functions (e.g., `/users/1234/payments`).
- **Hierarchical:** Nested resources imply relationships.
- **Stateless:** Server retains no session state; authentication tokens are sent with every request.

**REST HTTP Verbs:**
| REST HTTP Verb | Usage |
| :--- | :--- |
| POST | Create |
| GET | Read |
| PUT | Update/replace |
| PATCH | Update/modify |
| DELETE | Delete |

**Discovery Techniques:**
- **OPTIONS Method:** Use `curl -i -X OPTIONS <url>` to query supported HTTP verbs. A successful response often includes an `Allow` header (e.g., `Allow: HEAD, GET, PUT, DELETE, OPTIONS`). Useful but often restricted on enterprise APIs.
- **Verb Brute-Forcing:** Iteratively test endpoints with various HTTP verbs. *Warning: Brute-forcing verbs like DELETE or PUT can alter/delete data.*

```javascript
// Example: Verb Discovery Script
const discoverHTTPVerbs = function(url) {
  const verbs = ['POST', 'GET', 'PUT', 'PATCH', 'DELETE'];
  const promises = [];
  verbs.forEach((verb) => {
    const promise = new Promise((resolve, reject) => {
      const http = new XMLHttpRequest();
      http.open(verb, url, true)
      http.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');
      http.onreadystatechange = function() {
        if (http.readyState === 4) return resolve({ verb: verb, status: http.status });
      }
      setTimeout(() => resolve({ verb: verb, status: -1 }), 1000);
      http.send({});
    });
    promises.push(promise);
  });
  Promise.all(promises).then(values => console.log(values));
}
```

## Authentication Mechanisms
Understanding authentication schemes is critical for API analysis and payload discovery. 

**Major Authentication Schemes:**

### 1. HTTP Basic Auth
- **How it works:** Username and password sent on each request, typically base64-encoded in the `Authorization` header (`Authorization: Basic base64(user:pass)`).
  - *Tip:* Use browser console functions `btoa('user:pass')` to encode and `atob('base64string')` to decode these credentials during analysis.
- **When to use:** Only over SSL/TLS encrypted traffic to prevent interception.
- **Strengths:** Natively supported by all major browsers.
- **Weaknesses:** Sessions do not expire; easy to intercept.

### 2. HTTP Digest Authentication
- **How it works:** Hashed `username:realm:password` sent on each request.
- **When to use:** When stronger protection against interception is needed without strict TLS guarantees.
- **Strengths:** More difficult to intercept; server can reject expired tokens.
- **Weaknesses:** Encryption strength depends entirely on the hashing algorithm used.

### 3. OAuth
- **How it works:** "Bearer" token-based authentication allowing delegated access (e.g., signing in via Google/Twitch).
- **When to use:** Modern APIs requiring delegated access or integration with third-party providers.
- **Strengths:** Tokenized permissions shareable across applications.
- **Weaknesses:** Phishing risk; central site compromise affects all connected apps.

## Endpoint Shapes
Determining payload shapes helps understand expected data.

### Common Shapes
Industry-standard specifications share predictable payload structures.
- **OAuth 2.0:** Requires specific parameters (`response_type`, `client_id`, `scope`, `state`, `redirect_uri`). Identifying OAuth allows you to consult public documentation for payload structures.
  - *Example JSON payload:*
    ```json
    {
      "response_type": "code",
      "client_id": "id",
      "scope": ["scopes"],
      "state": "state",
      "redirect_uri": "uri"
    }
    ```

### Application-Specific Shapes
Internal APIs lack common specifications, requiring trial and error.
- **Error Messages:** Verbose HTTP errors (e.g., `401`, `400`) can leak expected parameter names or constraints (e.g., an error saying `publicProfile only accepts "auth" and "noAuth" as params`).
- **UI Interaction:** Use privileged accounts and browser developer tools (Network tab) or proxies (Burp) to capture and inspect valid outgoing request shapes.
- **Parameter Brute-Forcing:** If a variable name is known but the value is not, reduce the **solutions space** (the list of possible combinations for a field) to the smallest viable size (e.g., knowing a token is exactly 12 characters or strictly hexadecimal). 
  - *Testing Invalid Solutions:* In addition to valid solutions, search for invalid solutions to reduce the solutions space further and potentially uncover bugs in the application code.

# Chapter 13: What are OAuth 2 and OpenID Connect?

## Core Concepts

**OAuth 2** is a specification for separating authentication responsibilities in a system. It allows multiple applications to rely on a centralized authentication application, minimizing redundant implementation efforts, reducing the number of credentials a user manages, and enhancing security. 

**OpenID Connect (OIDC)** is an identity protocol built on top of the OAuth 2 specification. It introduces specific standards and identity-based features that OAuth 2 intentionally leaves open.

### Actors in an OAuth 2 System

Understanding OAuth 2 is much like understanding the protocol for visiting a large corporation's headquarters. As illustrated in Figure 13.1, you can't just walk into any room. You must first go to the front desk, prove who you are (using an ID), and obtain an access card that grants entry to specific doors for a limited time.

![Figure 13.1 The OAuth 2 specification is very similar to accessing an office building.](figures/figure-13-1.png)

In an OAuth 2 system, we have the following main actors (see Figure 13.2):

- **The User**: The person interacting with the client application (like the visitor to the building).
- **The Client**: The application (frontend, mobile, or backend) that needs authentication and authorization to interact with a backend service.
- **The Resource Server**: The backend application that authorizes and serves the requests sent by the client (like the specific rooms you want to access).
- **The Authorization Server**: The application responsible for authenticating the user/client, securely storing credentials, and issuing tokens (like the front desk).

![Figure 13.2 The participants in an OAuth 2 framework.](figures/figure-13-2.png)

As shown in Figure 13.3, the simplest authorization flow involves the client first obtaining an access token from the authorization server, which it then uses to gain authorization for requests sent to the backend resource server.

![Figure 13.3 The simplest explanation of the OAuth 2 authentication procedure.](figures/figure-13-3.png)

## Tokens

Tokens are the artifacts (the "access cards") issued by the authorization server that a client uses to prove its identity and authorization rights to the resource server. Figure 13.4 depicts this using an analogy of an alien, Zglorb, using an access card to enter specific areas of a Mothership. Tokens generally have a short lifespan (e.g., minutes) to mitigate the risk of misuse if compromised.

![Figure 13.4 Zglorb (the user) needs to access the Mothership (resource server).](figures/figure-13-4.png)

Tokens are classified by how they provide authorization data to the resource server:

### Opaque Tokens

- **How it works**: Opaque tokens contain no identifiable data about the user or client. They act like a physical key to a treasure chest (Figure 13.5)—you don't know what's inside until you try it. 

![Figure 13.5 An analogy to opaque tokens.](figures/figure-13-5.png)

When the resource server receives one, it must make a direct **introspection call** to the authorization server to find out if the key is valid and fetch the associated authorization details, as detailed in Figure 13.6.

![Figure 13.6 The token introspection call.](figures/figure-13-6.png)

- **When to use**: Use opaque tokens when the token must carry a large amount of data or highly sensitive data that is unsafe to send over the network directly within the token.

### Non-opaque Tokens (e.g., JWT)

- **How it works**: Non-opaque tokens store readable information about the client and user. As illustrated in Figure 13.7, you can compare them to a signed document. The resource server can parse and validate them locally to apply constraints without invoking the authorization server.

![Figure 13.7 The non-opaque token is like a signed document.](figures/figure-13-7.png)

**JSON Web Token (JWT)** is the most common implementation, consisting of three Base64-encoded, dot-separated sections (Figure 13.8):
  - **Header**: Contains metadata like the cryptographic algorithm or key ID used for signing.
  - **Body**: Contains claims and data about the issued entity (client/user).
  - **Signature**: A cryptographically generated value proving the authorization server issued the token and its contents are unaltered.

![Figure 13.8 The anatomy of a JWT token.](figures/figure-13-8.png)

- **When to use**: This is the default and most frequent choice for modern systems, as it eliminates the network overhead of making introspection calls for every request.

```text
// Example JWT structure
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

## Grant Types (Flows)

A "grant type" is the specific protocol flow a client follows to obtain a token from the authorization server. 

*(Note: The "Implicit" and "Password" grant types are deprecated due to severe security vulnerabilities and should not be used in modern applications).*

### Authorization Code Grant Type

- **How it works**: This is the most used grant type today. Figure 13.9 illustrates this using an example of an accountant, Mary, who needs to access an invoices application. The client redirects the user to the authorization server's login page. After successful authentication, the authorization server redirects the user back to the client, passing an "authorization code" in the URL. The client then makes a secure, backend-to-backend request to the authorization server, exchanging the authorization code and its own client credentials for an access token.

![Figure 13.9 The authorization code grant type.](figures/figure-13-9.png)

- **When to use**: Use this flow whenever an application needs to authenticate a human user (e.g., web or mobile apps). As shown in Figure 13.10, returning an authorization code instead of the token directly to the browser adds a layer of protection. It forces the client to make a subsequent request with its own credentials, preventing a malicious actor from simply intercepting the redirect to get the access token.

**Why the extra step? (Front-Channel vs Back-Channel)**
A common point of confusion is why the client must call "give me access token" (Step 5) *after* the user has already provided their credentials.
- **The Front-Channel (Insecure):** The initial Authorization Code is returned via a browser redirect (the front-channel). Because it passes through the open internet, URL history, and HTTP Referer headers, it is vulnerable to interception. If the Authorization Server returned the actual Access Token here, a hacker could steal it.
- **The Back-Channel (Secure):** Instead of exposing the token, the server returns a temporary "Authorization Code". The legitimate **Client Application** (usually a backend server rendering the frontend, like a Node.js server, Spring Boot app, or a Backend-For-Frontend) takes this code and makes a secure, hidden, **backend-to-backend** API call. During this call, the application authenticates itself using its own **Client ID and Client Secret**. 

This completely ensures that the Access Token never touches the vulnerable web browser, and proves that the application requesting the token is the legitimate app that initiated the flow.

```mermaid
sequenceDiagram
    participant User as User (Browser)
    participant Client as Client App (e.g. Node.js Backend)
    participant AuthServer as Authorization Server
    
    rect rgb(255, 228, 225)
    Note over User, AuthServer: Front-Channel (Browser Visible & Less Secure)
    User->>Client: 1. Click "Login"
    Client->>User: 2. Redirect to Auth Server
    User->>AuthServer: 3. Provide Credentials (Login)
    AuthServer->>User: 4. Redirect back with Authorization Code
    User->>Client: 5. Pass Authorization Code via URL
    end

    rect rgb(224, 255, 255)
    Note over Client, AuthServer: Back-Channel (Backend-to-Backend & Highly Secure)
    Client->>AuthServer: 6. Send Auth Code + Client ID/Secret
    AuthServer->>Client: 7. Return Access Token directly to Backend
    end
```

![Figure 13.10 After logging in and receiving an authorization code, the client must make an additional request.](figures/figure-13-10.png)

### PKCE (Proof Key for Code Exchange)

- **How it works**: PKCE is a security enhancement for the Authorization Code flow. 
  1. The browser application generates a random string (`verifier`).
  2. It hashes that string using the standardized SHA-256 algorithm to create a `challenge`.
  ```javascript
  verifier = random();
  challenge = hash(verifier, 'SHA-256');
  ```
  3. The client sends the `challenge` during the initial login redirect (step 3 in Figure 13.11), explicitly sending `code_challenge_method=S256` so the server knows which cryptographic math was used.
  4. Later, when exchanging the authorization code for the token, the client sends the plaintext `verifier` (step 5 in Figure 13.11). 
  5. **Why it is mathematically secure**: Hash functions are strictly *one-way*. It is mathematically impossible to reverse a `challenge` back into a `verifier`. If a hacker intercepts the initial browser redirect, they only steal the hashed `challenge`. When the hacker tries to exchange the authorization code for a token, the server demands the plaintext `verifier`. The hacker is stuck because they cannot calculate it. Meanwhile, the legitimate client sends the plaintext `verifier` it kept in memory. The authorization server hashes it using SHA-256 and compares it against the initial `challenge`. If they match, the identity is mathematically proven.

![Figure 13.11 The client will send a challenge in step 3 and the verifier in step 5.](figures/figure-13-11.png)

- **When to use (Confidential vs Public Clients)**: PKCE was specifically invented for **Public Clients** to protect the authorization code flow when client credentials cannot be securely hidden.
  - **Confidential Clients** (e.g., a backend Node.js or Spring Boot server) can safely store a static `Client Secret` because their source code is hidden on a secure server.
  - **Public Clients** (e.g., an SPA running directly in the web browser like React/Angular, or a Native iOS/Android app) *cannot* safely store a `Client Secret`. Because their code runs directly on the user's device, hackers can easily reverse-engineer them and steal hardcoded passwords. 

Therefore, use PKCE whenever you have a Public Client. Instead of relying on a static password, the browser/mobile app dynamically generates the `verifier` and `challenge` for *every single login attempt*, proving its identity securely without needing a vulnerable static secret.

> [!WARNING]
> **PKCE does NOT protect against XSS (Cross-Site Scripting)**
> If a hacker injects malicious JavaScript into your Single Page Application (XSS), they can easily read the browser's memory, steal the `verifier`, or just steal the Access Token directly after the login completes. PKCE *only* protects against URL interception attacks (e.g., intercepting the redirect over the network). 
> 
> Because of this XSS vulnerability, the modern industry best practice for web applications is to avoid SPAs with PKCE entirely and instead use the **Backend-For-Frontend (BFF)** pattern. In a BFF architecture, the browser never sees the `verifier` or the tokens; the backend handles the entire OAuth 2 flow and issues a secure, XSS-proof `HttpOnly` cookie to the browser instead.

### Client Credentials Grant Type

- **How it works**: Sometimes an app needs authorization without user intervention. The client application authenticates directly with the authorization server using only its own credentials (client ID and secret) to obtain an access token, as shown in Figure 13.12.

![Figure 13.12 The client grant type.](figures/figure-13-12.png)

- **When to use**: Use this when a system needs authorization without user intervention. Commonly used for machine-to-machine communication, such as a scheduled backend service calling another backend service.

### Refresh Tokens

- **How it works**: Because access tokens are deliberately short-lived (often expiring in minutes), the authorization server often issues a long-lived "refresh token" alongside the access token. When the access token expires, the client sends the refresh token to the authorization server to automatically obtain a new access token (Figure 13.13) without requiring the user to log in again.

![Figure 13.13 A client app can use a refresh token to get a new access token.](figures/figure-13-13.png)

- **When to use**: Use refresh tokens to drastically improve user experience by preventing users from having to manually re-authenticate every time their short-lived access token expires.

> [!CAUTION]
> **The Danger of Stolen Refresh Tokens & Refresh Token Rotation**
> Because refresh tokens are long-lived, stealing one is disastrous—an attacker could use it to mint endless access tokens. 
> 
> To protect against this, modern Authorization Servers enforce **Refresh Token Rotation**. Every time a client uses a refresh token to get a new access token, the server *invalidates* the old refresh token and issues a brand new one. 
> 
> If a hacker steals a refresh token and uses it, the legitimate user will eventually try to use that same token. The server will detect that an already-invalidated token is being reused. Because reuse strongly indicates a breach, the server will instantly **revoke all tokens** (both access and refresh) associated with that session. This immediately locks the hacker out and forces the legitimate user to safely log in again.

## OpenID Connect (OIDC) vs OAuth 2

OIDC is a standardized identity layer built directly over the OAuth 2 authorization framework. While OAuth 2 governs authorization (delegated access), OIDC governs authentication (identity).

Think of OAuth 2 like electrical sockets worldwide: they all deliver power, but physical sockets look different and you need adapters when you travel. OpenID Connect restricts this liberty, introducing a standard framework with common rules.

**Key OIDC Additions:**
- **Standardized Scopes**: Introduces strictly defined scopes like `openid` and `profile`.
- **ID Token**: A secondary token returned to the client alongside the access token. The ID token strictly contains identity details about the authenticated user and client.
- **Terminology**: In OIDC, a "grant type" is frequently called a "flow," and the "authorization server" is typically called an "Identity Provider" (IdP).

## OAuth 2 Vulnerabilities

OAuth 2 is a framework; vulnerabilities typically stem from improper implementation rather than flaws in the specification itself:
- **Cross-Site Request Forgery (CSRF)**: Can occur if the client application fails to apply standard CSRF protection mechanisms.
- **Stealing Client Credentials**: Results from unprotected storage or transmission of client secrets.
- **Replaying Tokens**: Intercepted tokens can be reused maliciously. Mitigated by enforcing short token lifespans and securing transmission layers.
- **Token Hijacking**: Bad actors interfering with the authentication process to steal access or refresh tokens.

# Chapter 22: Secure Application Configuration

## Content Security Policy (CSP)

**The Simple Explanation:**
Think of CSP as a strict "guest list" for your website. Normally, a browser will blindly download and execute any script or image a web page asks for, making it easy for attackers to sneak in malicious code (like XSS). CSP is a rulebook sent by the server that tells the browser *exactly* which external resources (scripts, images, fonts) are allowed to load. If a resource isn't on the list, the browser blocks it.

**How it's implemented:** 
It is typically sent as an HTTP response header (`Content-Security-Policy`) but can also be placed in an HTML `<meta>` tag. *(Note: Legacy headers like `X-Content-Security-Policy` and `X-Webkit-CSP` are obsolete and should not be used).*

```html
<meta http-equiv="Content-Security-Policy" content="default-src 'self'; img-src data:" />
```

### Key Rules (Directives)
Directives are the specific categories of rules you set in your policy:
- **`default-src`**: The fallback rule. If you don't specify a rule for a specific type of resource (like images or fonts), it falls back to this.
- **`script-src`**: Specifically controls where JavaScript can be loaded from.
- **`frame-ancestors`**: Controls who is allowed to embed your site in an `<iframe>` (setting this to `'none'` is the primary defense against clickjacking).
- **`sandbox`**: Locks down the page, disabling pop-ups, plugins, and sometimes even scripts.
- **`report-uri`**: A URL where the browser will automatically send a JSON report if it blocks something (incredibly useful for debugging).

### Common Values (Source Lists)
- **`'self'`**: Only allow resources from the exact same domain as the current page.
- **`https:`**: Only allow resources loaded over a secure HTTPS connection.
- **`data:`**: Allow base64-encoded resources (like tiny inline images).
- **`'none'`**: Block everything for this rule.
- **`*`**: Wildcard (allow anything). *Avoid using this!*

*(Note: By default, CSP blocks all "inline" scripts like `<script>alert(1)</script>` or `onclick="..."`. You have to explicitly use `'unsafe-inline'` to allow them, which defeats the purpose of CSP and is highly discouraged).*

### Modern Defense: Strict CSP
Instead of trying to maintain a massive whitelist of allowed domains (which attackers often find ways to bypass), modern web applications use a "Strict CSP" based on cryptography.

#### 1. Nonce-based Strict CSP (Best for dynamic sites)
**How it works:** The server generates a random, unguessable token (`nonce`) every time the page loads. It puts this nonce in the CSP header AND adds it as an attribute to legitimate `<script>` tags. If an attacker injects a script, they won't know the random nonce, so the browser blocks it.
```http
Content-Security-Policy: script-src 'nonce-RANDOM_12345' 'strict-dynamic';
```
```html
<!-- Allowed: The nonces match -->
<script src="/my-app.js" nonce="RANDOM_12345"></script>

<!-- Blocked: Attacker doesn't know the nonce -->
<script src="http://evil.com/hack.js"></script> 
```

#### 2. Hash-based Strict CSP (Best for cached/static sites)
**How it works:** The developer creates a cryptographic hash (SHA-256) of the actual script code and puts that hash in the CSP header. The browser hashes the script before running it. If the hashes don't match (meaning the script was altered), it is blocked.
```http
Content-Security-Policy: script-src 'sha256-B2yPHKaXnvFWtRChIbabYmUBFZdVfKKXHbWtWidDVF8='
```

### Example of a Good Policy
```http
Content-Security-Policy:
  default-src: 'self';                           /* Only trust our own domain by default */
  script-src: 'self' 'nonce-{RANDOM}' 'strict-dynamic'; /* Use random nonces for scripts */
  frame-ancestors: 'none';                       /* Prevent clickjacking */
  img-src: data: https:;                         /* Allow secure images and data URIs */
  report-uri: https://reporting.megabank.com     /* Log any violations here */
```

## Cross-Origin Resource Sharing (CORS)
**How it works:** A browser security mechanism that relaxes the Same Origin Policy (SOP). SOP is a critical concept that restricts a web application to only making network calls within its own origin. Without SOP, a malicious script on one tab could execute privileged actions (e.g., purchases) on another tab where the user is authenticated, leading to Cross-Site Request Forgery (CSRF). CORS allows specific, intentional cross-origin network requests (`fetch` or `XMLHTTPRequest`) to complete without significantly degrading this secure posture.

### Types of CORS Requests

#### Simple CORS Requests
**How it works:** Limited to `POST`, `HEAD`, or `GET`. Can only contain safe-listed headers (`Accept`, `Accept-Language`, `Content-Language`, `Content-Type`). `Content-Type` must be `application/x-www-form-urlencoded`, `multipart/form-data`, or `text/plain`. Finally, the request must not make use of advanced network request features like event listeners attached to the `XMLHTTPRequest` object. The browser automatically attaches an `origin` header.
**Server response:** Must include `access-control-allow-origin: <source-origin>` matching the browser's origin.
**When to use:** Sufficient for the most common network requests that do not modify state or utilize custom headers.

#### Preflighted CORS Requests
**How it works:** For requests not meeting simple criteria. The browser sends an `HTTP OPTIONS` request ("preflight check") before the actual request, containing `origin`, `access-control-request-method`, and `access-control-request-headers`. If the server doesn't respond with matching permissions, a CORS Policy error is thrown.
**When to use:** Enforced by browsers for any unusual or state-changing requests.

### Implementing CORS (Node.js/Express)
```javascript
const express = require('express');
const app = express();
const cors = require('cors');

const corsOptions = {
  origin: 'https://mega-bank.com'
};

app.get('/users/:id', cors(corsOptions), function (req, res, next) {
  res.sendStatus(200);
});

app.listen(443, function () {
  console.log('listening on port 443')
});
```
*Note: Always apply the principle of least privilege; allowlist specific origins instead of using wildcards (`*`).*

## Additional Security Headers

### Strict Transport Security (HSTS)
**How it works:** Informs the browser that a web page should only be loaded over HTTPS, upgrading future HTTP requests automatically. Mitigates man-in-the-middle attacks caused by initial HTTP redirects.
**Parameters:** `max-age` (seconds to remember config), `includeSubDomains` (applies to all subdomains), `preload` (Google currently runs a preload list to which your application can be submitted; Firefox and Chrome will check this list prior to loading a page).

```http
Strict-Transport-Security: max-age=<expire-time>; includeSubDomains; preload
```

### Cross-Origin-Opener Policy (COOP)
**How it works:** Modifies the sharing of browsing context (current page, iframes, tab data). Prevents unintended information leakage by restricting navigation back to the opening context.
**Values:** `unsafe-none` (default), `same-origin-allow-popups`, `same-origin` (most secure).

### Cross-Origin-Resource-Policy (CORP)
**How it works:** Mitigates side-channel timing attacks (e.g., Spectre) by restricting where a resource can be accessed within the browser.
**Values:** `same-origin` (most secure), `same-site`, `cross-origin` (least secure).

### Headers with Security Implications
- **`X-Content-Type-Options`**: Setting to `nosniff` prevents MIME-sniffing, stopping browsers from converting non-executable files to executable.
- **`Content-Type`**: Indicates the content type to the client for correct interpretation.

### Legacy Security Headers
- **`X-Frame-Options`**: Replaced by CSP `frame-ancestors`.
- **`X-XSS-Protection`**: Removed. Replaced by default CSP functionality.
- **`Expect-CT`**: Removed. TLS certificates generated after May 2018 support CT by default.
- **`Referrer-Policy`**: Modern browsers limit referrer information by default. Can be disabled in legacy browsers by setting the header to `strict-origin-when-cross-origin`.
- **`X-Powered-By`**: Fingerprinting vector. Disable to prevent information disclosure.
- **`X-Download-Options`**: IE-only; no longer needed.

## Securing Cookies
Cookies are the primary method for authenticating users on a per-request basis. Set via the `Set-Cookie` HTTP header.

### Cookie Attribute Flags
- **`Path`**: Restricts cookies to specific paths (e.g., `Path=/website`).
- **`Secure`**: Ensures cookies are only sent over unencrypted network calls (HTTPS only). Prevents man-in-the-middle attacks.
- **`Expires`**: Discards the cookie after a specified date.
- **`HttpOnly`**: Prevents JavaScript code from accessing the cookie, mitigating XSS risks.
- **`SameSite`**: Accepts `Lax`, `Strict`, or `None`. Prevents CSRF attacks. Usually set to `Strict`.
- **`Domain`**: Should generally be avoided. Loosens restrictions to subdomains, potentially introducing account takeover (ATO) risks if subdomains are vulnerable.

```http
Set-Cookie: auth_token=abc123; Secure; HttpOnly; SameSite=Strict;
```

### Testing Cookies
Cookies are typically tested via interception tools like Burp Suite or ZAP, which act as HTTP proxies to perform man-in-the-middle attacks on development/staging environments. When using these over HTTP, local applications must be configured with a TLS certificate and a decryption key provided to the proxy. Alternatively, cookies can be inspected directly in the browser's Developer Tools (F12 -> Application tab -> Storage -> Cookies) or via the JavaScript console using `document.cookie`.

## Framing and Sandboxing

### Traditional Iframe
**How it works:** Creates a separate DOM and JavaScript context that is sandboxed to prevent leakage to the main frame. Communcation is limited to the `postMessage` API.
**When to use:** When you need a highly isolated environment for third-party content and can tolerate UI constraints, code duplication, and potential performance degradation.

```html
<iframe src="https://other-website.com" sandbox></iframe>
```
*The `sandbox` attribute forces extra isolation layers:*
- *Treats content as a separate origin*
- *Blocks form submissions*
- *Blocks script execution*
- *Disables APIs*
- *Prevents links from accessing other browsing contexts*
- *Prevents the use of plug-ins*
- *Prevents the iframe from viewing the parent browsing context*
- *Disables autoplay*

### Web Workers
**How it works:** Separate isolated threads inside the browser capable of same-origin script execution without DOM access. Can make in-domain and cross-origin HTTP requests. Communication via `postMessage` or `MessageChannel` APIs.
**When to use:** When you require more isolation than running third-party code in the same execution context, but don't need DOM interaction.

```javascript
if (window.Worker) {
  const myWorker = new Worker('code.js');
  myWorker.terminate();
}
```

### Subresource Integrity (SRI)
**How it works:** A verification method ensuring third-party code has not been modified. Loads code with an `integrity` attribute containing a base64-encoded cryptographic hash (SHA-256, 384, or 512). The browser compares hashes before execution.
**When to use:** When integrating static third-party JavaScript vendors and you want to ensure the code remains untampered.

```html
<script src="other-website.com/stuff.js"
  integrity="sha384-NmJhYmViMzNiMmU1NzllMDMyODdl"
  crossorigin="anonymous"></script>
```

### Shadow Realms
**How it works:** An upcoming JavaScript feature that creates a new execution context with its own global objects, intrinsics, and built-ins. Takes up less memory than iframes and allows synchronous code execution.
**When to use:** For next-generation browser JavaScript sandboxing without the asynchronous limitations and UI interference of iframes.

```javascript
const shadowRealm = new ShadowRealm();
const doSomething = await shadowRealm.importValue('./file.js', 'redDoSomething');
doSomething();
```

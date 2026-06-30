# Chapter 34. Defense Against Client-Side Attacks

## Defending Against Prototype Pollution
Prototype pollution allows attackers to pollute an object without direct access by modifying related objects in the JavaScript prototypal inheritance hierarchy.

### Key Sanitization
**How it works:** Validate and sanitize all user-provided keys against an allowlist prior to merging or manipulating JavaScript objects.
**When to use:** Whenever the application accepts a static set of expected keys. Not viable if flexible, unpredictable user input is required.

```javascript
const allowedKeys = ["street", "city", "state", "firstName", "lastName"];

const isKeyValid = function(key) {
  if (!allowedKeys.includes(key)) {
    return false;
  } else {
    return true;
  }
};

const updateUserData = function(data) {
  let isValid = true;
  for (const [key, value] of Object.entries(data)) {
    if (!isKeyValid(key)) {
      isValid = false;
    }
  }
  
  if (!isValid) {
    console.error("an invalid key was found!");
  } else {
    updateUserData(data);
  }
};
```

### Prototype Freezing
**How it works:** Use JavaScript's built-in `Object.freeze()` function to make objects immutable. A frozen object cannot have its keys or values updated for the remainder of the browsing session (until the page is refreshed).
**When to use:** Use sparingly. Excellent for defending specific objects from pollution, but heavily diminishes legitimate object functionality. Avoid bulk freezing JavaScript or DOM APIs, as this will break normal application functionality.

### Null Prototypes
**How it works:** Pass `null` into the object constructor (`Object.create(null)`) to instantiate an object without a prototype. This cuts off the prototype chain, making it impossible to walk up and pollute parent objects.
**When to use:** During instantiation of new objects when you suspect they may be subject to prototype pollution attacks later in their life cycle.

```javascript
// traditional object creation
const myObj1 = { username: "testUser1" }

// manual object constructor invocation inheriting from null
const myObj2 = Object.create(null);
myObj2["username"] = "testUser2";
Object.getPrototypeOf(myObj2); // null
```

## Defending Against Clickjacking
Note: `X-Frame-Options` headers are considered obsolete by major browsers and are no longer an effective defense.

### Frame Ancestors (CSP)
**How it works:** Implement the `frame-ancestors` directive in the Content-Security-Policy (CSP) header or meta tag. Setting it to `none` prevents any web page from loading inside an iframe.
**When to use:** The standard, easiest, and most effective first-line defense against clickjacking.

```http
Content-Security-Policy: frame-ancestors 'none';
```
To loosen the policy and allowlist specific domains:
```http
Content-Security-Policy: frame-ancestors subdomain.my-website.com
```

To allow framing only by the application itself:
```http
Content-Security-Policy: frame-ancestors 'self'
```

### Framebusting
**How it works:** JavaScript functions (framekillers) that detect if the website is being framed within an unauthorized iframe and either block or rapidly unload the application code.
**When to use:** As an alternative mitigation if supporting older browsers that lack CSP `frame-ancestors` support, or in rare framing use cases where CSP is not an acceptable solution.

CSS (Ensure it loads in the `<head>`):
```css
html {
  display:none;
}
```

JavaScript (Import into the `<body>`):
```javascript
if (self == top) {
  document.documentElement.style.display = 'block';
}
```

## Defending Against Tabnabbing

### Cross-Origin-Opener Policy (COOP)
**How it works:** A CSP policy that determines which websites are given access to a `window` object reference when opened in a hyperlink.
**When to use:** The first-line solution for preventing tabnabbing on new applications. Should be set by default.

```http
Cross-Origin-Opener-Policy: same-origin
```

### Link Blockers
**How it works:** Use the `noopener` and `noreferrer` HTML attributes on hyperlinks. `noopener` sets the `window.opener` reference of the target site to `null`, while `noreferrer` blocks access to referrer information.
**When to use:** When generating dynamic links, or for large websites spanning multiple domains where a relaxed COOP policy is needed.

```html
<a href="malicious-website.com" rel="noopener noreferrer">click me</a>
```

## Isolation Policies (Fetch Metadata)
**How it works:** Newer browsers send fetch metadata headers (`Sec-Fetch-*`) with every request to provide the server with context about how an application is being requested and where it will load. The server can combine these values to create powerful custom mitigations.
**When to use:** As a strong defense-in-depth mechanism. Currently supported mainly by recent Chrome and Firefox builds, so it must be paired with other mitigations.

**Relevant Headers:**
- `Sec-Fetch-Site`: Request origin (`same-origin`, `same-site`, `cross-site`, `none`).
- `Sec-Fetch-Mode`: Mode of request (`same-origin`, `no-cors`, `cors`, `navigate`).
- `Sec-Fetch-Dest`: Where content will be loaded (e.g., `audio`, `audioworklet`, `embed`, `font`, `frame`, `manifest`, `object`, `paintworklet`, `report`, `script`, `serviceoworker`, `sharedworker`, `style`, `track`, `video`, `xslt`).
- `Sec-Fetch-User`: Indicates if the request was user-initiated (`1`) or script-initiated (`null`).

**Example: Server-side logic to block framing (clickjacking prevention)**
```javascript
app.get('/index.html', function(req, res, next) {
  if (req.headers["Sec-Fetch-Dest"]) {
    const dest = req.headers["Sec-Fetch-Dest"];
    if (dest === "frame") {
      return res.sendStatus(400);
    } else {
      return res.sendFile("/index.html");
    }
  }
});
```

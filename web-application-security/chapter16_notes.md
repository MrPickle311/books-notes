# Chapter 16: Client-Side Attacks

## Overview
Client-side attacks target the browser client directly, requiring no vulnerable web server or server-side network calls.
- **Client-Targeted Attacks**: Vulnerabilities affecting both client and server (e.g., Regular Expression Denial of Service - ReDoS).
- **Client-Specific Attacks**: Vulnerabilities existing solely on the client (e.g., DOM-based XSS), where both sink and source occur in the browser (e.g., `window.location.hash` rendered via `eval()`).

**Advantages for Attackers**:
- Highly stealthy; exploitation occurs without server awareness or network logging.
- Offline payload development; attackers can download the client-side app and test millions of payloads locally without detection.

---

## Prototype Pollution Attacks

**How it works (The Simple Explanation)**:
In JavaScript, objects inherit properties and methods from a "prototype" (think of it as a master blueprint). If you ask an object for a property it doesn't have, it looks at its blueprint to see if it's there. 

**Prototype Pollution** happens when an attacker finds a way to modify that shared master blueprint (usually `Object.prototype`). By adding or changing a property directly on the blueprint, *every single object* in the entire application suddenly inherits that injected property. It's like poisoning a city's water reservoir at the source—everyone downstream is affected.

**Technical Explanation**:
JavaScript uses prototypal inheritance. When an object property is accessed, the interpreter traverses up the "prototype chain" (e.g., `obj -> obj.__proto__ -> Object.prototype`) looking for it. An attacker exploiting Prototype Pollution targets vulnerable functions to overwrite or inject properties into these base prototypes. Because almost all objects inherit from `Object.prototype` by default, polluting it compromises inaccessible objects across the application.

**Common Attack Vectors (Where to look)**:
This attack is often used when direct Cross-Site Scripting (XSS) is unavailable. It typically targets functions that blindly manipulate nested objects using user-controlled input, such as:
- Recursive merge operations (e.g., vulnerable versions of `lodash.merge` or the `merge` npm package)
- Deep object cloning
- Path assignment (setting properties based on a string path)

Attackers exploit these by passing malicious JSON containing keys like `__proto__` or `constructor.prototype`.

**Technical Examples**:

### 1. How JavaScript Looks Up Properties (The Prototype Chain)
If we have a `Bob` object created from a `Technician` class, JavaScript links their blueprints together.
```javascript
// Bob's immediate blueprint is Technician
Bob.__proto__ === Technician.prototype; // true

// Technician's blueprint is the global Object
Bob.__proto__.__proto__ === Object.prototype; // true
```
*When you call a function like `Bob.toString()`, JavaScript searches up this chain:*
1. Does `Bob` have a custom `toString()`? **No.**
2. Does `Technician` have `toString()`? **No.**
3. Does the global `Object` have `toString()`? **Yes.** (It executes the default one).

### 2. A Simple Pollution Example
If a function carelessly writes data to a prototype, an attacker can modify a parent blueprint, changing behavior for *all* children.
```javascript
// An insecure function that takes user input and writes directly to a prototype
function addTechnicianFunctionality(userInput) {
  Technician.prototype[userInput.name] = userInput.data;
}

// The attacker sends a malicious payload overwriting a standard function
const maliciousInput = {
  name: "toString",
  data: function() { console.log("You have been hacked!"); }
};
addTechnicianFunctionality(maliciousInput);

// Now, ANY Technician object calling toString() executes the attacker's code!
Bob.toString(); // Prints: "You have been hacked!"
```

### 3. Real-World Attack (Exploiting a Vulnerable Merge Function)
In the real world, attackers target vulnerable deep-merge functions. By passing a JSON payload with `__proto__`, they can trick the function into climbing the prototype chain and modifying the global `Object.prototype`.

```javascript
// 1. We have a regular user object that is NOT an admin
const user = { username: "Alice" }; 
console.log(user.isAdmin); // undefined

// 2. The attacker sends a malicious JSON payload
// Note: JSON.parse is used because raw JavaScript objects drop the __proto__ key, but JSON keeps it as a string
const maliciousPayload = JSON.parse('{"__proto__": {"isAdmin": true}}');

// 3. The vulnerable merge function blindly copies properties, accidently writing to Object.prototype
vulnerableMerge({}, maliciousPayload);

// 4. Now EVERY object in the application inherits isAdmin = true!
console.log(user.isAdmin); // true (Polluted!)
console.log({}.isAdmin);   // true (Even brand new objects are polluted!)
```

*Bypassing basic filters:*
If a security filter blocks the word `__proto__`, attackers can use `constructor.prototype` to achieve the exact same global pollution:
```javascript
const bypassPayload = JSON.parse('{"constructor": {"prototype": {"isAdmin": true}}}');
vulnerableMerge({}, bypassPayload);
```

**Archetypes of Prototype Pollution**:
- **Denial of Service (DoS)**: Interfering with application execution by altering property types (e.g., injecting a float where an integer is expected).
- **Property Injection**: Modifying expected values to invoke unintended client-side network or function calls.
- **Remote Code Execution (RCE)**: Upgrading to XSS (client-side) or true server-side RCE (Node.js) by polluting data that is passed to a script execution sink like `eval()` or `DOMParser.parseFromString()`.

---

## Clickjacking Attacks

**How it works**:
An attack where malicious UI elements are overlaid or merged with legitimate UI elements, transparently tricking the browser into sending input to a malicious endpoint. Commonly implemented by loading the target website inside an invisible iframe directly underneath the attacker's cursor or buttons.

**When to use / Attack Vectors**:
Deployed against applications lacking framing controls (e.g., missing `X-Frame-Options` or Content Security Policy `frame-ancestors`). Used to steal credentials, initiate unauthorized transactions, or escalate privileges. A classic historical example is the 2008 Adobe Flash clickjacking exploit (discovered by Robert Hansen and Jeremiah Grossman), which tricked users into passing clicks through to an invisible Flash settings iframe, granting the attacker microphone and camera access.

**Exploit Example**:
```html
<div id="clickjacker">
  <span id="fake_button">click me</span>
</div>
<iframe id="target_website" src="target-website.com"></iframe>
```
```css
/* Attacker CSS to hide the target website but allow clicks to pass through to it */
#target_website { opacity: 0; }
#fake_button { pointer-events: none; }
```

---

## Tabnabbing and Reverse Tabnabbing

**How it works**:
A combination of phishing and redirect attacks that abuse the browser's DOM `window` API to overwrite or redirect the contents of a tab.
- **Traditional Tabnabbing**: The attacker's website opens a new tab pointing to a legitimate site. The attacker retains a reference to the opened `window` object and later redirects it to a phishing page.
- **Reverse Tabnabbing**: A legitimate website opens a malicious link in a new tab. The newly opened malicious tab uses the `window.opener` property to redirect the original, legitimate tab to a phishing page.

**When to use / Attack Vectors**:
Used to execute highly convincing phishing campaigns since the user has already verified the initial legitimacy of the targeted tab.

**Key Terms to Understand**:
- **`window.open(url)`**: The JavaScript command used to open a brand new browser tab. When a script runs this, the browser keeps a "connection" between the original tab and the newly opened tab, allowing the opening script to control the new tab later (used in Traditional Tabnabbing).
- **`window.opener`**: A property that exists inside the *newly opened tab*. It acts as a direct bridge back to the original tab that created it. If Tab A opens Tab B, then inside Tab B, `window.opener` refers to Tab A. (Note: This bridge also exists if a user clicks a regular HTML link `<a target="_blank">` unless explicitly secured).
- **`window.opener.location.replace(url)`**: The specific attack payload used in Reverse Tabnabbing. 
  - `window.opener`: "Reach back to the original tab that opened me."
  - `.location`: "Access that original tab's URL/navigation system."
  - `.replace("...")`: "Delete the current page history and load this fake website instead." (Using `.replace()` instead of `.href` prevents the user from using the browser's "Back" button to escape the trap).

**Technical Implementations**:

### 1. Traditional Tabnabbing
**The Scenario:** A user visits the attacker's website. The attacker's script opens a legitimate site in a new tab. Once the user trusts that new tab, the attacker quietly redirects it to a phishing page.
```javascript
// 1. The attacker's site opens a new tab to a real, trusted site (e.g., a bank)
const trustedTab = window.open("https://real-bank.com");

// 2. The user switches to the new tab, sees it's really the bank, and trusts it.
// 3. While the user is distracted, the attacker redirects that tab to a fake login page.
setTimeout(() => {
  trustedTab.location.replace("https://fake-bank-login.com");
}, 10000); 
```

### 2. Reverse Tabnabbing (The DOM API Attack)
**The Scenario:** A user is on a legitimate website (like a forum) and clicks a link posted by an attacker. The link opens in a *new* tab. That new tab then uses JavaScript to secretly redirect the *original* tab.
```javascript
// This code runs on the attacker's site (in the newly opened tab)

// 'window.opener' is a direct reference back to the legitimate tab that spawned this one.
// The attacker uses it to redirect the original tab to a phishing page.
if (window.opener) {
  window.opener.location.replace("https://fake-forum-relogin.com");
}
// The trap is set: When the user closes this attacker tab and goes back to the forum, 
// they are actually looking at a fake login screen asking for their credentials!
```

### 3. The Vulnerable HTML Link (Why Reverse Tabnabbing Works)
Reverse tabnabbing happens because the legitimate site creates links insecurely. Using `target="_blank"` by itself exposes the `window.opener` object to the new tab.
```html
<!-- VULNERABLE: Opens a new tab and gives it control over the current tab -->
<a href="https://attacker-website.com" target="_blank">Click here for cute cats</a>

<!-- SECURE: The 'rel="noopener"' attribute severs the connection, making window.opener null -->
<a href="https://attacker-website.com" target="_blank" rel="noopener noreferrer">Click here for cute cats</a>
```

### 4. Reverse Tabnabbing via Iframes
**The Scenario:** Instead of a link, a legitimate site embeds an attacker-controlled page using an `<iframe>`. The iframe can hijack the main page.
```javascript
// This code runs inside the embedded malicious iframe

// 'window.parent' gives the iframe access to the main page hosting it.
// The attacker redirects the entire parent window to their phishing site.
window.parent.location.replace("https://fake-login-page.com");
```
*(Note: To prevent this, developers must use the `sandbox` attribute on iframes to restrict top-level navigation.)*

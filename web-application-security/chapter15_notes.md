# Chapter 15: Attacking Data and Objects

Modern programming languages separate capabilities into **data** (objects) and **actions** (functions).

## First-Class Citizens
A programming language design concept where an entity can be assigned, reassigned, modified, passed as an argument, and returned from a function. Storing data as first-class citizens introduces powerful capabilities but also exploitable vectors.

## Mass Assignment (Autobinding / Object Injection)
A vulnerability allowing attackers to change unintended object fields by passing additional, unvalidated keys within an object to a function. Also known historically as *autobinding attack* (in Spring MVC and ASP.NET frameworks) and *object injection* (in legacy PHP).

### How it works
Attackers intercept or forge a data payload sent to a server-side API, appending additional key-value pairs (e.g., `isAdmin: true`). If the backend updates the database object without explicitly validating the provided keys against an allowed list, the unintended fields are overwritten.

### When to use [Attack Vector]
Exploited in systems that frequently pass state-related data objects (e.g., user state in video games, dynamic web forms).

### Example
**Vulnerable Endpoint & Helper:**
```javascript
app.post("updatePlayerData", function(req, res, next) {
  // if client sent back player state data, update in the database
  if (!!req.body.data) {
    db.update(session.currentUser, req.body.data);
    return res.sendStatus(200); 
  }
});

const update = function(data) {
  for (const [key, value] of Object.entries(data)) {
    database.upsert({ `${key}`: `${value}` })
  }
}
```

**Malicious Payload:**
```json
{
  "playerId": 123,
  "playerPosition": { "x": 125, "y": 346 },
  "playerHP": 90,
  "isAdmin": true 
}
```
*Because `update()` lacks key validation, `isAdmin: true` is persisted.*

## Insecure Direct Object Reference (IDOR)
A vulnerability where objects on a server are directly accessible via user-supplied parameters (e.g., URL query parameters, HTTP POST bodies, URL fields) without proper authorization checks. Spotlighted in the 2007 OWASP Top 10, these remain simple to exploit.

### How it works
An endpoint takes an identifier from user input and uses it to directly reference a backend object or file. An attacker modifies this identifier to point to an object they do not own or have permission to access. Tools like Burp Suite or ZAP are often used to intercept and alter requests before they hit the server.

### When to use [Attack Vector]
Exploited against endpoints utilizing direct identifiers (like database IDs or filenames) without verifying user ownership or role-based access control.

### Example
**Vulnerable Endpoint:**
```javascript
app.get('/files/:id', function(req, res, next) {
  return res.sendFile(`/filesystem/files/${req.params.id}`);
});
```
*An attacker requests `other-report-card.txt` instead of `my-report-card.txt` (e.g., intercepting `HTTP GET https://mywebsite.com/files/my-report-card.txt`) to bypass access controls and escalate privileges.*

## Serialization Attacks
**Serialization** converts in-memory objects into formats (JSON, XML, YAML, base64) for transmission or storage. **Deserialization** reverts it to raw data memory addresses and pointers.

### Web Serialization Explained
An important concept is that the serialized format should be easy to deserialize or store. For example, an in-memory `farmer` object might look like this in JSON:
```json
{
  "name": "Joe Carrot",
  "age": 62,
  "location": "Montana, USA",
  "crops": {
    "wheat": {
      "measurement": "acres",
      "amount": 100
    }
  }
}
```
Other popular formats for data serialization used by web applications include XML, YAML, and base64 (a format for serializing text in binary).

### Attacking Weak Serialization
Targeting vulnerabilities in libraries used to serialize/deserialize data to achieve Remote Code Execution (RCE) or Cross-Site Scripting (XSS).

### How it works
If a serialization library fails to properly handle input characters (like escaping quotes), an attacker can craft a payload that breaks out of the expected data structure. When this malformed serialized object is processed or evaluated (e.g., via `eval()`), the injected payload executes as code.

### When to use [Attack Vector]
Exploited against applications utilizing vulnerable serialization libraries that fail to safely handle untrusted input structure generation.

### Attack Methodology
1. Find a function performing serialization.
2. Read the function carefully or test it with common payloads.
3. Identify a failure in proper data serialization (e.g., unescaped characters).
4. Craft a payload capable of script execution.
5. Call the function with the payload to obtain RCE (server) or XSS (client).

### Example
Vulnerability in `serialize-javascript` (npm library versions <= 3.0.9):
Inputting a payload such as `{"foo": /1"/, "bar": "a\"@__R-<UID>-0__@"}` results in the serialized JSON `{"foo": /1"/, "bar": "a\/1"/}`. The structure of the resulting JSON object escapes quotes improperly. A proof of concept such as `eval('('+ serialize({"foo": /1" + console.log(1)/i, "bar": '"@__R-<UID>-0__@'}) + ')');` leads to code execution due to the serializer's inability to format the string into properly escaped JSON.

*Note: Since most websites do not implement their own serializers, scanning for open-source serializers with known vulnerabilities is an advanced but effective technique for finding these flaws in the wild.*
